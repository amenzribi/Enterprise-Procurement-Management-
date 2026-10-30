# Project handover (current)

Updated 2026-10-30 after Sprint 5 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `e3c4747` | Sprint 3 closure (filters, affinity, question) |
| `f88345a` | Sprint 4 RFQs / quotes / comparison / validation |
| `98d7176` | S4 Angular null-check fix |
| `c59f56d` | Handover after S3 close / S4 delivery |
| `5d8e0b0` | Sprint 4 UAT + self-labelled S3–4 closure docs |
| `f732bef` | S4 supplier-link security harden (S4SL-02/04/05/06/08) |
| *(S5 commit)* | Award + purchase orders (this handover updated after commit) |
| Ahead of `origin/main` | Do not push until asked |

## Running now (this machine)

- Frontend: **http://localhost:4300** (proxy → 8081)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V10**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit.

## Docs

- S3–4 closure: [sprint-3-4-closure.md](sprint-3-4-closure.md), UAT [sprint-4-uat.md](sprint-4-uat.md)
- Supplier-link security: [sprint-4-supplier-link-security.md](../reviews/sprint-4-supplier-link-security.md)
- Sprint 5: [sprint-5.md](../product/sprint-5.md), [sprint-5-handover.md](sprint-5-handover.md), [sprint-5-test-results.md](sprint-5-test-results.md)

## Residual / next

- Independent QA/review on same integrated revision
- S4SL-01/03/07/09–11 deferred or accepted as documented; S2CR-08 → Sprint 9
- Native-speaker Arabic review outstanding
- Solution Guide §15 pilot gate **unmet** — no production / real supplier messaging
- Next product sprint: **S6 shipment** (not started)
