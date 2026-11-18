# Sprint 1 test results

Only checks that were actually run are reported as run. Machine: the development laptop (Windows 11, SQL Server 2025 Developer, database `procurement_test`). Backend runs were made from a copy outside the workspace (`C:\qa\s1\BackEnd`), as the project requires. Logs: `C:\qa\s1\*.log`.

## 1. Final state

| Check | Result | Log |
|---|---|---|
| Backend `mvnw.cmd verify -Pdb-it` (unit, module structure, SQL Server integration) | **253 / 253 passed**, build success | `full4.log` (2026-11-16) |
| Frontend `npm run lint` | Passed, no findings | `fe-lint.log` |
| Frontend `npm test` | **253 / 253 passed** (32 files) | `fe-test.log` |
| Frontend `npm run build` | Succeeded. One budget warning that existed before the sprint (`sign-in.scss`, 9.24 kB against 8 kB) | `fe-build.log` |
| Database permission scripts | 12 / 12 checks passed for the runtime user; control run failed as intended | section 4 |
| Browser suite (Playwright, 66 tests) | **Not run** | — |
| CI pipeline | **Not run** (nothing pushed) | — |
| Load test, dynamic security scan, penetration test, backend dependency scan | **Not run** | — |

The frontend checks ran on 2026-11-15 after the last frontend change of the sprint; no frontend file changed afterwards.

Backend test count: 208 before the sprint, 253 after (45 new tests: 38 in 9 new classes, 7 added to 3 existing classes).

## 2. Runs, in order

| Run | Content | Result |
|---|---|---|
| 1 `baseline` | Full suite before any change | 206 / 208. Two failures in `RequestSubmissionIntegrationTests` (application lock not obtained within 2 s under 8 parallel submissions, and a count that followed from it) while the machine was busy. Not reproduced in any later run |
| 2 `b12`, `b12b` | Work packages 1 to 5, with the existing approval and user-administration classes | First attempt: 3 failures, all in the new tests' own set-up (start-up properties passed as defaults; a test exchange rate dated today disturbed another test). Fixed; 62 / 62 |
| 3 `full1` | Full suite after work packages 1 to 5 | 241 / 241 |
| 4 `b3`, `b3b` | Database waits, purge, observability, first matrix runs | Database tests passed. Matrix run produced the observed table that was then reviewed |
| 5 `full2` | Full suite with everything | **63 failures, caused by the sprint.** See section 3 |
| 6 `full3` | After the fixes | 253 / 253 |
| 7 `mx` | Matrix alone, after it was changed to build its own order, shipment and receipt | 3 / 3 |
| 8 `full4` | Final full suite | **253 / 253** |

## 3. The regression in run 5 and what it taught

Two observability changes altered response bodies that existing tests compare exactly:

