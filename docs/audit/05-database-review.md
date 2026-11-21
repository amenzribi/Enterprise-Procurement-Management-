# 05. Database review

Scope: Flyway migrations V1–V22, the data-access code, and the live catalog of a database built from them (`procurement_qa_msg_verify`: schema V1–V22, demo seed, one full browser-suite run). Read-only. Status of each finding: **Verified** (read in code or in the catalog) or **Hypothesis** (needs execution plans or load).

## Summary

The schema is carefully built: every table has a primary key, keys come from sequences, 94 CHECK constraints, 213 foreign keys, optimistic versions on the business aggregates, and an audit log that is append-only by trigger and cannot be truncated. Types follow the project conventions without exception among the columns checked.

The weaknesses are operational rather than structural:

- More than half of the foreign keys have no supporting index.
- Readers block on writers and can wait without limit.
- Three tables that hold tokens or stored responses are never cleaned.
- The application connects with one database principal that can also alter the schema, which leaves the audit protections removable.

None of these is visible at today's data volume (42 requests, 427 audit events). They will show under load and over time.

| Measured in the catalog | Value |
|---|---|
| Tables | 74 (+ `flyway_schema_history`) |
| Foreign keys | 213 |
| Foreign keys with no unfiltered index led by the FK column | **117** |
| Indexes | 191 |
| CHECK constraints | 94 |
| Sequences | 59 |
| Tables with an optimistic `version` column | 18 |
| Triggers | 3, all protecting the audit tables |
| Tables without a primary key | 0 |
| Cascading deletes | 9 |
| `READ_COMMITTED_SNAPSHOT` | OFF |

## Findings

### DB-1. One database principal runs migrations and the application — High — Verified

- **Location:** `BackEnd/src/main/resources/application.yml` (`spring.datasource.*`, a single `DB_USERNAME`); `db/migration/V3__audit_hardening_and_org_keys.sql:9-11`.
- **Explanation:** Flyway and the running application use the same connection. The V3 comment records the gap: "a principal that can ALTER the schema (db_owner) can still drop these constraints and triggers. Separate runtime/migration principals (Sprint 1) and external anchoring of chain heads (Sprint 9) close that gap." Neither exists in the code: there is one datasource and no anchoring job.
- **Business impact:** anyone who obtains the application's database credentials, or any SQL injection found later, can disable the audit triggers and rewrite history. The hash chain then proves nothing to an auditor, because the chain heads can be rewritten too.
- **Recommendation:** two SQL Server principals. A migration login (`db_ddladmin` + data access) used only by Flyway (`spring.flyway.user/password`); a runtime login with `db_datareader`/`db_datawriter`, `EXECUTE`, and explicit `DENY ALTER` plus `DENY UPDATE, DELETE` on `audit_event`. Export chain heads daily to a store the application cannot write (file share with object lock, or a signed e-mail to the auditor).
- **Complexity:** S for the two logins (configuration and a provisioning script); M for anchoring.
- **Verification:** as the runtime login, `DISABLE TRIGGER trg_audit_event_append_only ON audit_event` must fail with a permission error; the application test suite must still pass with that login.

### DB-2. 117 of 213 foreign keys have no supporting index — Medium — Verified (impact: Hypothesis)

- **Location:** catalog query, list kept in `C:\qa\audit\catalog.txt`. The ones that matter because the child table grows and is read by parent:

| Child column | Why it matters |
|---|---|
| `approval_task_event.task_id`, `.round_id` | Task history is read per task and per round on every request page |
| `approval_link_token.task_id` | Tokens revoked per task on every decision |
| `goods_receipt.shipment_id`; `goods_receipt_line.shipment_line_id`, `.purchase_order_line_id`, `.request_line_id` | Remaining-quantity checks on every receipt |
| `shipment_line.purchase_order_line_id`, `.request_line_id` | Same checks from the shipment side |
| `purchase_order_line.award_line_id`, `.request_line_id`; `award.rfq_id`, `.quotation_id`; `award_line.rfq_line_id`, `.request_line_id`; `quotation_line.rfq_line_id`; `rfq_line.request_line_id` | Joins along the sourcing-to-order chain |
| `supplier_invoice.purchase_order_id` | Invoices per order at closure |
| `notification_outbox.recipient_user_id`, `audit_event.actor_user_id` | Large tables; needed only if queried by user |
| `purchase_request.current_owner_user_id` | "My work" style queries |
| `internal_conversation.user_low_id` | Covered only as the second column of the pair index |

  The remaining ~85 point at small reference tables (`country`, `currency`, `company`) or are "who did it" columns (`created_by`, `uploaded_by`). For those the cost is limited to deletes and key updates on the parent, which this application does not perform.
