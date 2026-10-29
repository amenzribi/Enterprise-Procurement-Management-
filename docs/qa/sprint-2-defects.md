# Sprint 2 — Defects

Severity scale as in `test-strategy.md`. Reviewer findings are referenced, not duplicated.

- First run: git tree `767933d` (2026-10-27/28), where defects QA2-D-01 to D-06 were raised.
- Re-test: git tree `69be61a10917020ab71c337c1fa9cfc14ec66f6e` (commit `487d53b` plus 3 staged FrontEnd files), 2026-10-29. QA2-D-07 and D-08 were raised in the re-test.
- Fix verification: git tree `bf12fb7f31163aafe8d34cb14b15e0063033f0f2` (69be61a plus staged changes to `ApprovalEngine.java`, `OutboxSender.java` and `ApprovalIntegrationTests.java`), 2026-10-29. D-07 and D-08 verified fixed; QA2-D-09 and D-10 raised.
- Closure verification: 2026-10-29 (Developer, same session as the D-09/D-10/D-11 fixes). See `docs/qa/sprint-2-test-results.md`. This is **not** an independent QA re-test.

| ID | Severity | Summary | Status (closure revision) |
|---|---|---|---|
| QA2-D-01 | Major | Notification bell fails WCAG 2.5.3 (axe `label-content-name-mismatch`) above 99 unread | **Closed — verified fixed** |
| QA2-D-02 | Major | Rule editor cannot target a company without rules; no user picker (Reviewer **S2CR-02**) | **Closed — verified fixed** |
| QA2-D-03 | Minor | ALL stage policy has no automated test | **Closed — verified fixed** |
| QA2-D-04 | Minor | No second Sfax department manager in demo data (Reviewer **S2CR-03**) | **Closed — verified fixed** |
| QA2-D-05 | Minor | One-tap link summary lines lack quantity/unit | **Closed — verified fixed** |
| QA2-D-06 | Trivial | `POST /testing/clock` expires existing sessions | **Closed — not a defect** (works as designed) |
| QA2-D-07 | Minor | Absent approver's delegate who is already eligible in the same stage keeps one task without the "on behalf of" mark | **Closed — verified fixed** |
| QA2-D-08 | Trivial | Each task e-mail carries two different valid link tokens (plain-text and HTML parts) | **Closed — verified fixed** |
| QA2-D-09 | Major | e2e "absence delegation … on behalf of the absent approver" fails: its assertion encodes the old D-07 behaviour | **Closed — verified fixed** |
| QA2-D-10 | Minor | Tracker shows the raw routing code `ABSENCE_DELEGATE` / `ABSENCE_UNRESOLVED` instead of translated text | **Closed — verified fixed** |
| QA2-D-11 | Major | Approver inbox search can be overwritten by a slower unfiltered response | **Closed — verified fixed** |

Open Blocker/Major: **none**. Outstanding non-code item: native-speaker Arabic review (S2DR-13). Reviewer **S2CR-08** (rate limit behind a proxy) remains deferred to Sprint 9 and is not described as fixed. Historical note: S2CR-01 (token in stored outbox) was verified placeholder-at-rest on `69be61a`; tokens still appear in dev `.eml` files.

## QA2-D-01 — Bell accessible name vs visible text above 99 unread (Major) — Closed

- **Original:** with more than 99 unread, the badge showed `99+` while `aria-label` said "Notifications, 192 unread", so axe failed on the inbox in 4 e2e variants.
- **Fix verified:** `notification-bell.ts` now uses `shownCount()` (capped to `99+`) in both the badge and `aria-label`; the exact count moved to `title`. With manager.tn at **310 unread**, `accessibility-approvals.spec.ts` passes in EN/AR on desktop and phone (e2e 23/23).

## QA2-D-02 — First rule for a new company (Major) — Closed

- **Fix verified:** `GET /api/v1/admin/companies` returns `DEMO-CPA, DEMO-FR-PLT, DEMO-HOLD, DEMO-TN-PLT`, which includes companies without rules (QA2-73). The rule editor takes its company options from that endpoint and loads a named-approver picker per company (`approval-rules.ts`). The DB-IT test `administratorsListCompaniesAndUsersOfTheirGroup` passes, and the axe check on rule admin passes.
- Not executed: clicking through the creation of a first rule for DEMO-HOLD in the browser.

## QA2-D-03 — ALL policy unverified (Minor) — Closed

- **Fix verified:** the DB-IT test `allStageNeedsEveryApproverAndOneRejectionEndsIt` exists and passes (SQL Server, 100/100).

## QA2-D-04 — Demo data gap for delegation (Minor) — Closed

- **Fix verified:** the demo data now has `manager2.tn@demo.local` (Hichem Gharbi, Sfax/Maintenance). Karim's absence delegate list returns Hichem (QA2-52). The e2e "absence delegation … on behalf of the absent approver" runs and passes (it was skipped before).
- Side effect: Department Manager stages in Sfax now create two ANY tasks (Karim + Hichem). Reminder volume doubles accordingly, which is expected.

## QA2-D-05 — Link summary without quantities (Minor) — Closed

- **Fix verified:** the summary returns `lines: [{description, quantity, unit}]`, `lineCount` and `currencyMinorUnits` (QA2-63b: `{"description": "Mechanical seal P-204", "quantity": 1, "unit": "EA"}`). The page formats the amount with the server's minor units.

## QA2-D-06 — Clock hook expires sessions (Trivial) — Closed, not a defect

A +60-minute shift returns 401 for an existing session. This is the idle timeout behaving correctly on the shifted clock. QA scripts sign in again after each shift.

