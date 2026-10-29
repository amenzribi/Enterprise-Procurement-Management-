# Sprint 2 — Test Results

| | |
|---|---|
| Closure revision | **`c99e249b1e30293d0fe43cee5e0fecf35fcfd99f`** (`c99e249`). 2026-10-29 Developer run after QA2-D-09 / D-10 / D-11. Earlier trees: `bf12fb7` (D-07/D-08 verification), `69be61a`, `767933d`. |
| Date | 2026-10-29 (closure). First run 2026-10-27/28. |
| Environment | Windows 11; backend `spring-boot:run` profiles `dev,demo` on **8081**, session-generated `DEMO_USER_PASSWORD` (never committed); `ng serve --port 4300` proxying to 8081; SQL Server (`procurement_dev` for live checks, `procurement_test` for DB-IT). Ports 8080/4200 untouched. Demo database was **not** wiped. |
| Tester | Developer (closure run). The 52-check QA2 API script was **not** re-executed; those rows below remain historical. |
| Defects | [sprint-2-defects.md](sprint-2-defects.md). D-01 – D-11 closed. Outstanding: native-speaker Arabic review; S2CR-08 deferred to S9. |

## Closure run (2026-10-29) — freshly executed

| Suite | Result | Notes |
|---|---|---|
| Backend `.\mvnw.cmd -o verify -Pdb-it` (pre-closure, 102 tests) | **102 / 102 passed**, BUILD SUCCESS | Tree before the new delegate-is-author test. |
| Backend `ApprovalIntegrationTests#absenceDelegationNeverRoutesATaskToTheRequestAuthor` | **1 / 1 passed** | New DB-IT: adminOverride Karim → requester.tn; author holds no task; Karim `ABSENCE_UNRESOLVED`. |
| Backend `.\mvnw.cmd -o verify -Pdb-it` (closure revision) | **103 / 103 passed**, BUILD SUCCESS | Includes `absenceDelegationNeverRoutesATaskToTheRequestAuthor`. |
| Frontend lint | **Pass** (twice: before and after inbox `switchMap`) | `ng lint` |
| Frontend unit tests | **196 / 196 passed** (24 files) | Was 194 on 69be61a, 195 with D-10, 196 with D-11. |
| Frontend production build | **Pass** | Initial total 496.17 kB / 131.78 kB |
| E2E `npm run e2e` (4300 → 8081, msedge) | **Run 1:** 22 passed, 1 failed (D-11 inbox search). **Run 2 (after D-11 fix): 23 passed, 0 failed, 0 skipped** (7.3 min). | Includes `accessibility.spec.ts`, `accessibility-approvals.spec.ts` (EN/AR desktop/phone), session, request journey, approval journey (D-09 assertions). |
| Secrets scan of this revision | No passwords, tokens or connection strings in the diff. Demo password only in a temp file outside the repo. | — |

### Live API / security (focused, against 8081 — not the historical 52-check script)

| Check | Result |
|---|---|
| `GET /api/v1/system/status` anonymous | 401 `unauthenticated` (status details are not public) |
| `POST /api/v1/auth/login` without CSRF | 403 `csrf-invalid` |
| `POST /api/v1/auth/login` with CSRF cookie + header | 200 |
| Hichem inbox unfiltered vs `q=DEMO-TN-PLT-PR-2026-000244` | 64 vs **1** (D-11 evidence) |
| SQL on `procurement_dev` for that request | `IN_APPROVAL`, one `PENDING` task, `routing_note=ABSENCE_DELEGATE`, assignee 501 |
| Cross-company 404 | FR requester `GET /requests/768` (TN) → 404 `not-found`; body matches unknown id `999999999` except `instance` |
| Tracker API still returns routing **codes** | Sami `GET /requests/768/approval` → `routingNote: ABSENCE_DELEGATE` (UI translates; D-10) |

Historical QA2-10 … QA2-130 results in §2 were **not** re-run. Do not treat them as new.

## 1. Automated suites (historical QA runs)

## 1. Automated suites (run by QA)

| Suite | bf12fb7 | 69be61a |
|---|---|---|
| Backend + SQL Server `mvnw.cmd -o verify -Pdb-it` | **102 / 102 passed** | 100 / 100 |
| Frontend lint / unit / build | Not re-run (no FrontEnd change since 69be61a) | Pass / **194 / 194** / Pass |
| E2E `npm run e2e` (4300 → 8081) | **22 passed, 1 failed (QA2-D-09), 0 skipped** (6.2 min) | 23 / 0 / 0 |