- **Explanation:** SQL Server does not index foreign-key columns automatically. Without one, a join from parent to child scans the child table, and a delete on the parent scans every child to check the constraint. `purchase_request` has cascading deletes to five children (draft discard), of which `attachment` and the others are indexed, so that path is covered.
- **Business impact:** none at current volume. With tens of thousands of receipts, shipments and task events, request pages and receipt validation slow down progressively and hold shared locks longer, which, with RCSI off, also blocks writers.
- **Recommendation:** one new migration adding about 20 indexes for the rows in the table above. Do not index all 117: every index slows writes, and `purchase_request` already carries 9 nonclustered indexes.
- **Complexity:** S.
- **Verification:** on a database loaded to target volume (report 04), capture actual execution plans for request detail, receipt creation and the approval inbox before and after; `sys.dm_db_missing_index_details` must be empty for these tables after the load test.

### DB-3. Readers block on writers, with no time limit — High — Verified

- **Location:** `is_read_committed_snapshot_on = 0` on every project database (catalog); JDBC URL `lockTimeout=-1` (driver default, visible in the startup log); no `setQueryTimeout`, no `@Transactional(timeout=…)` anywhere except `system/JdbcDatabaseProbe.java:112`.
- **Explanation:** under plain READ COMMITTED a `SELECT` waits for any uncommitted row it needs. Every audited write holds its company's `audit_chain_head` row and the business rows until commit. A reader that touches those rows waits, and nothing bounds the wait. The application lock helper fails fast (2 s, `platform/persistence/TransactionLocks.java:28`), but ordinary row locks do not.
- **Business impact:** one slow or stuck transaction (a long import, a deadlock retry, a paused debugger in UAT, a network stall to the database) freezes every request that reads the same rows. With a pool of 10 connections the whole application stops answering within seconds, with no error and nothing in the log.
- **Recommendation:**
  1. Enable `READ_COMMITTED_SNAPSHOT` on the database (`ALTER DATABASE … SET READ_COMMITTED_SNAPSHOT ON`, a deployment step rather than a Flyway migration, because it needs exclusive access).
  2. Add `lockTimeout=10000` to the JDBC URL and a default transaction timeout.
  3. Map the resulting lock-timeout error to the existing 503 `busy` answer; `platform/web/ApiExceptionHandler.java:63-69` already does this for `PessimisticLockingFailureException`.
- **Risk of the change:** the code that relies on blocking uses explicit lock hints (`UPDLOCK` in `audit/AuditLog.java`, `notification/OutboxSender.java:114`, `messaging/InternalMessagingStore.lockConversation`) or `sp_getapplock`, and those behave the same under RCSI. Code that reads, decides and then writes without a hint is the part to re-test: optimistic `version` checks protect the 18 versioned tables; the unversioned ones (for example `buyer_assignment`, `quotation_line`, `shipment_line`) need a review of each read-then-write. RCSI also adds a 14-byte row version and tempdb load. **Run the full integration and browser suites with RCSI on before UAT.**
- **Complexity:** S to switch, M to re-verify.
- **Verification:** integration test that holds a write transaction open and asserts a concurrent read returns within 100 ms (RCSI) and a concurrent write fails with 503 after the lock timeout.

### DB-4. Tables that grow without cleanup — Medium — Verified

- **Location and evidence:**

| Table | Cleanup | Evidence |
|---|---|---|
| `idempotency_record` | **None in production**: `Idempotency.purge()` exists but is called only from a test | `platform/web/Idempotency.java:104-106`; the only caller is `test/.../OutboxIntegrationTests.java:92` |
| `approval_link_token`, `quote_link_token`, `password_reset_token` | None found (`DELETE FROM` appears for no token table) | grep of `BackEnd/src/main` |
| `in_app_notification` | None | same |
| `internal_message`, `conversation_message`, `assistant_turn` | None, by decision (retention undecided) | `docs/architecture/internal-messaging.md` |
| `notification_outbox` | Purged every 6 h | `notification/OutboxSender.java:78-80,105-106` |
| `audit_event` | Never, by design | ADR-006 |

