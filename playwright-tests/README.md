# Namma Journey — end-to-end test suite

Playwright suite covering the REST API, the React frontend and the live driver-tracking
pipeline against a locally running stack.

## What it needs

| Component | Where | Notes |
|---|---|---|
| Postgres | `localhost:5432/namma_journey` | credentials read from the repo-root `.env` |
| Backend | `localhost:8080` | start with `--otp.test.mode=true` (see below) |
| Frontend | `localhost:5173` | `cd frontend && npm run dev` |
| Redis | optional | the backend falls back to Caffeine; `--app.cache.type=caffeine` skips it |

```bash
# backend — OTP test mode stops real e-mail being sent to synthetic test addresses.
# trusted-proxies is REQUIRED for this suite: X-Forwarded-For is only believed from a
# configured proxy, and every context here fakes its own client IP (see "Rate limiting").
# Both loopback forms are needed — a connection to "localhost" arrives as ::1, not 127.0.0.1.
java -jar target/travel-booking-platform-1.0.0.jar --otp.test.mode=true --app.cache.type=caffeine \
  --app.ratelimit.trusted-proxies=127.0.0.1,::1

# frontend
cd frontend && npm run dev

# tests
cd playwright-tests && npx playwright test
npx playwright show-report
```

## How accounts are created

`global-setup.ts` creates a **brand-new user and a brand-new driver on every run** through the
real endpoints, and writes them to `test-data/accounts.json`:

- **User** — `POST /api/auth/otp/send` → the OTP is read from the `otps` table → `POST
  /api/auth/user/signup` → `POST /api/auth/user/login`.
- **Driver** — owner `POST /api/owner/drivers` → the generated password is only ever sent by
  e-mail, so the suite writes a known bcrypt hash to the `drivers` row → the genuine first-login
  flow runs: `driver/login` (returns `firstLogin: true` + OTP) → `driver/verify-otp` →
  `driver/login` again for a token.
- **Owner** — an existing seeded account whose password we don't hold, so its JWT is minted with
  the backend's own secret.

Direct DB access (`helpers/db.ts`) exists only for the two values that never leave the server:
the OTP and the driver's generated password.

## Rate limiting

`RateLimitingFilter` budgets anonymous callers per IP (100/min), `/api/auth` per IP (10/min)
and **signed-in callers per account** (300/min). `X-Forwarded-For` is only believed when the
request arrives from an address listed in `app.ratelimit.trusted-proxies` — otherwise a caller
could mint a fresh identity per request and walk through the auth limit. That is why the suite
requires the backend to be started with the loopback addresses trusted. Every API context and
browser context then sends its own synthetic client IP. In the browser this is applied via route interception **scoped to localhost only** —
setting it as a blanket header adds a custom header to third-party requests (OSRM routing, map
tiles) and gets them CORS-blocked. Rate limiting itself is asserted in `06-security.spec.ts`
using two pinned IPs.

## Layout

| File | Covers |
|---|---|
| `01-public-api.spec.ts` | health, public pricing/reviews/packages, place search, route preview, anonymous rejection |
| `02-auth.spec.ts` | user + driver login, duplicate/invalid/superseded OTP, tampered credentials, reset-OTP non-enumeration |
| `03-user-api.spec.ts` | booking CRUD + validation, payments, profile, reviews, package bookings, notifications |
| `04-driver-api.spec.ts` | profile, availability, the assign → accept → start → end → settle lifecycle, location round-trip |
| `05-owner-api.spec.ts` | bookings, pricing, driver management, revenue, reviews, payments, travel packages |
| `06-security.spec.ts` | IDOR, role crossing, driver impersonation, JWT tampering/expiry, rate limiting |
| `07-frontend.spec.ts` | real login forms, route guards, and a clean-load sweep of all 18 authenticated routes |
| `08-live-tracking.spec.ts` | the driver → backend → user tracking pipeline through both real UIs |

Specs run serially (`workers: 1`) because they share the two created accounts and walk a booking
through its status machine.

## Business rules the tests encode

Discovered while building the suite; the tests assert these deliberately:

- `POST /api/owner/bookings/{id}/assign-driver` moves the booking straight to **CONFIRMED**.
- A driver cannot be assigned to a trip whose dates overlap one they are already on (HTTP 409),
  so every booking the suite creates gets its own date window.
- Payment requires a confirmed booking.
- `end-trip` raises a **UPI** invoice and leaves the booking **STARTED**; the trip only becomes
  **COMPLETED** when the owner verifies that payment. `cash-payment` is rejected on a UPI invoice.
- Live location is stored in memory per booking id, so it is lost on backend restart and returns
  404 until the driver publishes the first fix.
