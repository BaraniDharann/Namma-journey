# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Telegram driver dispatch — trip cards pushed to drivers in Telegram with inline accept/reject,
  webhook signature verification, and account linking (`TELEGRAM_ENABLED=false` disables it)
- Per-driver booking rejection tracking, so a rejected trip is not re-offered to the same driver
- Flyway-managed schema with a versioned baseline migration
- Redis distributed caching, with automatic fallback to in-process Caffeine when Redis is unreachable
- Full-stack Playwright E2E suite covering auth, booking, allocation and live tracking
- Backend unit test suite
- Secret scanning and a nightly E2E job in CI

### Changed
- **Schema is now owned by Flyway.** `spring.jpa.hibernate.ddl-auto` moved from `update` to
  `validate` — the application refuses to start if entities and schema disagree
- Legacy hand-run SQL scripts moved to `db/legacy-scripts/` as history; they must not be run
- Hardened error handling and authentication across backend services
- Rate limiting now resolves client IP through a configurable trusted-proxy list
- Local development scripts moved from the repository root into `scripts/`
- README rewritten as a full-stack entry point; the endpoint reference moved to
  `docs/API_REFERENCE.md`
- **Documentation reorganised.** The repository root went from 55 markdown files to 9. Build
  logs, superseded migration notes and IDE-specific fix guides were removed; the remaining
  reference material moved into `docs/api/`, `docs/guides/` and `docs/postman/`
- `.env.example` now documents every variable the application reads, including CORS, caching,
  rate limiting and the owner bootstrap secret
- Playwright videos and E2E screenshots are no longer tracked in git (21 MB); they are
  regenerated on every run

### Fixed
- Frontend login flows for user, driver and owner roles
- Live tracking map and driver location streaming reliability
- **Live tracking under Docker Compose.** The SPA opened its SockJS connection against
  `localhost:8080` rather than its own origin, and nginx proxied `/api/` but had no `/ws/` rule
  and no `Upgrade` headers, so the handshake never reached the backend. The client is now
  same-origin by default, and nginx and the Vite dev server both carry `/ws/` through
- `.env.example` pointed `DB_URL` at port 5433 and database `postgres`, matching neither the
  README quick start nor the Compose service
- `DEPLOYMENT_CHECKLIST.md` required three `SENDGRID_*` variables the project has never used, told
  deployers to rely on `ddl-auto: update` after the schema had moved to Flyway, and published a
  default owner password

### Security
- `JWT_SECRET` no longer has a fallback default — the application will not start without it
- Placeholder values substituted for real credentials in `.env.example`
- Personal contact details (name, email address and mobile number) removed from documentation
  and E2E test fixtures, which now use `demo.user@example.com`
- **`OWNER_UPI_ID` no longer has a default.** A personal UPI ID was hardcoded as the fallback in
  `PaymentService`, `TestController` and `application.yml`, so any deployment that did not set the
  variable collected customer payments into that account. The application now refuses to start
  without it, matching how `JWT_SECRET` already behaved
- `/actuator/**` is no longer `permitAll()`. Only `/actuator/health`, its sub-paths and
  `/actuator/info` are anonymous; `metrics`, `prometheus` and `caches` now require authentication
  — `/actuator/caches` accepts DELETE, so cache eviction was anonymously reachable
- Removed `TestController`, a debug endpoint that echoed the owner UPI ID back to the caller
- Removed the remaining real personal email address and mobile number from committed test
  fixtures, and deleted the superseded `e2e-test/` suite that carried them
- Resolved the two critical `websocket-driver` advisories reached through `sockjs-client`
- Recorded the two remaining `npm audit` findings, and why they are accepted, in `SECURITY.md`
- **Payment settlement is now idempotent.** `markCashReceived` and `verifyPayment` both accepted
  an already-settled payment and re-ran the whole completion path — re-completing the booking,
  re-notifying the passenger and evicting the revenue caches — so a single fare could be counted
  into owner revenue as many times as the endpoint was called
- **Cash collection is checked against the fare.** `amountReceived` was carried on the request and
  never read: a driver could report collecting 1 rupee on a 2500 rupee trip and the booking still
  settled as paid in full
- The owner verify endpoint refuses `CASH` payments, which previously let an owner settle a cash
  fare without the driver ever confirming the money changed hands
- Every settlement records the actor in the new `payments.verified_by` column
- `payments.booking_id` is now `UNIQUE`. Two concurrent initiate-payment calls could both insert,
  after which every read through `findByBookingId` threw `NonUniqueResultException` and the
  booking became permanently unpayable (`V4__harden_payments.sql`, which de-duplicates first)
- The UPI deep link percent-encodes every interpolated value, so a payee address containing `&`
  can no longer inject its own `am=` parameter, and formats the amount with `Locale.ROOT` — on a
  comma-decimal host a 2500.00 fare was rendering as `am=2500,00`
- Payment and QR endpoints get their own tight per-account rate limit (`RATELIMIT_PAYMENT`,
  default 15/min) instead of sharing the 300/min signed-in budget, since each call renders a QR
- An unsupported or missing `paymentMethod` is a 400 rather than a 500 raised inside the transaction
- `SECURITY.md` documents what the payment flow does and does not guarantee, including the manual
  verification trust boundary
- Spring Boot 3.4.1 → 3.4.13

## [1.0.0] - Initial

- User, driver and owner authentication with email OTP and Google OAuth
- Travel booking with per-kilometre and hourly pricing
- Owner-managed driver onboarding with document verification
- UPI payment collection flow
- Reviews and ratings
- Live driver location tracking over WebSocket
