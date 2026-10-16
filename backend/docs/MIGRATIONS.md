# Flyway Community migration runbook

## Release status

This is a LOCAL implementation, not approval to deploy or change production.
`V1__initial_schema.sql` is derived from the current JPA entities. It has NOT been
compared with the complete production DDL. A successful local test does not certify
production compatibility. No production connection is required by the test suite.

## Dependencies and ownership

Spring Boot 4.1.1 manages Flyway 12.4.0, PostgreSQL JDBC 42.7.13 and Testcontainers
2.0.5. Use `spring-boot-starter-flyway` and the open-source
`org.flywaydb:flyway-database-postgresql` module. No Flyway Gradle plugin, license
key, Teams/Enterprise dependency, undo, paid diff/generate, or paid dry-run is used.

Flyway SQL is the only schema creation mechanism in normal local/test startup.
Hibernate uses `validate`. Basic SQL initialization is disabled. Existing production
and development credentials, Docker Compose volumes and deployment configuration
are unchanged.

## Configuration and startup risk

- Default (including an unprofiled Render launch): Flyway disabled, Hibernate
  `validate`, automatic baseline disabled, clean disabled, validation on migrate
  enabled. Do not externally override these safety settings during onboarding.
- `local`: Flyway enabled; requires `LOCAL_DB_URL`, `LOCAL_DB_USER`, and
  `LOCAL_DB_PASSWORD`. There are no local-profile credential defaults. Supply a
  NEW isolated database, never an existing development/production endpoint.
- Migration tests: create a new PostgreSQL 17 container and random schema per test.
  JDBC parameters are passed directly from that container with higher priority
  than environment variables. The tests do not use external DB URLs or the local
  profile. Docker absence fails tests; it is not silently skipped.

IMPORTANT: This changes the default from `update` to `validate`. An empty default
DB now fails startup instead of creating tables. An existing DB with an incompatible
schema also fails startup instead of being changed. A matching existing schema can
start without a Flyway history table when Flyway is disabled; that does not mean
migration onboarding has completed. Do NOT deploy before checking existing DDL and
arranging the cutover. Selecting `local` against a non-empty database without history
fails safely; it never baselines automatically.

Run existing and migration tests with the installed Gradle:

```sh
gradle -p backend test --no-daemon
```

Docker must be running. Testcontainers downloads `postgres:17-alpine` and its normal
support images if missing. It creates disposable resources with no bind mount to
existing DB data, does not reuse existing containers, and closes only resources it
created. Tests never issue destructive table/schema cleanup SQL. A failed-migration
fixture is only in test resources and is excluded from the application jar.

## V1 boundaries

V1 contains rooms, participants, answers and shared_results, primary keys, existing
entity unique constraints, four foreign keys, lengths and timestamp precision.
String room status includes Hibernate's enum-value CHECK. UUIDs/times are generated
by the application, not new SQL defaults. Foreign keys do not cascade deletes.
No answer-index CHECK or cross-room composite FK is newly introduced.

V1 does not add performance indexes, modes, question/analysis versions, retention,
revocation, seed data, or any changes to existing answers/public share tokens.
Do not use IF NOT EXISTS to hide schema drift.

## New database

Use a fresh isolated PostgreSQL and the `local` profile with its required credentials.
Migration creates V1 and the history table before JPA validates. A second migrate
must execute zero migrations. Never baseline a new empty database: that would skip V1.

## Existing database: prerequisites (future, separately authorized)

1. Confirm server version, database/schema identity, connection route and privileges.
   On Neon, review a direct migration connection separately from a pooled app URL.
2. Obtain an independently restorable backup and rehearse restoration. A branch or
   a short history-retention window alone is not an independent backup strategy.
3. Obtain an approved schema-only export, then compare it OFFLINE with V1 and with
   Hibernate's expected schema: types, lengths, precision, nullability, defaults,
   PK/UNIQUE/FK/CHECK definitions, delete rules, indexes, triggers/extensions and
   schema/search_path. Production definitions are not yet available in full.
4. Rehearse on an isolated copy with representative data. Record row counts and
   ordered snapshots/hashes including IDs, original answers and share tokens.