On `767933d` the same e2e gave 18 passed, 4 failed (QA2-D-01) and 1 skipped (QA2-D-04). The bf12fb7 SQL Server count matches the Developer's (102/102). The e2e does not match the Developer's 23/23 expectation: the one failure is a stale assertion (QA2-D-09), and the product behaviour is correct.

## 2. Independent live checks (API script against 8081)

On bf12fb7: **52 / 52 passed** (main script, which now includes QA2-60b). Separately, `audit_event` was checked with SQL and holds `APPROVAL_ABSENCE_ROUTED`. On 69be61a: 52 run, 51 passed; QA2-59 failed (QA2-D-07). Script: `qa2_api.py` in the QA scratchpad (not committed).

| ID | Check | Result |
|---|---|---|
| QA2-10 | Preview converts USD 8 400 → TND 26 040.000 at 3.1 (rate date shown) | Pass |
| QA2-20 / 21 / 22 | Submit → IN_APPROVAL/"Submitted"; last stage → APPROVED; deciding again → 409 `task-not-pending` | Pass |
| QA2-30 – 34 | Question: second open question 409; `questionOpen`; requester gets ANSWER; answer clears it; answer without a question 409 | Pass |
| QA2-35 / 36 | Reject without reason 422; reject → REJECTED, no actions | Pass |
| QA2-40 / 41 | Return → REVISION_REQUESTED, number kept, editable; resubmit at TND 100 → round 2 re-evaluated to 1 stage | Pass |
| QA2-50 / 51 | Delegate to another company's user or to the requester → 422 | Pass |
| QA2-52 | Karim's absence-delegate list holds Hichem (D-04) | Pass |
| QA2-55 – 58 | Admin absence with override 201; overlap 422 `absence-overlap`; self-delegation 422; new task routed to Hichem, none to Karim | Pass |
| QA2-59 | Delegate who is also eligible: one task for Hichem, on behalf of Karim, `ABSENCE_DELEGATE`, `ABSENCE_ROUTED` event + audit record | **Pass on bf12fb7** (failed on 69be61a → D-07) |
| QA2-60b | One token per e-mail, shared by the text and HTML parts (D-08) | Pass on bf12fb7 |
| QA2-60 – 63b | Link e-mail produced; GET and repeated summary (scanner prefetch) never approve; `no-store` + `no-referrer`; minimal summary fields; lines with quantity and unit (D-05) | Pass |
| QA2-64 / 65 | Approve without CSRF 403; another signed-in user (CEO) → neutral 404, task still pending; security log line written without the token | Pass |
| QA2-66 – 68b | Link approve OK; same Idempotency-Key replays an identical body; used link 404 body identical to a random token's and a wrong user's; second token of the same e-mail dies after the first is used | Pass |
| QA2-69 | Decision event has channel EMAIL | Pass |
| QA2-67b | Rate limit: 429 `too-many-requests` within one minute from one address | Pass |
| QA2-70 / 72 / 73 | Overlapping rule refused at activation (`ambiguous-approval-rule`); JPY without rate → `missing-exchange-rate`; admin company list includes companies without rules (D-02) | Pass |
| QA2-80 / 81 | Concurrent link-approve vs inbox-reject: exactly one wins (404/200 → REJECTED); same key with a different body → 422 `idempotency-key-reused` | Pass |
| QA2-90 | Clock +3 d → one reminder per task, second run 0; +10 d → one escalation per task, second run 0; events show REMINDER ×2 and ESCALATED ×2 for 2 tasks | Pass |
| QA2-100 – 107 | PDF upload 201; EXE renamed `.pdf` 415; SVG 415; `..\..\..\boot.pdf` stored as `boot.pdf`; > 20 MB 413; other-company download 404; `attachment` + `nosniff`; upload after submit 409 | Pass |
| QA2-120 – 122 | Later-stage approver 404 before her task; other company 404 on detail and approval; FR manager's inbox excludes TN | Pass |
| QA2-130 | Leila's (ar) task e-mail is `dir="rtl"` with Arabic text | Pass |
| Log scan | None of 777 link tokens found in the backend or dev-server log; no ERROR lines | Pass |

## 3. Results per acceptance criterion

