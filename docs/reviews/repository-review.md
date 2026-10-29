# Repository review (Sprint 2 closure)

- Date: 2026-10-29
- Reviewer: Developer (same session that applied the Sprint 2 closure fixes). **This is a self-review, not an independent review.** Earlier independent reviews remain in `sprint-0-code-review.md`, `sprint-1-code-review.md` and `sprint-2-code-review.md`.
- Scope: architecture, transactions/concurrency, SQL Server migrations, authz and company isolation, approval/absence/e-mail/outbox/attachments, frontend/API/a11y/i18n, tests and documentation drift. Sprint 0–2 code as it stood at handover plus the Sprint 2 closure changes in this revision.
- Method: static reading of modules, migrations, tests and docs; runtime evidence is the checks listed in `docs/qa/sprint-2-test-results.md` (this revision), not the historical QA2-52 API script.
- Verdict: **acceptable for Sprint 2 closure** once QA2-D-09, D-10 and D-11 are closed on this revision. No Blocker remains. S2CR-08 stays deferred to Sprint 9. Native-speaker Arabic review stays outstanding (S2-AC-01 / S2DR-13).

## Confirmed defects

| ID | Severity | Location | Evidence | Impact | Recommended fix | Disposition |
|---|---|---|---|---|---|---|
| QA2-D-09 | Major | `FrontEnd/e2e/approval-journey.spec.ts` (absence-delegation journey) | On tree `bf12fb7` the e2e asserted the tracker task did **not** contain "Karim Haddad". After S2CR-15 / D-07 the task correctly reads "Hichem … on behalf of Karim Haddad (Demo)" plus the routing note. That is S2-AC-06. The assertion encoded the old D-07 behaviour. | Sprint gate S2-AC-14 red; Hichem's inbox approve step never ran in e2e. | Strengthen the assertion to require one task, Hichem, `tracker.onBehalfOf` for Karim, and `tracker.routingNotes.ABSENCE_DELEGATE`. Do not weaken it. | **Fixed** this revision. Fresh e2e 23/23. |
| QA2-D-10 | Minor | `approval-tracker.ts`; `public/i18n/{en,fr,ar}.json` | Tracker printed `task.routingNote` verbatim (`ABSENCE_DELEGATE` / `ABSENCE_UNRESOLVED`). Explicit inbox delegation stores `DELEGATED`, which would have fallen through to a generic "OTHER" text if only the two absence codes were mapped. | Users saw routing codes instead of EN/FR/AR. Violates the "every user-visible string is translated" convention. | Map known codes to `tracker.routingNotes.*`; unknown codes → `OTHER`; add `DELEGATED`. | **Fixed** this revision. Unit test `routingNoteKey`; e2e asserts the translated absence note. |
| QA2-D-11 | Major | `FrontEnd/src/app/features/approvals/approvals-inbox.ts` `load()` | After D-09 the same e2e failed at Hichem's inbox: search for `DEMO-TN-PLT-PR-2026-000244` (task exists, `ABSENCE_DELEGATE` to user 501) timed out. API `GET /approvals/inbox?q=…` returns `totalItems=1` in 107 ms; unfiltered list is 64 items / 277 ms. `load()` subscribed without cancelling the previous request, so the slow first page could overwrite the filtered page. `requests-list.ts` already used `switchMap`. | With a large pending inbox, search can show the wrong page and hide the request the user just looked up. | One in-flight inbox request (`switchMap`); unit test that a slow unfiltered flush is cancelled after `?q=` changes. | **Fixed** this revision. Re-run e2e 23/23. |
| RR-01 | Major (docs) | `README.md`, `docs/architecture/architecture.md`, `docs/tooling.md` | README: "Java 25", "Current state: Sprint 0". Architecture: "Java 25 LTS", "Sprint 0 baseline". Tooling: Temurin 25 as project target. `pom.xml` and the runtime are **Java 21** (`java.version` 21; Temurin 21.0.12.1). ADR-010 rejected Java 25. | Onboarding and later agents follow the wrong JDK. | Align README, architecture and tooling with ADR-008/010 and the actual compiler target. | **Fixed** this revision. |

## Not defects (confirmed behaviour)

| ID | Topic | Evidence | Disposition |
|---|---|---|---|
| RR-02 | BUYER / PURCHASING_MANAGER / ADMIN see no requests | `RequestAccessPolicy` javadoc and `visibleTo`: those roles are excluded until purchasing visibility is added. | Sprint 3 work. **Do not** obtain group visibility by removing company-scoped queries; add an explicit authorised relationship (`buying_company_id` / processing company) as in `docs/analysis/design-review-response.md`. |
| RR-03 | ALL-stage absence, delegate already an approver | `ApprovalEngine.planTasks`: the absent approver keeps a `PENDING` task flagged `ABSENCE_UNRESOLVED`. One person never holds two required approvals. | Intended. Documented in `docs/product/sprint-2.md`. Task moves on return, admin re-route of the absence, or SLA escalation (S2-AC-09). |
| RR-04 | Delegate is the request author | `planTasks` rewrites routing to `ABSENCE_UNRESOLVED` on `request.isAuthor(assignee)`. New DB-IT `absenceDelegationNeverRoutesATaskToTheRequestAuthor`. | Intended (QA2-35 / S2DR-02). Covered this revision. |
| RR-05 | One-tap token in stored outbox | S2CR-01: placeholder `{{link:approval:task:user}}` resolved at send time. Dev `.eml` files still contain the live token (dev only). | Accepted in `sprint-2-code-review.md`. No change. |
| RR-06 | Rate limit keyed on `getRemoteAddr()` | S2CR-08, `ApprovalLinkController.throttle()`. | **Deferred to Sprint 9.** Not described as fixed. Not a Sprint 2 closure blocker while the app is reached directly. Must be fixed before any reverse-proxy deployment. |