5. Resolve mismatches explicitly before baseline. Do not infer equivalence from
   Hibernate validate alone: it does not check every index/constraint/default.
   Flyway validate checks scripts versus history, not all live database objects.
   Observed with Hibernate 7.4.5: an integer entity property backed by bigint passes
   validate, while a text backing column fails. The tests preserve both observations.

## Existing database: cutover sequence (plan only)

Do not run this sequence until production change approval is obtained.

1. Freeze schema-changing deploys; ensure no old instance can continue Hibernate
   `update` during the cutover. Arrange a maintenance window if necessary.
2. Confirm recoverability, exact target identity and complete schema equivalence.
3. With a reviewed standalone Flyway Community workflow aligned to the app's
   Flyway version, explicitly baseline the verified EXISTING schema at version 1.
   Baseline writes the history table; it does not prove or repair schema equivalence.
4. Run history validation and Hibernate validate against the approved copy first.
   With V1 as the only migration, migrate after baseline executes no application DDL.
5. Separately authorize migration execution for future releases. Default application
   startup stays migration-disabled in this change. Any production profile/env or
   pipeline activation is outside this implementation and must be reviewed.
6. Verify private API behavior, public shares and preserved data. Keep an application
   rollback available, while remembering the old build still has Hibernate update.
   An app rollback must not silently restore schema-changing startup behavior.

Do not enable `baselineOnMigrate`. Do not add B1 baseline migrations or automatic
startup baseline hooks. Community explicit baseline is sufficient for this project.

## Future versions

Only after V1 onboarding, propose V2 indexes on answers(room_id, question_index) and
participants(room_id), based on real size/execution plans. Keep retention/mode
changes separate. If concurrent index creation becomes necessary, review its
non-transactional behavior and Flyway PostgreSQL lock configuration separately.
Do not edit applied migrations. Add forward migrations and preserve older-app
compatibility through nullable/additive changes and staged backfills when needed.

## Failures and recovery

- Fail startup on migration/checksum/JPA validation errors; do not ignore failures.
- Normal PostgreSQL DDL in this V1 is transactional. The test-only failing V2 creates
  a table and causes a duplicate-PK error to verify that its entire transaction,
  including its history record, rolls back. Previously committed V1 stays intact.
- Correct a never-successfully-applied migration only after verifying rollback and
  confirming that no environment has already applied it. Correct an already applied
  version through a new forward version, not a checksum rewrite.
- For checksum mismatches, restore the original versioned file/artifact and examine
  deployment history. Do not run repair simply to make validation green.
- Repair changes metadata, not application data or partially applied schema. It is
  not automated here. Any genuinely necessary repair requires separate investigation
  and authorization. No destructive cleanup or paid undo is part of recovery.
- For unexpected data impact, stop writes, preserve evidence and use the rehearsed
  recovery plan. Restoring a backup itself needs a separately approved operation.

## Local verification record — 2026-10-16

`gradle -p backend test bootJar --no-daemon` passed: 21 tests, zero failures,
zero errors, zero skips (12 existing tests plus 9 migration/integration tests).
Actual disposable server: PostgreSQL 17.11; Flyway 12.4.0; Hibernate 7.4.5.Final.
The full two-user API test completes all 13 questions and creates/reads a public
share, checking authentication, duplicate/third-participant rejection and hidden
answers before both submit. Baseline verification preserves every application row
and reads old private results and the old public share after onboarding.
The jar contains V1 and open-source Flyway core/PostgreSQL modules, and contains
neither the test-only failing V2 nor commercial Flyway artifacts.

Initial test runs were NOT ignored: an integer-to-bigint rejection assumption was
incorrect, and exact exception-message expectations used an outdated hyphenated
wording. The tests now independently document permitted numeric widening and assert
that true text/integer incompatibility and missing tables fail for the expected
Hibernate schema-validation cause. All tests were rerun successfully.

This validates the entity-derived local schema only. Production rollout remains
blocked on complete production-DDL comparison, backup/restore rehearsal, verified
existing-data onboarding and a separately approved cutover. No production readiness
claim follows from these local results.
