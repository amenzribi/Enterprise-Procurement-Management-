# Project handover (current)

Updated 2026-10-31 after Sprint 10 local delivery. Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `fbb1afd` / `58487d3` / `2ce95d8` / `97cc3bb` | Sprint 9 security + docs |
| `6046316` | Sprint 10 GLOBAL_ADMIN + V13 + admin UI/docs |
| Ahead of `origin/main` | Do not push until asked |

Unrelated dirty tree under purchasing/supplier/org/import (and V7) is **CRLF-only / user work** — leave uncommitted unless Mohamed asks. **Do not edit applied V7.**

## Running now (this machine)

- Frontend: **http://localhost:4300** (`ng serve --port 4300 --proxy-config proxy.conf.8081.json`)
- Backend health: **http://localhost:8081/actuator/health**
- Profiles: `dev,demo`; DB: SQL Server `procurement_dev`; Flyway through **V13**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit or print.
- Demo users include **`global.admin@demo.local`** (GLOBAL_ADMIN), `admin@demo.local`, `receiver.tn@demo.local`, `ceo@demo.local`, …

## Docs

- Combined manual scenarios (S5–10): [sprint-5-6-7-8-9-10-manual-scenarios.md](sprint-5-6-7-8-9-10-manual-scenarios.md)
- Sprint 10 plan: [sprint-10.md](../product/sprint-10.md)
- Sprint 10 automated verification: [sprint-10-automated-verification.md](sprint-10-automated-verification.md)
- Sprint 10 manual plan: [sprint-10-manual-test-plan.md](sprint-10-manual-test-plan.md)
- Proposed S11 messaging / S12 AI: see `docs/product/sprint-11-*.md`, `sprint-12-*.md`

## Manual acceptance

- Sprint 5–10 user acceptance: **pending** (Mohamed) — do not mark passed without explicit confirmation

## Residual / next

- Independent QA/review on the integrated revision
- **Next product sprint: proposed Sprint 11 — buyer–supplier communication** (external identity model first)
- Proposed Sprint 12 AI blocked until hosting/provider ADR
- Native-speaker Arabic review outstanding
- §15 pilot gate **unmet**
- MFA / NFR-01 / NFR-10 unmet
