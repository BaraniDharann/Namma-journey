# Load tests

Two [k6](https://k6.io) scripts:

| Script | What it does | Pass/fail |
|---|---|---|
| `k6/booking-flow.js` | 3 minutes of realistic mixed traffic: anonymous browsing, bookings (each followed by a deliberate double-tap retry with the same `Idempotency-Key`), dashboards, and live tracking (drivers posting GPS every 2s while the owner polls) | Yes: thresholds on errors, p95 per kind, and **zero duplicate bookings** |
| `k6/stress.js` | Raises the request rate from 50 to 1600/s to find where one instance stops keeping up | No: read where p95 and errors turn upward |

## Running them

Use a **throwaway** environment: its own database, its own `JWT_SECRET`, high rate limits.

1. Create an empty database (e.g. `namma_loadtest`) and start the backend against it with
   `JWT_SECRET=<throwaway>`, `RATELIMIT_*=1000000`, `OSRM_BASE_URL=http://127.0.0.1:9` (so routing
   falls back to the built-in estimate instead of hammering the public OSRM demo server) and
   `METRICS_SCRAPE_TOKEN` set. Flyway creates the schema.
2. Seed accounts:
   ```sql
   INSERT INTO users (id, created_at, login_type, name, phone, role)
   SELECT ('00000000-0000-4000-8000-' || lpad(g::text, 12, '0'))::uuid, now(), 'EMAIL', 'Load User ' || g,
          '9' || lpad(g::text, 9, '0'), 'ROLE_USER' FROM generate_series(1, 50) g;
   INSERT INTO drivers (id, aadhaar_number, created_at, license_number, mobile, name, password, role, status, first_login, email_verified)
   SELECT nextval('drivers_id_seq'), 'LT-A-' || g, now(), 'LT-L-' || g, '8' || lpad(g::text, 9, '0'), 'Load Driver ' || g,
          'x', 'ROLE_DRIVER', 'ACTIVE', false, true FROM generate_series(1, 5) g;
   ```
3. Mint tokens with the throwaway secret: `node loadtest/mint-tokens.mjs <secret> tokens.json`
4. Run (Docker, no install needed):
   ```bash
   docker run --rm -v "$PWD/loadtest/k6:/scripts:ro" -v "$PWD:/data" \
     -e BASE_URL=http://host.docker.internal:8080 -e TOKENS=/data/tokens.json \
     grafana/k6:0.54.0 run /scripts/booking-flow.js
   ```
   Watch it live on the Grafana dashboard (`docker compose up prometheus grafana`).

## Results (2026-10-13, one backend instance, 12-core laptop, everything on the same machine)

**booking-flow.js — passed every threshold**

| | Result |
|---|---|
| Requests | 15,547 in 3 min, **0 failed** |
| p95 latency (client) | reads 33 ms · bookings 51 ms · tracking 30 ms |
| p95 / p99 latency (server, Prometheus) | 37 ms / 85 ms |
| Double-tap retries | 727, **all replayed the original booking, 0 duplicates** |
| Driver double-bookings in the DB afterwards | **0** |
| Fares not at exactly 2 decimals | **0** of 730 |
| DB pool peak | 2 of 20 connections, 0 waiting |

**stress.js — found and fixed a real bottleneck**

| Run | Sustained throughput | Errors | Limiting factor |
|---|---|---|---|
| Before | ~98 req/s | 0.7% | DB pool exhausted (20/20 busy, 53 queued): "my bookings" ran 2 queries per booking (N+1) |
| After batching the lookups | **~192 req/s** | **0%** | The test machine itself (100% CPU running k6 + Docker + Postgres + Redis); the app used 27% CPU and the DB pool never queued |

The true single-instance ceiling is above what one laptop can generate. Measure production
capacity with k6 on a separate machine against the real server size.
