# Legacy schema scripts (superseded — do not run)

These are the hand-run `.sql` files that used to live in the repository root. Together with
Hibernate's `ddl-auto: update` they were how the schema actually got built, which meant no
environment could be rebuilt from source with any confidence: the files carry no ordering, no
record of what had already been applied, and several are one-off repairs (`fix-*.sql`) for
damage caused by earlier ones.

Their cumulative result is now captured in a single versioned baseline:

    src/main/resources/db/migration/V1__baseline_schema.sql

**Do not run anything in this directory.** It is kept only as history — to explain why a column
or constraint looks the way it does. Applying any of it to a current database will either fail
or corrupt the Flyway history.

## Making a schema change now

Add a new file under `src/main/resources/db/migration`:

    V2__add_driver_rating_column.sql
    V3__backfill_booking_status.sql

Flyway applies them in version order at startup, once per database, and records each in
`flyway_schema_history`. `spring.jpa.hibernate.ddl-auto` is `validate`, so the application
refuses to start if the entities and the schema disagree — a mismatch surfaces immediately
rather than as corrupt data later.
