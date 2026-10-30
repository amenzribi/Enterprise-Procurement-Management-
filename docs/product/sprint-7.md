# Sprint 7 — Receipt, invoice capture and closure

**Status:** Locally implemented (developer). Manual user acceptance **pending**. Independent QA/review outstanding.  
**Edition:** Core (receipt lite).  
**References:** Solution Guide v3.0 UC-20, UC-21, UC-22 (manual attest), UC-23 (status lite), UC-25; FR-RCV-01, FR-RCV-02, FR-RCV-04; assumption A7 / RC-09.  
**Depends on:** Sprint 6 shipment delivery (`DELIVERED`).  
**Pilot gate:** Unmet — local development only.

## Business objective

After goods are **delivered** (shipment milestone), an authorised receiver records inspection (full / partial / damaged / non-conforming). Quantities received, accepted and rejected stay explicit. The requester confirms formal **acceptance**. Purchasing records a **manual final invoice** (no AI, no algorithmic three-way tolerance) and payment status. When conditions are met, the system **proposes** closure; an authorised user **confirms** (never auto-closes). Optional supplier score on close.

## In scope

| ID | Criterion |
|---|---|
| S7-AC-01 | **Eligibility.** Goods receipt only when request status is `DELIVERED`, `PARTIALLY_RECEIVED` or `NON_CONFORMING`. Actor is `RECEIVER` of the requesting/charged company, or the requester/author. Cross-company → 404. |
| S7-AC-02 | **Receipt lines.** Linked to PO lines (and optional shipment). `qty_received`, `qty_accepted`, `qty_rejected`, `qty_damaged` with `qty_accepted + qty_rejected ≤ qty_received` and `qty_damaged ≤ qty_received`. Sum of received across receipts ≤ shipped (or PO qty if no shipment qty). Concurrent over-receive → 409. |
| S7-AC-03 | **Conditions.** Line condition `OK`, `DAMAGED`, `MISSING`, `NON_CONFORMING` with optional note and photo **reference** (metadata; binary upload remains the existing request attachments). |
| S7-AC-04 | **Status.** Partial accepted quantities → `PARTIALLY_RECEIVED`; any non-conforming/damaged without full accept → may set `NON_CONFORMING`; full accept of ordered qty → `ACCEPTED` after requester confirmation. Delivery alone never implies `ACCEPTED`. |
| S7-AC-05 | **Service acceptance.** Requester (or RECEIVER) may record service acceptance with comment when no goods receipt is required (no physical receipt recorded yet; PO confirmed). Advances to `ACCEPTED`. Dedicated `SERVICE` purchase-type value remains a later schema extension — Core uses this attestation path. |
| S7-AC-06 | **Manual invoice.** Buyer/PM of buying company records final invoice number, amount, currency, document reference; optional human **match attested** flag (not automated tolerances — FR-RCV-03 remains deferred). Payment status `PENDING` / `PAID`. May advance to `INVOICED` / `PAID`. |
| S7-AC-07 | **Closure (A7).** `closureReady` when status ≥ `ACCEPTED`, invoice captured, and (goods: no outstanding accepted shortfall on active lines **or** shortfall explicitly waived in note). User with PURCHASING_MANAGE (buying company) or requester confirms close → `CLOSED` + optional score 1–5. Never auto-close. |
| S7-AC-08 | **Visibility / i18n.** Authors see receipts; EN/FR/AR + RTL; audit `RECEIPT_*`, `INVOICE_*`, `REQUEST_CLOSED`. |

## Out of scope

- AI invoice reading; algorithmic three-way match with tolerances (FR-RCV-03 deferred P2).
- Dashboards / CEO view (S8); ERP file export (S8); recharge billing (Trading / FR-FIN-01).
- QR warehouse scanning; inventory valuation; bank/LC payments.
- Dedicated FINANCE role (buyers/PMs record invoice status in Core).

## Unresolved (explicit)

- Per-category over-receipt tolerance: **blocked by default** (design-review). No silent over-receive.
- Who may waive remaining quantity on close: purchasing manager only in this slice.

## Demo path

Omar (receiver, TN) records partial/damaged receipt on a delivered request → Sami confirms acceptance → Ines/Mehdi records final invoice + match attested → Mehdi closes with supplier score. Claire/Julien isolated.
