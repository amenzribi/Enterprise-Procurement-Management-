# ADR-015 — Separate SQL Server logins for migrations and for the running application

| | |
|---|---|
| Status | **Direction approved** by the owner on 2026-11-16 (two accounts for UAT). Scripts verified on the test database; **no login created, no environment changed.** Plan: `ADR-013-to-017-implementation-plans.md` |
| Date | 2026-11-16 |
| Sprint | 1 (hardening) |
| Audit | DB-1, D-3, S-12 |

## Context

Flyway and the application use one database principal. Whoever obtains the application's credentials, or any SQL injection found later, can disable the audit triggers, change the schema and rewrite the audit trail. The comment of migration V3 already named this gap.

## Proposed decision

Two principals per environment.

| Principal | Used by | Rights |
|---|---|---|
| Migration login | Flyway, during a release only (`SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD`) | `db_ddladmin`, `db_datareader`, `db_datawriter`; `UPDATE`, `REFERENCES`, `EXECUTE` on the schema |
| Runtime login | The running application (`DB_USERNAME`, `DB_PASSWORD`) | `db_datareader`, `db_datawriter`; `UPDATE` on the schema for sequences. `DENY ALTER, TAKE OWNERSHIP` on the schema; `DENY UPDATE, DELETE` on `audit_event`; no write on the migration history |

- No application code changes: Spring Boot reads the two Flyway variables natively.
- The migration password is a release secret; it is not present on the host while the application runs.
- Development laptops keep one Windows-authenticated principal.

## Evidence so far

`scripts/db/verify-runtime-permissions.sql`, run on `procurement_test` with two temporary users: the runtime user is refused `DISABLE TRIGGER`, `ALTER`, `CREATE`, `DROP`, update and delete on `audit_event`, and writes to the migration history; it can read, write data, use sequences and take application locks (12 of 12). A control run as the migration user fails the refusals, as it must.

## Known limits

- The runtime login can still update `audit_chain_head` (it must, to append) and insert audit events. The triggers keep the head forward-only. A runtime login cannot rewrite history, but a database owner still can: external anchoring of chain heads (audit S-12) remains a separate item.
- `db_datawriter` allows `DELETE` on business tables; the application needs it for drafts.
- **Not verified:** the application and its test suite running with the runtime login. The development server accepts Windows authentication only.

## Procedure, rollback (not executed)

1. Server in mixed authentication mode. Run `scripts/db/create-application-logins.sql` with passwords from the secret store.
2. Run `verify-runtime-permissions.sql`: 12 PASS.
3. Run the full backend suite with the two logins on a test database.
4. Switch the environment's variables; restart.

Rollback: point the variables back to the previous login and restart; then drop the users and logins.

## Approval needed

1. The decision.
2. Mixed authentication on the UAT server, and who holds the migration password.