| Change | Broke | Resolution |
|---|---|---|
| A `traceId` added to every error body | Tests asserting that two refusals are byte-identical (unknown and invalid links, another company's request, wrong password and unknown e-mail) | Removed from the body. The id stays in the `X-Request-Id` header |
| Health probes enabled (`/actuator/health` gained a `groups` field) | `AuthenticationIntegrationTests.diagnosticsAreRestricted` | Probes reverted to disabled |

Those failures left the sign-in throttle of the shared test context tripped, so 50-odd later tests failed at sign-in. They were consequences, not separate defects.

The same run showed one real application weakness, fixed in the sprint: the administrator endpoint for cancelling an absence answered 404 for an unknown id and 403 for an existing one to a non-administrator.

## 4. Database permission scripts

Run on `procurement_test` with two temporary users created `WITHOUT LOGIN`, then dropped.

First attempt found two defects in the scripts as first written:

- `DENY CONTROL ON SCHEMA` also denied reading and sequences to the runtime user.
- The check script ran inside one transaction; after the first refusal the transaction was doomed, so the following "refused" lines were false passes.

Both were corrected and the run repeated:

| Check, as the runtime user | Result |
|---|---|
| `DISABLE TRIGGER` on `audit_event` | Refused (1088) |
| `ALTER TABLE`, `CREATE TABLE`, `DROP TABLE` | Refused (1088, 262, 3701) |
| `UPDATE`, `DELETE` on `audit_event` | Refused by permission (229) |
| Write to `flyway_schema_history` | Refused (229) |
| `SELECT`; `NEXT VALUE FOR`; insert, update, delete on a data table; insert permission on `audit_event`; `sp_getapplock` | Allowed |

Control: the same script as the migration user reports 7 failures (it may alter the schema), which shows the script distinguishes the two. After cleanup: no leftover user, trigger enabled, no probe table or column.

**Not verified:** the application running with the runtime login. The local server accepts Windows authentication only.

## 5. New tests and what each proves

| Test class | Tests | Proves |
|---|---|---|
| `document/ContentSnifferTests` | 4 | S-2: a script with a PDF header is refused under 8 executable-looking names; matching extensions accepted |
| `document/ScanPolicyTests` | 5 | S-1: no scanner gives `PENDING` and quarantine; infected gives 422; failing or silent scanner gives 503 |
| `approval/ApprovalIntegrationTests` (extended) | — | S-1, S-2 through the API: status `PENDING`, three bad names refused with 415 |
| `platform/web/ClientAddressResolverTests` (extended) | 8 | S-3: forged left-most values ignored; trusted chain skipped; several header lines; `Forwarded` header |
| `approval/AdministratorLimitsIntegrationTests` | 9 | S-4 to S-7: see `sprint-01-security-changes.md` §3 |
| `dashboard/ErpExportCsvTests` | 2 | S-14 |
| `audit/AuditRecordJsonTests` | 1 | S-13: hostile text stays one JSON string |
| `platform/config/DeploymentConfigurationGuardTests` | 6 | S-15, S-16: each unsafe combination is reported |
| `StartupConfigurationTests` (extended) | 3 | The application really refuses to start: `dev` with HTTPS; no mail mode outside development |
| `platform/persistence/DatabaseWaitsIntegrationTests` | 3 | DB-3: 503 within the lock timeout, recovery; setting on every connection. DB-4: token purge respects the retention |
| `platform/persistence/SqlServerErrorsTests` (extended) | 2 | Error 1222 recognised however wrapped |
| `platform/web/ObservabilityIntegrationTests` | 5 | Request id generated by the server; found in the log, absent from bodies; access line without query string; metrics for global administrators only; nothing else exposed |
| `AuthorizationMatrixIntegrationTests` | 3 | 179 endpoints against 3 refused callers; privilege escalation; file upload to another company's request |

## 6. Authorization matrix figures

- 179 endpoints found in the test profile. The audit's figure was 180; the difference was not reconciled. The test counts what the running application exposes, and the two testing endpoints exist only with the `dev` profile, so they are outside it.
- Anonymous: 401 on 170; the 9 public endpoints answer according to their own token or credentials.
- Supplier contact: 403 on every endpoint outside the portal allow-list.
- Requester of another company: 403 or 404 on every endpoint that takes an object id, including a real order, RFQ, shipment, milestone and receipt of the other company; 403 on every administration, purchasing, dashboard, supplier and import-read endpoint.
- Observed answers of the last run: `C:\qa\s1\BackEnd\target\authorization-matrix-actual.txt`. Reviewed expectations: `BackEnd/src/test/resources/authorization-matrix.txt`.

## 7. Remediation phase (2026-11-17)

### Backend

| Run | Code state | Result |
|---|---|---|
| `final-backend` | R-1 to R-4 and second correction (RR-1, RR-2, RR-5 to RR-9) | **274 passed, 0 failed, 0 skipped** |
| `rr3` | + RR-3 (first version) | 65 tests of 6 classes, 18 failed: a null-handling defect in the new creator lookup (500), and the leftover data it caused. Fixed in `UserDirectory.createdBy` |
| `rr3b` | + fix | see the next checkpoint |

### Frontend (after R-1 to R-4)

Lint clean; 253 passed, 0 failed (32 files); production build succeeds. Not yet rerun after RR-3 (no frontend source change in RR-3).

### Browser suite: three attempts, none uninterrupted

| Attempt | Total | Passed | Failed | Skipped | Duration | Interruption |
|---|---|---|---|---|---|---|
| 2026-11-17 15:37 | stopped early | — | 2 | — | — | Sleep requested at 15:42:13 (event 187), resumed 16:13:48 |
| 2026-11-17 16:20 | 69 | 65 | 4 | 0 | 2 h 9 min (about 37 min of test time) | Sleep requested at 16:26:30 (event 187), resumed 17:58:19 |

The four failures of the 16:20 run, investigated:

| Test | Cause | Action |
|---|---|---|
| `accessibility-approvals` EN desktop (first test) | **Test race, not a product defect.** The helper `signIn()` switched the language; the account preference was saved in the background (797 ms under load); the next step's full page load read the old preference from `GET /me` and the interface switched back to French, so "Submit request" was never found. Happens only when the stored language differs, i.e. on the first sign-in against a fresh database; the same first test failed in both fresh-database runs | Fixed: `signIn()` waits until the account preference is saved (`FrontEnd/e2e/support.ts`) |
| `accessibility-purchasing` EN desktop | `ERR_NETWORK_IO_SUSPENDED`: ran across the sleep (1.5 h) | Environmental |
| `accessibility-purchasing` AR desktop | Expected the sign-in page, stayed on `/purchasing`; ran seconds after the machine resumed | Consistent with post-resume network loss; not proven. To watch in the next run |
| `sprint-01-hardening` company administrator | **Defect in the new test:** read the company list before it was filled | Fixed: waits for the two options |

**Slow sign-in (investigated).** In the 16:20 run: 126 sign-ins, median 550 ms, 95th percentile 1.9 s, maximum 5.5 s. In the same seconds as the slowest ones, trivial requests were also 30 to 60 times slower than their medians (`/auth/csrf` 280 ms against 9 ms; `/me` 367 ms against 6 ms), which shows a machine-wide slowdown, not a sign-in-specific wait. The 2026-11-16 failure (sign-in 9.8 s against a 10 s test limit) is consistent with this; its log was overwritten before analysis. An isolated measurement on an idle instance is the next step.

### Checkpoint 2026-11-17 (RR-3 tests)

- `rr3b` (RR-3 with the fix): 6 classes, **65 passed, 0 failed, 0 skipped** (`AdministratorLimitsIntegrationTests` 21, `ApprovalIntegrationTests` 17, `AuthorizationMatrixIntegrationTests` 4, `UserAdminIntegrationTests` 7, `ModularityTests` 1, `DeploymentConfigurationGuardTests` 15).
- **Sign-in measured on an idle machine:** 40 sequential sign-ins, server time median 104 ms, 95th percentile 155 ms, maximum 175 ms (client 106, 168, 239 ms). Against median 550 ms and maximum 5.5 s during the browser run: the slowness is load on the laptop, not a sign-in defect.
- **What put the machine to sleep:** both sleeps (15:42:13, 16:26:30) were requested by `StartMenuExperienceHost.exe`, i.e. *Sleep* chosen in the Windows Start menu. No setting prevents that; the next run needs the laptop left awake.

### Checkpoint 2026-11-17 (final code) (RR-3 and review fixes F-3, F-4, F-6 to F-12)

| Check | Result | Log (`C:\qa\s1`) |
|---|---|---|
| Backend `verify -Pdb-it`, SQL Server | **284 passed, 0 failed, 0 skipped** | `final-fix.log` |
| Frontend lint | Passed | `final2-fe-lint.log` |
| Frontend unit tests | **253 passed, 0 failed** (32 files) | `final2-fe-test.log` |
| Frontend production build | Succeeded; the 2 budget warnings that predate the sprint | `final2-fe-build.log` |
| Browser suite | **69 passed, 0 failed, 0 skipped, 0 flaky, 26.0 min, uninterrupted** (section below) | `e2e\pw-final.log` |

Backend count: 208 before the sprint, 284 now.

### Final browser run (2026-11-17 19:17 to 19:43), final code

| Total | Passed | Failed | Skipped | Flaky | Duration |
|---|---|---|---|---|---|
| **69** | **69** | **0** | **0** | **0** | **26.0 min** |

- **Uninterrupted:** no sleep or resume event in the System log during the run, and no clock leap in the server log.
- Instance: jar built by the `final-fix` backend run, `dev,demo` profiles on :8085, fresh database `procurement_qa_s1val`, `ng serve` on :4205, Microsoft Edge. Log `C:\qa\s1\e2e\pw-final.log`; JSON `C:\qa\s1\e2e\pw-results-final.json`.
- Sign-ins during the run: 148, median 292 ms, 95th percentile 957 ms, maximum 2.2 s; no test failed at sign-in.
- After the run: instance stopped; `docs/qa/evidence` and `docs/design/verification` restored (no evidence file changed).

## 8. Business security decisions F-1, F-2, F-5 (2026-11-17)

| Check | Result | Log (`C:\qa\s1`) |
|---|---|---|
| Affected classes, old tests (expected to fail) | 14 failures in AdministratorLimits, UserAdmin and Receipt; these tests assumed one person could create and activate, or record and pay | `f125-a.log` |
| Affected classes, adapted and new tests | UserAdmin 7/7, Receipt 36/36, AuthorizationMatrix 4/4; AdministratorLimits 2 and Purchasing 1 failed (below) | `f125-b.log` |
| After the fixture fixes | AdministratorLimits **33/33**, Purchasing **6/6** | `f125-c.log`, `f125-e.log` |
| Backend `verify -Pdb-it`, SQL Server, before the fourth review | **293 passed, 0 failed, 0 skipped** | `f125-full.log` |
| Fourth review fixes, affected classes | AdministratorLimits 36/36, UserAdmin 7/7, Purchasing 6/6, Receipt 38/38: **87/87** | `r4-a.log` |
| **Backend `verify -Pdb-it`, SQL Server, final code** | **298 passed, 0 failed, 0 skipped** | `r4-full.log` |
| Frontend lint, final code | Passed | — |
| Frontend unit tests (`npm test`), final code | **255 passed, 0 failed** (32 files) | — |
| Frontend production build, final code | Succeeded; the 2 budget warnings that predate the sprint (initial bundle, `sign-in.scss`) | — |
| Browser suite, attempt 1 (21:39 to 22:11) | 67 passed, **2 failed**: `ordering-logistics-journey` still ticked the "attested" box that F-5 removed (test not updated); `request-admin` sign-in helper race (below) | `e2e\pw-r4-attempt1.log` |
| Both specs after the fixes, same instance | **8/8** (with the Arabic accessibility runs before them) | — |
| Browser suite, attempt 2 (22:20 to 22:48), fresh database | 68 passed, **1 failed**: `request-to-closure-20-step`, receipt form never shown to Omar within 45 s (below) | `e2e\pw-r4-attempt2.log`, `e2e\backend-r4-attempt2.log` |
| Both journeys after the fix, same instance | **2/2** | — |
| Browser suite, attempt 3 (22:59 to 23:26), fresh database | 67 passed, **2 failed**, both time limits: the first test of the run waited more than 10 s for the approval preview on a cold server and dev server (server answered in 2.4 s once called); `messaging-journey` exceeded its 60 s limit after the supplier's reply was sent (34 s in attempt 1). Limits raised to 30 s and 120 s; other journeys already allow 120 to 180 s | `e2e\pw-r4-attempt3.log` |
| Browser suite, attempt 4 (23:29 to 23:56), fresh database | 67 passed, **2 failed**, both time limits again in other tests: `approval-journey` absence delegation exceeded 60 s (no refusal in the server log during the test); `internal-messaging` unread badge not refreshed within 15 s | `e2e\pw-r4-attempt4.log` |
| Browser suite, attempt 5, fresh database, screenshots outside the working tree | **Stopped by the owner** shortly after it started (00:01, 2026-11-18); no result |
| Browser suite, attempt 6, fresh database, screenshots outside the working tree (OneDrive not paused, owner's choice) | 67 passed, **2 failed** (08:15 to 08:48), time limits: `request-admin` cleanup preview took 16.6 s on the server (0.4 to 1.3 s in earlier runs), `request-to-closure-20-step` request page not shown within 15 s (server answers up to 20 s). Machine at 60 to 100% CPU with the instance idle (OneDrive, Chrome) | `e2e\pw-r4-attempt6.log` |
| **Browser suite, attempt 7, final code, fresh database** | **69 passed, 0 failed, 0 skipped, 0 flaky, 25.7 min, uninterrupted** (08:56 to 09:22, 2026-11-18) | `e2e\pw-final.log`, `e2e\pw-results-final.json` | `e2e\pw-final.log` |

**The sign-in helper race:** the accessibility specs leave `admin@demo.local` with Arabic saved. The application
applies the saved language after its post-sign-in CSRF refresh, as designed (S1DR-21); the helper looked at the page
before that, saw English, did not switch, and then waited for an English preference nobody saved. The helper now
retries the switch until the page and the saved preference both show the requested language. Not a product defect.

**The receipt-panel wait (attempt 2):** the server offered the receipt throughout: request 42 was DELIVERED, and all
17 of Omar's dossier calls in the window answered 200 (server log); after the run the same page showed the form. The
helper reloaded the page about every 2.5 s whenever the panel was not yet rendered, and under load the page needed
longer to load the request and the dossier, so each reload restarted it. The helper (and the same loop in
`ordering-logistics-journey`) now waits up to 10 s for the form before reloading. One `GET /receipts/requests/42`
of the buyer answered 503 (lock wait) during her logistics updates in the same window; it was retried by the next
call and did not cause the failure. Not a product defect.

**Machine load during attempts 1 to 4:** every failure after the two test defects of attempt 1 is a time limit, in a
different test each run, with correct server answers. OneDrive used about one full core throughout (17,000 CPU
seconds since the morning; still about 100% of a core with the instance idle after attempt 4), synchronising the
workspace, where each run rewrites 81 evidence screenshots. The 20-step and Ledger specs now accept
`E2E_EVIDENCE_DIR` and `E2E_SHOTS_DIR` (defaults unchanged); attempt 5 writes them under `C:\qa\s1\e2e`.

**Attempt 7, the run of record:** jar built by the `r4-full` backend run (298/298), `dev,demo` profiles on :8085,
fresh database `procurement_qa_s1val`, `ng serve` on :4205, Microsoft Edge, screenshots under `C:\qa\s1\e2e`.
No sleep or resume event in the System log during the run, no gap over two minutes in the server log, no lock
failure; 150 sign-ins. The backend code did not change between `r4-full` and this run; the frontend changes after
the unit-test run are browser specs only (lint passed). After the run: instance stopped; `docs/qa/evidence` and
`docs/design/verification` unchanged.

The failure screenshots of attempts 1 and 2 show pages left open by `receipt-rules.spec.ts` (its API helpers keep
their contexts for the whole file), not the failing test's page; read the per-page screenshots, not the first one.

The three failures of `f125-b` were the tests reaching an earlier refusal than the rule they check, never an accepted
action: a new account is now inactive, so it was refused as delegate or approver for that reason
(`delegate-not-eligible`, `invalid-value`) before the creator rule; and the requester, whose home company is not the
buying company, was refused as `not-eligible` before `buyer-is-requester`. The fixtures now activate the account
through a second administrator, and buy the request directly through the requester's plant, so each test reaches the
rule it names.
