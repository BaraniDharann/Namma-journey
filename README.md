# Namma Journey

A full-stack car travel booking platform built for the Indian market — temple visits, devotional
trips, tours and long-distance travel. Customers book a trip, an owner assigns a driver, and the
customer watches that driver approach in real time.

[![CI](https://github.com/BaraniDharann/Namma-journey/actions/workflows/main.yml/badge.svg)](https://github.com/BaraniDharann/Namma-journey/actions/workflows/main.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## What's in the box

**Three roles, three separate front-ends.** Customers book and track; drivers accept trips and
stream their location; owners manage drivers, pricing, packages and revenue.

- **Booking & pricing** — per-kilometre and hourly pricing models, route costing, travel packages
- **Driver allocation** — owner-assigned or broadcast-to-drivers with per-driver rejection tracking
- **Live tracking** — driver location streamed over WebSocket/STOMP and drawn on a Leaflet map
- **Telegram dispatch** *(optional)* — trip cards pushed to drivers in Telegram, accept/reject inline
- **Payments** — UPI collection flow with owner-side reconciliation
- **Auth** — email+OTP signup, Google OAuth, JWT with role-based access for USER / DRIVER / OWNER
- **Reviews & ratings**, PDF/Excel export, email notifications

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.4, Spring Security, JPA/Hibernate |
| Database | PostgreSQL 15, schema owned by **Flyway** |
| Cache | Redis (falls back to in-process Caffeine if unreachable) |
| Realtime | WebSocket + STOMP over SockJS |
| Frontend | React 18, Vite 5, Tailwind CSS 4, React Router 6 |
| Maps & routing | Leaflet, OSRM (routing), Photon (geocoding), GraphHopper |
| Testing | JUnit 5, Playwright (full-stack E2E) |
| Deploy | Docker Compose, GitHub Actions |

---

## Quick start

### With Docker (recommended)

Everything — Postgres, Redis, backend and frontend — comes up together.

```bash
git clone https://github.com/BaraniDharann/Namma-journey.git
cd Namma-journey
cp .env.example .env     # then fill in the values, see Configuration below
docker compose up --build
```

- Frontend → <http://localhost>
- Backend API → <http://localhost:8080>
- Health check → <http://localhost:8080/api/health>

### Without Docker

**Prerequisites:** Java 17+, Maven 3.6+, Node 20+, PostgreSQL 14+, Redis (optional).

```bash
# 1. Database — Flyway creates every table on first boot, so an empty database is enough
createdb travel_booking_db

# 2. Configuration
cp .env.example .env      # fill in DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET at minimum

# 3. Backend  → http://localhost:8080
mvn clean package -DskipTests
java -jar target/travel-booking-platform-1.0.0.jar

# 4. Frontend → http://localhost:5173
cd frontend
npm install
npm run dev
```

Running without Redis? Set `APP_CACHE_TYPE=caffeine` and the app caches in-process instead.

---

## Configuration

All configuration is environment-driven — see [.env.example](.env.example) for the full list.
Nothing sensitive has a default; the application refuses to start without the required values.

| Variable | Required | Notes |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | ✅ | PostgreSQL connection |
| `JWT_SECRET` | ✅ | No fallback — generate with `openssl rand -hex 64` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | ✅ | Gmail SMTP app password for OTP delivery |
| `GOOGLE_CLIENT_ID` / `_SECRET` | ➖ | Only needed for Google sign-in |
| `TELEGRAM_*` | ➖ | Leave `TELEGRAM_ENABLED=false` to disable entirely — see [TELEGRAM_SETUP.md](TELEGRAM_SETUP.md) |
| `REDIS_HOST`, `REDIS_PORT` | ➖ | Defaults to `localhost:6379` |
| `OWNER_UPI_ID`, `OWNER_UPI_NAME` | ➖ | Payment collection details |

> **Never commit your `.env`.** It is gitignored, and CI runs a secret scan over the full history.

---

## Database schema

Flyway owns the schema. Migrations live in
[src/main/resources/db/migration](src/main/resources/db/migration) and run automatically at
startup; `spring.jpa.hibernate.ddl-auto` is `validate`, so the application refuses to boot if the
entities and the schema disagree.

To change the schema, add a new file — never edit an applied one:

```
V4__add_driver_rating_column.sql
```

The hand-run SQL scripts in [db/legacy-scripts/](db/legacy-scripts/) are **history only**. Do not
run them; see [db/legacy-scripts/README.md](db/legacy-scripts/README.md) for why they exist.

---

## Testing

```bash
mvn test                              # backend unit tests

cd playwright-tests
npm ci && npx playwright install
./run-full-stack-e2e.sh               # full-stack E2E — needs a running Postgres
```

The E2E suite boots the real backend and frontend and walks a booking through its entire status
machine. It runs nightly in CI ([.github/workflows/e2e.yml](.github/workflows/e2e.yml)) rather than
on every pull request, because it takes minutes and needs a live database.

---

## Documentation

| Document | What it covers |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Component layout and request flow |
| [HLD.md](HLD.md) | High-level system design |
| [docs/API_REFERENCE.md](docs/API_REFERENCE.md) | Full endpoint reference with request/response examples |
| [TELEGRAM_SETUP.md](TELEGRAM_SETUP.md) | Setting up the optional Telegram driver dispatch |
| [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md) | Pre-production checklist |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to set up, branch, test and submit changes |
| [SECURITY.md](SECURITY.md) | Reporting a vulnerability |

Deeper reference material lives under [docs/](docs/):

- [docs/api/](docs/api/) — per-feature API documentation (booking, driver allocation, payments, reviews, RCM)
- [docs/guides/](docs/guides/) — setup and testing walkthroughs with cURL examples
- [docs/postman/](docs/postman/) — Postman collections

> These are working notes kept from the build and are **not** all current — [docs/API_REFERENCE.md](docs/API_REFERENCE.md)
> is the authoritative endpoint reference.

---

## Project layout

```
├── src/main/java/com/travelplatform/   Spring Boot backend
│   ├── config/                         security, WebSocket, caching, rate limiting
│   ├── controller/                     REST + WebSocket endpoints
│   ├── service/                        business logic
│   ├── repository/                     Spring Data JPA
│   └── entity/                         JPA entities
├── src/main/resources/db/migration/    Flyway migrations (authoritative schema)
├── frontend/                           React + Vite SPA
│   └── src/pages/{user,driver,owner}/  one page tree per role
├── playwright-tests/                   full-stack E2E suite
├── db/legacy-scripts/                  superseded SQL — history only, do not run
├── scripts/                            local dev helper scripts
└── docs/                               reference documentation
```

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first — it covers local
setup, the migration rules, and what CI expects of a pull request. By participating you agree to
the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

Found a vulnerability? Please **don't** open a public issue — see [SECURITY.md](SECURITY.md) for
how to report it privately.

## License

Released under the [MIT License](LICENSE).
