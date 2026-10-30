# Project handover (current)

Updated 2026-10-30 after Sprint 7 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `ca429f0` | Sprint 5 baseline: award snapshot immutability IT |
| `ca9c024` | Sprint 6 shipments (V11) |
| `aaf951d` | Handover hash for S6 |
| *(Sprint 7)* | Receipts V12 + receipt module + UI + e2e (see `git log`) |
| Ahead of `origin/main` | Do not push until asked |

## Running now (this machine)

- Frontend: **http://localhost:4300** (proxy → 8081)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V12**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit.
- New demo user: `receiver.tn@demo.local` (Omar, RECEIVER)

## Docs

- Manual scenarios (S5+S6+S7): [sprint-5-6-7-manual-scenarios.md](sprint-5-6-7-manual-scenarios.md)
- Sprint 7: [sprint-7.md](../product/sprint-7.md), [sprint-7-handover.md](sprint-7-handover.md)
- Prior: [sprint-5-6-manual-scenarios.md](sprint-5-6-manual-scenarios.md), [sprint-5-6-test-results.md](sprint-5-6-test-results.md)

## Manual acceptance

- Sprint 5 user acceptance: **pending** (Mohamed)
- Sprint 6 user acceptance: **pending** (Mohamed)
- Sprint 7 user acceptance: **pending** (Mohamed)

## Residual / next

- Independent QA/review on the integrated revision
- S2CR-08 / supplier-link follow-ups → Sprint 9
- Native-speaker Arabic review outstanding
- §15 pilot gate **unmet**
- Next product sprint: **S8 visibility / ERP files** (not started)
