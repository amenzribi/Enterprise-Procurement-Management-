# Sprint 1: database changes

Finding ids are those of `docs/audit/05-database-review.md`.

**No database-wide setting was changed and no login was created.** The only schema change is one additive migration (four indexes). Everything else is application configuration, or a script that waits for approval.

## Summary

| Finding | Status | What exists now |
|---|---|---|
| DB-1 One principal for migrations and the application | **Prepared, not applied** (gate G3-c) | Scripts in `scripts/db/`; grants verified on the test database |
| DB-2 Foreign keys without index | **Done for the 4 with query evidence** | Migration `V23` |
| DB-3 Unbounded lock waits | **Reduced**: waits are bounded and answered 503. Readers still block on writers until RCSI is approved (gate G3-b) | Configuration, error mapping, test |
| DB-4 Tables never cleaned | **Reduced**: expired tokens are purged. Notifications and messages wait for a retention decision | `ExpiredTokenPurge`, test |
| DB-5 to DB-12 | Open | Not in this sprint |

## 1. Bounded waits (DB-3)

| Setting | Value | Where |
|---|---|---|
| Lock wait per statement | 10 s (`DB_LOCK_TIMEOUT_MS`) | `spring.datasource.hikari.connection-init-sql: SET LOCK_TIMEOUT …`, applied to every pooled connection |
| Transaction limit, also the statement timeout | 30 s (`DB_TRANSACTION_TIMEOUT`) | `spring.transaction.default-timeout` |

- A statement that waits longer than the lock timeout fails with SQL Server error 1222. `platform/web/ApiExceptionHandler.java` answers 503 `busy` with `Retry-After: 2`, as it already did for application locks. Transaction and query timeouts get the same answer.
- Application locks (`sp_getapplock`) keep their own 2 s timeout.
- **Proof:** `DatabaseWaitsIntegrationTests` holds an uncommitted write and calls an endpoint that reads the row: the answer is 503 `busy` within the lock timeout, and the next call succeeds. A second test checks that every pooled connection carries the setting.
- **Behaviour change:** an operation that used to wait indefinitely now fails after 10 s and can be retried. A single transaction longer than 30 s is rolled back; the import of a very large file is the one known candidate (audit S-17, open).
- **Rollback:** remove the two settings from `application.yml`, or set large values through the two environment variables.

**Transaction boundaries reviewed for this change:**

| Area | Finding |
|---|---|
| Sign-in | Password hashing runs outside any transaction (unchanged, sound) |
| Audit chain head (`audit/AuditLog`) | Held with `UPDLOCK` to commit; now also bounded by the 10 s wait |
| Outbox sender | Claims in a short transaction, sends outside it (unchanged, sound) |
| Token purge (new) | Deletes in batches of 1,000, each its own statement, no surrounding transaction |
| New audit events (absences, rules, rates) | Written last in the existing transaction, as the audit recommends |

**Not done:** `READ_COMMITTED_SNAPSHOT` stays OFF. The script `scripts/db/enable-read-committed-snapshot.sql` contains the procedure, the backup step and the rollback. It was not run on any database.

## 2. Indexes with query evidence (DB-2)

Migration `V23__foreign_key_indexes_with_query_evidence.sql`. The audit listed about 20 candidates out of 117 unindexed foreign keys and asked for evidence. Reading every statement on those tables shows that only four columns are used as a search condition:

| Index | Statement that needs it |
|---|---|
| `ix_approval_link_token_task (task_id)` | `approval/ApprovalStore`: tokens of a request's tasks revoked on every decision |
| `ix_goods_receipt_line_shipment_line (shipment_line_id)` | `receipt/ReceiptStore`: quantity received per shipment line |
| `ix_goods_receipt_line_po_line (purchase_order_line_id)` | `receipt/ReceiptStore`: quantity received per order line (two statements) |
| `ix_shipment_line_po_line (purchase_order_line_id)` | `logistics/LogisticsStore`, `receipt/ReceiptStore`: quantity shipped per order line |

- The other candidates (`approval_task_event.task_id`, `award.rfq_id`, the `request_line_id` columns, `supplier_invoice.purchase_order_id`, and others) are not searched by: those tables are read by `request_id` or their parent key, already indexed. They are left alone until a load test shows a need.
- **Evidence is from reading the code, not from execution plans.** No plan was captured: the databases hold a few hundred rows.
- **Applied to:** `procurement_test` only (by the test run). Any other database receives it at the next application start, as with every migration.
- **Rollback:** `DROP INDEX` for the four names. No data or constraint changes.
- **Audit correction:** SQL Server's own missing-index list for the audited database was empty (`C:\qa\audit\catalog.txt`).

## 3. Retention (DB-4)

- **Audit correction:** `Idempotency.purge()` already runs every hour (`platform/web/Idempotency.java`, `@Scheduled`). The audit said nothing called it. No change was needed.
- `platform/persistence/ExpiredTokenPurge.java` (new): every 6 hours, deletes rows of `approval_link_token`, `quote_link_token` and `password_reset_token` that expired more than 90 days ago (`app.retention.expired-tokens`). None of these tables is referenced by a foreign key. A token row holds a hash, an owner and dates; what the token did is in the audit trail.
- **Proof:** `DatabaseWaitsIntegrationTests.tokensAreDeletedOnlyAfterTheRetentionThatFollowsTheirExpiry`.
- **Not done, needs a business decision:** in-app notifications, internal and supplier messages, assistant turns. `audit_event` is never purged, by design.

## 4. Two database logins (DB-1): prepared, not applied

| File in `scripts/db/` | Purpose |
|---|---|
| `create-application-logins.sql` | Creates the migration and runtime logins and users. Passwords come from the command line |
| `grant-application-roles.sql` | Migration user: schema changes and data. Runtime user: data only, `DENY ALTER` on the schema, `DENY UPDATE, DELETE` on `audit_event` |
| `verify-runtime-permissions.sql` | Impersonates the runtime user and prints PASS or FAIL for 12 checks; every statement is rolled back |

- Application side: nothing to change in code. A deployment sets `DB_USERNAME`/`DB_PASSWORD` to the runtime login and `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` to the migration login.
- **Verification done:** see `sprint-01-test-results.md`, section 4 (grants applied to two password-less users in `procurement_test`, checks run, users dropped).
- **Not verified:** the application running with the runtime login. The development SQL Server accepts Windows authentication only, so SQL logins cannot sign in there. The full suite must be run once with the two logins on a server in mixed mode before UAT.

## 5. Decisions needed before going further

| Gate | Action | Precondition |
|---|---|---|
| G3-b | Enable RCSI on each database | Backup; maintenance window; full backend and browser suites on a copy with the option on |
| G3-c | Create the two logins and switch the application | SQL authentication on the server; suites rerun with the runtime login |
| — | Retention periods for notifications and messages | Business decision |
