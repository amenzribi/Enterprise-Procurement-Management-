# ADR-014 — Database isolation: read committed snapshot, lock and transaction timeouts

| | |
|---|---|
| Status | Timeouts: applied in Sprint 1. `READ_COMMITTED_SNAPSHOT`: **direction approved** on 2026-11-16 for testing on a database copy; **not enabled on any database**; enabling needs a separate approval per database. Plan: `ADR-013-to-017-implementation-plans.md` |
| Date | 2026-11-16 |
| Sprint | 1 (hardening) |
| Audit | DB-3, P-1 (`docs/audit/05-database-review.md`, `04`) |

## Context

The databases run plain READ COMMITTED: a reader waits for any uncommitted writer, and before Sprint 1 nothing limited the wait. With a pool of 10 connections one stuck transaction could stop the application without an error.

## Decided and applied in Sprint 1

| Setting | Value | Effect |
|---|---|---|
| `SET LOCK_TIMEOUT` on every pooled connection | 10 s (`DB_LOCK_TIMEOUT_MS`) | A statement that waits longer fails with error 1222, answered 503 `busy` |
| Default transaction timeout | 30 s (`DB_TRANSACTION_TIMEOUT`) | A longer transaction is rolled back, answered 503 `busy` |

These are session settings of the application's own connections. They are reversible by configuration.

## Proposed: enable `READ_COMMITTED_SNAPSHOT` (RCSI)

| Option | For | Against |
|---|---|---|
| A. Keep READ COMMITTED with the timeouts | No change | Readers still queue behind writers; under load users see "busy" instead of data |
| B. Enable RCSI on each database | Readers never wait for writers; standard setting for web applications on SQL Server and the default on Azure SQL | Row versions in tempdb; 14 bytes per modified row; code that reads, decides and writes without a lock must be rechecked |
| C. SNAPSHOT isolation per transaction | Finer control | Code changes in every transaction; update conflicts to handle |

**Proposed decision: option B.**

- What stays correct by construction: every place that relies on blocking uses an explicit lock (`UPDLOCK` on the audit chain head, the outbox claim, the conversation lock) or `sp_getapplock`; these behave the same under RCSI. The 18 versioned tables are protected by their optimistic version.
- What must be rechecked before enabling: read-then-write on unversioned tables (`buyer_assignment`, `quotation_line`, `shipment_line`) and the quantity checks on receipts and shipments, which read sums and then insert.

## Procedure, rollback (not executed)

`scripts/db/enable-read-committed-snapshot.sql` holds the steps:

1. On a copy of the database: enable RCSI, run the full backend suite and the browser suite. Only if both pass:
2. Maintenance window, application stopped, backup (`COPY_ONLY`, `CHECKSUM`).
3. `ALTER DATABASE … SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE`.
4. Start, smoke test.

Rollback: the same statement with `OFF` in the same window. No data is converted in either direction.

## Approval needed

1. Option B.
2. On which databases and when (the test database first).
