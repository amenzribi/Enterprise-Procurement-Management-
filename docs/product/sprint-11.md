# Sprint 11 — Contextual buyer–supplier communication

**Status:** Locally implemented (developer). Manual UAT pending. Not independently reviewed. Does not constitute production approval.

## Objective

Authorised buyers and purchasing managers can exchange messages with invited supplier contacts on an RFQ or PO thread, with strict internal/external separation and no automatic business-state changes.

## Acceptance criteria

| ID | Criterion |
|---|---|
| S11-AC-01 | Conversations link to RFQ or PO + one supplier; competing suppliers never share a thread or see each other’s messages. |
| S11-AC-02 | Audience `INTERNAL` vs `EXTERNAL` is server-enforced and immutable; supplier APIs/notifications never expose internal notes. |
| S11-AC-03 | Access: assigned buyer or purchasing manager for the buying company; supplier contacts via active `supplier_contact` only. Requesters, approvers, receivers and GLOBAL_ADMIN do not gain access by default. |
| S11-AC-04 | External identity: `SUPPLIER_CONTACT` accounts + invitation/setup; quote-link tokens do not grant messaging. Portal APIs deny internal/admin endpoints. |
| S11-AC-05 | Paginated ordered history, plain-text limits, sender + timestamp, unread/read state, idempotent send, closed-context read-only. |
| S11-AC-06 | Append-only message history (no edit/delete of body). RFQ→PO opens a new thread without copying private/competing content. |
| S11-AC-07 | In-app (and outbox) notifications without leaking message body or tokens; demo/file mail; notification failure does not roll back a saved message when enqueued in the same TX after insert. |
| S11-AC-08 | Attachments **out of scope** for Sprint 11 (explicit). |
| S11-AC-09 | Messaging never awards, issues POs, approves, receives or closes. |
| S11-AC-10 | Revocation/deactivation bumps credentials and denies promptly. Invite/revoke audited without secrets. |
| S11-AC-11 | UI EN/FR/AR + RTL for buyer panel and supplier portal; rate limits/CSRF on authenticated APIs. |
| S11-AC-12 | Demo `contact.skf@demo.local` via `DEMO_USER_PASSWORD`; no committed credentials. |

## Permissions matrix

| Actor | Open thread | Send INTERNAL | Send EXTERNAL | Invite/revoke contact | Read internal | Read external |
|---|---|---|---|---|---|---|
| Assigned BUYER (buying co.) | Yes | Yes | Yes | Yes | Yes | Yes |
| PURCHASING_MANAGER (buying co.) | Yes | Yes | Yes | Yes | Yes | Yes |
| Active SUPPLIER_CONTACT for supplier | No (list own) | No | Yes (portal) | No | No | Yes |
| Other supplier / revoked contact | No | No | No | No | No | No |
| Requester / approver / receiver / GA alone | No | No | No | No | No | No |

## Lifecycle

1. Buyer/PM opens or reuses conversation for RFQ|PO + supplier already on the RFQ/PO.
2. Invite contact → account + SETUP token (or reuse demo contact).
3. Messages append; EXTERNAL visible to contact; INTERNAL never.
4. Close RFQ / cancel PO → read-only.
5. Revoke contact → no portal access; sessions invalidated via credentials_version.

## Out of scope

Attachments; inbound e-mail replies; WebSockets; Sprint 12 AI; production go-live gates.
