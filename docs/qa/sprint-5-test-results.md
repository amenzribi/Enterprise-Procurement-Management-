# Sprint 5 — automated test results

| | |
|---|---|
| Date | 2026-10-30 |
| Tree | Local main after Sprint 5 delivery (see [project-handover.md](project-handover.md)) |
| Role | Developer self-check |

## Commands and results

| Check | Command | Result |
|---|---|---|
| Backend IT (ordering + sourcing + modulith) | `.\mvnw.cmd -o test -Pdb-it "-Dtest=OrderingIntegrationTests,SourcingIntegrationTests,ModularityTests"` | **Pass** (3 + 2 + 1) |
| Backend full verify | `.\mvnw.cmd -o verify -Pdb-it` | **113/113** pass (one prior approval email-link flake; re-run of that method and full suite green) |
| Frontend lint | `npm run lint` | Pass |
| Frontend unit | `npm test` | **201/201** |
| Frontend build | `npm run build` | Pass (initial bundle budget warning ~289 B over 500 kB) |
| Secrets scan (S5 paths) | Pattern search for password/secret/apiKey in ordering + V10 | No secrets |

## Ordering IT coverage

- Award → PO draft (number `{BUYING}-PO-{YYYY}-{seq6}`) → issue → confirm
- Cross-company award 404; award before commercial validation 409; wrong quotation 422
- Concurrent award → single award row

## Not claimed

- Independent QA/reviewer sign-off
- Playwright e2e covering award/PO UI (manual scenario provided)
- Pilot gate / production exposure