- **Explanation:** `idempotency_record` stores the full response body of every write made with an `Idempotency-Key`. That is 61 rows after one test run; the frontend sends a key on most writes. The class documents a 24-hour retention that nothing enforces.
- **Business impact:** steady growth of a table read on every write; response bodies (request content, names) kept indefinitely, which is a data-retention exposure; expired tokens kept forever.
- **Recommendation:** call `Idempotency.purge()` from a scheduled job (the code exists); add purges for expired link and reset tokens (keep a period for audit, for example 90 days); decide notification and message retention with the business; plan archival or partitioning of `audit_event` by year once volume is known.
- **Complexity:** S.
- **Verification:** integration test with an adjustable clock (already available in the dev profile): rows older than the retention are gone after the job runs.

### DB-5. Per-company audit chain serialises all writes of a company — Medium — Verified (ceiling: Hypothesis)

- **Location:** `audit/AuditLog.java:52-88` (`lockHead` with `UPDLOCK`, held to commit); chain key `company:<id>` or `global` (`audit/AuditHasher.java:73-75`).
- **Explanation:** every state change appends to one chain and locks its head row until the business transaction commits. Two writes of the same company never overlap from the audit call to commit. Sign-in, sign-out and every event without a company share the single `global` chain.
- **Business impact:** this is the write-throughput ceiling. If an audited transaction takes 20–40 ms after its audit call, one company gets roughly 25–50 writes per second; that is ample for 100 users, a limit for 500 with bursts (month-end, imports). A transaction that takes the head early and then does slow work makes everyone in that company wait. The lock order differs between flows (the audit head is last in submission, mid-transaction in messaging), which is a deadlock risk when a transaction writes two audit events of different chains.
- **Recommendation:** keep the design (it is what makes the chain unforkable). Make the audit call the last statement before commit wherever possible, never do I/O after it, and measure contention in the load test. If the ceiling is reached, split chains further (per company and month) before considering anything heavier.
- **Complexity:** M (review of each write path).
- **Verification:** load test scenario "concurrent writes, one company" (report 04) with `sys.dm_os_wait_stats` (`LCK_M_U`) and `sys.dm_tran_locks` sampled during the run.

### DB-6. Amount and quantity columns without range checks — Low — Verified

- **Location:** 32 `DECIMAL` columns have no CHECK constraint mentioning them (catalog), including `purchase_order.total_amount`, `purchase_order_line.quantity`/`unit_price`, `award_line.quantity`, `quotation.*_amount`, `rfq_line.quantity`, `po_payment_milestone.amount`. `purchase_request_line`, `goods_receipt_line` and `shipment_line` are checked.
- **Explanation:** validation exists in the services, but the database would accept a negative order quantity or amount from a bug, an import or a manual correction.
- **Business impact:** silent corruption of order and award totals that feed the ERP export.
- **Recommendation:** new migration adding `CHECK (… >= 0)` (and `> 0` for quantities), after verifying existing rows.
- **Complexity:** S.
- **Verification:** the migration applies on a copy of production-like data; an integration test asserts the constraint rejects a negative value.

### DB-7. History tables accept any status text — Low — Verified

- **Location:** no CHECK on `purchase_request_transition.from_status/to_status/stage`, `approval_task_event.kind/from_status/to_status/channel`, `approval_task.approver_type`, `notification_outbox.kind`, `in_app_notification.kind`, `document_counter.kind`, `exchange_rate.source`, `audit_event.entity_type` (catalog).
- **Explanation:** current-state columns are constrained (`purchase_request.status`, `approval_task.status`), their history is not.
- **Business impact:** low; a typo in code produces history that reports and timelines cannot interpret.
- **Recommendation:** add CHECKs where the value set is closed (`approval_task_event.channel`, `approval_task.approver_type`, `document_counter.kind`). Leave open sets alone.
- **Complexity:** S.

### DB-8. Searches that cannot use an index — Medium — Verified (cost: Hypothesis)

