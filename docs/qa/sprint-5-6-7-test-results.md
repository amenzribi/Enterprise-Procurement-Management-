# Sprint 5–7 automated test evidence (developer self-labelled)

| | |
|---|---|
| Date | 2026-10-30 |
| Label | Developer self-check — **not** independent QA/reviewer sign-off |
| Manual UAT | Sprints 5, 6 and 7 **pending** |

## Commands and results

| Check | Result |
|---|---|
| `BackEnd: .\mvnw.cmd -o verify -Pdb-it` | **BUILD SUCCESS** — Tests run: **120**, Failures: 0 |
| Receipt IT | 3 green (partial receipt → confirm → invoice → close; early/over-qty; concurrent) |
| Frontend lint | pass |
| Frontend unit | **27** files / **201** tests |
| Frontend build | complete; initial bundle budget warning (~500.87 kB) |
| Playwright e2e (`npm run e2e` @ :4300) | **29 passed** (9.4m), 0 failed — includes award→PO→ship→receipt→invoice→close |
| Secrets scan | clean on Sprint 7 paths |

## Self-review

Developer AC walkthrough + green suites only. No independent QA/reviewer sign-off.
