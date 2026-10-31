# Sprint 11 full e2e regression (before Sprint 12)

| | |
|---|---|
| Baseline revision | `83ac01c` / `c0c4cb1` |
| Date | 2026-10-31 |
| Command | `$env:E2E_BASE_URL='http://localhost:4300'; npm run e2e` |
| Result | **32 passed** (includes `messaging-journey`) |

Prior handover noted a full-suite run with one messaging failure, then a messaging-only re-pass. This document records the **full suite re-run after the messaging session fix**, before Sprint 12 work continued.

Messaging checks covered by suite + IT: EXTERNAL send/reply, INTERNAL hidden, revoke, supplier-portal-only denial, no business-state change via messaging.
