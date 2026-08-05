# Contributing to Namma Journey

Thanks for taking the time to contribute. This document covers what you need to get a change
merged — local setup, the few rules that aren't obvious from the code, and what CI will check.

## Getting set up

See [Quick start](README.md#quick-start) in the README. The Docker route is the fastest way to a
working stack; you only need the manual route if you're changing the backend and want fast rebuilds.

You will need a `.env` — copy `.env.example` and fill it in. **Never commit it.** CI runs a secret
scan across the full git history, and a leaked credential in a pull request means the credential
has to be rotated, not just the commit removed.

## Before you open a pull request

```bash
mvn test                    # backend unit tests
cd frontend && npm run build   # the frontend build is part of CI and must pass
```

If your change touches booking flow, driver allocation or auth, also run the E2E suite:

```bash
cd playwright-tests
npm ci && npx playwright install
./run-full-stack-e2e.sh
```

It needs a running Postgres and takes several minutes. It's worth it — those three areas are where
regressions actually hurt.

## Database changes

Flyway owns the schema. **Add a new migration; never edit one that has already been applied.**

```
src/main/resources/db/migration/V4__add_driver_rating_column.sql
```

Applied migrations are checksummed in `flyway_schema_history`, so editing one breaks every existing
database rather than upgrading it. `ddl-auto` is `validate`, which means the application will refuse
to start if your JPA entity and your migration disagree — that failure is the point, not a nuisance.

The SQL files in `db/legacy-scripts/` are history. Don't add to them and don't run them.

## Code style

There's no formatter enforced in CI, so the rule is simply: **match the file you're editing.**
Follow its naming, its comment density, its idioms. A pull request that reformats surrounding code
is much harder to review than one that doesn't.

- **Java** — standard Spring conventions. Business logic in `service/`, not in controllers.
- **React** — functional components with hooks. Shared logic goes in `src/hooks/`, API calls
  through `src/utils/api.js` rather than raw `axios` in components.

## Commits and pull requests

- Branch off `main`, one logical change per pull request.
- Write commit messages that say *why*, not just *what*. The diff already shows what.
- Fill in the pull request template — especially how you tested it.
- Small pull requests get reviewed quickly. Large ones sit.

## Reporting bugs

Open an issue using the bug report template. The single most useful thing you can include is a
reliable reproduction: exact steps, the role you were logged in as (user/driver/owner), and what you
expected instead. A stack trace from `logs/` helps for backend issues.

## Security issues

Do not open a public issue. See [SECURITY.md](SECURITY.md).

## Questions

Open a discussion or an issue with the question label. If something in the setup was confusing,
that's a documentation bug worth reporting — it will be confusing for the next person too.
