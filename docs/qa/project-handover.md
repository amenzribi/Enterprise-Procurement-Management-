# Project handover (current)

Updated 2026-10-31 after Sprint 9 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `281c6d9` / `d12eb2b` / `0b39925` | Sprint 8 delivery + evidence / handover |
| `fbb1afd` | Sprint 9: trusted-proxy rate limits + supplier-link revoke/redaction/ITs |
| `58487d3` / `2ce95d8` | Sprint 9 plan, QA reports, proposed S10–S12, handover hash |
| Ahead of `origin/main` | Do not push until asked |

Unrelated dirty tree under purchasing/supplier/org/import (and V7) is **user work** — leave uncommitted unless Mohamed asks.

## Running now (this machine)

- Frontend: **http://localhost:4300** (`ng serve --port 4300 --proxy-config proxy.conf.8081.json`)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V12** (no new migration in S9)
- Trusted proxies: `APP_TRUSTED_PROXIES` / `app.security.trusted-proxies` (empty by default — forwarded headers ignored)
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit or print.
- Demo users include `receiver.tn@demo.local` (Omar) and `ceo@demo.local` (Hedi, GROUP_MANAGEMENT)

## Docs

- Combined manual scenarios (S5–S9): [sprint-5-6-7-8-9-manual-scenarios.md](sprint-5-6-7-8-9-manual-scenarios.md)
- Sprint 9 plan: [sprint-9.md](../product/sprint-9.md)
- Sprint 9 automated verification: [sprint-9-automated-verification.md](sprint-9-automated-verification.md)
- Sprint 9 manual plan: [sprint-9-manual-test-plan.md](sprint-9-manual-test-plan.md)
- Proposed S10 GLOBAL_ADMIN: [sprint-10-global-admin.md](../product/sprint-10-global-admin.md)
- Proposed S11 messaging: [sprint-11-supplier-communication.md](../product/sprint-11-supplier-communication.md)
- Proposed S12 AI: [sprint-12-ai-assistant.md](../product/sprint-12-ai-assistant.md)

## Manual acceptance

- Sprint 5–9 user acceptance: **pending** (Mohamed) — do not mark passed without explicit confirmation
- Combined manual testing at end of S5–9 journey: **pending**
- Full-system / design review (Codex or Claude): **pending** (after UAT)

## Residual / next

- Independent QA/review on the integrated revision
- **Next product sprint: proposed Sprint 10 — GLOBAL_ADMIN** (not started)
- Native-speaker Arabic review outstanding
- §15 pilot gate **unmet** (blocks pen-test production exposure, training, go-live, real supplier send)
- MFA (FR-IAM-04) blocked on IdP decision
- NFR-01 load measure / NFR-10 restore drill unmet
- AI request draft / assistant blocked — no hosting/provider ADR
- Do **not** claim Core complete merely because numbered S0–S9 local delivery ends; add-ons and gates remain
