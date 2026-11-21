# Status

Short, current state of the project. Detail and history are in [HANDOFF.md](HANDOFF.md).

_Updated 2026-11-17._

| Area | State |
|---|---|
| Published baseline | `c7b901d` (Meridian refinement), pushed to `enterprise/main` on 2026-11-12 |
| Work in progress | Internal employee messaging on `feat/internal-messaging` (from `c7b901d`), uncommitted, awaiting the owner's review |
| Review instance | http://localhost:4202 (backend :8082 on the tested code, database `procurement_qa_messaging`); runs while the session that started it is alive |
| Hardening sprint 1 | Remediation complete and verified on one code state: backend 284/284, frontend lint, 253/253, build, browser 69/69 uninterrupted. Uncommitted. Waiting for the owner: Go/No-Go, F-1, F-2, F-5, commit plan. Report: [docs/sprints/sprint-01-remediation-report.md](docs/sprints/sprint-01-remediation-report.md) |
| CI | `.github/workflows/ci.yml` written; **never run** (nothing pushed) |
| Release gates | Independent QA, penetration test, MFA verification and restore drill still open; pilot, UAT and production are no-go |

## Internal messaging (in review)

- One-to-one messages between employees of one company, request discussions, "Message approver", live updates over Server-Sent Events. Design and rules: [docs/architecture/internal-messaging.md](docs/architecture/internal-messaging.md).
- Schema: V22 (new tables only).
- Final verification (2026-11-14, backend tree `bb5bdd8`, frontend tree `eaec3f5`): backend 208/208 on SQL Server; frontend lint, 253/253 unit tests, build; full Playwright suite 66/66 on an isolated instance with a fresh database. Send API about 30 ms and live event about 3 ms after it under normal load; a deadlock on simultaneous retries was found and fixed. Details: [HANDOFF.md](HANDOFF.md).
- Live delivery works on one backend node only; deploy one node until the multi-node option is decided.
- Decisions needed from the owner: message retention (pending; nothing is deleted); how live delivery reaches other nodes before any multi-node deployment; e-mail for messages (disabled for this phase; extension point only).

## Hardening sprint 1 (closed by the owner 2026-11-18; three commits approved for push to `enterprise/feat/internal-messaging`)

- Source: the enterprise readiness audit in [docs/audit/](docs/audit/). Work, evidence and limits: [docs/sprints/](docs/sprints/); reviews: [docs/reviews/sprint-01-independent-review.md](docs/reviews/sprint-01-independent-review.md).
- Three independent reviews. R-1 to R-4 (Major) fixed; re-review findings fixed; RR-3 (four eyes for approval, buying, ordering and receiving roles, every administrator, permanent) implemented; review fixes F-3, F-4, F-6 to F-12 done.
- Final code: backend 284/284 on SQL Server; frontend lint, 253/253 unit tests, build; browser suite 69/69 in one uninterrupted run (2026-11-17).
- **F-1, F-2, F-5 (owner approved 2026-11-17):** two-person activation, administrator roles never from the creator, two-administrator bootstrap, procurement separation of duties. Implemented without migration or new state. Fourth independent review: 5 Major, 3 Minor; fixes applied and tested. Backend 298/298, frontend lint, 255/255, build. Browser: **69/69 in one uninterrupted run** on the final code (2026-11-18). Final report: `docs/sprints/sprint-01-remediation-report.md`. Waiting for the owner: GO/NO-GO, attester-versus-payer option, commit plan.
- **Still pending from the owner:** Go/No-Go, commit and push plan, ADR-013 to ADR-017 implementation.
- New mandatory configuration and administration rules: [README.md](README.md).

## Next phase, recorded elsewhere

- Functional findings 1 to 16: [HANDOFF.md](HANDOFF.md).
- GLOBAL_ADMIN gaps: [docs/guides/global-admin-review.md](docs/guides/global-admin-review.md).
- Assistant decisions (provider, transport, retention, action scope): [docs/product/assistant-next-phase.md](docs/product/assistant-next-phase.md).
