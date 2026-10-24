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
- [ ] `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` — online payments. Without
      them the "Online" option refuses politely and UPI QR + cash work as before. See *Razorpay* below.
- [ ] `OUTBOX_TRANSPORT` — `in-process` (default, no extra infrastructure) or `sqs`. See *AWS SQS*
      below.
- [ ] `STORAGE_TYPE=s3` + `S3_*` — **required on any host without a persistent disk** (Render's free
      plan has none: every deploy deletes uploaded driver documents and trip photos). An S3 bucket
      in `ap-south-1` with Block Public Access on; the app serves files through short-lived signed
      URLs.

### Razorpay

1. Dashboard → **Account & Settings → API keys** → generate keys (Test mode first).
2. Dashboard → **Webhooks → Add New Webhook**:
   - URL: `https://api.nammajourneys.site/api/payments/razorpay/webhook`
   - Secret: the value of `RAZORPAY_WEBHOOK_SECRET` (`openssl rand -hex 32`)
   - Events: `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`, `refund.failed`
3. **Payments and refunds → Capture settings**: automatic capture on.
4. Going live: finish KYC, switch the dashboard to Live, generate live keys, **add the webhook
   again in Live mode** (test and live webhooks are separate), and swap the three variables. No
   code change.

### AWS SQS (when `OUTBOX_TRANSPORT=sqs`)

Jobs (notification emails, Razorpay refunds) are always written to the `outbox_events` table
first; SQS only replaces the in-process runner, so several backend instances share the work.

1. Create a standard queue `namma-jobs-dlq` (retention 14 days).
2. Create a standard queue `namma-jobs` with **visibility timeout 120 s**, receive wait time 20 s,
   and a redrive policy to `namma-jobs-dlq` with **maxReceiveCount 5**.
3. Create an IAM user (or role, on AWS hosting) allowed only `sqs:SendMessage`,
   `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:ChangeMessageVisibility`,
   `sqs:GetQueueAttributes` on `namma-jobs`.
4. Set `OUTBOX_TRANSPORT=sqs`, `OUTBOX_SQS_QUEUE_URL`, `OUTBOX_SQS_REGION=ap-south-1`, and either
   `OUTBOX_SQS_ACCESS_KEY` / `OUTBOX_SQS_SECRET_KEY` or the standard AWS credential variables.
5. Add a CloudWatch alarm on `ApproximateNumberOfMessagesVisible > 0` for `namma-jobs-dlq`: a
   message there is an email that never went out, or a refund a passenger is still owed.

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
- [ ] Pay a confirmed trip **online** (test UPI `success@razorpay`), then check the Razorpay
      dashboard's webhook log shows a `200` for `payment.captured`
- [ ] Cancel that trip and confirm the payment goes `REFUND_PENDING` → `REFUNDED` and the refund
      shows in the Razorpay dashboard
- [ ] `SELECT status, count(*) FROM outbox_events GROUP BY status;` — no `DEAD` rows

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

- **UPI QR payment verification is manual.** Nothing in this codebase observes a direct UPI
  transfer. An owner confirms it from their own bank app. This is fine when the person clicking
  verify owns the receiving account, and not fine otherwise. Online (Razorpay) payments are
  verified automatically by signature and refunded automatically on cancellation; UPI QR
  payments are refunded by the owner by hand.
- **Single-instance WebSocket.** Live tracking uses an in-memory STOMP broker, so running more
  than one backend replica requires an external broker relay first.
- **`xlsx` carries an unpatched advisory.** It is used write-only for the revenue export and no
  code path parses a workbook, so it is not reachable — but it must be replaced before anything
  ever reads an uploaded spreadsheet.
