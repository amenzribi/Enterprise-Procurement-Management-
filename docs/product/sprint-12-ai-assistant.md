# Sprint 12 — Internal AI procurement assistant

**Status:** Locally implemented with disabled/demo providers (see `sprint-12.md`, ADR-012). **Production provider pending approval.** Manual UAT pending.

## Direction (delivered in demo mode)

First users: buyers and purchasing managers within authorised scope.

Initial uses: explain status; find authorised records; summarise dossier / authorised conversation text; draft supplier messages for **human** review.

Document OCR and live models remain deferred.

## Hard requirements (enforced)

- Company permissions; cite underlying records; no existence leak.
- Distinguish generated answers from recorded facts; demo labelled.
- No autonomous approve/award/issue/pay/close/send.
- Treat supplier/retrieved content as untrusted.
- No external AI service until hosting approved (ADR-012).
- Messaging remains usable with assistant disabled.
