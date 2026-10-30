# Sprint 5–6 automated test evidence (developer self-labelled)

| | |
|---|---|
| Date | 2026-10-30 |
| Label | Developer self-check — **not** independent QA/reviewer sign-off |
| Manual UAT | Sprint 5 and Sprint 6 **pending** |

## Commands and results

| Check | Command | Result |
|---|---|---|
| Backend verify (SQL Server IT) | `BackEnd: .\mvnw.cmd -o verify -Pdb-it` | **BUILD SUCCESS** — Tests run: **117**, Failures: 0, Errors: 0, Skipped: 0 |
| Ordering IT (incl. snapshot) | included above | 4 tests green |
| Logistics IT | included above | 3 tests green (confirm gate, over-qty, concurrent ship, milestones ≠ ACCEPTED) |
| Modulith | included above | green |
| Frontend lint | `FrontEnd: npm run lint` | All files pass linting |
| Frontend unit | `FrontEnd: npm test` | **27** files / **201** tests passed |
| Frontend build | `FrontEnd: npm run build` | Complete; known initial-bundle budget warning (~500.35 kB) |
| Playwright e2e | `E2E_BASE_URL=http://localhost:4300` + `npm run e2e` | **29 passed** (10.0m), 0 failed, 0 flaky — includes `ordering-logistics-journey` |
| Secrets scan | pattern scan on Sprint 6 paths | clean |

## Self-review (honest)

Developer walkthrough of ACs against code and green suites only. No separate reviewer or QA role sign-off on this revision.

## Open / deferred

- S2CR-08 and documented supplier-link follow-ups → Sprint 9
- Native-speaker Arabic review outstanding
- Solution Guide §15 pilot gate unmet
- Independent QA/review on the integrated revision outstanding
