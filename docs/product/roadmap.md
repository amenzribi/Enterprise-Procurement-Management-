# Sprint Roadmap — Core edition

Realigned on 2026-10-27 to the Solution Guide v3.0 (`docs/analysis/solution-guide-v3-review.md`).

Two-week sprints with a demo at the end of each. Each sprint delivers a complete vertical slice: database migration,
backend, frontend, permissions, audit, EN/FR, and automated tests. A sprint is complete only when QA and the
reviewer have checked the same integrated revision and no blocking defect remains. From S1 onwards every sprint's
acceptance includes: "request detail shows current stage, owner and since when" for the stages it adds (DR-08).

Capacity assumption: 3 full-time engineers (backend, frontend, full-stack) plus part-time QA. The plan must be
re-validated if the team differs.

| Sprint | Objective | Main content | Use cases (v3.0) | Status |
|---|---|---|---|---|
| S0 | Foundation | Spring Boot modular monolith, Angular shell and design system, SQL Server + Flyway schema, health/connectivity check, EN/FR, test baseline | — | Done |
| S1 | Identity, company scope, request drafting | Sign-in, roles and scopes, company isolation, create/edit/submit multi-line request with unique number, "My requests", detail | UC-01 (EN/FR), UC-02 (form), UC-03 | Done |
| S2 | Approvals, tracker, Arabic | Arabic + RTL; approval rules (stages ALL/ANY, thresholds in company currency, exchange rates); approval inbox; approve / ask a question / return / reject / delegate; absence delegation; one-tap e-mail approval link + mobile approval screen; reminders and escalation; e-mail + in-app notifications via outbox; request tracker; attachments; v3.0 user-facing statuses | UC-01 (AR), UC-04, UC-05, UC-06 (e-mail), UC-28 (rules) | Done (local closure 2026-10-29; native Arabic review outstanding) |
| Gate | Pilot client signed | Business gate before S3 (v3.0 §15); the pilot shapes S3–S9. **Not passed.** Restricts rollout, not local development. | — | Unmet |
| S3 | Central buying queue | Buying queue with filters, completeness check, question to requester, buyer assignment with suggestion by category and workload, purchase mode, grouping of similar needs, internal transfer proposal, buy again, supplier list, organisation admin, master data import wizard (duplicate detection) | UC-07, UC-08, UC-09, UC-28 (org, master data) | Locally implemented 2026-10-29 (developer). Grouping / internal transfer / buy again remain G12 (not in this slice). Pilot gate unmet. Not independently reviewed. |
| S4 | Quotes and evaluation | Quote requests, secure supplier quote link (no account), Quick quote, comparison, landed cost, technical evaluation, commercial validation. AI quote reading deferred (FR-SRC-05 / no provider port). | UC-10 – UC-14 | Locally implemented 2026-10-30 (developer). Self-labelled S3+S4 closure (`docs/qa/sprint-3-4-closure.md`). AI reading deferred. Pilot gate unmet. Not independently reviewed. |
| S5 | Award and order | Award decision with reason, purchase order, supplier confirmation, payment milestones, price memory | UC-15 – UC-17 | Locally implemented 2026-10-30 (developer). Pilot gate unmet. Not independently reviewed. |
| S6 | Shipment tracking | Milestones local/international, transport document references, delay flags (AI reading / arrival ML deferred) | UC-18, UC-19 | Locally implemented 2026-10-30 (developer). Manual UAT pending. Pilot gate unmet. Not independently reviewed. |
| S7 | Receipt, invoice, closure | Goods receipt (full/partial/damaged) with evidence references, service acceptance attestation, manual invoice + match attest, payment status, user-confirmed closure + lite score (AI / algorithmic three-way / dashboards / ERP deferred) | UC-20 – UC-25 | Locally implemented 2026-10-30 (developer). Manual UAT pending. Pilot gate unmet. Not independently reviewed. |
| S8 | Visibility and ERP files | Dashboards, CEO view, audit pack (PDF), ERP file exchange. AI request draft (UC-02) blocked — no provider. | UC-26, UC-27, UC-16 (ERP files) | Locally implemented 2026-10-31 (developer). Manual UAT pending for S5–S8. Pilot gate unmet. Not independently reviewed. |
| S9 | Pilot readiness (security hardening) | Proxy-aware rate limits (S2CR-08); supplier-link revoke/rate-limit/IT; security regression. MFA, restore drill, training, go-live, independent pen-test remain **gates** (not claimed done). | — | Locally implemented 2026-10-31 (developer). Manual UAT pending for S5–S9. Pilot gate unmet. Not independently reviewed. |
| S10 | Global administration | GLOBAL_ADMIN: users, activation, roles/membership, password setup/reset/change, session revocation, audit. Extends identity — not a parallel IAM. | FR-IAM-* extension | Locally implemented 2026-10-31 (developer). Manual UAT pending for S5–S10. Pilot gate unmet. Not independently reviewed. |
| S11 | **Proposed** — Buyer–supplier communication | Contextual RFQ/PO messaging; supplier-visible vs internal threads; external identity model; notifications; real send disabled in demo | New (requested) | **Proposal** |
| S12 | **Proposed** — Internal AI assistant | Status/explain/find/summarise/draft-for-review; permissions + citations; no autonomous actions; blocked until hosting/provider ADR | FR-AI-* / UC-02 class | **Proposal** |
| After | Add-ons | Trading (external customers, sales offers, LC, intercompany billing), Enterprise (budget, supplier portal, integrity, ERP APIs), Teams/WhatsApp approval channels, PostgreSQL support | UC-29, UC-30 | By demand |

