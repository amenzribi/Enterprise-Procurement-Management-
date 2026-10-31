# Sprint 9 — Automated verification report

| | |
|---|---|
| Revision | local Sprint 9 commits (see handover / git log) |
| Date | 2026-10-31 |
| Label | Developer self-checks — **not** independent QA/reviewer sign-off |
| Manual UAT | Pending for Sprints 5–9 |

## Commands executed

| Check | Command | Result |
|---|---|---|
| Unit (address resolver + Modulith) | `.\mvnw.cmd verify -Pdb-it "-Dtest=ClientAddressResolverTests,ModularityTests,…"` (after `@Autowired` fix) | **Pass** — exit 0 |
| IT security / sourcing / approval | `.\mvnw.cmd verify -Pdb-it "-Dtest=ClientAddressResolverTests,ModularityTests,SourcingIntegrationTests,QuoteLinkSecurityIntegrationTests,ApprovalIntegrationTests"` | **Pass** — exit 0 (post constructor fix) |
| Full backend | `.\mvnw.cmd verify -Pdb-it` | **Pass** — exit 0; Surefire **129** tests, **0** failures, **0** errors (includes `QuoteLinkSecurityIntegrationTests`, Sourcing, Approval, Logistics, Receipt, Dashboard, Ordering) |
| Frontend lint | `npm run lint` | **Pass** |
| Frontend unit | `npm test` | **Pass** — 28 files / **202** tests |
| Frontend build | `npm run build` | **Pass** (initial bundle budget warning +1.05 kB over 500 kB — pre-existing class) |
| E2E | `$env:E2E_BASE_URL='http://localhost:4300'; npm run e2e` (S9 restart 4300→8081) | **Pass** — **31** passed (8.9m); ordering/receipt/close, dashboards, EN/FR/AR, phone a11y |
| Secrets scan | Grep on Sprint 9 paths for hardcoded passwords / private keys | **Pass** — no matches in S9 sources |

### Prior failure (fixed before green run)

`ClientAddressResolver` had two constructors; Spring required `@Autowired` on the `@Value` constructor. Symptom: `No default constructor found` → ApplicationContext failure for Approval/Sourcing/QuoteLink ITs. Fixed, then re-verified.

## Coverage vs S9-AC

| AC | Automated evidence |
|---|---|
| S9-AC-01 trusted proxies | `ClientAddressResolverTests` (4) |
| S9-AC-02 rate limits | `QuoteLinkSecurityIntegrationTests`; approval links share `RequestRateLimiter` + resolver; login uses resolver |
| S9-AC-03 revoke on reissue | `SourcingIntegrationTests.reissueQuoteLinkRevokesPreviousTokenAndClosedRfqIsNeutral404` |
| S9-AC-04 toString redaction | `SourcingViews` public request records override `toString` |
| S9-AC-05 security IT | invalid/expired/revoked/closed + 429 in sourcing/quote-link tests |
| S9-AC-06 docs | this file + manual plan + combined S5–9 guide |

## Explicitly not executed as “passed”

- Independent pen-test  
- Performance load (NFR-01)  
- Restore drill (NFR-10)  
- MFA (FR-IAM-04)  
- User manual acceptance  
- GLOBAL_ADMIN (proposed Sprint 10 — not implemented)  
- Buyer–supplier messaging / AI (proposed S11/S12)

## Developer browser checks

| Check | Result |
|---|---|
| Health after restart | Recorded in handover |
| Sign-in / shell smoke | Optional; e2e covers journey |
| Arabic RTL / mobile (S9 screens) | No new FE screens in S9; regression via existing e2e accessibility where run |

## Secrets scan (Sprint 9 paths)

Scanned for literal `password=`, private keys, raw demo passwords in committed S9 sources. Demo credentials remain env/`%TEMP%\epi-e2e-pw.txt` only. No password printed in this report.