## QA2-D-07 — Delegate who is also an eligible approver loses the "on behalf of" mark (Minor) — Closed

- **Fix verified on bf12fb7:** QA2-59 (Karim absent → Hichem; ANY stage resolving both) now gives one task: Hichem, `onBehalfOf` Karim, `routingNote` `ABSENCE_DELEGATE`. Events are `TASK_CREATED` then `ABSENCE_ROUTED`. `audit_event` holds `APPROVAL_ABSENCE_ROUTED` for request 657 with payload `{"taskId":340,"stage":1,"absent":101,"assignee":501}`. The UI shows "Hichem Gharbi (Demo) · on behalf of Karim Haddad (Demo)".
- The ALL-stage case (absent approver keeps own task, `ABSENCE_UNRESOLVED`), the order-independence case and the delegate-is-author case are covered by the DB-IT tests `anyStageCollapsesAnAbsenceOntoADirectApproverInBothDirections` and `allStageNeverGivesTwoRequiredApprovalsToOnePerson`, which pass (102/102). They were not exercised live because no demo rule uses ALL.
- Observation (not a defect): the audit actor of `APPROVAL_ABSENCE_ROUTED` at submission is `system`, while `SUBMITTED` is Sami. This is consistent with first-stage routing being a system action (`ApprovalEngine.java:118`).

### Original report (69be61a)

- **Requirement:** S2-AC-06 says tasks created during an absence go to the delegate, marked "on behalf of" the absent user, and the audit shows both.
- **Actual:** the circumstances are Karim absent, delegate Hichem, and a Department Manager stage that resolves both Karim and Hichem. Only one task is created: Hichem's, with `onBehalfOf: null` and no `original` user on `TASK_CREATED`. `ApprovalEngine.java:168` skips the second routing to the same assignee, so whichever routing comes first wins. Karim's absence leaves no trace on the request.
- **Repro (QA2-59):** `POST /admin/absences {userId: Karim, delegateUserId: Hichem, adminOverride: true}` → Sami submits a TND 100 request → `GET /requests/{id}/approval` shows 1 task, Hichem, `onBehalfOf: null`.
- **Impact:** routing is correct and the request can be approved. The losses are the audit and display trace of the delegation. In an **ALL** stage that holds both users, one approval from Hichem satisfies the stage where two were configured. That is acceptable delegation semantics, but nobody is told. No demo rule uses ALL with two managers.
- **Severity: Minor.** No wrong decision, no data exposure, workaround exists (the absence record itself is audited). It should be fixed or explicitly accepted before Sprint 3. Suggested fix: when deduplicating, keep the routing that carries `original` (or record both on the task).

## QA2-D-08 — Two valid tokens per e-mail (Trivial) — Closed

- **Fix verified on bf12fb7:** links are resolved once per e-mail at send time (`OutboxSender.send`). QA2-60b: the task e-mail to manager.tn has text/plain and text/html parts with **one** distinct token, and that token works (QA2-66) and is single-use (QA2-68). None of 777 tokens appear in the backend or dev-server logs.

### Original report (69be61a)

- **Actual:** each task e-mail contains two different `/a#` tokens, one in the text/plain part and one in the HTML part. Both are valid (summary 200 for each). This doubles the number of live credentials per task, and the outbox stores both in clear (see S2CR-01).
- **Mitigation observed:** once one token is used, the other answers the neutral 404 (QA2-68b), because the task is no longer pending.
- **Suggested fix:** generate one token per task notification and reuse it in both parts.

## QA2-D-09 — Absence-delegation e2e asserts the old behaviour (Major) — Closed

- **Fix verified (2026-10-29):** the journey now requires exactly one tracker task, containing "Hichem", `tracker.onBehalfOf` for `Karim Haddad (Demo)`, and `tracker.routingNotes.ABSENCE_DELEGATE`. That matches S2-AC-06 / S2CR-15. Hichem then searches his inbox by request number and approves; the requester sees `APPROVED`. Fresh `npm run e2e` (4300 → 8081): **23 passed, 0 failed**. The assertion was not weakened.

## QA2-D-10 — Raw routing code shown in the tracker (Minor) — Closed

- **Fix verified (2026-10-29):** `routingNoteKey` maps `ABSENCE_DELEGATE`, `ABSENCE_UNRESOLVED` and `DELEGATED` to `tracker.routingNotes.*` in EN, FR and AR; unknown codes use `OTHER` (never the raw code). The D-09 e2e asserts the translated absence note. Unit test `routingNoteKey` covers the three known codes and the fallback. RTL: Arabic keys are present; native-speaker review remains outstanding.

## QA2-D-11 — Inbox search overwritten by a slower unfiltered response (Major) — Closed

- **Raised (2026-10-29)** on the D-09 fix revision: after the tracker assertions passed, Hichem's inbox search for `DEMO-TN-PLT-PR-2026-000244` timed out. SQL: one `PENDING` task, assignee 501 (Hichem), `routing_note = ABSENCE_DELEGATE`. Live API as Hichem: unfiltered inbox `totalItems=64` (277 ms); `q=<number>` `totalItems=1` (107 ms). `ApprovalsInboxPage.load()` started a new HTTP call without cancelling the previous one, so the first page (20 of 64, sorted by due date, without this request) could land after the filtered page.
- **Fix verified:** `switchMap` on a `reload$` stream, same pattern as `requests-list.ts`. Unit test `drops a slow earlier response when the search has changed since (QA2-D-11)` passes. Fresh e2e 23/23.
