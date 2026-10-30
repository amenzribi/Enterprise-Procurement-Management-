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

## Sprint 2 carry-over (not closure blockers)

| # | Item | Req | Size |
|---|---|---|---|
| S2-C1 | S2CR-08: proxy-aware client address for one-tap rate limits (trusted-proxy list + per-token limit) | S2-AC-07 | S — **Sprint 9** |
| S2-C2 | Native-speaker review of Arabic UI and e-mail texts | NFR-11, S2DR-13 | S |
| S2-C3 | Admin action to re-route an `ABSENCE_UNRESOLVED` task without waiting for escalation (RR-10) | FR-APR-03 | S |
| S2-C4 | Bind parameters in `ApprovalStore` SQL that currently concatenates numeric ids (RR-07) | NFR-05 | S |

## Sprint 3 carry-over

| # | Item | Req | Size |
|---|---|---|---|
| S3-C1 | ~~Inbox UI filters still missing from S3-AC-01~~ | FR-PUR-01 | S — **Done** in Sprint 3 closure (`e3c4747` / S3-D-01): API + UI expose country, category, amount band, requester, required date, assigned buyer, completeness (plus prior filters) |
| S3-C2 | ~~Buyer suggestion uses lowest open load only~~ | FR-PUR-03 | S — **Done** in Sprint 3 closure (`e3c4747` / S3-D-02): `buyer_category_affinity` then lowest open load |
| S3-C3 | Near-duplicate / grouping of similar needs, internal transfer, buy again | FR-REQ-03, G12 | M — confirm with pilot (**still open**) |

## Sprint 4 carry-over / residuals

| # | Item | Req | Size |
|---|---|---|---|
| S4-C1 | AI quotation reading (provider port + human review) | FR-SRC-05 | L — **Deferred P2** (not in S4 delivery) |
| S4-C2 | Penetration test of public supplier quote links before any production exposure | S4-AC-03, NFR | M — blocked on pilot gate §15 + Sprint 9 security work |
| S4-C3 | Fresh independent QA/reviewer pass on the same integrated S3+S4 revision | delivery rules | M — outstanding (self-labelled closure only; see [sprint-3-4-closure.md](../qa/sprint-3-4-closure.md)) |

## Sprint 5 onwards

See [roadmap.md](roadmap.md). Award / PO / supplier confirmation are Sprint 5 (manual UAT pending). Shipment
tracking (local/international milestones, document references, delay flags) is Sprint 6 without AI (manual UAT
pending). Receiving / manual invoice / user-confirmed closure are Sprint 7 (manual UAT pending; AI three-way
and dashboards/ERP remain deferred to later). Grouping of similar needs, internal transfer and "buy again"
stay confirm-with-pilot (G12). S2CR-08 stays Sprint 9. Native-speaker Arabic review outstanding. Pilot gate §15 unmet.
