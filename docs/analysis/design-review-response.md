# Lead Response to the Sprint 0 Design Review

Review: [../reviews/sprint-0-design-review.md](../reviews/sprint-0-design-review.md). Each finding is accepted,
adapted, or rejected with a reason. Where the fix lands in a document, that document has been updated.

| Finding | Decision | Where resolved |
|---|---|---|
| DR-01 Visibility model (blocker for S1) | Accepted. Visibility model defined below; implemented and tested in S1. | ADR-007 (decision log) |
| DR-02 Central enforcement of scope | Accepted. Mandatory scoped-query component + architecture test; RLS spike in S1. | ADR-007 |
| DR-03 Hash-chain concurrency/atomicity/canonical form | Accepted. Per-company chain, chain-head row locked in the business transaction, fixed canonical form. Applied in Sprint 0 code. | ADR-006 |
| DR-04 Chain rewritable by DB principals | Accepted. Separate migration/runtime principals decided in S1, external anchoring of chain head in S9. | ADR-006, roadmap S9 |
| DR-05 FX needed by S2 thresholds | Accepted. Currency and rate reference data moves to S2; thresholds in company currency at submission-date rate. | Roadmap S2 |
| DR-06 One currency per request | Accepted. | S1-AC-03 |
| DR-07 Spec statuses with stable codes | Accepted. S1 submit = `DRAFT → CREATED`; full enumeration published in S1. | S1-AC-05 |
| DR-08 Owner and stage timeline from S1 | Accepted. | S1-AC-11, roadmap rule |
| DR-09 Manual final-invoice capture | Accepted; added to S6. | Roadmap S6, register FR-RCV-04 |
| DR-10 Unrecorded Phase 1 deferrals | Accepted. Each listed as an RC row; **client sign-off required before S1 planning**. | recommended-changes RC-15…RC-20 |
| DR-11 Parallel stages | Accepted. Circuit = ordered stages, each with ALL/ANY approvers and optional condition. | ADR-005 |
| DR-12 Notification channel | Accepted. SMTP via outbox from S2, localised templates, deep links. | ADR-009, roadmap S2 |
| DR-13 Language on user account | Accepted. `preferred_language` on `user_account` from S1; localStorage is pre-login cache. | S1-AC-12 |
| DR-14 Integrated auth is dev-only | Accepted. Non-dev profiles fail fast without `DB_URL`. Applied in Sprint 0 code. | ADR-001 |
| DR-15 Status endpoint disclosure | Accepted. Details gated by `app.status.expose-details` (dev/demo only) in S0; admin-only in S1. | S1-AC-13 |
| DR-16 Precision/rounding | Accepted. Rules below. | Architecture §2 |
| DR-17 Timestamp type | Accepted. `DATETIME2(3)` UTC ↔ `Instant`; `DATE` ↔ `LocalDate`. Applied in S0. | ADR-001 |
| DR-18 Keys and business numbers | Accepted. `BIGINT` from sequences; counter table locked at submission. Applied in S0. | ADR-001 |
| DR-19 Collation | Adapted. Keep UTF-8 collation (harmless, aligns `VARCHAR` should any appear); `NVARCHAR` throughout; `COLLATE DATABASE_DEFAULT` in temp tables; duplicate detection uses a normalised (accent-stripped, upper-case) key column. | ADR-001 |
| DR-20 Supported editions | Accepted. Minimum SQL Server 2019 Standard; Express (with Advanced Services for full-text) for small installs. | ADR-001 |
| DR-21 Idempotency | Accepted. `Idempotency-Key` on state-changing POSTs from S2; export batch id for ERP files. | Architecture §2 |
| DR-22 404 vs 403 | Accepted. Out of scope → 404; in scope but not permitted → 403. | Architecture §5 |
| DR-23 S1 AC testability | Accepted. Timeouts and demo-user table added. | S1-AC-01/02 |
| DR-24 Modules created on demand | Accepted. Applied in S0 code. | ADR-002 |
| DR-25 AI quality gate now | Accepted. Metrics fixed now; evaluation-set collection starts in S1 with demo data. | Roadmap S8 |
| DR-26 Automated accessibility checks | Accepted. axe-core checks from S1. | Architecture §6 |
| DR-27 CI platform | Accepted as an open decision for the client (repository host unknown). | Open questions |
| DR-28 ERP codes on aggregates | Accepted. `erp_company_code` in S0; each new aggregate gets `erp_code`. | Architecture §3 |
| DR-29 Committed-data tests | Accepted (QA strategy owner informed). | QA strategy |
| DR-30 Spring Boot 4.1 decision | Accepted. | ADR-008 |
| DR-31 Team capacity, S6 overload | Accepted. Assumption: 3 full-time engineers + part-time QA. Logistics milestones move from S6 to S5. | Roadmap |

## Visibility model (DR-01)

A request carries `requesting_company_id`, `charged_company_id`, `buying_company_id` (and later
`processing_company_id` when a central purchasing company handles it). Access is granted when the user's scope
includes the company named in the column relevant to their role:

| Role | Column(s) granting visibility | From status |
|---|---|---|
| Requester / department manager / company approvers | `requesting_company_id` (+ department scope) | Draft (own) / Created |
| Central purchasing manager, buyer | `processing_company_id` or `buying_company_id` = their company | Approved (in purchasing review) |
| Finance | `charged_company_id`, `buying_company_id` | Approved |
| Logistics / store | `buying_company_id` or delivery site's company | Order issued |
| Group management | All companies of the group | All except other users' drafts |

Supplier prices and quotations are visible only to purchasing, evaluators assigned to the request, finance and
group management (§20).

## Precision and rounding (DR-16)

Quantity `DECIMAL(19,4)` with unit of measure. Amounts `DECIMAL(19,4)` + ISO-4217 code. Currency table stores
minor units (TND 3, EUR 2, USD 2). FX rate `DECIMAL(19,8)` = units of quote currency per 1 unit of base currency,
with date and source, stored on each converted document. Rounding `HALF_UP` to the currency's minor units at line
and document totals (matches common Tunisian/French ERP practice).

## Open questions for the client

1. Sign-off on Phase 1 → Phase 2 moves (RC-05, RC-15…RC-20).
2. Repository host / CI platform (GitHub, GitLab, Azure DevOps).
3. AI hosting: local GPU server or a client-approved endpoint (decides S8 feasibility and cost).
4. PO vs approved amount tolerance (recommended: lower of 10% or a per-company cap, with reason; above → management
   re-approval) and over-receipt (recommended: blocked by default, per-category tolerance optional).
