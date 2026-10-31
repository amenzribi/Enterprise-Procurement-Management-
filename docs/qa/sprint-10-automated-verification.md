# Sprint 10 — Automated verification report

| | |
|---|---|
| Revision | `6046316` (+ handover hash note) |
| Date | 2026-10-31 |
| Label | Developer self-checks — **not** independent QA/reviewer sign-off |
| Manual UAT | Pending for Sprints 5–10 |

## Commands executed

| Check | Command | Result |
|---|---|---|
| Full backend | `.\mvnw.cmd verify -Pdb-it` | **Pass** — Surefire **134** tests, **0** failures (includes `UserAdminIntegrationTests`, identity, Modulith, Architecture) |
| Frontend lint | `npm run lint` | **Pass** |
| Frontend unit | `npm test` | **Pass** — 28 files / **202** tests |
| Frontend build | `npm run build` | **Pass** (initial bundle budget warning ~+2.4 kB over 500 kB — pre-existing class) |
| E2E | `npm run e2e` against http://localhost:4300 → :8081 | **Pass** — **31** passed (9.5m), including approvals, purchasing, ordering, dashboards, session, accessibility EN/FR/AR |
| Secrets scan | Grep on Sprint 10 paths for hardcoded passwords / private keys | **Pass** — no matches |

## Coverage vs S10-AC

| AC | Automated evidence |
|---|---|
| S10-AC-01 GLOBAL_ADMIN / cannot grant by company admin | `UserAdminIntegrationTests.companyAdminCannotGrantGlobalAdmin` |
| S10-AC-02 create / roles / activate | `globalAdminCanCreateUser…`, `secondGlobalAdminCanBeDeactivated…` |
| S10-AC-03 demo + bootstrap | `DemoUserSeeder` includes `global.admin@demo.local`; `GlobalAdminBootstrap` (demo profile skips) |
| S10-AC-04 password change/reset | `passwordResetRedeemIsSingleUse`; `PasswordPolicy` |
| S10-AC-05 credentials_version | CurrentUserRefresh + bump on reset/deactivate |
| S10-AC-06 last GLOBAL_ADMIN | `lastActiveGlobalAdminCannotBeDeactivated` |
| S10-AC-07 audit without secrets | actions use non-secret payloads |
| S10-AC-08 UI EN/FR/AR | i18n keys + route `/admin/users` |
| S10-AC-09 rate limit / CSRF | redeem uses `RequestRateLimiter` + `ClientAddressResolver`; admin APIs CSRF-protected |
| S10-AC-10 must_change | `MustChangePasswordFilter` |

## Explicitly not passed

- Manual UAT; independent pen-test; MFA; §15 pilot; S11/S12
