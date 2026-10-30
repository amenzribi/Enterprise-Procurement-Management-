# Project handover (current)

Updated 2026-10-30 after Sprint 3 closure and Sprint 4 local delivery.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `e3c4747` | Sprint 3 closure (filters, affinity, question) |
| `f88345a` | Sprint 4 RFQs / quotes / comparison / validation |
| `98d7176` | S4 Angular null-check fix |
| Ahead of `origin/main` by 6 | Do not push until asked |

## Running now (this machine)

- Frontend: **http://localhost:4300** (proxy → 8081)
- Backend health: **http://localhost:8081/actuator/health** → UP
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev` (Windows auth); Flyway through **V9**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (env `DEMO_USER_PASSWORD` for the backend process). Never commit.

## Docs

- S3: [sprint-3-handover.md](sprint-3-handover.md), [sprint-3-test-results.md](sprint-3-test-results.md), [sprint-3-defects.md](sprint-3-defects.md), [sprint-3-acceptance-matrix.md](sprint-3-acceptance-matrix.md)
- S4: [sprint-4.md](../product/sprint-4.md), [sprint-4-handover.md](sprint-4-handover.md)

## Next

Independent QA/review; Sprint 5 award/PO when ready; pilot gate §15 still unmet for production; S2CR-08 Sprint 9; native Arabic review outstanding.
