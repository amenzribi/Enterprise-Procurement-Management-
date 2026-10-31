# Sprint 11 (proposed) — Buyer–supplier communication

**Status:** Proposal. Priority 1 after GLOBAL_ADMIN. Not Sprint 9 scope.

## Direction (requested)

- Participants: authorised buyer/purchasing and the supplier’s authorised contact.
- Threads linked to RFQ, PO or dossier; suppliers see only their conversations/documents.
- Never expose competing quotes, evaluations, budgets or internal comments.
- Separate supplier-visible vs internal requester/approver/buyer discussion.
- Define send/add-participant/attach rules; history; unread; notifications.
- External identity/access model **before** implementation — quotation link ≠ full messaging session.
- Distinguish in-app messages from real e-mail; track delivery failures; keep real external send **disabled** in demo/test.

## Open decisions (block implementation)

1. Supplier identity: account vs token-scoped mailbox vs portal add-on.  
2. Whether existing quote-link tokens may open a read-only thread.  
3. Retention and legal hold.
