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

### Fixed
- Frontend login flows for user, driver and owner roles
- Live tracking map and driver location streaming reliability

### Security
- `JWT_SECRET` no longer has a fallback default — the application will not start without it
- Placeholder values substituted for real credentials in `.env.example`

## [1.0.0] - Initial

- User, driver and owner authentication with email OTP and Google OAuth
- Travel booking with per-kilometre and hourly pricing
- Owner-managed driver onboarding with document verification
- UPI payment collection flow
- Reviews and ratings
- Live driver location tracking over WebSocket
