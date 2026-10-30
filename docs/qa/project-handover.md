# Project handover (current)

Updated 2026-10-30 after Sprint 6 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `9c7b354` | Sprint 5 award + purchase orders |
| `e1da662` | Purchasing e2e queue search when paginated |
| `c64f2e4` | Supplier-link security review reconciliation |
| `ca429f0` | Sprint 5 baseline: award snapshot immutability IT |
| *(Sprint 6 commit)* | Shipments V11 + logistics module + UI + e2e (see `git log`) |
| Ahead of `origin/main` | Do not push until asked |

## Running now (this machine)

- Frontend: **http://localhost:4300** (proxy → 8081)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V11**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit.

## Docs

- S3–4 closure: [sprint-3-4-closure.md](sprint-3-4-closure.md), UAT [sprint-4-uat.md](sprint-4-uat.md)
- Supplier-link security: [sprint-4-supplier-link-security.md](../reviews/sprint-4-supplier-link-security.md)
- Sprint 5: [sprint-5.md](../product/sprint-5.md), [sprint-5-handover.md](sprint-5-handover.md)
- Sprint 6: [sprint-6.md](../product/sprint-6.md), [sprint-6-handover.md](sprint-6-handover.md)
- Manual scenarios (S5 + S6 + combined): [sprint-5-6-manual-scenarios.md](sprint-5-6-manual-scenarios.md)
- Automated evidence: [sprint-5-6-test-results.md](sprint-5-6-test-results.md)

## Manual acceptance

- Sprint 5 user acceptance: **pending** (Mohamed)
- Sprint 6 user acceptance: **pending** (Mohamed)

## Residual / next

- Independent QA/review on the same integrated revision
- S4SL deferred/accepted items as documented; S2CR-08 → Sprint 9
- Native-speaker Arabic review outstanding
- Solution Guide §15 pilot gate **unmet** — no production / real supplier messaging
- Next product sprint: **S7 receipt / invoice / closure** (not started)
