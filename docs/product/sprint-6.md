# Sprint 6 — Shipment tracking

**Status:** Locally implemented (developer). Manual user acceptance **pending**. Independent QA/review outstanding.  
**Edition:** Core (logistics lite).  
**References:** Solution Guide v3.0 UC-18, UC-19; register FR-LOG-01, FR-LOG-02; MVP M7 without AI; roadmap S6.  
**Depends on:** Sprint 5 PO issue + in-app supplier confirmation.  
**Pilot gate:** Unmet — local development only.

## Business objective

After a purchase order is **confirmed** (supplier confirmation recorded by purchasing), an authorised buyer or purchasing manager creates one or more shipments linked to the PO, advances local or international milestones with planned and actual dates, flags delays, and records transport document references. Delivery to site/warehouse updates the request to `DELIVERED` (user status RECEIVED) but **does not** record accepted receipt (S7).

## In scope

| ID | Criterion |
|---|---|
| S6-AC-01 | **Eligibility.** Shipment only when PO status is `CONFIRMED`. Actor is accepted assigned buyer or PURCHASING_MANAGER of `buying_company_id`. Cross-company → 404. |
| S6-AC-02 | **Shipment create.** Kind `LOCAL` or `INTERNATIONAL` (default from request purchase type). Lines from PO with quantities > 0; sum of shipped quantities per PO line across shipments ≤ PO line quantity. Concurrent over-shipment → 409. |
| S6-AC-03 | **Milestone templates.** On create, seed milestones per §12.1 (local vs international) with optional planned dates. Record actual date / clear delay; corrections of planned dates allowed; actual dates cannot move backwards without explicit note. |
| S6-AC-04 | **Request status.** Completing a milestone with actual date advances request status to the mapped shipment status (never past `DELIVERED`, never to `ACCEPTED`). Tracker/owner updated; audit `SHIPMENT_*`. |
| S6-AC-05 | **Delay alerts.** Milestone is late when planned date &lt; today (UTC date) and actual is null; list and detail expose `late`. Optional in-app notice to requester when a milestone is marked late or overdue on refresh. |
| S6-AC-06 | **Transport documents.** Attach document metadata (kind, reference, optional note) to a shipment; kinds include commercial invoice, packing list, certificate of origin, B/L, AWB, customs, delivery note, CMR. No AI extraction (deferred). Binary file optional via storage key when uploaded. |
| S6-AC-07 | **Visibility.** Requester/authors see shipments on the dossier; buyers/PMs act. EN/FR/AR + RTL. |

## Out of scope

- AI reading of transport documents (MVP M7 “without AI”; same class as FR-SRC-05).
- Arrival-date ML prediction beyond planned/actual + late flag.
- Dedicated LOGISTICS role (buyers/PMs act in Core; role deferred).
- Accepted receipt, invoicing, payments, ERP (S7/S8).
- External supplier shipment portal.

## Unresolved (explicit)

- Whether ISSUED (unconfirmed) POs may ship: **no** for Core — require CONFIRMED.
- Split multi-carrier routing beyond multiple shipments with quantity caps: not in this slice.

## Demo path

Ines (after PO CONFIRMED) creates LOCAL or INTERNATIONAL shipment → sets planned dates → records actuals through milestones → attaches B/L or delivery note reference → request reaches DELIVERED. Sami sees IN_TRANSIT / RECEIVED user statuses. Claire/Julien remain isolated.
