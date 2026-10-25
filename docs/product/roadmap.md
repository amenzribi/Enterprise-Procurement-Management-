# Sprint Roadmap — Essentials MVP

Two-week sprints with a demo at the end of each. Each sprint delivers a complete vertical slice: database migration,
backend, frontend, permissions, audit, EN/FR, and automated tests. A sprint is complete only when QA and the
reviewer have checked the same integrated revision and no blocking defect remains. From S1 onwards every sprint's
acceptance includes: "request detail shows current stage, owner and since when" for the stages it adds (DR-08).

Capacity assumption: 3 full-time engineers (backend, frontend, full-stack) plus part-time QA. The plan must be
re-validated if the team differs.

| Sprint | Objective | Main content | Stories |
|---|---|---|---|
| S0 | Foundation | Spring Boot modular monolith, Angular shell and design system, SQL Server + Flyway schema, health/connectivity check, EN/FR, test baseline | — |
| S1 | Identity, company scope, request drafting | Login (local dev accounts, OIDC-ready), roles and scopes, company isolation, organisation admin (read + edit), create/edit/submit multi-line request with unique number, "my requests" list | US-01 (without attachments) |
| S2 | Approval circuits | Currencies and exchange rates (admin entry), staged circuits (ALL/ANY) per company/category/amount compared in company currency, approver inbox, approve/reject/revise/request info/delegate, absence delegation, idempotent actions, attachments, e-mail notifications via outbox, decision history view | US-03, US-04, US-01, US-11 (partial) |
| S3 | Central purchasing | Central inbox with filters, completeness check, return, assignment rules, accept/decline, purchase mode, SLA reminders/escalation (outbox + scheduler), in-app notifications | US-05, US-15 |
| S4 | Sourcing and comparison | Supplier registry, RFQ local/international, manual quotation entry, conversion with stored rates, landed cost, comparison table | US-06 |
| S5 | Evaluation, order, shipment milestones | Technical/commercial/management validation with thresholds, award decision, PO record and status tracking, shipment milestones local/international with planned vs actual dates and late flags | US-07, US-08 (milestones) |
| S6 | Receipt and closure | Transport documents, late alerts, partial/non-conforming receipt with photos, service acceptance, manual final-invoice capture (number, amount, currency, document), closure conditions | US-08 (documents), US-09 |
| S7 | Visibility and ERP files | Pipeline dashboard, management one-click view, audit viewer, CSV/Excel export, supplier/article import, Excel import of parts list | US-10, US-11, US-12 |
| S8 | AI-assisted request drafting | AI provider port (local model or client-approved endpoint), free-text → draft request, field-level provenance and confidence, mandatory human review, labelled EN/FR evaluation set (collected from S1) and quality gate: per-field exact match for quantity and unit ≥ 95%, item description accepted without edit ≥ 80%, zero automatic submissions | US-02 |
| S9 | Hardening and release candidate | MFA via IdP, performance tests vs NFR-01, security review, separate DB principals verified, external anchoring of audit chain heads, backup/restore runbook, UAT with demo group, documentation and training material | — |

Phase 2 candidates (ordered by commercial value): budget control, external customer requests and sales quotes,
document-reading AI, supplier portal, internal recharging, integrity signals, NL assistant, API ERP connectors,
Arabic/RTL, vertical packs.

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
