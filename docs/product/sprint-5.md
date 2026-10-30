# Sprint 5 — Award and purchase order

**Status:** Locally delivered (developer). Not independently reviewed.  
**Edition:** Core.  
**References:** Solution Guide v3.0 UC-15…UC-17; register FR-EVL-01 (award gate), FR-ORD-01; roadmap S5.  
**Depends on:** Sprint 4 commercial validation.  
**Pilot gate:** Unmet — local development only; no production supplier messaging.

## Business objective

After technical and commercial validation, an authorised buyer/purchasing manager awards a single submitted quotation (with a mandatory reason), creating an immutable commercial snapshot. From that award they create a purchase order (draft then issue). The supplier may confirm the PO in-app (demo / local). Payment milestones and a simple price-memory row are recorded. No ERP API send (S8). No shipment (S6 — register FR-LOG-01 follows the roadmap, not this sprint).

## In scope

| ID | Criterion |
|---|---|
| S5-AC-01 | **Award eligibility.** Award only when request status is `COMMERCIAL_VALIDATION` (or `MANAGEMENT_VALIDATION` if used). Actor is accepted assigned buyer or PURCHASING_MANAGER of `buying_company_id`. Quotation must be `SUBMITTED`, belong to the request’s RFQ, and supplier still active/visible. |
| S5-AC-02 | **Single-supplier award.** One award per request in this Core slice (no split/partial multi-supplier award). Mandatory free-text reason. Snapshot stores quotation id, supplier, currency, merchandise/freight/duty/other, line unit prices and quantities at award time. Later quote edits do not change the award. |
| S5-AC-03 | **Concurrency.** Optimistic `request.version` + exclusive request lock. Second award → 409. Award → request status `AWARDED`. Audit `AWARD_RECORDED`. |
| S5-AC-04 | **PO create.** From an award, authorised actor creates a PO in `DRAFT`: unique number `{BUYING_COMPANY}-PO-{YYYY}-{seq6}`, buying / requesting / charged companies from the request (charged never rewritten), supplier and award reference, lines from award snapshot, delivery/required date/payment terms/incoterm copied or editable in draft. |
| S5-AC-05 | **PO issue.** Explicit **Issue** action (not save). Draft → `ISSUED`. Idempotent with Idempotency-Key. Issued commercial lines are immutable; draft edits allowed only while `DRAFT`. |
| S5-AC-06 | **Supplier confirmation.** PO `ISSUED` → `CONFIRMED` by purchasing actor recording supplier confirmation (no supplier portal account in Core). Optional confirmation note. |
| S5-AC-07 | **Payment milestones.** Zero or more milestones on a PO (label, due date, amount, currency, status PLANNED/DUE/PAID). Editable before issue; after issue append-only status updates. |
| S5-AC-08 | **Price memory.** On award, upsert `(group_id, supplier_id, category_id, description_norm)` → last unit price, currency, awarded_at for future display (not auto-fill AI). |
| S5-AC-09 | **Isolation & audit.** Cross-company → 404; forbidden → 403. Award, PO create/issue/confirm audited. Tracker shows stage/owner. EN/FR/AR + RTL. |

## Out of scope

- Split awards, committee minutes (FR-EVL-02), LCs/guarantees (FR-ORD-02).
- ERP file/API send (S8), shipment/receipt (S6/S7).
- Real supplier e-mail of the PO (local notice / in-app only).
- Legal cancellation/amendment engines beyond “draft editable, issued immutable”.
- Tax engines beyond stored amounts on the award snapshot.

## Unresolved (explicit)

- Formal **management validation** stage before award: **not required** for Core MVP when commercial validation is complete (aligned with S4-AC-06). If a pilot later mandates it, add a gate ADR.
- Supplier confirmation by external token: **not** in this sprint (in-app recording only).

## Demo path

Ines (after commercial validation) awards DEMO-SKF quote with reason → creates PO draft → issues → records supplier confirmation. Sami sees AWARDED then ORDERED user statuses. Claire/Julien still cannot open the Tunisia dossier.
