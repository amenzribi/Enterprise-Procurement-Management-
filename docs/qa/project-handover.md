# Project handover (current)

Updated 2026-10-30 after Sprint 3–4 **self-labelled** closure ([sprint-3-4-closure.md](sprint-3-4-closure.md)). Not independent sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `e3c4747` | Sprint 3 closure (filters, affinity, question) |
| `f88345a` | Sprint 4 RFQs / quotes / comparison / validation |
| `98d7176` | S4 Angular null-check fix |
| `c59f56d` | Project handover after S3 close / S4 delivery |
| Ahead of `origin/main` | Do not push until asked |

## Running now (this machine)

- Frontend: **http://localhost:4300** (proxy → 8081)
- Backend health: **http://localhost:8081/actuator/health** → UP
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev` (Windows auth); Flyway through **V9**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (env `DEMO_USER_PASSWORD` for the backend process). Never commit.

## Docs

- **Closure:** [sprint-3-4-closure.md](sprint-3-4-closure.md) (criterion matrix + dispositions)
- UAT (user scenarios only): [sprint-4-uat.md](sprint-4-uat.md)
- S3: [sprint-3-handover.md](sprint-3-handover.md), [sprint-3-test-results.md](sprint-3-test-results.md), [sprint-3-defects.md](sprint-3-defects.md), [sprint-3-acceptance-matrix.md](sprint-3-acceptance-matrix.md)
- S4: [sprint-4.md](../product/sprint-4.md), [sprint-4-handover.md](sprint-4-handover.md)

## Next

Independent QA/review on the same integrated revision; Sprint 5 award/PO when ready (do not confuse uncommitted S5 sketches with delivered work); pilot gate §15 still unmet for production; S2CR-08 Sprint 9; native Arabic review outstanding.
