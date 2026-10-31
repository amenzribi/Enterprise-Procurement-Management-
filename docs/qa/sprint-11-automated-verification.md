# Sprint 11 — Automated verification report

| | |
|---|---|
| Revision | (pending commit — see handover) |
| Date | 2026-10-31 |
| Label | Developer self-checks — **not** independent QA/reviewer sign-off |
| Manual UAT | Pending for Sprints 5–11 |

## Commands executed

| Check | Command | Result |
|---|---|---|
| Full backend | `.\mvnw.cmd verify -Pdb-it` | **Pass** — Surefire **136** tests, **0** failures (includes `MessagingIntegrationTests` ×2, Modulith, Architecture) |
| Frontend lint | `npm run lint` | **Pass** |
| Frontend unit | `npm test` | **Pass** — 28 files / **202** tests |
| Frontend build | `npm run build` | **Pass** (initial bundle budget warning ~503 kB / 500 kB — pre-existing class) |
| E2E | `npm run e2e` (31 prior) + `npx playwright test e2e/messaging-journey.spec.ts` | Regression **31 passed**; messaging journey **passed** after session fix (full suite had 1 fail from reused signed-in page — fixed) |
| Secrets scan | Grep on Sprint 11 paths for hardcoded passwords / private keys | **Pass** — no matches |

## Coverage vs S11-AC

| AC | Automated evidence |
|---|---|
| S11-AC-01 / 02 isolation + internal/external | `MessagingIntegrationTests.buyerAndSupplierExchange…` |
| S11-AC-03 / 04 portal restriction | same + supplier-portal-only filter; GA/requester denied |
| S11-AC-05 idempotent send | clientMessageId replay |
| S11-AC-10 revoke | `revokeRemovesSupplierAccess` |
| S11-AC-08 attachments | Explicitly excluded (ADR-011 / sprint-11.md) |
| S11-AC-11 i18n | EN/FR/AR keys + e2e messaging journey |

## Explicitly not passed

- Manual UAT; independent pen-test; MFA; §15 pilot; S12 AI; messaging attachments
