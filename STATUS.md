# Status

Short, current state of the project. Detail and history are in [HANDOFF.md](HANDOFF.md).

_Updated 2026-11-21._

| Area | State |
|---|---|
| Published baseline | `c7b901d` (Meridian refinement), pushed to `enterprise/main` on 2026-11-12 |
| Work in progress | **Sprint 2 P1: CLOSED, GO** (owner, 2026-11-21) on `enterprise/sprint-2` (`19824ac`; code `f57f700`). Not merged into `main`. Next: Git integration proposal and P2 decisions, each awaiting approval |
| Review instance | None running |
| Hardening sprint 1 | Closed 2026-11-18; committed and pushed on `enterprise/feat/internal-messaging` (`72a2280`), CI green |
| CI | `.github/workflows/ci.yml`; last runs green: 44875958782 (`f57f700`) and 44876567944 (`19824ac`) |
| Release gates | **Google Cloud UAT: not yet approved** (P2 needs its own approval and D8, D9, D16, D18). **Production: NO-GO** (independent QA, penetration test, MFA verification, restore drill, real mail delivery open) |

## Internal messaging (in review)

- One-to-one messages between employees of one company, request discussions, "Message approver", live updates over Server-Sent Events. Design and rules: [docs/architecture/internal-messaging.md](docs/architecture/internal-messaging.md).
- Schema: V22 (new tables only).
- Final verification (2026-11-14, backend tree `bb5bdd8`, frontend tree `eaec3f5`): backend 208/208 on SQL Server; frontend lint, 253/253 unit tests, build; full Playwright suite 66/66 on an isolated instance with a fresh database. Send API about 30 ms and live event about 3 ms after it under normal load; a deadlock on simultaneous retries was found and fixed. Details: [HANDOFF.md](HANDOFF.md).
- Live delivery works on one backend node only; deploy one node until the multi-node option is decided.
- Decisions needed from the owner: message retention (pending; nothing is deleted); how live delivery reaches other nodes before any multi-node deployment; e-mail for messages (disabled for this phase; extension point only).

## Sprint 2 (P0 approved; **P1 CLOSED, GO, 2026-11-21**; P2 not started)

- **Branch:** local `sprint-2` from `72a2280` (Sprint 1, CI green). All Sprint 2 work is uncommitted; nothing pushed, merged, deployed or provisioned; no Google Cloud resource.
- **P0 approved 2026-11-18:** D1a Google Cloud UAT (e2-standard-4), ADR-018 in principle (provisioning needs a separate approval), D10 Sprint 1 pull request prepared but not merged, D11 HTTPS and IP allow-list, D12 Secret Manager, D13 ADR-016, D14 anchoring deferred, D15 Cloud Monitoring, D17 SQL Server 2022. **Pending:** D8 (Developer licence), D9 (mail relay), D16 (domain), D18 (trial account).
- **P1 implemented:** fail-closed ClamAV; two database identities in CI; Global Administrator Audit Center; private monitoring endpoint and `/healthz`; ADR-016 links only (temporary passwords removed; Arabic e-mails repaired); snapshot isolation tested on a disposable copy. Full backend 324/324 before stabilisation; frontend lint, 259/259, build. Report: [docs/sprints/sprint-02-progress.md](docs/sprints/sprint-02-progress.md).
- **Pushed and CI green (2026-11-21):** `enterprise/sprint-2` at `f57f700`, run https://github.com/amenzribi/Enterprise-Procurement-Management-/actions/runs/44875958782: backend **351/351 as the restricted runtime identity**, permission verification 12/12, jar, frontend lint, 268/268, build, 0 production advisories. The CI rounds also fixed: background jobs now obey `app.jobs.enabled`; **import jobs scoped to their company or creator** (security, ADR-007; global administrators read but do not create imports); several order-dependent tests. Not merged, deployed or provisioned. Database: SQL Server 2022 confirmed; PostgreSQL deferred (ADR-001 note). Report: [docs/sprints/sprint-02-progress.md](docs/sprints/sprint-02-progress.md).
- Snapshot isolation is **not** enabled on any database people use.