- **Location:** `LOWER(col) LIKE '%text%'` in `request/RequestLogisticsQueries.java:84-85` (four columns across three joined tables), `supplier/SupplierService.java:55`, `identity/UserAdminStore.java:193`, `identity/UserDirectory.java:113`; `LIKE '%text%'` in `messaging/InternalMessagingStore.java:61,65`.
- **Explanation:** a leading wildcard and a function on the column each prevent an index seek, so every search scans. The databases use a case-insensitive collation, which makes `LOWER()` unnecessary. In `supplier/SupplierService.java:56` the user's `%` and `_` are not escaped (the other searches escape them, each with its own code), so a search for `_` matches everything; this is not an injection, the value is bound.
- **Business impact:** search time grows linearly with requests and suppliers. Fine for thousands of rows, slow for hundreds of thousands.
- **Recommendation:** drop `LOWER()`; escape wildcards consistently with one shared helper; for request search prefer prefix search on the number (`number LIKE 'text%'`, indexable) plus contains-search on the title limited to the user's scope. Full-text search only if measurement requires it (it is a SQL Server feature, not new infrastructure).
- **Complexity:** S.
- **Verification:** execution plans on a loaded database; p95 of the search scenario in report 04.

### DB-9. Offset pagination and double counting on lists — Low — Verified (cost: Hypothesis)

- **Location:** `OFFSET ? ROWS FETCH NEXT ?` with a separate `COUNT(*)` in `notification/NotificationController.java:54-66` (three statements per call), `identity/UserAdminStore.java:197`, and the request lists (`request/ScopedRequests`, JPA specifications with a count query).
- **Explanation:** deep pages scan and discard all earlier rows, and the count repeats the filter. `page * size` at `NotificationController.java:66` is an `int` product with no upper bound on `page`: a very large page overflows to a negative offset and SQL Server rejects it, which surfaces as a 500.
- **Business impact:** low today. Messaging already uses keyset pagination (`beforeId`/`afterId`), which is the pattern to copy if a list becomes slow.
- **Recommendation:** bound `page`; move the notification query out of the controller into a store.
- **Complexity:** S.

### DB-10. Collation is not controlled — Low — Verified

- **Location:** `README.md` instructs `COLLATE Latin1_General_100_CI_AS_SC_UTF8`; the catalog shows `French_CI_AS` on a database created without it. No migration or startup check sets or verifies the collation.
- **Explanation:** 88 `VARCHAR` columns hold codes and statuses. Under a non-UTF-8 collation they are code-page text; behaviour differs between environments (sorting, case rules, and conversion when compared with `NVARCHAR` parameters, which is what the JDBC driver sends by default, `sendStringParametersAsUnicode=true` in the startup log).
- **Business impact:** environment-dependent behaviour; potential index scans on `VARCHAR` keys compared with Unicode parameters (for example `audit_event.chain_key`, `user_role.role`, `purchase_request.status`).
- **Recommendation:** state the required collation in the deployment runbook and fail startup if `DATABASEPROPERTYEX(DB_NAME(),'Collation')` differs. Check plans for `CONVERT_IMPLICIT` on status and key filters under load; if present, set `sendStringParametersAsUnicode=false` only after confirming no `NVARCHAR` column is searched with non-Latin text (Arabic titles are, so a per-query fix is more likely).
- **Complexity:** S for the check; M if implicit conversions are confirmed.
- **Verification:** execution plans with warnings enabled on the loaded database.

### DB-11. Idempotency record is written after the action commits — Low — Verified

- **Location:** `platform/web/Idempotency.java:33-36` ("The action runs in its own transaction and the record is written right after it commits").
- **Explanation:** a crash between the two leaves a committed action without its record, so a client retry runs the action again. The class comment relies on business checks (task no longer pending) to stop the duplicate. That holds for decisions, not necessarily for creations (shipment, invoice, organisation units).
- **Business impact:** rare duplicate creation after a crash at the wrong moment.
- **Recommendation:** list the creation endpoints that have no natural uniqueness and add one (as internal messaging does with `client_message_id`), or write the record inside the action's transaction.
- **Complexity:** M.

### DB-12. Cross-module SQL makes schema changes risky — Medium — Verified

- **Location:** tables read by other modules through SQL: `purchase_order` by dashboard, logistics, messaging, receipt, request; `supplier` by identity, messaging, ordering, sourcing; `approval_task` by identity, messaging, request; `company` by identity, messaging, purchasing; `shipment` by receipt, request; `rfq` by messaging, ordering, request. `messaging/MessagingStore.java:285-302` **inserts into `user_account` and `user_role` and updates `user_account`**.
- **Explanation:** the Spring Modulith test checks Java imports only. A column change in one module can break SQL in another, and the compiler will not say so.
- **Business impact:** regressions on schema changes, found only by integration tests of the other module, if they cover the query.
- **Recommendation:** see report 02, finding A-1. For the database: keep a list of cross-module readers per table next to the migrations and require the integration suite on every migration.
- **Complexity:** M.