| AC | Status | Evidence | Not covered by QA in this run |
|---|---|---|---|
| S2-AC-01 Arabic | **Passed** | e2e "question and answer … (AR)"; axe AR desktop/phone; QA2-130; i18n parity unit test | Manual visual RTL review of every screen |
| S2-AC-02 Approval rules | **Passed** | QA2-70, QA2-73; DB-IT rule specificity/overlap and admin companies/users; axe on rule admin | Clicking through creation of a first rule for DEMO-HOLD in the UI |
| S2-AC-03 Exchange rates | **Passed** | QA2-10, QA2-72; DB-IT exchange-rate test; summary amount scaled to `currencyMinorUnits` | Rounding edge cases (only covered by DB-IT) |
| S2-AC-04 Submission starts the circuit | **Passed** | QA2-20, QA2-41; DB-IT self-exclusion and `no-eligible-approver` | Self-exclusion and `no-eligible-approver` not reproducible live with the demo users (DB-IT only) |
| S2-AC-05 Approver actions | **Passed** | QA2-21/22, 30–36, 40/41, 50/51; DB-IT `allStageNeedsEveryApproverAndOneRejectionEndsIt`; e2e return/reject/question journeys | SLA pause during a question not independently timed (DB-IT only) |
| S2-AC-06 Absence delegation | **Passed** (closure) | QA2-52, 55–59 (D-07, historical); DB-IT ANY/ALL + **delegate-is-author**; e2e journey 23/23 with on-behalf-of + translated routing note; ALL/`ABSENCE_UNRESOLVED` documented in `sprint-2.md` | Chained absences still DB-IT/`route()` only; not executed live |
| S2-AC-07 One-tap e-mail approval | **Passed** | QA2-60–69, 60b (D-08 fixed), 67b, 80, 81; log scan; DB-IT `invalidLinksAllLookTheSame` | Expiry and reassigned-task invalidation not re-executed live (DB-IT only). Clear token in outbox: Reviewer S2CR-01. |
| S2-AC-08 Inbox | **Passed** | QA2-122; inbox search (`q`) used by the script and e2e; e2e journeys | Sorting and paging not independently checked |
| S2-AC-09 Reminders and escalation | **Passed** | QA2-90 (clock hook, exactly once per task); DB-IT `remindersAndEscalationsAreSentOnce` | — |
| S2-AC-10 Notifications | **Passed** | Outbox e-mails in the recipient's language (QA2-130); no duplicates on job re-runs (QA2-90); bell (D-01 fixed) | Crash/retry back-off not executed |
| S2-AC-11 Request tracker | **Passed** | e2e journeys (Submitted/Returned/Rejected/Approved); API user statuses in QA2-20/21/36/40 | — |
| S2-AC-12 Attachments | **Passed** | QA2-100–107; DB-IT `attachmentsAreSniffedLimitedAndScoped` | Line-level attachments and Office types not checked live |
| S2-AC-13 Visibility | **Passed** | QA2-65, 105, 120–122; DB-IT `newEndpointsKeepCompaniesApart` | — |
| S2-AC-14 Quality | **Passed** (closure) | e2e 23/23 after D-09/D-11; lint pass; 196/196 unit; production build; axe inside e2e. Native-speaker Arabic review still outstanding (S2DR-13), recorded as open, not as a failed AC. | Independent QA re-run of the 52-check API script not done this session |

Summary on the closure revision: 14 criteria executed. **14 passed** for the code/AC set; native Arabic review remains an open non-code item. No open Blocker or Major. The S1 regression subset is inside the e2e run and passed. Historical bf12fb7 summary (12 passed / 1 with Minor / 1 failed) is superseded by this closure run.

## 4. Notes

- **Data:** no scratch database was created. The live checks wrote to the shared dev/demo database, which now holds about 200 QA requests: manager.tn has 310 unread notifications and a pending inbox above 100. The e2e relies on inbox search, so it is unaffected.
- **Harness:** the scheduled outbox job can send an e-mail just before the on-demand job. The QA script retries its mailbox lookup, and the product is not affected.
- **Cleanup:** the servers on 8081 and 4300 were stopped after the run; 8080/4200 were never touched. The clock offset was reset to 0. The `user_absence` table in the dev DB was checked with SQL after the run: all 9 rows with a future end date have `cancelled_at` set, so no absence is active.