## Hardening sprint 1 (closed 2026-11-18; committed, pushed, **CI green**)

- Commits on `feat/internal-messaging`: `a72b7c0` messaging, `0523c73` Sprint 1 code, `b92cb3f` documents. Pushed to the new remote branch `enterprise/feat/internal-messaging` (head `b92cb3f`); `main` unchanged at `c7b901d`; no merge, no deploy.
- **CI run 44265601621 on `b92cb3f`: failure.** Frontend job passed (lint, unit tests, build, advisories). Backend job: **297 of 298 passed**; `ReceiptIntegrationTests.concurrentReceiptConfirmationAuditsOnce` errored because one of four simultaneous sign-ins of the same account got 503 `busy` (lock failure after 931 ms, while another sign-in finished at the same moment: a SQL Server deadlock victim, not the 10 s lock timeout). The resources in that deadlock are not known: CI keeps no deadlock graph and the error handler logs only the exception class. Not reproduced locally (every local run passed this test). https://github.com/amenzribi/Enterprise-Procurement-Management-/actions/runs/44265601621
- **Separate defect found while investigating:** two deadlocks recorded by the local SQL Server during browser attempt 6: the receipt view of a request (reads shipment lines) and recording a logistics milestone (updates milestone, then shipment) lock `shipment` and `shipment_milestone` in opposite order; the reader is killed and the user gets 503. Fix proposed, not made.
- **Stabilization commit `72a2280`** (owner-approved): milestone recording writes the shipment row before the milestone (the receipt-view deadlock, reproduced by a test that failed before the fix); the sign-in audit append is retried at most 3 times, in a new transaction, only for SQL Server error 1205; lock failures log the SQL Server error number; CI keeps deadlock reports on failure. Local: backend 302/302, frontend lint, 255/255, build.
- **CI run 44276571868 on `72a2280`: success.** Backend 302/302 on Linux and SQL Server 2022 (0 lock failures), frontend lint, 255/255, build, 0 production advisories. https://github.com/amenzribi/Enterprise-Procurement-Management-/actions/runs/44276571868
- Remaining concurrency risk: the sign-in deadlock of the first CI run was not reproduced and its exact locks are unknown; it is now retried, and a recurrence will be named by the CI deadlock reports and the error-number log.
- **Sprint 2:** execution plan finalised against the owner's provisional decisions D1 to D7 (`docs/sprints/sprint-02-plan.md`, uncommitted): phases P0 to P6, 11 gaps found and resolved in the plan, decisions D1a and D8 to D15 still open. Updated for the owner's Google Cloud choice: ADR-018 proposed (`docs/decisions/ADR-018-uat-hosting-google-cloud.md`, uncommitted): one e2-standard-4 VM, SQL Server 2022 on Linux, Caddy, ClamAV, Cloud Monitoring, Cloud Storage backups; about 130 to 140 USD a month, trial credit lasts about 65 to 70 days at 24/7. Open: D8 (Developer licence for acceptance testing), D16 (domain), D18 (trial account, owner's action). **P0 approved 2026-11-18** (D1a, ADR-018 in principle, D10 to D15, D17); D8, D9, D16, D18 pending. **P1 (application readiness) implemented** on local branch `sprint-2`, uncommitted: fail-closed ClamAV, two DB identities in CI, Audit Center, private monitoring endpoint, links-only passwords (ADR-016), snapshot isolation tested on a disposable copy (not safe to enable yet). Backend 324/324, frontend lint, 259/259, build. Report: `docs/sprints/sprint-02-progress.md`. Waiting for the owner (commit and push, findings 1 and 2, ADR-016 step 5). Nothing provisioned, pushed or merged; no AI assistant before infrastructure validation.
- Not committed, on purpose: `FrontEnd/angular.json` (Angular CLI analytics ID), `docs/tooling.md`, `.anchor/`, `docs/superpowers/`, and this checkpoint text.

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
