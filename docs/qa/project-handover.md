# Project handover (current)

Updated 2026-10-31 after Sprint 8 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `d82cc1a` | Sprint 7 receipts (V12) + invoice/closure + e2e |
| `0f5f7cf` / `03eebe1` | S7 handover / Playwright evidence |
| `a3f56a1` | Baseline: ordering e2e timeout 180s |
| `281c6d9` | Sprint 8 dashboards, audit pack, ERP CSV |
| *(follow-up)* | Ordering e2e asserts durable PO panel state; handover hashes |
| Ahead of `origin/main` | Do not push until asked |

Unrelated dirty tree under purchasing/supplier/org/import (and V7) is **user work** — leave uncommitted unless Mohamed asks.

## Running now (this machine)

- Frontend: **http://localhost:4300** (`ng serve --port 4300 --proxy-config proxy.conf.8081.json`)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V12** (no new migration in S8)
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit.
- Demo users include `receiver.tn@demo.local` (Omar) and `ceo@demo.local` (Hedi, GROUP_MANAGEMENT)

## Docs

- Combined manual scenarios (S5–S8): [sprint-5-6-7-8-manual-scenarios.md](sprint-5-6-7-8-manual-scenarios.md)
- Sprint 8 plan: [sprint-8.md](../product/sprint-8.md)
- Sprint 8 test evidence: [sprint-8-test-results.md](sprint-8-test-results.md)

## Manual acceptance

- Sprint 5 user acceptance: **pending** (Mohamed)
- Sprint 6 user acceptance: **pending** (Mohamed)
- Sprint 7 user acceptance: **pending** (Mohamed)
- Sprint 8 user acceptance: **pending** (Mohamed)

## Residual / next

- Independent QA/review on the integrated revision
- S2CR-08 / supplier-link follow-ups → Sprint 9
- Native-speaker Arabic review outstanding
- §15 pilot gate **unmet**
- AI request draft (FR-AI-01) blocked — no provider
- Next product sprint: **S9 pilot readiness** (not started in this session)
