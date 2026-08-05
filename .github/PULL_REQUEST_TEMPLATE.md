## What does this change?

<!-- A sentence or two. The diff shows what changed; tell us why. -->

## Related issue

<!-- Closes #123 — or "none" if this is self-contained. -->

## How was it tested?

<!-- Be specific. "Ran the tests" is less useful than "added a case to
     DriverServiceRejectionTest covering a double rejection, plus manual check of the
     driver bookings page". -->

- [ ] `mvn test` passes
- [ ] `cd frontend && npm run build` passes
- [ ] E2E suite run (`playwright-tests/run-full-stack-e2e.sh`) — *required if this touches booking
      flow, driver allocation, or authentication*
- [ ] Manually verified in a running app

## Database changes

- [ ] No schema change
- [ ] Adds a **new** Flyway migration under `src/main/resources/db/migration`
- [ ] JPA entities updated to match (`ddl-auto=validate` will fail the boot otherwise)

> Never edit an already-applied migration — it's checksummed in `flyway_schema_history` and
> editing it breaks every existing database.

## Checklist

- [ ] I read [CONTRIBUTING.md](../blob/main/CONTRIBUTING.md)
- [ ] No secrets, credentials, real email addresses or phone numbers in the diff
- [ ] New code matches the style of the files around it
- [ ] Documentation updated if behaviour or configuration changed
- [ ] `CHANGELOG.md` updated under `[Unreleased]` for anything user-facing

## Screenshots

<!-- For frontend changes. Delete this section otherwise. -->
