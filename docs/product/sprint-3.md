# Sprint 3 — Central buying queue, suppliers, organisation, import

Status: locally implemented 2026-10-29 (developer). Pilot gate unmet. Not independently reviewed.
Two weeks · Reference: Solution Guide v3.0 §3.7, §8 (M3), §14, §20, UC-07, UC-08, UC-09,
UC-28 (org, master data); French spec v2.1 §3.6–3.7, §8, §14, §21; register FR-PUR-01…04, FR-SRC-01 (list only),
FR-ORG-01/02, FR-ERP-02 (wizard only), FR-REQ-03 (near-duplicate warning).

## Pilot-client business gate

Solution Guide v3.0 §15: a paying pilot client must be signed before sprint 3, and that client then shapes
sprints 3–9. **This gate is unmet.** It restricts **rollout** (no production, no pilot tenant, no claim that a
client approved the slice). It does **not** block local development against the demo group. Do not invent
approval or record the gate as passed.

## Objective

An approved request appears in the buying company's purchasing inbox. A purchasing manager checks completeness,
sets or confirms the purchase mode, and assigns a buyer; the buyer accepts or returns with a reason. The group
has a supplier register (shared and company-scoped), organisation administration, and a master-data import wizard
that reports row-level errors. Cross-subsidiary purchasing access is only through an explicit authorised
relationship (`buying_company_id` / processing company), never by dropping company-scoped queries.

## Out of scope

RFQs, quotation comparison, AI buyer/quote suggestions, supplier portals, internal refacturing, live ERP
connectors or APIs. Grouping of similar needs, internal transfer proposals and "buy again" wait for the pilot
(G12). Placeholder screens and fake successful imports are not delivered functionality.

## Acceptance criteria

| ID | Criterion |
|---|---|
| S3-AC-01 | **Buying queue.** Requests in `IN_PURCHASING_REVIEW` or `ASSIGNED_TO_BUYER` whose `buying_company_id` is a company where the user holds BUYER or PURCHASING_MANAGER appear in `GET /api/v1/purchasing/queue`. Filters: company, country, category, priority, amount band, status, requester, required date, purchase mode, assigned buyer, completeness, text search. Pagination and sort. Completeness is computed server-side (mandatory header/lines/attachments for the category). |
| S3-AC-02 | **Completeness and return.** A purchasing manager or assigned buyer can ask the requester a purchasing question (SLA-free, does not restart approval) or **return the dossier** with a reason → `PURCHASING_RETURNED`. The authors may edit and resubmit; resubmit returns to `IN_PURCHASING_REVIEW` **without** a new approval round. Approval return (`REVISION_REQUESTED`) is unchanged. |
| S3-AC-03 | **Buyer assignment.** A PURCHASING_MANAGER of the buying company assigns a buyer who holds BUYER in that company. Suggestion (not auto-assign): same category affinity if recorded, else lowest open assigned-and-accepted workload. Assignment is optimistic (`version`) under the request lock. The buyer **accepts** or **returns** with a reason; history is append-only (`buyer_assignment`). Reassignment supersedes the previous row. Only one ACCEPTED assignment per request. |
| S3-AC-04 | **Purchase mode.** On a request in purchasing, a PURCHASING_MANAGER may set the effective mode (`DIRECT`, `CENTRALIZED`, `ON_BEHALF`) within what the requesting company's `purchasing_mode` allows (`MIXED` allows all three; a fixed company mode only allows itself). `charged_company_id` (cost allocation) never changes here. `buying_company_id` becomes the requesting company (DIRECT / ON_BEHALF) or the group's purchasing centre (CENTRALIZED). `buying_company_proposed` becomes false. Audit records previous and new mode and buying company. |
| S3-AC-05 | **Supplier register.** List and maintain suppliers of the caller's group. Visibility: `SHARED` (group-wide) or `COMPANY` (owning company, plus extra `supplier_company` grants). A supplier referenced by a request, assignment or (later) RFQ cannot be hard-deleted (deactivate only). Codes unique per group. EN/FR/AR screens, pagination, search, country and category. No supplier portal, no RFQ. |
| S3-AC-06 | **Organisation administration.** ADMIN of the group creates and updates companies (type, country, currency, language, time zone, purchasing mode, parent, ERP code), sites, departments and cost centres. Codes unique per parent. Deactivating a company does not delete requests. Cross-group → 404. |
| S3-AC-07 | **Master-data import.** ADMIN or PURCHASING_MANAGER uploads CSV (UTF-8) for `SUPPLIER` or `ORGANISATION` (sites/departments/cost centres). Authorised, validated, row-level errors, duplicate detection (normalised code / tax id / name+country), all-or-nothing per file inside one transaction, safe retry of a failed job (same file hash does not double-insert). Result page lists accepted / rejected rows. Never reports success without persisting. No live ERP. |
| S3-AC-08 | **Visibility and isolation.** BUYER / PURCHASING_MANAGER see a request iff they hold that role in `buying_company_id` (or, after assignment, the assigned buyer) **and** the status is at purchasing or later (not drafts, not in-approval). ADMIN still sees no business requests. Out of scope → 404; forbidden action on a visible request → 403. Every new endpoint has a cross-company test. Do not obtain group visibility by removing scoped queries. |
| S3-AC-09 | **Audit and notices.** Assign, accept, return, mode change, completeness return, supplier create/update/deactivate, org create/update, import commit: audit event in the same transaction (ADR-006). Requester and buyer are notified of assignment, accept, return and completeness return. |
| S3-AC-10 | **Quality.** EN/FR/AR including RTL; loading/empty/error; 360 px; axe on queue, supplier list, org admin, import; e2e: Sami submits → Karim approves → Ines sees it in the CPA queue → Mehdi assigns Ines → Ines accepts; FR manager cannot see the TN request; Claire cannot see the CPA queue item. Backend tests against SQL Server. |

