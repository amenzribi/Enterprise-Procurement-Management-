# Product Backlog

Ordered. Items reference the [requirements register](requirements-register.md). Estimates are relative (S/M/L).

## Sprint 1 — Identity, company scope, request drafting

| # | Item | Req | Size |
|---|---|---|---|
| 1 | Spring Security with local accounts (dev/demo), BCrypt/Argon2, session or JWT, sign-in/out screens | FR-IAM-01 | M |
| 2 | Role and scope model (role × company/department/country/category), seeded demo users per role | FR-IAM-02 | M |
| 3 | Company-scope resolver and enforcement in services; cross-company access tests helper | FR-IAM-03 | M |
| 4 | ~~Organisation admin~~ — moved to Sprint 2 (needed with circuit configuration) | FR-ORG-01/02 | M |
| 5 | Request aggregate (header, lines, actors), migration, numbering sequence per company/year | FR-REQ-01 | L |
| 6 | Draft create/edit form (multi-line, validation, EN/FR) | FR-REQ-01 | L |
| 7 | Submit with checks and audit event | FR-REQ-03, FR-AUD-01 | M |
| 8 | "My requests" table with sort/filter/pagination | FR-REQ-01 | M |
| 9 | OpenAPI documentation published in dev | NFR-08 | S |
| 10 | CI pipeline definition (build + tests for both apps) | NFR-07 | S |
| 10a | Sprint 0 review carry-over: CR-19 test of JPA + SQL ids from the shared sequence on the first writable entity; CR-25 verification flags events beyond the chain head; CR-26 short timeout only for the status probe, not the whole pool; CR-27 document `socketTimeout` for production; CR-28 limit `repeatable:missing` to dev/local or justify; CR-06 health details `when-authorized` (S1-AC-13) | NFR-05, NFR-06 | S |

Deferred to S9 (deployment): CR-18 i18n file cache-busting.

## Sprint 2 — Approval circuits

| # | Item | Req | Size |
|---|---|---|---|
| 10b | Sprint 1 review carry-over: S1CR-09 `TransactionLocks` fails fast on an auto-commit connection + DB test that no transaction/lock survives commit and rollback; S1CR-10/11 suggestions (line-number ceiling, per-request user lookup) | NFR-05 | S |
| 11 | Circuit rule table and admin screen | FR-APR-01 | L |
| 12 | Approval state machine, approver inbox, actions with mandatory reasons | FR-APR-02 | L |
| 13 | Delegation (absence and per-task) with authority-limit checks | FR-APR-03 | M |
| 14 | Attachments (request/line), file store port, antivirus hook point | FR-REQ-02 | M |
| 15 | Decision history timeline on request detail | FR-AUD-01 | M |
| 16 | Mobile-optimised approval view | NFR-02 | S |

## Sprint 3 onwards

See [roadmap.md](roadmap.md); items are broken down at each sprint planning from the register rows tagged with that
sprint.
