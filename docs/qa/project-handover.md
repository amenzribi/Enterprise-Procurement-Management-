# Project handover (current)

Updated 2026-10-31 after Sprint 12 local delivery (demo/disabled assistant). Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `6046316` / `d9d9458` | Sprint 10 GLOBAL_ADMIN |
| `c0c4cb1` / `83ac01c` | Sprint 11 messaging + V14 + e2e note |
| `b967b3e` | Sprint 12 assistant + V15 + ADR-012 (demo/disabled) |
| Ahead of `origin/main` | Do not push until asked |

Unrelated dirty tree under purchasing/supplier/org/import (and V7) is **CRLF-only / user work** — leave uncommitted unless Mohamed asks. **Do not edit applied V7.**

## Running now (this machine)

- Frontend: **http://localhost:4300** (`ng serve --port 4300 --proxy-config proxy.conf.8081.json`)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V15**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit or print.
- Assistant: **demo** mode under `dev,demo` (`app.assistant.mode=demo`). Default elsewhere remains **disabled**. No live provider.

## Docs

- Combined manual index (S5–12): [sprint-5-to-12-manual-index.md](sprint-5-to-12-manual-index.md)
- Sprint 12 plan: [sprint-12.md](../product/sprint-12.md) · [ADR-012](../decisions/ADR-012-ai-assistant-hosting.md)
- Sprint 12 automated verification: [sprint-12-automated-verification.md](sprint-12-automated-verification.md)
- Sprint 12 manual plan: [sprint-12-manual-test-plan.md](sprint-12-manual-test-plan.md)
- S11 e2e baseline before S12: [sprint-11-regression-before-s12.md](sprint-11-regression-before-s12.md)

## Manual acceptance

- Sprint **5–12** user acceptance: **pending** (Mohamed) — do not mark passed without explicit confirmation

## Residual / next

- Independent QA/review on the integrated revision
- **Hosting / live AI provider decision** (ADR-012 options A/B) — blocked for live acceptance
- OCR / document ingestion, autonomous actions, forecasting, messaging attachments — deferred
- Native-speaker Arabic review outstanding
- §15 pilot gate **unmet**
- MFA / NFR-01 / NFR-10 unmet