Add-ons follow the editions of the Solution Guide v3.0 §14 (Trading, Enterprise) and are scheduled by client demand.

## Why this order

The journey is strictly sequential (a request must exist before it is approved, approved before it is bought), so
each sprint unlocks the next and is demonstrable end to end. Identity and company isolation come first because
retrofitting them is the most expensive defect class in multi-company systems. AI comes after the manual journey
works, so its output lands in a validated form and its quality can be measured against real fields.

## Sprint 1 — objective and acceptance criteria

**Objective:** an authenticated requester in one company can draft, edit and submit a multi-line request and see it
in "My requests"; nobody can see another company's data.

| ID | Criterion |
|---|---|
| S1-AC-01 | Users sign in with demo accounts (dev/demo profile only); passwords hashed; sessions expire after 30 min idle and 8 h absolute; sign-out works. |
| S1-AC-02 | Demo users are seeded per the table below; the current user, company and roles are shown in the shell. |
| S1-AC-03 | A requester creates a draft with header fields (§6.1 identification and classification, one currency per request) and 1..n lines (description, quantity > 0 as DECIMAL(19,4), unit, estimated unit price ≥ 0, required date, delivery location). |
| S1-AC-04 | The request records entered-by, requester, beneficiary, requesting, charged and buying company (§3.2); entered-by may differ from requester when the user holds the "enter on behalf" permission. |
| S1-AC-05 | Submission validates mandatory fields, assigns a unique number `{COMPANY}-PR-{YYYY}-{seq6}` (drafts have no number), moves `DRAFT → CREATED` (the S1 end state until circuits exist in S2), and writes an audit event in the company's hash chain in the same transaction. The full status enumeration (header and line, spec §5) with EN/FR labels is published. |
| S1-AC-06 | "My requests" lists requests with number, title, status, amount and currency, required date; sortable, filterable, paginated; with loading, empty and error states. |
| S1-AC-07 | API requests for a request in another company return 404 (not 403, to avoid disclosure); automated tests prove it for every new endpoint. |
| S1-AC-08 | Concurrent edits of a draft are rejected with a clear message (optimistic locking). |
| S1-AC-09 | All new screens and validation messages are complete in English and French; amounts and dates are locale-formatted. |
| S1-AC-10 | Forms are keyboard-operable, labelled, announce errors to screen readers, and work at 360px width; automated axe-core checks pass on new screens. |
| S1-AC-11 | Request detail shows current stage, current owner and since when; every transition records owner and timestamp. |
| S1-AC-12 | The language preference is stored on the user account and used after sign-in on any device; before sign-in the browser choice applies. |
| S1-AC-13 | `/api/v1/system/status` version details and actuator health details are visible only to administrators; anonymous callers get UP/DOWN only. |
| S1-AC-14 | Cross-company isolation is enforced through the central scoped-query component, and an architecture test fails if a controller uses an unscoped repository. |

### S1 demo users (demonstration data)

| User | Company | Roles |
|---|---|---|
| requester.plant.tn | Plant subsidiary (Tunisia) | Requester |
| assistant.plant.tn | Plant subsidiary (Tunisia) | Requester, enter on behalf |
| manager.maint.tn | Plant subsidiary (Tunisia) | Department manager (Maintenance) |
| requester.plant.fr | Plant subsidiary (France) | Requester |
| buyer.central | Central purchasing company | Buyer |
| purchasing.manager | Central purchasing company | Purchasing manager |
| ceo.group | Holding | Group management |
| admin | Holding | Administrator |

## Sprint 2

Objective and acceptance criteria: [sprint-2.md](sprint-2.md).
