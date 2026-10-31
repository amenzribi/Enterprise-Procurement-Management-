# Project handover (current)

Updated 2026-10-31 after Sprint 11 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `6046316` / `d9d9458` | Sprint 10 GLOBAL_ADMIN |
| `c0c4cb1` | Sprint 11 messaging + V14 + SUPPLIER_CONTACT portal |
| Ahead of `origin/main` | Do not push until asked |

Unrelated dirty tree under purchasing/supplier/org/import (and V7) is **CRLF-only / user work** — leave uncommitted unless Mohamed asks. **Do not edit applied V7.**

## Running now (this machine)

- Frontend: **http://localhost:4300** (`ng serve --port 4300 --proxy-config proxy.conf.8081.json`)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V14**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit or print.
- Demo users include **`global.admin@demo.local`**, **`contact.skf@demo.local`** (supplier portal), `buyer@demo.local`, …

## Docs

- Combined manual scenarios (S5–11): [sprint-5-6-7-8-9-10-11-manual-scenarios.md](sprint-5-6-7-8-9-10-11-manual-scenarios.md)
- Sprint 11 plan: [sprint-11.md](../product/sprint-11.md) · ADR-011
- Sprint 11 automated verification: [sprint-11-automated-verification.md](sprint-11-automated-verification.md)
- Sprint 11 manual plan: [sprint-11-manual-test-plan.md](sprint-11-manual-test-plan.md)

## Manual acceptance

- Sprint 5–11 user acceptance: **pending** (Mohamed) — do not mark passed without explicit confirmation

## Residual / next

- Independent QA/review on the integrated revision
- **Next product sprint: proposed Sprint 12 — internal AI assistant** (blocked until hosting/provider ADR)
- Native-speaker Arabic review outstanding
- §15 pilot gate **unmet**
- MFA / NFR-01 / NFR-10 unmet
- Messaging attachments deferred (S11-AC-08)