## Suggestions (not closure blockers)

| ID | Severity | Location | Evidence | Impact | Recommended fix | Disposition |
|---|---|---|---|---|---|---|
| RR-07 | Suggestion | `approval/ApprovalStore.java` (`withStages`, `absencesOfUsers`, `decidedTasksOfUser`) | Numeric ids concatenated into SQL. | No injection (ids are longs). Style risk if a later change interpolates text. | Use bind parameters throughout. | Backlog. |
| RR-08 | Suggestion | `docs/architecture/architecture.md` §1 | Context diagram lists purchasing, sourcing, ordering, logistics, receiving, supplier, dashboard, integration modules that have no code. Modulith only verifies packages that exist. | Readers treat future modules as present. | List only packages that exist; mark the rest as planned. | Partial fix this revision (status + Java 21 + module list). Remaining module diagram cleanup is backlog. |
| RR-09 | Suggestion | `docs/product/requirements-register.md` vs `roadmap.md` | FR-APR-04 still "S3 Planned" although reminders/escalation shipped in S2. NFR-11 still "Deferred" although Arabic shipped in S2. FR-SRC-01 is S4 in the register and S3 on the roadmap; FR-ERP-02 import is S7 in the register and S3 on the roadmap. | Sprint planning uses the wrong sprint tag. | Align register with shipped work and with the approved S3 roadmap (supplier **list/register** and master-data **import wizard** in S3; RFQs stay S4; ERP **connectors** stay deferred). | Register rows for shipped S2 items corrected this revision; S3 tags applied in the Sprint 3 plan. |
| RR-10 | Suggestion | No admin "re-route now" for `ABSENCE_UNRESOLVED` | Engine leaves the task pending. | Administrators must edit the absence or wait for escalation. | Dedicated admin action in a later sprint. | Backlog. |
| RR-11 | Suggestion | Native-speaker Arabic review | S2DR-13 / S2-AC-01. Translations and RTL exist; no native review recorded. | Residual wording/layout risk in AR. | Keep open. | Outstanding. |

## Areas reviewed (no new confirmed defect)

| Area | What was checked | Result |
|---|---|---|
| Architecture / Modulith | Packages under `com.epi.procurement`; `ArchitectureTests` (controllers never use repositories; only `ScopedRequests` uses `PurchaseRequestRepository`); `ModularityTests`. | Pass on this machine. |
| Transactions / concurrency | `ApprovalEngine.decide` lock order request → round → tasks → audit; `TransactionLocks.lockExclusively` (`sp_getapplock`); optimistic `version` on request and task; idempotency 24 h. | As designed (S2DR-08). Concurrent link-approve vs inbox-reject covered by historical QA2-80; not re-run live this session. |
| SQL Server / Flyway | V1–V6 only; `DATETIME2(3)`, `DECIMAL`, `NVARCHAR`, `BIGINT` sequences. Demo data in `db/demo`. Tests against `procurement_test` (name must end `_test`). | No H2/PostgreSQL. Dev DB was not wiped. |
| Auth / CSRF / session | SPA CSRF cookie, `csrf-invalid` 403, session idle 30 min / absolute 8 h, demo gated on profile. Live: POST login without CSRF token → 403 `csrf-invalid`; with token → 200. | OK. |
| Company isolation | ADR-007: out of scope → 404. `newEndpointsKeepCompaniesApart` DB-IT; live: FR requester on a TN request → 404, body matches unknown id. | OK. Visibility is **not** widened by dropping scoped queries. |
| Approval / absence / FX | Rule versions immutable once used; FX HALF_UP to minor units; absence chain ≤ 3 hops; `planTasks` order-independent (S2CR-15). | OK. ALL/`ABSENCE_UNRESOLVED` documented. |
| E-mail links | Fragment token, SHA-256 at rest, POST summary/approve, 72 h, placeholder resolved per mail, neutral 404. Rate limit still `getRemoteAddr()` (S2CR-08). | OK for direct access. |
| Outbox | Claim `SENDING` + `UPDLOCK`/`READPAST`, send outside tx, backoff, max 8, purge after `P30D`. | Matches S2CR-06 verification. |
| Attachments | Sniffed allow-list, generated names, `attachment` + `nosniff`, app lock per request. | Matches S2CR-12 verification. Not re-uploaded live this session. |
| Frontend / a11y / i18n | Standalone signals OnPush zoneless; EN/FR/AR key parity unit test; logical CSS; axe e2e `accessibility.spec.ts` and `accessibility-approvals.spec.ts` (EN/AR, desktop/phone) inside the 23/23 run. | OK. Native AR review still open. |
| Secrets | No passwords in the repo. Demo password via `DEMO_USER_PASSWORD` (session-generated, never committed). Scan of this revision's diff: no hardcoded secrets. | OK. |

## Closure blockers

Treated as Sprint 2 closure blockers: **QA2-D-09**, **QA2-D-11**, **RR-01** (doc JDK/state). **QA2-D-10** is Minor and is fixed on the same revision. **S2CR-08** is not a closure blocker. Native Arabic review is not a code defect and stays outstanding.