### Demo data (demo profile only)

Existing: `buyer@demo.local` (Ines, BUYER, DEMO-CPA), `purchasing.manager@demo.local` (Mehdi, PURCHASING_MANAGER,
DEMO-CPA). Labelled "(Demo)" suppliers: one SHARED (international bearings, country DE), one COMPANY scoped to
DEMO-TN-PLT. Lyon (CENTRALIZED) and Sfax (MIXED) approved requests propose the purchasing centre as buying company
so Ines and Mehdi see them in the CPA queue; purchasing may then set DIRECT / ON_BEHALF (buying becomes the
requesting company). `charged_company_id` never changes with the mode.

## Technical design

| Topic | Decision |
|---|---|
| Modules | New `purchasing` (queue, assignment, mode, completeness). New `supplier`. Organisation writes stay in `organization`. Import in `organization` (org + supplier files dispatched to the owning module). Request status and visibility stay in `request`. |
| Statuses | Final approval moves `IN_APPROVAL → IN_PURCHASING_REVIEW` (user status still APPROVED). Assignment → `ASSIGNED_TO_BUYER` (user ASSIGNED). Purchasing return → `PURCHASING_RETURNED` (user RETURNED). Resubmit from purchasing return → `IN_PURCHASING_REVIEW`. |
| Visibility | Extend `RequestAccessPolicy.visibleTo` with BUYER / PURCHASING_MANAGER predicates on `buying_company_id` + purchasing statuses. Assigned buyer is also a participant. |
| Locks | Request row first (`getForSystemUpdate` / `lockExclusively("request:" + id)`), then assignment insert, then audit. Optimistic `version` on assign/accept/return/mode. |
| Import | Store the file in the existing document store; parse CSV; validate; on commit insert in one transaction. Failed rows never insert. Duplicate = same group + normalised code, or same tax id. |

## API (under `/api/v1`)

| Method & path | Purpose |
|---|---|
| `GET /purchasing/queue` | Filtered, paginated queue |
| `GET /purchasing/queue/count` | Badge |
| `GET /purchasing/buyers?companyId=&categoryId=` | Eligible buyers + suggestion |
| `POST /purchasing/requests/{id}/assign` | Assign buyer (`buyerUserId`, `version`, optional `purchaseMode`) |
| `POST /purchasing/requests/{id}/accept` | Buyer accepts |
| `POST /purchasing/requests/{id}/return-assignment` | Buyer returns (`reason`, `version`) |
| `POST /purchasing/requests/{id}/purchase-mode` | Set mode |
| `POST /purchasing/requests/{id}/completeness-return` | Return dossier to requester |
| `POST /purchasing/requests/{id}/resubmit` | Author resubmit after purchasing return |
| `GET /purchasing/requests/{id}/assignments` | History |
| `GET/POST /suppliers`, `GET/PUT /suppliers/{id}`, `POST /suppliers/{id}/deactivate` | Register |
| `GET/POST /admin/organization/companies`, sites, departments, cost-centres | Org admin |
| `POST /imports`, `GET /imports/{id}` | Upload + result |

All state-changing POSTs: CSRF, `Idempotency-Key`, RFC 9457 errors.

## Tests

- DB-IT: queue isolation (Claire / Julien / Sami vs Ines / Mehdi); assign concurrency; accept/return; mode preserves `charged_company_id`; supplier shared vs company; org cross-group 404; import duplicate and rollback.
- Frontend unit: queue filters, supplier visibility badge, import row table.
- e2e: S3-AC-10 journey + S1/S2 regression remains green.

## Traceability

| AC | Register | Guide / spec |
|---|---|---|
| S3-AC-01 | FR-PUR-01 | §8 queue + filters |
| S3-AC-02 | FR-PUR-02 | §8 completeness / return |
| S3-AC-03 | FR-PUR-03 | §8 assignment, accept/return |
| S3-AC-04 | FR-PUR-04 | §3.7 modes |
| S3-AC-05 | FR-SRC-01 | §14 register (not RFQ) |
| S3-AC-06 | FR-ORG-01/02 | UC-28 org |
| S3-AC-07 | FR-ERP-02 | §21 import wizard, no connector |
| S3-AC-08 | FR-IAM-03 | ADR-007, design-review visibility |
| S3-AC-09 | FR-AUD-01, S2 notices | ADR-006 |
| S3-AC-10 | NFR-03/09/11 | delivery rules |
