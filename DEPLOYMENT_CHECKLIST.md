# Deployment checklist

Work through this before putting Namma Journey in front of real customers. It assumes you have
already run the project locally — see [README.md](README.md) for that.

The items marked **blocking** are ones where getting it wrong is not a degraded deployment but a
compromised one.

---

## 1. Configuration

Copy `.env.example` to `.env` and fill it in. Nothing sensitive has a fallback: the application
refuses to start rather than run on a value baked into the source.

### Required — the app will not boot without these

- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — PostgreSQL connection
- [ ] `JWT_SECRET` — **blocking.** Unique per environment, generated with `openssl rand -hex 64`.
      Reusing one across environments means a token minted in staging is valid in production.
- [ ] `OWNER_UPI_ID` — **blocking.** The account that receives every customer payment. Check it
      character by character; a typo sends real money to whoever owns that address.
- [ ] `MAIL_USERNAME`, `MAIL_PASSWORD` — Gmail account and **app password** (not the account
      password). OTP delivery and driver credentials both depend on this; without it nobody can
      sign up.

### Required to get right, though they have defaults

- [ ] `CORS_ALLOWED_ORIGINS` — set to the real frontend origin. The default only covers localhost,
      so every browser request from your actual domain is blocked until you change it.
- [ ] `OTP_TEST_MODE=false` — **blocking.** When on, OTPs are written to the database instead of
      emailed. Anyone who can read the `otps` table can take over any account.
- [ ] `OWNER_BOOTSTRAP_SECRET` — set it, create your first owner, then **clear it again**. While
      it holds a value, `POST /api/auth/owner/create-admin` mints owner tokens, which is full
      control of the platform.
- [ ] `RATELIMIT_TRUSTED_PROXIES` — **blocking behind a proxy.** Set to your load balancer or
      ingress range. Left empty behind a proxy, every request appears to come from the balancer
      and shares one rate-limit bucket. Set to `*`, any caller can spoof `X-Forwarded-For` and
      bypass rate limiting entirely.
- [ ] `JPA_DDL_AUTO=validate` — leave it. `update` lets Hibernate quietly alter live tables.

### Optional

- [ ] `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — only for Google sign-in
- [ ] `TELEGRAM_*` — see [TELEGRAM_SETUP.md](TELEGRAM_SETUP.md); leave `TELEGRAM_ENABLED=false` to
      disable
- [ ] `REDIS_HOST` / `REDIS_PORT`, or `APP_CACHE_TYPE=caffeine` to run without Redis
- [ ] `OSRM_BASE_URL` / `PHOTON_BASE_URL` — **the defaults are public demo servers.** They are rate
      limited and not licensed for production traffic. Self-host or buy a provider before launch.

---

## 2. Pre-flight

- [ ] `mvn clean package` succeeds and the test suite passes
- [ ] `cd frontend && npm ci && npm run build` succeeds
- [ ] Database exists and is reachable. Flyway creates every table on first boot, so an empty
      database is enough — do **not** run anything in `db/legacy-scripts/`
- [ ] Existing database backed up, if this is not a first deploy
- [ ] TLS terminates in front of the application. A JWT sent over plain HTTP is a compromised JWT
- [ ] Frontend built with `VITE_API_BASE_URL` pointing at the real API, or left unset so it uses
      the same origin through the nginx proxy

---

## 3. Deploy

### Docker Compose

```bash
docker compose up --build -d
docker compose logs -f backend
```

### Manual

```bash
mvn clean package -DskipTests
java -jar target/travel-booking-platform-1.0.0.jar
```

Send `SIGTERM` (not `SIGKILL`) to stop it. The application drains in-flight requests for up to 25
seconds; killing it outright severs whatever booking or payment was mid-flight.

---

## 4. Verify

- [ ] `curl -f http://<host>:8080/api/health` returns healthy
- [ ] Flyway applied every migration:
      `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;`
- [ ] Create the first owner, then clear `OWNER_BOOTSTRAP_SECRET` and restart:

```bash
curl -X POST http://<host>:8080/api/auth/owner/create-admin \
  -H "Content-Type: application/json" \
  -H "X-Bootstrap-Secret: $OWNER_BOOTSTRAP_SECRET" \
  -d '{"email":"YOUR_OWNER_EMAIL","password":"YOUR_OWNER_PASSWORD","name":"Owner"}'
```

- [ ] Sign up as a customer and confirm the OTP email actually arrives
- [ ] Create a driver as the owner and confirm the credential email arrives
- [ ] Book a trip, assign the driver, start the trip, and confirm the live map updates — this
      exercises the WebSocket path, which is the piece most likely to be broken by a proxy that
      does not forward `Upgrade` headers
- [ ] Complete a payment end to end and confirm it appears in owner revenue exactly once

---

## 5. Security verification

- [ ] `/actuator/metrics`, `/actuator/prometheus` and `/actuator/caches` return 401/403 to an
      unauthenticated caller. Only `/actuator/health` and `/actuator/info` should be public
- [ ] A customer cannot read another customer's booking:
      `GET /api/user/<someone-elses-id>/bookings` with your own token returns 403
- [ ] Rate limiting responds: hammer `/api/auth/user/login` and confirm a `429` with `Retry-After`
- [ ] `.env` is not in the image, not in the repository, and not world-readable on the host
- [ ] The backend container runs as a non-root user (`docker compose exec backend id` → uid 10001)

---

## 6. Operations

- [ ] `uploads/` is a persistent volume. Driver licences and Aadhaar scans live there; without a
      volume every redeploy discards them
- [ ] Database backups scheduled and a restore actually tested
- [ ] Log aggregation configured, or at least log rotation — `logs/` grows without bound
- [ ] Someone is watching `/actuator/health` and knows what to do when it goes red

---

## Known limitations to accept before launching

These are documented in [SECURITY.md](SECURITY.md); they are design decisions, not oversights.

- **Payment verification is manual.** Nothing in this codebase observes the actual UPI transfer.
  An owner confirms it from their own bank app. This is fine when the person clicking verify owns
  the receiving account, and not fine otherwise.
- **Single-instance WebSocket.** Live tracking uses an in-memory STOMP broker, so running more
  than one backend replica requires an external broker relay first.
- **`xlsx` carries an unpatched advisory.** It is used write-only for the revenue export and no
  code path parses a workbook, so it is not reachable — but it must be replaced before anything
  ever reads an uploaded spreadsheet.
