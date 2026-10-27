# Requirements Register

Traceability from the specification to delivery. Release: **MVP** (Essentials) or **P2** (Phase 2).
Sprint = target sprint in [roadmap.md](roadmap.md). Status: Planned / In progress / Done / Deferred.

## Functional

| ID | Requirement | Spec | Story | Release | Sprint | Status |
|---|---|---|---|---|---|---|
| FR-ORG-01 | Group, companies (parent, subsidiary, central purchasing), sites, departments, cost centres | §3.1, §3.6, §23 | — | MVP | S0 schema, S2 admin | In progress |
| FR-ORG-02 | Per-company currency, language, time zone, ERP company code, purchase mode | §3.6, §3.7 | — | MVP | S0 schema, S1 | In progress |
| FR-IAM-01 | Authentication (local dev accounts; OIDC in production) | §24, §22 | — | MVP | S1 | Done (S1) |
| FR-IAM-02 | Roles of §20 with scope by company/department/country/category | §20 | — | MVP | S1 | Done (S1) |
| FR-IAM-03 | Company isolation for every query and endpoint | §3.6, §17 | — | MVP | S1→all | Done (S1) |
| FR-IAM-04 | MFA for management, purchasing, finance (via IdP) | §24 | — | MVP | S9 | Planned |
| FR-REQ-01 | Multi-line request with unique number and request actors (entered by, requester, beneficiary, requesting/charged/buying company) | §3.2, §6.1 | US-01 | MVP | S1 | Done (S1) |
| FR-REQ-02 | Attachments per request and per line, versioned | §6.1, §19 | US-01 | MVP | S2 | Planned |
| FR-REQ-03 | Pre-submission checks: mandatory fields by category, near-duplicate warning | §6.3 | — | MVP | S1–S2 | Partly done (S1: mandatory fields; duplicate warning in S2) |
| FR-REQ-04 | Stock / framework-contract / price-history checks | §6.3 | — | P2 | — | Deferred |
| FR-REQ-05 | Excel import of a parts list into one request | §6.2 | — | MVP | S7 | Planned |
| FR-REQ-06 | Per-line status, header status derived (assumption A2) | §5 | — | MVP | S1→S6 | Planned |
| FR-APR-01 | Circuit configuration by company, department, category, amount, priority, purchase type | §7.1 | US-04 | MVP | S2 | Planned |
| FR-APR-02 | Approve / reject (reason) / revise / request information / delegate | §7.2 | US-03 | MVP | S2 | Planned |
| FR-APR-03 | Delegation of authority matrix and dated absence delegation | §7.1 | — | MVP | S2 | Planned |
| FR-APR-04 | SLA per step, reminders, escalation | §7.1 | — | MVP | S3 | Planned |
| FR-APR-05 | BPMN graphical designer | §7.1 | — | P2 | — | Deferred (ADR-005) |
| FR-PUR-01 | Central inbox of approved requests with filters | §8 | US-05 | MVP | S3 | Planned |
| FR-PUR-02 | Completeness check and return to requester | §8 | — | MVP | S3 | Planned |
| FR-PUR-03 | Rule-based assignment; buyer accepts or declines with reason | §8 | US-05 | MVP | S3 | Planned |
| FR-PUR-04 | Purchase mode per request: direct / centralised / on behalf | §3.7 | US-15 | MVP | S3 | Planned |
| FR-PUR-05 | Grouping similar requests from several subsidiaries into one RFQ | §8 | — | P2 | — | Deferred |
| FR-PUR-06 | AI buyer suggestion | §8, §17 | — | P2 | — | Deferred |
| FR-SRC-01 | Supplier registry (local by country, international), categories | §14 | — | MVP | S4 | Planned |
| FR-SRC-02 | RFQ local (quick quote) and international | §9.1 | — | MVP | S4 | Planned |
| FR-SRC-03 | Manual quotation entry with currency, lead time, payment terms, Incoterm, validity, compliance | §9.2 | US-06 | MVP | S4 | Planned |
| FR-SRC-04 | Conversion to comparison currency, landed-cost estimate, comparison table | §9.2 | US-06 | MVP | S4 | Planned |
| FR-FX-01 | Currencies with minor units, exchange rates by date and source | §9.1, §15 | — | MVP | S0 currencies, S2 rates | In progress |
| FR-SRC-05 | AI reading of quotations | §9.2, §17 | — | P2 | — | Deferred |
| FR-EVL-01 | Technical, commercial, management validation with thresholds and reasons | §10 | US-07 | MVP | S5 | Planned |
| FR-EVL-02 | Committees, conflict-of-interest declarations, Arabic/French minutes | §10 | — | P2 | — | Deferred |
| FR-ORD-01 | PO in platform or external reference, local/international, status tracking | §11 | — | MVP | S5 | Planned |
| FR-ORD-02 | Payments, LCs, guarantees with expiry alerts | §11 | — | P2 | — | Deferred |
| FR-LOG-01 | Shipment milestones local/international, planned vs actual, late alerts | §12.1 | US-08 | MVP | S5 | Planned |
| FR-LOG-02 | Transport documents attached and tracked | §12.2 | US-08 | MVP | S6 | Planned |
| FR-RCV-01 | Receipt full/partial/non-conforming with photos; service acceptance | §13 | US-09 | MVP | S6 | Planned |
| FR-RCV-02 | Closure conditions (assumption A7) | §13 | — | MVP | S6 | Planned |
| FR-RCV-04 | Manual final-invoice capture (number, amount, currency, document) | §13 | — | MVP | S6 | Planned |
| FR-RCV-03 | Three-way match PO/receipt/invoice | §13 | — | P2 | — | Deferred |
| FR-DSH-01 | Pipeline indicators and filters | §16.1 | — | MVP | S7 | Planned |
| FR-DSH-02 | Management "where / who / how much / late / when" view | §16.2 | US-10 | MVP | S7 | Planned |
| FR-AUD-01 | Complete decision history per request | §7.2, §24 | US-11 | MVP | S0 table, S2 view | In progress |
| FR-ERP-01 | Export approved requests and POs (CSV/Excel) | §21.1 | US-12 | MVP | S7 | Planned |
| FR-ERP-02 | Import suppliers, articles, statuses (CSV/Excel) | §21.1 | US-12 | MVP | S7 | Planned |
| FR-ERP-03 | API connectors (SAP, Oracle, Dynamics, Sage, Odoo) | §21.2–§21.4 | — | P2 | — | Deferred |
| FR-AI-01 | Free text → structured draft request, human-reviewed | §6.2, §17 | US-02 | MVP | S8 | Planned |
| FR-EXT-01 | External customer requests, sales quotes, customer orders | §3.4, §17 M17 | US-13, US-14 | P2 | — | Deferred |
| FR-FIN-01 | Internal recharging of centralised purchases | §3.3, §23 | US-16 | P2 | — | Deferred |
| FR-BUD-01 | Budget lines, pre-commitment, commitment, consumption | §15 | — | P2 | — | Deferred |
| FR-INT-01 | Integrity signals | §18 | — | P2 | — | Deferred |

## Non-functional

| ID | Requirement | Spec | Sprint | Status |
|---|---|---|---|---|
| NFR-01 | Common screens < 2 s at agreed load | §25 | S9 (measured from S3) | Planned |
| NFR-02 | Responsive: validation and receipt from a phone | §25, §7.1, §13 | S0 shell → all | In progress |
| NFR-03 | English and French, user-selectable, persisted | Client decision (RC-03) | S0 → all | In progress |
| NFR-04 | Multi-currency, UTC storage, per-company time zone | §22 | S0 schema → S4 | In progress |
| NFR-05 | Tamper-evident audit trail | §24 | S0 → all | In progress |
| NFR-06 | No secrets in source; configuration by environment | §24 | S0 | In progress |
| NFR-07 | Automated tests and CI | §25 | S0 → all | In progress |
| NFR-08 | Documented REST API (OpenAPI) | §22, §25 | S0/S1 | In progress |
| NFR-09 | WCAG 2.2 AA for core journeys | Client instruction | S0 → all | In progress |
| NFR-10 | Backups, restore test, DR plan | §24 | S9 (runbook) | Planned |
| NFR-11 | Arabic / RTL | §22 | P2 | Deferred (RC-03) |
