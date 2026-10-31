# Sprint 11 — Buyer–supplier communication

**Status:** Accepted and implemented locally (see `sprint-11.md`, ADR-011). Manual UAT pending.

## Direction (delivered)

- Participants: authorised buyer/purchasing manager and invited `SUPPLIER_CONTACT` accounts.
- Threads linked to RFQ or PO + one supplier; suppliers see only their EXTERNAL messages.
- Never expose competing quotes, evaluations, budgets or INTERNAL notes.
- Quotation link ≠ messaging session.
- Attachments deferred (explicit). Demo/file mail; no inbound e-mail replies.
- Retention / legal hold: deferred (append-only history for now).

## Decisions (ADR-011)

1. Supplier identity: dedicated `SUPPLIER_CONTACT` user accounts + `supplier_contact` membership.
2. Quote-link tokens never open messaging.
3. Retention/legal hold left for a later decision; messages are append-only.
