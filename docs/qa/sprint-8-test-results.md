# Sprint 8 — Developer test evidence

| | |
|---|---|
| Revision | local Sprint 8 commit (see handover) |
| Date | 2026-10-31 |
| Label | Developer self-checks only — **not** independent QA/reviewer sign-off |
| Manual UAT | Pending for Sprints 5–8 |

## Commands and results

| Check | Command | Result |
|---|---|---|
| Backend verify (SQL Server) | `cd BackEnd; .\mvnw.cmd verify -Pdb-it` | **123 tests, 0 failures** (includes `DashboardIntegrationTests`, `SimplePdfTests`, Modulith) |
| Frontend lint | `cd FrontEnd; npm run lint` | Passed |
| Frontend unit tests | `npm test` | **202 passed** (28 files) |
| Frontend build | `npm run build` | Passed; initial bundle **501.05 kB** vs 500 kB budget (**warning only**, not raised) |
| E2E | `npm run e2e` with `E2E_BASE_URL=http://localhost:4300` against backend `:8081` | First full run: **30 passed, 1 failed** (ordering journey hit 60s suite timeout after closing successfully). Raised journey timeout to 180s; focused re-run of ordering + dashboards: **3 passed**. Full suite expected **31 passed** with that fix. |
| Secrets scan | ripgrep for password/token patterns on Sprint 8 paths | Clean (no hardcoded demo passwords) |

## Scope covered by automated checks

- Pipeline / management / group panel permissions and company isolation (IT).
- Audit pack PDF + cross-company 404 (IT).
- ERP CSV export role gate (IT).
- Architecture: no `purchase_request` SQL outside request module.
- Frontend dashboards page load + nav permission.
- Playwright dashboards EN + AR mobile (after live servers).

## Flakes / skips

- Earlier full run saw one approval race flake (`linkApprovalAndInboxRejectionRaceHasExactlyOneWinner`); re-run of that test and full verify: green. Not weakened.
