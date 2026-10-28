# Review of the Solution Guide v3.0

Source: `docs/specification/Enterprise_Procurement_Intelligence_Solution_Guide_EN_v3.0.md` (October 2026), kept
unchanged next to the original French specification v2.1. Reviewed 2026-10-27 against the specification v2.1, the
recommended changes, and what Sprints 0–1 delivered.

## 1. Verdict

Version 3.0 is the better product definition. It answers most points of our review: it splits the scope into Core,
Trading and Enterprise editions; it adds a pilot-client gate before Sprint 3; it removes the unrealistic "everything
in Phase 1" promise; and it describes one clear end-to-end journey (Sami's pump P-204 request). It becomes the
**primary functional reference** from Sprint 2. The French specification v2.1 remains the detailed reference for
data and rules that v3.0 does not repeat (actors §3.2, closure conditions §13, ERP mappings §21).

## 2. Client decisions on v3.0 conflicts (2026-10-27)

| Topic | v3.0 says | Decision | Consequence |
|---|---|---|---|
| Database | PostgreSQL by default, SQL Server supported, migrations on both (§2, §13, roadmap S1) | **SQL Server only** (ADR-001 stands) | PostgreSQL support recorded as a later add-on. The "no third-party licence" claim (§1) still needs rewording (RC-02). |
| Arabic | Arabic RTL, French, English from Sprint 1 (§1, §2, §13) | **Arabic arrives in Sprint 2** | Sprint 2 adds `ar.json`, RTL layout, Arabic e-mails; from then on all three languages are complete in every sprint. |
| One-tap approval | E-mail, Teams or WhatsApp secure link (§2, §9, UC-06) | **E-mail link + mobile web in Sprint 2**; Teams and WhatsApp later | Teams needs a Microsoft 365 app registration; WhatsApp needs a Business API account. Both are channel adapters on the same signed-link mechanism. |

## 3. What v3.0 changes compared with our plan

| # | Topic | v3.0 | Our plan before | Resolution |
|---|---|---|---|---|
| G1 | Editions | Core / Trading add-on / Enterprise add-ons (§14) | RC-15…RC-20 "Phase 2 moves, awaiting sign-off" | v3.0 settles most of them: external customer journey, sales offers, letters of credit and full intercompany billing are **Trading**; budget control, supplier portal, integrity alerts, Ask the app, ERP APIs are **Enterprise**. RC-15 and RC-16 (LC part) accepted through v3.0. |
| G2 | Document-reading AI | **Core**: AI quote reading (S4), transport documents (S6), invoices (S7), AI request draft from text or photo (Step 1) | RC-17 moved document AI to Phase 2; AI request draft in S8 | Follow v3.0: AI is Core, but each AI feature is an increment behind a provider port with human review and a measured quality gate (RC-08 kept as the method). AI hosting remains an open client decision; it does not block Sprint 2. |
| G3 | Supplier quote link | Suppliers upload quotes through a one-time link, no account (S4) | Manual quote entry only | Adopted for S4. It is the first external-facing surface, so §12 applies: penetration test before it is opened in production. |
| G4 | Status model | 12 user-facing statuses (§8): Draft, Submitted, Returned for revision, Rejected, Approved, Assigned, Quoting, Awarded, Ordered, In transit, Received, Closed | 31 detailed codes, header and line | Keep the detailed codes internally (needed for line tracking and audit) and **display the 12 v3.0 statuses** as the user-facing status. Sprint 1's `CREATED` is shown as "Submitted"; `IN_APPROVAL` also maps to "Submitted". The "Awarded" status is added to the enumeration (S5). |
| G5 | Request number | Example "REQ-2026-00418" | `{COMPANY}-PR-{YYYY}-{seq6}` (S1) | Keep ours: v3.0 gives an example, and per-company numbering is required for multi-company isolation and ERP mapping. A configurable prefix per company is possible later. |
| G6 | Request tracker | Parcel-style timeline with owner and expected date (S2) | Detail page with transition history (S1) | S2 turns it into the tracker: steps, current owner, since when, next actor, expected dates. |
| G7 | Approver roles | Department manager, **budget approver**, technical evaluator (§4) | DEPARTMENT_MANAGER only | S2 adds BUDGET_APPROVER and MANAGEMENT_APPROVER as circuit stage roles; TECHNICAL_EVALUATOR in S4; FINANCE, LOGISTICS, RECEIVER in their sprints. |
| G8 | Questions to the requester | "Ask a question without sending the request back" (Step 2), requester "answers questions" | Planned as a flag in S2 | Kept in S2 (UC-05). |
| G9 | Reminders | Reminders in S2 roadmap | SLA reminders/escalation in S3 | Moved to S2. |
| G10 | Organisation admin, master data import wizard | Import wizard with duplicate detection and AI matching (S3) | Org admin moved to S2 | Org admin and the import wizard go together in **S3**, as in v3.0. S2 only needs approval-rule and delegation administration. |
| G11 | Attachments | Datasheet added at Step 1 | S2 | Kept in S2: approvers need the datasheet to decide. |
| G12 | Grouping, internal transfer | Grouping (S3), internal transfer UC-09 (Core basic) | Grouping deferred | Grouping in S3. Internal transfer (UC-09) is not placed in the v3.0 sprint table; scheduled in S3 as "propose transfer" only, recharge billing is Trading. **Open: confirm with the pilot client.** |
| G13 | Price memory, buy again, delay prediction, supplier scorecard, audit pack | Core smart features | Not planned | Placed in S5 (price memory), S6 (delay prediction), S7 (scorecard), S8 (audit pack). "Buy again" in S3. |
| G14 | Payment milestones | S5 (e.g. 30% advance, 70% on delivery) | RC-16 moved payments to Phase 2 | Payment **milestones tracking** in S5 (Core); letters of credit and guarantees stay Trading. |
| G15 | Invoice matching | Three-way match in Core (S7) | Manual invoice capture only | Three-way match in S7. |
| G16 | Two-step verification | For managers, buyers and finance (§12) | Through the client IdP in S9 | Keep IdP-based MFA for production; add TOTP for local accounts in S9 if the pilot client has no IdP. |
| G17 | Pilot gate | Paying pilot signed before S3 | Not in plan | Added to the roadmap. It is a business gate, not an engineering task. |
| G18 | Workflow engine | "Configurable workflow engine, no programming" | Rule table + state machine (ADR-005) | Consistent: the rule table *is* the configurable engine. A BPMN designer stays out of scope. |
| G19 | Pricing | Requesters and approvers free | — | Product decision; no engineering impact now. Licence metering (counting paid roles) noted for later. |

