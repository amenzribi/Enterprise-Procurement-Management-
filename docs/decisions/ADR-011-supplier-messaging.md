# ADR-011 — Contextual buyer–supplier messaging and external contact identity

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-31 |
| Sprint | 11 |

## Context

Buyers need to communicate with a supplier about a specific RFQ or PO without exposing competing quotes, budgets, evaluations or internal notes. Quotation bearer links (Sprint 4) authenticate a single quote action and must not become a messaging session.

## Decision

### External identity

- Supplier contacts are first-class `user_account` rows with role **`SUPPLIER_CONTACT`**, invited by an authorised buyer or purchasing manager for a specific supplier.
- Access to conversations is gated by an active row in **`supplier_contact`** (supplier ↔ user), not by possession of a quote-link token.
- Contacts reuse session, CSRF, password hashing, `credentials_version`, must-change and password setup/reset from Sprint 10.
- `SUPPLIER_CONTACT` may call only supplier-portal messaging APIs, `/api/v1/me`, auth/password/logout. All other business and admin APIs return 403.
- GLOBAL_ADMIN does **not** gain supplier-conversation access merely by managing the installation (ADR-007).

### Conversations

- One thread per `(context_type ∈ {RFQ, PO}, context_id, supplier_id)`.
- RFQ → PO: open a **new** PO thread; do not copy internal notes or other suppliers’ messages.
- Messages have audience **`INTERNAL`** (buyer/PM only) or **`EXTERNAL`** (buyer/PM + authorised contacts for that supplier). Audience is immutable after insert (append-only history; no silent rewrite; no edit/delete of body in Sprint 11).
- Competing suppliers never share a thread.
- Closed RFQ (`CLOSED`) or cancelled PO (`CANCELLED`) → conversation read-only (no new messages). Issued/confirmed POs remain writable until cancelled.
- Idempotent send via `Idempotency-Key` / stored `client_message_id` per conversation.

### Attachments

**Excluded in Sprint 11.** Request attachments are request-scoped and must not become supplier-visible by ID reference. No messaging file upload/download in this sprint.

### Delivery

- Polling (no WebSockets).
- In-app notifications for internal recipients; e-mail alerts via transactional outbox without message body or tokens (demo/file capture; real external send remains environment-controlled).
- Messaging never changes award, approval, PO, receipt or closure state.

## Consequences

- Demo contact `contact.skf@demo.local` linked to `DEMO-SKF`.
- Quote links remain quote-only.
- Pilot/security gates (MFA, pen-test, etc.) still apply before production exposure of the portal.