## Checked and found sound

- **Types:** all instants `DATETIME2(3)`; all amounts and quantities `DECIMAL(19,4)`, rates `DECIMAL(19,8)`; no `FLOAT`, `MONEY`, `TEXT` or `DATETIME`. `VARCHAR`/`CHAR` only for codes, statuses, hashes and storage keys.
- **Keys:** every table has a primary key; identifiers come from sequences; document numbers come from `document_counter` under `sp_getapplock` (`request/RequestNumbering.java`, `ordering/PoNumbering.java`, `receipt/ReceiptNumbering.java`, `logistics/ShipmentNumbering.java`).
- **Audit integrity inside the database:** append-only trigger on `audit_event` (`V1:197`), no-delete and forward-only triggers on `audit_chain_head` (`V3:28+`), and a foreign key from the head to the last event that blocks `TRUNCATE`.
- **Injection:** no user input is concatenated into SQL. The three dynamic fragments are an integer (`approval/ApprovalStore.java:248`), a column chosen from two constants (`ApprovalStore.java:314-319`) and a constant filter (`notification/NotificationController.java:54`).
- **Outbox:** claimed with `UPDLOCK, READPAST, ROWLOCK`, 20 per batch, stale claims retried with the same Message-ID (`notification/OutboxSender.java:110-124`). Safe with two nodes.
- **Reminders:** `approval_task_reminder` has primary key `(task_id, kind)` (`V5:215-221`), so a reminder cannot be recorded twice.
- **Tokens at rest:** only hashes are stored (`approval_link_token.token_hash`, `quote_link_token`, `password_reset_token.token_hash`).
- **Internal messaging (V22):** canonical conversation by filtered unique index; idempotent sends by unique `(conversation_id, sender_user_id, client_message_id)`; conversation row locked first (deadlock found and fixed on 2026-11-14, test `t10`).
- **Migrations:** additive; `clean-disabled: true`; demo data only in repeatable scripts loaded by the `demo` profile; `ddl-auto: validate`.

## Not verified

- Execution plans and real query cost: the databases hold a few hundred rows; SQL Server recorded no missing-index suggestion and reports most nonclustered indexes as unused, which says nothing at this size.
- Behaviour with `READ_COMMITTED_SNAPSHOT` on.
- Backup and restore: no script or runbook exists (report 08). The databases use the FULL recovery model, so without log backups the log file grows without limit.
- Migration time on a large database (V17 and V20–V21 update existing rows).

## Checks to run on a loaded database

```sql
-- Blocking during the load test
SELECT r.session_id, r.blocking_session_id, r.wait_type, r.wait_time, r.wait_resource FROM sys.dm_exec_requests r WHERE r.blocking_session_id <> 0;
-- Lock waits accumulated
SELECT wait_type, waiting_tasks_count, wait_time_ms FROM sys.dm_os_wait_stats WHERE wait_type LIKE 'LCK_M_%' ORDER BY wait_time_ms DESC;
-- Missing indexes suggested by the optimizer
SELECT OBJECT_NAME(d.object_id), d.equality_columns, d.inequality_columns, d.included_columns, s.user_seeks, s.avg_user_impact
FROM sys.dm_db_missing_index_details d JOIN sys.dm_db_missing_index_groups g ON g.index_handle = d.index_handle
JOIN sys.dm_db_missing_index_group_stats s ON s.group_handle = g.index_group_handle WHERE d.database_id = DB_ID();
-- Most expensive statements
SELECT TOP 20 qs.total_elapsed_time / qs.execution_count AS avg_us, qs.execution_count, SUBSTRING(t.text, 1, 200)
FROM sys.dm_exec_query_stats qs CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) t ORDER BY qs.total_elapsed_time DESC;
-- Implicit conversions in cached plans
SELECT TOP 20 SUBSTRING(t.text, 1, 200) FROM sys.dm_exec_cached_plans p CROSS APPLY sys.dm_exec_query_plan(p.plan_handle) q
CROSS APPLY sys.dm_exec_sql_text(p.plan_handle) t WHERE CAST(q.query_plan AS NVARCHAR(MAX)) LIKE '%CONVERT_IMPLICIT%';
```