## 4. Use case traceability (v3.0 §16)

| UC | Use case | Sprint | Status |
|---|---|---|---|
| UC-01 | Sign in and choose the language | S1 (EN/FR), S2 (Arabic) | EN/FR done |
| UC-02 | Create a request (form, text or photo) | S1 form; S8 text/photo AI | Form done |
| UC-03 | Save, edit and submit a draft | S1 | Done |
| UC-04 | Follow a request in the tracker | S2 | Planned |
| UC-05 | Approve, question, return, reject or delegate | S2 | Planned |
| UC-06 | Approve in one tap from e-mail, Teams or WhatsApp | S2 (e-mail); later (Teams, WhatsApp) | Planned |
| UC-07 | Review and assign approved requests | S3 | Planned |
| UC-08 | Group similar needs from several companies | S3 | Planned |
| UC-09 | Propose an internal transfer between subsidiaries | S3 (proposal), Trading (billing) | Planned — confirm |
| UC-10 | Select suppliers and send quote links | S4 | Planned |
| UC-11 | Quick quote for urgent local purchases | S4 | Planned |
| UC-12 | Check AI-read quotes and compare offers | S4 | Planned |
| UC-13 | Review offers technically | S4 | Planned |
| UC-14 | Approve price, currency and payment terms | S4 | Planned |
| UC-15 | Record the award decision | S5 | Planned |
| UC-16 | Create the purchase order and send it to the ERP | S5 (PO), S8 (ERP files) | Planned |
| UC-17 | Supplier confirmation and payment milestones | S5 | Planned |
| UC-18 | Update shipment milestones and documents | S6 | Planned |
| UC-19 | Delay alerts and new arrival dates | S6 | Planned |
| UC-20 | Full, partial or damaged receipt | S7 | Planned |
| UC-21 | Accept a service | S7 | Planned |
| UC-22 | Match order, receipt and invoice | S7 | Planned |
| UC-23 | Track payments | S7 | Planned |
| UC-24 | Recharge costs to a subsidiary | S7 (record), Trading (billing) | Planned |
| UC-25 | Close the request | S7 | Planned |
| UC-26 | Dashboards and CEO view | S8 | Planned |
| UC-27 | Export an audit pack | S8 | Planned |
| UC-28 | Manage users, companies, rules and master data | S2 (rules, delegations), S3 (org, import wizard) | Planned |
| UC-29 | External customer request | Trading add-on | Out of Core |
| UC-30 | Ask the app | Enterprise add-on | Out of Core |

## 5. Risks introduced by v3.0

| Risk | Mitigation |
|---|---|
| Core now contains AI document reading in S4, S6, S7; local AI hardware and quality are unproven. | Provider port, labelled evaluation sets per document type, human check next to the source, feature flag per client. AI hosting decision needed before S4. |
| External surfaces (approval links, supplier quote links) arrive in S2 and S4. | Signed, single-use, short-lived tokens bound to one action; no data beyond the summary without sign-in; rate limiting; audit of every use; penetration test before production exposure. |
| Arabic added mid-project. | Logical CSS properties already used; S2 adds RTL checks to the e2e and axe suites. |
| Sprint 8 now holds dashboards, CEO view, audit pack and ERP files. | Data for "who, since when, late" is captured from S1 (DR-08), so S8 aggregates rather than reconstructs. |
