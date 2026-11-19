<!-- checkpoint:start -->
# Handoff: Sprint 2 P1 committed locally (five commits, 2026-11-19); push awaiting approval

**Resume here.** Read `CLAUDE.md`, this section, `STATUS.md`, `docs/sprints/sprint-02-plan.md` and `docs/sprints/sprint-02-progress.md`. Do not commit, push, deploy, merge, create cloud resources or start P2 without the owner's approval. A copy of this block is kept in `C:/qa/checkpoints/` (STATUS.md and HANDOFF.md were found reverted to older content on 2026-11-18 evening; cause unknown; rebuilt from the session record).

| Item | Value |
|---|---|
| Branch | local `sprint-2` from `72a2280`; remote `enterprise/feat/internal-messaging` at `72a2280` (CI green, run 44276571868) |
| Recovery references | `refs/backup/sprint1/branch-before-commits` (`c7b901d`), `refs/backup/sprint1/working-tree-before-commits` (`79c60d8`), `refs/backup/sprint1/before-stabilization` (`b92cb3f`) |
| Not to commit | `FrontEnd/angular.json` (CLI analytics ID), `docs/tooling.md`, `.anchor/`, `docs/superpowers/` |

## Sprint 2 P1 (implemented, uncommitted)

| Step | Main files | Evidence |
|---|---|---|
| P1.1 ClamAV fail-closed | `document/ClamAvScanner`, `ClamAvConfiguration`, `ScanPolicy` (counters), `DeploymentConfigurationGuard`, `application.yml` (`APP_CLAMAV_HOST`) | ClamAvScannerTests 8, guard tests |
| P1.2 Two DB identities | `.github/workflows/ci.yml`, `scripts/db/grant-application-roles.sql`, `verify-runtime-permissions.sql`, `SqlServerTestSupport.ownerJdbc()`, `SqlServerIntegrationTests` | 12/12 checks on a disposable copy; suite as runtime identity only in CI (needs a push) |
| P1.3 Audit Center | `auditcenter/*`, `audit/AuditSearch`, `AuditLog.verifyChainInBatches`, `V25__audit_event_time_index.sql`, `SecurityConfiguration`, `MeAssembler` (`AUDIT_VIEW`), `FrontEnd/.../admin/audit-center*`, i18n | AuditCenterIntegrationTests 6, frontend spec 4 |
| P1.4 Monitoring | `pom.xml` (Prometheus registry), `SecurityConfiguration.isManagementPort`, `platform/web/HealthzController`, guard (`MANAGEMENT_SERVER_*`), `system-status.service.ts`, proxy files | ManagementPortIntegrationTests 3, Observability 6 |
| P1.5 ADR-016 | `UserAdminController/Service/Views`, `users-admin.ts`, i18n, `PasswordResetMailListener` (`PASSWORD_RESET_SELF`), `messages*.properties` (nine Arabic keys repaired), `e2e/sprint-01-hardening.spec.ts` | PasswordMailTests 2, AdministratorLimits |
| P1.6 Snapshot isolation | `ReadIsolationIntegrationTests`; ADR-014 status | Disposable copy 321/324 before stabilisation |

## Stabilisation (owner approved 2026-11-18)

1. **Snapshot-safe first writers and 2. first-writer 503: done.** `request/FirstWriterConcurrencyIntegrationTests` reproduced, before the fix, 503 `busy` in normal mode and a duplicate document counter under READ_COMMITTED_SNAPSHOT. Fix: the existence check of `createCounterIfMissing` (Request, Po, Shipment, Receipt numbering) and of `AuditLog.createHeadIfMissing` reads `WITH (READUNCOMMITTED)`, and the row is re-created at most three times if the first writer rolled back. After: FirstWriter 3/3, RequestSubmission 4/4, SqlServerIntegration 20/20 in normal mode and on the disposable snapshot database. Gap-free numbering verified: rollback reuses the number, increments are serialised on the counter row, numbered requests are never deleted (javadoc of `RequestNumbering`).
3. **Global Administrator e-mail correction: done.** `identity/EmailChange{Service,Store,Controller,Tokens}`, events `EmailChangeVerificationRequested`/`EmailAddressChanged`, `notification/EmailChange{LinkResolver,MailListener}`, `Notifications.enqueueToAddress`, migration `V26__account_email_change.sql`, `UserAdminService` (activation and reset links refused with `email-change-pending` while open), `SecurityConfiguration` (public `POST /api/v1/auth/email-change/confirm`), matrix lines; frontend: section in `users-admin.ts` (permission `USER_ADMIN_GLOBAL`), public page `features/auth/email-change-confirm.ts` at `/e` (token posted only on click), `safe-return-url.ts` excludes `/e`, i18n en/fr/ar; approval refusal code `email-change-four-eyes`. Tests: `EmailChangeIntegrationTests` 5, `users-admin.spec.ts` 3, `email-change-confirm.spec.ts` 4, `e2e/sprint-02-email-change.spec.ts` 2 (passes alone). README rule row; ADR-016 status.
4. **Validation: all done except one clean complete browser run.**
   - Backend full suite **337/337** (`C:/qa/s1/p1-final-full3.log`, all fixes included; jar in `C:/qa/s1/BackEnd/target`).
   - Disposable RCSI database: FirstWriter, RequestSubmission, ReadIsolation, EmailChange, SqlServerIntegration **33/33**; database **dropped**.
   - Frontend: lint clean, **268/268**, build OK (two budget warnings that predate Sprint 1). Lint rerun clean after the last e2e changes.
   - Live probes: `/healthz` and `/actuator/health` answer only `{"status":"UP"}`; `/actuator/prometheus`, `/env`, `/loggers`, `/api/v1/admin/audit/events` answer 401 anonymously.
   - Secret scan of diff and new files: clean (only deliberately refused test values).
   - **Browser run 1** (22:21–22:59): 68/71, invalid. The machine slept 22:29–22:40 (the keep-awake call in `C:/qa/s1/e2e/run-full.ps1` threw a cast error and never took effect; **fixed** with `[uint32]`). It found three real defects, all fixed test-first (red, then green):
     - **S5** the application did not start with background jobs on (`AuditCenter` executor ambiguous). Fixed with `@Qualifier("applicationTaskExecutor")`; `auditcenter/AuditCenterWiringTests`.
     - **S6, present since Sprint 10 (`6046316`):** set-up and reset e-mails carried the raw `{{link:password-reset:...}}` text (the sender pattern rejected a hyphenated kind and the `SETUP` argument). Fixed: `NoticeLinkResolver.KIND`/`ARG` shared with `OutboxSender.PLACEHOLDER`; `placeholder()` refuses unmatched kinds. Tests: `notification/MailLinkIntegrationTests` 3 (links redeemed from the .eml files) and `support/SentMail` helper.
     - **S7** a supplier contact's two simultaneous first "mark as read" calls gave 500 (duplicate participant). Fixed: `MessagingStore.ensureParticipant` is one locked `INSERT ... WHERE NOT EXISTS (UPDLOCK, HOLDLOCK)`; test in `MessagingIntegrationTests`.
     - One sign-in failure at 22:25 during host-wide slowness (no lock refusal); not reproduced; cause not proven.
   - **Browser run 2** (23:50–00:23, no sleep, clean backend log): **70/71**. The only failure was a locator in the new e-mail spec (`getByRole('status')` also matched the toast region); fixed (`locator('main')`), and the spec then passed alone 2/2. Logs: `C:/qa/s1/e2e/pw-run2-70of71.log`, `backend-run2.log`.
   - **Browser run 3** was started on a fresh database and **stopped by the owner's request** at 00:45 after about 33 tests: no result.
   - **2026-11-19:** added `e2e/sprint-02-mail-links.spec.ts` (2 tests: set-up, administrator reset and self-service reset links followed from the e-mail files) and a generic `mailedLink` helper in `e2e/support.ts`. **Browser run 4 (08:15–08:40): 73/73**, one uninterrupted run, fresh database, keep-awake confirmed (`C:/qa/s1/e2e/keep-awake.txt`), no sleep event, no 5xx, 187 e-mails with no unresolved placeholder. Archived: `C:/qa/s1/e2e/pw-run4-73of73.{log,json}`, `backend-run4.log`. Instance stopped; `docs/qa/evidence` and `docs/design/verification` untouched. Secret scan clean.
5. **Done 2026-11-19: five local commits on `sprint-2`** (`794e1b3`, `e4e8cef`, `cfcccd4`, `3235287`, then the documentation commit); recovery reference `refs/backup/sprint2/working-tree-before-commits` (`4770f92`). **Resume here: the owner decides on the push** (to `enterprise/feat/internal-messaging` or a new remote branch; never `main`), then CI proves P1.2. Open: the commit-1-only observation on `ApprovalIntegrationTests` (see the progress report); database decision: SQL Server 2022, PostgreSQL deferred (ADR-001 note). How the commits were made:
   1. Stage each commit from intermediate blobs (`git hash-object -w` + `git update-index --cacheinfo`), without touching the working tree.
   2. Check that each intermediate tree compiles and passes its targeted tests, in a copy outside the workspace.
   3. Then commit and, only if approved, push.

   The plan, in order:
      1. pre-existing defects S6 and S7 (mail links, messaging race: `OutboxSender`, `NoticeLinkResolver`, `MessagingStore`, `SentMail`, `MailLinkIntegrationTests`, `MessagingIntegrationTests`, `e2e/support.ts`, `e2e/sprint-02-mail-links.spec.ts`);
      2. P1.1–P1.6 (including the S5 fix and `AuditCenterWiringTests`);
      3. concurrency stabilisation S1–S3 (four numbering classes, the `AuditLog` head creation, `FirstWriterConcurrencyIntegrationTests`);
      4. e-mail correction S4 (V26);
      5. documents.

      Files shared between commits (staged per commit as intermediate blobs, without touching the working tree): `AuditLog` (P1 and S1), `SecurityConfiguration`, `UserAdminService`, `authorization-matrix.txt`, `AuthorizationMatrixIntegrationTests`, `messages*.properties`, `users-admin.ts`, `i18n/*.json` (P1 and S4). Verify each intermediate tree compiles and passes its targeted tests before proposing it.

## Commands

```powershell
& C:/qa/s1/run.ps1 <log-name> verify -Pdb-it          # full backend suite (copy outside the workspace)
cd FrontEnd; npm run lint; npm test; npm run build
& C:/qa/s1/e2e/start-instance.ps1; & C:/qa/s1/e2e/run-full.ps1   # browser suite, fresh database
```
<!-- checkpoint:end -->

# Handoff: Internal employee messaging, final verification (2026-11-14)

**State: ready for the owner's review. Not committed, not pushed.** The feature commit and any push wait for the owner's instruction. When committing, leave out `docs/tooling.md` and `.anchor/`.

Design, rules, API and limits: [docs/architecture/internal-messaging.md](docs/architecture/internal-messaging.md). Plan: [docs/superpowers/plans/2026-11-13-internal-messaging.md](docs/superpowers/plans/2026-11-13-internal-messaging.md). Short state: [STATUS.md](STATUS.md).

## Tested code state

| Item | Value |
|---|---|
| Branch | `feat/internal-messaging` |
| Base commit | `c7b901d719ac2b86ff438a1d34ff70e1ce314bb3` (approved Meridian refinement) |
| Working tree | Uncommitted: 16 modified tracked files (docs included), plus new files under `BackEnd/…/messaging/`, `FrontEnd/…/internal-messaging/`, the migration, tests and docs |
| `BackEnd/` tree hash | `bb5bdd80a7cdb3e7c15782d7b4db41f2c7172f8b` |
| `FrontEnd/` tree hash | `eaec3f501060dab2a25af0ec6fdd77af94ff7b26` |
| Last source edits | Backend 2026-11-14 00:06:58, frontend 2026-11-13 17:27:32; every check below ran after these |

The tree hashes are of the working tree as tested (36 files, +5,845 / −13 against `c7b901d` in those two folders). To compare later: `GIT_INDEX_FILE=<temp> git read-tree HEAD && git add -A -- BackEnd FrontEnd && git rev-parse $(git write-tree):BackEnd` (and `:FrontEnd`). It uses a temporary index and leaves the real index and refs untouched. The three backend copies used (tests in `C:\qa\im-test`, isolated suite in `C:\qa\im-verify`, review instance in `C:\qa\im-review`) were compared file by file with the workspace: no differences.

## Results

| Check | Where | Result |
|---|---|---|
| Backend `mvnw.cmd verify -Pdb-it` | SQL Server, `procurement_test` | **208 / 208**, build success (15 messaging tests, module structure test included) |
| Frontend lint | Workspace | Pass |
| Frontend unit tests | Workspace | **253 / 253** |
| Frontend build | Workspace | Pass. Two warnings: initial bundle 524.60 kB (500 kB budget; 518.70 kB before this work), `sign-in.scss` 9.24 kB (unchanged) |
| Full Playwright suite, 66 tests | Isolated instance: frontend :4203, backend :8083 (jar built from this tree), **fresh database** `procurement_qa_msg_verify` migrated and seeded for the run; nothing else using it | **66 / 66 in 27.0 min**, no retries |
| Messaging scenarios repeated | Review instance | 9 / 9 (3 scenarios × 3) |
| Message emails | 180 mail files of the isolated run | None from internal messaging; none links to `/conversations` |

**Isolation of the suite run.** Own backend, frontend and database; no screenshot or probe script ran during it. Playwright output, traces and the JSON report went to `C:\qa\im-verify` (outside OneDrive), with `--output` and `--reporter=list,json`. Two specs write screenshots to hard-coded paths in the repository (`docs/qa/evidence`, `docs/design/verification`). Those were restored with `git checkout` afterwards; no tracked file stays modified.

## 25-second send: investigated

The slow send was seen once, on the review instance, while the full suite was running. Measured afterwards on the isolated instance under normal load:

| Path | Median | p95 | Max |
|---|---|---|---|
| Send API round trip, direct to the backend (20 sends) | 30.5 ms | 55.4 ms | 55.4 ms |
| Send API round trip, through the dev-server proxy (20 sends) | 27.0 ms | 61.3 ms | 61.3 ms |
| Live event at the recipient, after the send response | 2.3–2.6 ms | 5.3–9.7 ms | 9.7 ms |
| Recipient's follow-up fetch of the new message | 12.9–19.7 ms | 19.4–23.3 ms | 23.3 ms |
| Browser: Send click → bubble shows "Sent" (10 sends) | 117 ms | 213 ms | 213 ms |
| Browser: the send request inside that (browser's own timing) | 26 ms | 38 ms | 38 ms |
| Browser: Send click → text displayed in the recipient's open thread | 210 ms | 226 ms | 226 ms |

- **Where the time goes:** a little under a quarter of the click-to-"Sent" time is the server; the rest is the browser (rendering, plus the test driver's polling). Event delivery after commit takes milliseconds. No event was missed (0 of 40).
- **The slow send was not reproduced.**
  - At the time, OneDrive sync was using about 46% CPU on Playwright traces written inside the synced folder, and the review backend runs under `spring-boot:run` with the C1 compiler only.
  - A SQL Server blocking monitor saw no lock wait over one second in the reruns.
  - The message was stored once.
  - Conclusion: machine load, not a code path. It can't be proven after the fact.
- **A real defect found while checking this:** simultaneous retries of one message could deadlock in SQL Server, and one tab got 503 `busy`.
  - Reproduced: one 503 in a round of eight.
  - Fixed: the conversation row is locked first, in every transaction that writes a message or participant.
  - Covered by `t10`: five rounds of eight simultaneous sends, all 200/201. It fails without the fix and passes with it.
  - The review instance's earlier "Lock failure" log lines could have had this cause; that is not proven.

## Access verification (backend, SQL Server tests)

| Requirement | Test | Verified |
|---|---|---|
| Removed participant who still sees the request | `t15` | Request page still 200. Every conversation endpoint is 404: detail, messages (newest, after, before), send, read, candidates, add participant. Not listed, unread excluded, no live event, gone from the others' participant list. Re-added by a participant: one audited addition, a system line, history visible again |
| Account deactivation | `t07` | Events stop at once, before the heartbeat. The heartbeat closes the stream. The old session gets 401 on messages, list, unread and stream. After reactivation, a new sign-in reconnects, recovers the message sent meanwhile exactly once, and gets live events |
| Authorization change (role and request access withdrawn) | `t06` | Discussion 404 and not streamed, while direct messages still stream. The request card shows nothing. Access given back: readable again, and the resync returns exactly the missed message |
| Reconnect | `t11`, `t06`, `t07` | Resync by `afterId` with no duplicates. After sign-out the stream closes at the heartbeat, and the old cookie gets 401 (stream included) |
| Private direct messages refused to outsiders | `t03`, `t04`, `t05` | Unrelated employee of the same company, company ADMIN, GLOBAL_ADMIN, user of another company: 404 on all 8 conversation calls. The text never appears in a response; not listed, not counted, not streamed. Supplier contact: 403 on all of them and on the stream. The two participants still read it |

**Removal has no screen in this release.** `t15` sets `removed_at` directly. While writing it, a latent defect turned up: adding a removed person again silently did nothing (unique key) yet answered 200. The row is now restored.

## Changes in this verification pass

- `InternalMessagingStore`: `lockConversation` (fixed lock order), and re-adding a removed participant restores the row.
- `InternalMessagingService`: takes the conversation lock before message and participant writes; the system line for an addition gets a unique key, so a second addition is recorded.
- `InternalMessagingIntegrationTests`:
  - `t03` widened to the full outsider matrix.
  - `t06` and `t07` extended to live delivery and restoration.
  - `t10` strengthened.
  - `t11` gained the sign-out checks.
  - New `t15` covers removed participants.
  - The in-memory send limit is reset before each test.
- `docs/architecture/internal-messaging.md`: removal, deactivation, authorization change, sign-out, lock order, a separate section on the single-node limitation, e-mail disabled, retention pending.

## Review instance

**http://localhost:4202** (frontend, this working tree), backend :8082 restarted on 2026-11-14 with the tested code, database `procurement_qa_messaging` (data kept), mail files in `C:\qa\im-review\mail`. The QA instance (:4201 / :8081) and the owner's :4200 / :8080 are untouched. The instance runs while the session that started it is alive. Sign in with the demo accounts (`requester.tn@demo.local`, `manager.tn@demo.local`, `manager2.tn@demo.local`, `buyer@demo.local`, `receiver.tn@demo.local`, `requester.fr@demo.local`, …) and the password given to the backend as `DEMO_USER_PASSWORD` (told to the owner in the session, not written here).

```powershell
# Backend (copy outside the workspace, isolated database, port 8082)
cd C:\qa\im-review\BackEnd
$env:SERVER_PORT="8082"; $env:DEMO_USER_PASSWORD="<choose>"; $env:APP_PUBLIC_BASE_URL="http://localhost:4202"; $env:MAIL_DIRECTORY="C:\qa\im-review\mail"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=procurement_qa_messaging;integratedSecurity=true;encrypt=true;trustServerCertificate=true"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
# Frontend
cd FrontEnd; npx ng serve --port 4202 --proxy-config C:\qa\im-review\proxy.conf.8082.json
# Isolated full suite (fresh database, jar on 8083, ng serve on 4203; output outside OneDrive)
java "-Djava.library.path=<copy>\target\native" -jar <copy>\target\procurement-0.1.0-SNAPSHOT.jar "--spring.profiles.active=dev,demo"
$env:E2E_BASE_URL="http://localhost:4203"; $env:PLAYWRIGHT_JSON_OUTPUT_NAME="C:\qa\im-verify\pw-results.json"
npx playwright test -c e2e/playwright.config.ts --reporter=list,json --output C:\qa\im-verify\pw-output
git checkout -- docs/qa/evidence docs/design/verification
```

Probes used for the timings (outside the repository): `C:\qa\im-verify\latency-api.mjs`, `latency-browser.mjs`, `sse-proxy-check.mjs`.

## Migration

V22 adds three tables and three sequences; nothing existing changes, no backfill. Rollback before any message exists: drop them. After messages exist, dropping them deletes employee correspondence. No migration was added in this pass.

## Decisions for the owner (open)

1. **Retention: pending a product decision.** No policy for employee messages exists in the project, and nothing is deleted automatically.
2. **Live delivery is limited to one backend node** (next section).
3. **E-mail for messages: disabled for this phase.** Nothing is sent. `InternalMessagePosted` is the extension point, if it is wanted later.

## Limitation: one backend node

Live connections are held in one backend process. With two or more nodes, a message stored through node A is not pushed to a recipient connected to node B. That recipient sees it at the next resync (reconnect, navigation, 30-minute stream end, or opening the conversation), and their unread badge is stale until then. Nothing is lost. Deploy one backend node until either sticky sessions with a per-node poll, or a shared broker (with an ADR under ADR-003), is chosen.

## Other limitations

- Removing a participant or leaving a discussion has no screen yet; the server rules for it are in place and tested.
- Former approvers keep request access under the existing request rules (`request_participant`), and therefore keep the discussion unless removed.
- Not in this release: attachments, groups, reactions, typing, presence, editing or deleting messages.
- The initial bundle is 24.6 kB over its budget (18.7 kB over before this work).

## Next action

The owner reviews http://localhost:4202, decides the three points above, then instructs on the commit and push.

## Skills Review

- ✅ `superpowers:writing-plans`, `backend-design:migration-safety`, `backend-design:auth-and-authorization`, `backend-design:idempotency-and-side-effects`, `securitymaxxing:security-review-method`: applied during the build (2026-11-13).
- This pass: no new skill invoked; the checks followed the owner's verification list.
- ❌ Self-assessment: the original race test (one round of four) passed while the deadlock existed; only the stronger test showed it. The first API probe hung because it never exited, which I first misread as proxy buffering until a chunk trace showed the proxy delivers events within 3 ms.

---

# Handoff: Meridian refinement, approved and committed (2026-11-12)

**Approved by the owner on 2026-11-12** and committed locally as `feat(ui): complete Meridian interface refinements`, annotated tag `meridian-refinement-approved` on `stabilization-and-ledger-redesign`. Find the commit with `git rev-parse meridian-refinement-approved^{commit}`. Not pushed; a push needs the owner's separate instruction.

**The committed source is the tested source.** The last product file was edited at 08:07:12 on 2026-11-12; lint, unit tests and the build ran after that, then the Playwright suite (finished 08:33:48). No file under `FrontEnd/src`, `FrontEnd/public`, the specs in `FrontEnd/e2e` or `BackEnd/src` changed afterwards. Evidence: [docs/qa/meridian-refinement/](docs/qa/meridian-refinement/) (lint, unit, build and full-suite logs, the keyboard and dashboard-link check with its log, and the two-theme axe script).

**Checkpoint before the refinement:** commit `507247a266f4121827d6a70b8224d2b50a0bc7ec` on `stabilization-and-ledger-redesign`, tag `meridian-before-refinement`. Local only; not pushed.

**Contents:** both refinement passes (2026-11-11 and 2026-11-12). See them with `git diff meridian-before-refinement meridian-refinement-approved`. UI only: no backend, migration, route, permission, validation or API change.

**Known at approval, carried forward**

- **Two build warnings.** Initial bundle 518.70 kB, 18.70 kB over the 500 kB budget (it was 6.50 kB over before the redesign). `sign-in.scss` 9.24 kB against the 8 kB per-component warning (new). Neither fails the build.
- **Visual checks not done.** Listed under "Not verified" below.
- **Draft validation.** A draft cannot be saved while a line has no unit (finding 15 below). Behaviour left as it is; recorded as a functional issue for the next phase.

**Review instance:** http://localhost:4201 (this working tree), backend on :8081, database `procurement_qa_redesign`. Separate from the owner's :8080 and :4200. It was restarted on 2026-11-12 and stops when the session that started it ends; restart commands are in the 2026-11-10 section below. Sign-in: the accounts in [docs/guides/global-admin-review.md](docs/guides/global-admin-review.md) with the `DEMO_USER_PASSWORD` given to the backend.

## What changed in this pass

| Area | Change |
|---|---|
| Request form | Saving shows a turning ring on the button that was pressed (`[busy]` on `appButton`); the other actions are disabled; the result is the toast |
| Attachments | Tinted dashed "add a file" control, styled progress bar, file rows with an icon tile |
| Lists | Filter bar as one contained strip on the request list, approvals inbox and purchasing inbox; page range and page number as a pill |
| Record | Shipment milestones as a timeline (reached, late, to come); purchasing, sourcing, order, logistics and receipt forms keep a 46 rem measure with the action at the start of the row |
| Mobile navigation | Drawer 18 rem wide with a rounded trailing edge, 44 px links, dimmed and softened page behind it |
| Home on phones | Shortcuts in one column; labels no longer break inside a word |
| Notifications | Escape now closes the menu when focus is still on the bell (it stayed open when the menu had nothing focusable) |
| Buttons, skeletons | Busy state; skeleton radius follows containers |
| Sign-in | On tall tablets the panel is centred; the earlier layout work is unchanged |

The interaction rules came from the `design-skills:interaction-design` skill (feedback near the trigger, every state designed, no wait without feedback).

## Verification (2026-11-12, final code)

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm test` | Passed, 245 of 245 |
| `npm run build` | Passed with two warnings: initial bundle **518.70 kB** (18.70 kB over the 500 kB budget; 136.58 kB estimated transfer); `sign-in.scss` 9.24 kB against the 8 kB per-component warning |
| Playwright, full suite, run alone | **63 of 63 passed** (24.0 min). No screenshot or other script touched the instance during the run. No source file changed after it |
| `ledger-verification` "deciding keeps focus" | Passed. Not reproduced on 2026-11-11 or 2026-11-12 (5 passes); the 2026-11-10 failure remains unexplained |
| axe WCAG A/AA, 17 screens in each theme, 1440 px | No violations (one buyer record view is skipped by the script: the buyer's "visible" list is empty) |
| Keyboard walk-through, scripted (`C:\qa\p360-redesign\verify.mjs`) | 16 checks, all passed: sign-in tab order and focus rings, sign-in by Enter, skip link, theme switch by Enter and Space, notification menu open and Escape, account menu, sidebar focus ring on ink, mobile drawer open, focus trap and Escape |
| Dashboard links | All 9 status bars clicked (the first by keyboard). Each lands on `/requests?scope=visible&status=…`, the filter shows that status, every row has it, and the list total equals the bar's count |
| Saving state, toast, dialog | Seen in both themes: one busy button and two disabled while the save is held; "Draft saved" toast; discard dialog takes focus and closes with Escape |
| Screens looked at this pass | Sign-in on phone, tablet and 200 % zoom; record with order, shipment and receipt panels (light and dark); logistics inbox (dark); form validation on a phone; mobile drawer (light and dark); notification menu; request list on desktop and phone |

**Investigated and closed**

- **Tracker looked collapsed in two full-page screenshots.** Measured in real viewports at 900 px and 3,200 px height, with and without reduced motion: 698 px wide, ten evenly spaced steps. It is a stitching artifact of full-page capture, not a layout defect; an element capture and a later full-page capture show it correctly.
- **Two accessibility specs failed on 2026-11-11.** Caused by a screenshot script signing in to the same accounts during the run. Captures now run only when the suite is not running.

**Not verified**

- Screen reader, real touch devices, native French and Arabic review, browser text-size settings other than zoom.
- Attachment upload progress and file rows with a real file: the styles changed, but no file was uploaded in this pass (the suite's attachment test passed).
- Dark theme by eye on: sourcing comparison table, supplier portal, assistant, request administration detail, approval rules editor, exchange rates, import.
- Error state and loading skeleton by eye (restyled, not captured).
- GLOBAL_ADMIN write actions and single-company ADMIN isolation (unchanged from 2026-11-11).
- The Playwright suite runs in light theme only.

## Functional findings for the next phase

Added to the fourteen below:

15. **A draft cannot be saved while a line has no unit.** Saving a new request with only a title returns "Line 1, unit: Invalid value". The form says "Save a draft at any time". Seen on the review instance; not compared with the commit before the redesign, and no styling change touches it. Decide whether drafts may have incomplete lines.
16. **Empty menus and Escape:** fixed for notifications in this pass; check other menus that can open empty.

Unchanged and still in their own documents: GLOBAL_ADMIN gaps ([review](docs/guides/global-admin-review.md)), dashboard aggregates (item 10 below), assistant provider, transport, retention and action scope ([plan](docs/product/assistant-next-phase.md)), all proposals pending the owner's decision.

## Next action

Wait for the owner's instruction to push. Then the next phase: the functional findings (1 to 16), the GLOBAL_ADMIN gaps, the dashboard aggregates and the assistant decisions, each in its document. `docs/tooling.md` and `.anchor/` stay uncommitted on purpose.

## Skills Review

- ✅ `design-skills:interaction-design`: invoked this pass; drove the busy state, the Escape fix and the states checked.
- ✅ `frontend-design:frontend-design`, `anthropic-skills:ui-ux-pro-max`, `design-skills:accessibility-audit`: loaded earlier in the session and applied; not invoked again this pass.
- ❌ Self-assessment: a `:host` rule could not reach the button's projected icon, so the ring and icon showed together; caught by capturing the saving state, not by reading the code.

---

# Handoff: Meridian refinement pass (2026-11-11)

**Checkpoint before this pass:** commit `507247a266f4121827d6a70b8224d2b50a0bc7ec` on `stabilization-and-ledger-redesign`, annotated tag `meridian-before-refinement`. Local only; not pushed. To return to it: `git checkout meridian-before-refinement`.

**This pass:** uncommitted working tree on top of that commit, left for the owner's review. UI only. No backend, migration, route, permission, validation or API change.

**Review instance:** http://localhost:4201 (this working tree) with its backend on :8081 and database `procurement_qa_redesign`. It is separate from the owner's :8080 and :4200, which were not touched. Sign-in: any demo account in [docs/guides/global-admin-review.md](docs/guides/global-admin-review.md) with the `DEMO_USER_PASSWORD` the backend was started with. Restart commands are in the section below this one.

## What changed in this pass

- **Sign-in layout.** A masthead row holds the brand, language and theme. Every vertical measure follows the viewport height, so the orbit, type and spacing shrink before the form. At 1366 × 640 the form, button and recovery link are in view with no scroll and 34 px between masthead and panel. The page grows and scrolls when content needs it. Glass panel: gradient surface, lit top edge. Stage: vignette and an outer ring.
- **Dashboard.** Lifecycle ring by phase with meaning, count and share; status bars that open the filtered list; "late on this page" count. Chart colour tokens for both themes.
- **Navigation.** Location trail in the top bar (journey group, page).
- **Dialogs.** One scrim with a soft blur, shared entrance, feature radius.
- **Home.** The request-list shortcut shows how many requests are the person's.
- **Documents.** [GLOBAL_ADMIN review](docs/guides/global-admin-review.md), [assistant next-phase plan](docs/product/assistant-next-phase.md), DESIGN.md, route checklist.
- **New translation keys (EN, FR, AR):** `shell.location`, `dashboards.pipeline.byPhase`, `dashboards.pipeline.byStatus`, `dashboards.phases.*`, `dashboards.management.lateOnPage`. Not reviewed by a native speaker.

## Verification (2026-11-11, on the refined tree)

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm test` | Passed, 245 of 245 |
| `npm run build` | Passed. Warning: initial bundle 517.41 kB, 17.41 kB over the 500 kB budget |
| Playwright, full suite | 61 of 63. The two failures (`accessibility-approvals` AR desktop, `accessibility` FR desktop) timed out while a screenshot script of mine was signing in to the same accounts and changing their saved language |
| Those two specs rerun alone | 10 of 10 passed |
| `ledger-verification` "deciding keeps focus" (intermittent on 2026-11-10) | Passed in the suite and 3 of 3 repeats. Not reproduced today; cause still not established |
| axe WCAG A/AA, 18 screens in each theme, 1440 px | No violations |
| Sign-in fit, measured (document size against viewport) | Fits without scrolling at 1366 × 640 in EN, FR and AR, both themes; 1366 × 768; 1920 × 1080; 1024 × 768; 820 × 1180; 390 × 844. Scrolls, with every control reachable, at 1280 × 600 (3 px), 360 × 640, 320 × 568 and at 200 % zoom |

The suite includes the receipt rules (partial receipt, unshipped line, two-person confirmation, service acceptance), order and split delivery, and the 20-step journey through invoice, payment and closure. All of those passed.

**Not verified**

- A full suite run with nothing else touching the accounts. The 63-of-63 claim rests on 61 in the suite plus the two reruns.
- Keyboard: only what the suite covers (skip link, drawer, focus after a decision, form error focus). No manual walk-through of the new trail, theme switch or dashboard links.
- Dashboard links: built on the list's existing `status` filter; not clicked through in a test.
- Dark theme by eye on order, logistics and receipt panels, supplier portal, assistant, dialogs and toasts.
- GLOBAL_ADMIN write actions and single-company ADMIN isolation (see the review document).
- Screen reader, real touch devices, native French and Arabic review, browser text-size settings other than zoom.

## Functionality gaps recorded for the next phase

Added to the nine below (2026-11-10):

10. **Dashboard measures that need the backend:** late count and ageing over all requests (today only on the loaded page); approvals overdue per approver; purchasing workload per buyer; delivery progress (shipments by milestone); a time series of submissions. Each needs an aggregate endpoint with company scope.
11. **Per-role dashboards:** requesters and receivers have no dashboard permission; their overview is the home page. Decide whether they get one.
12. **GLOBAL_ADMIN:** the six gaps in the review document (single-company demo ADMIN, role holders view, empty business menus, link from record to administration, administration activity view, last-GLOBAL_ADMIN protection).
13. **Assistant:** provider, transport, retention and action scope decisions in the plan.
14. **Tests:** screenshots and suites must not share demo accounts; give captures their own accounts or run them apart.

## Next action

The owner reviews http://localhost:4201 and the diff (`git diff meridian-before-refinement`). If accepted: run the full Playwright suite once more with nothing else signed in, then commit the refinement. Leave `docs/tooling.md` and `.anchor/` out.

## Skills Review

- ✅ `frontend-design:frontend-design`, `anthropic-skills:ui-ux-pro-max`: loaded earlier in this session and applied; not invoked again in this pass.
- ✅ `design-skills:design-systems`, `audit-suite:web-animation-design`, `design-skills:accessibility-audit`, `zizi-skills:i18n`: same; their rules drove the tokens, the reduced-motion fallback, the axe scan and the key-parity check.
- ❌ Refero, GetLayers, `design-skills:interaction-design`, `dataviz`: not used. Refero needs sign-in; GetLayers is not installed.
- ❌ Self-assessment: I ran screenshots against the same accounts as a running test suite, which broke two tests and mislabelled one capture. Caught by reading the screenshot, then confirmed by rerunning.

---

# Handoff: Meridian redesign, light and dark (2026-11-10)

**Candidate:** uncommitted working tree on `stabilization-and-ledger-redesign`, on top of `6869b2e`. UI only. No backend, migration, route, permission, validation or API change. Not committed, pushed or deployed.

**Running for review:** http://localhost:4201 (Angular dev server, serving this working tree) against a backend on :8081 with its own database `procurement_qa_redesign` (synthetic demo data only). Backend sources are an export of `HEAD` in `C:\qa\p360-redesign\src\BackEnd`. Demo accounts are in the README; the QA password is the `DEMO_USER_PASSWORD` the backend was started with. The owner's :8080 and :4200 were not touched. Both processes belong to the session that started them and stop with it; restart commands are under "Restart".

## What changed

- **Identity.** Ledger is replaced by Meridian ([DESIGN.md](DESIGN.md)): ink navigation, porcelain work surfaces, ultramarine for action, saffron for "waiting for you", and the ring (brand mark and lifecycle progress). Bricolage Grotesque and IBM Plex Sans replace Geist; both are self-hosted.
- **Themes.** Light and dark share every token. `ThemeService` follows the system until the person uses the switch (top bar, sign-in, guest pages); the choice is stored on the device.
- **Sign-in.** Full-screen stage with the lifecycle orbit and a glass panel. CSS and SVG with one pointer listener; still under reduced motion.
- **Navigation.** Ink sidebar, glass top bar and menus, saffron marker on the current item. One location now lights one item: sourcing, purchase orders and receipts share the request list's route and no longer all show as current.
- **Home.** Next-approval band with wait and due date, shortcuts taken from the navigation (same permissions), late approvals marked.
- **Dashboard, approvals, list, record, form.** Rebuilt or restyled as listed in [the route checklist](docs/design/redesign/route-checklist.md).
- **New shared components:** `app-brand-mark`, `app-stage-ring`, `app-theme-toggle`, `app-public-frame`.
- **New translation keys (EN, FR, AR):** `overview.shortcuts`, `dashboards.pipeline.total`, `theme.toDark`, `theme.toLight`. The Arabic and French are the developer's and have not had native review.

## Decisions

- No GSAP or Lenis. The cinematic skill's choreography and timings are implemented in CSS. Why: sign-in has no scroll scenes, and the initial bundle is already over its 500 kB warning.
- Glass only on the top bar, menus and the sign-in panel. Why: tables and forms must keep full contrast.
- A lifecycle ring next to every written status, never instead of it. Why: colour and shape must not carry meaning alone.
- The dashboard figure is the sum of the status counts the API returned. It makes no claim beyond 5,000 requests (D-05 still applies).

## Verification (2026-11-10, on this working tree)

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm test` | Passed, 245 of 245 |
| `npm run build` | Passed. Warning: initial bundle 515.47 kB, 15.47 kB over the 500 kB budget (it was 6.50 kB over before the redesign) |
| Playwright, full suite | 63 of 63 passed, on the tree before the last two small changes (one active navigation item; definition-list margin reset) |
| Playwright after those two changes: `session`, `accessibility`, `accessibility-purchasing`, `purchasing-journey`, `ledger-verification` | 32 of 33 passed. One failure: `ledger-verification` "deciding keeps focus in the queue". Repeated four times afterwards, it passed four times |
| axe WCAG A/AA, 18 screens in light and 18 in dark, 1440 px | First run: light all passed; dark found one defect (notification count text). After the fix, repeated on the final tree: no violations in either theme. Script: `C:\qa\p360-redesign\axe.mjs` |
| Horizontal overflow at 390, 820 and 1440 px, EN, FR and AR samples | None found on the screens captured |

The full suite covers sign-in and session expiry, request submission, the three-language approval journey with the e-mail link, return and rejection, purchasing assignment, line classification, sourcing, order and shipment, split delivery, partial receipt, two-person receipt confirmation, service acceptance, closure, messaging, request administration and the 20-step journey.

**Not verified**

- The focus failure above is intermittent (one failure in six runs today); its cause is not established. The earlier verification report also recorded intermittent races in this area.
- Invoice attestation recovery and payment recording were exercised only as far as the existing 20-step and receipt specs cover them; no new scenario was written.
- Dark theme was not looked at on the order, logistics and receipt panels, the administration screens, the supplier portal, the assistant, dialogs or toasts. The checklist lists them.
- Screen reader, a full keyboard walk-through, real touch devices, native French and Arabic review, runtime performance.
- The Playwright suite runs in light theme only.
- Backend tests were not run: no backend file changed.

**Release gates are unchanged:** independent QA, penetration test, MFA and restore drill are still open. This redesign does not alter the earlier no-go for pilot and production.

## Functional ideas for the next phase (not done here)

1. The shell asks for the approval and purchasing counts for every user; roles without the permission get a 403 on each navigation. Ask only when the permission is held.
2. Sourcing, Purchase orders and Receipts in the sidebar all open the request list unfiltered. Give each a filtered view (for example by stage).
3. The record's back link always reads "My requests", including for buyers who arrived from the purchasing inbox.
4. The supplier quote link takes one unit price for the whole request; quote per line.
5. The requester's home shows no due dates; the list API has no SLA field for requests in approval.
6. Dashboard: late count and ageing by stage as figures. The data is only on the paged table today.
7. Guest pages (`/p`, `/q`) had no language switch before; they have one now through the shared frame. Confirm the link e-mails should carry the recipient's language.
8. A Playwright project that runs the accessibility specs in dark theme.
9. A lint rule against raw colour values in component styles (carried over).

## Restart

```powershell
# Backend (isolated database, port 8081)
cd C:\qa\p360-redesign\src\BackEnd
$env:SERVER_PORT="8081"; $env:DEMO_USER_PASSWORD="<choose>"; $env:APP_PUBLIC_BASE_URL="http://localhost:4201"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=procurement_qa_redesign;integratedSecurity=true;encrypt=true;trustServerCertificate=true"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
# Frontend
cd FrontEnd; npx ng serve --port 4201 --proxy-config proxy.conf.8081.json
# Browser tests against it
$env:E2E_BASE_URL="http://localhost:4201"; $env:MAIL_DIR="C:\qa\p360-redesign\src\BackEnd\storage\mail"; npm run e2e
```

Running the suite overwrites `docs/qa/evidence` and `docs/design/verification`; both were restored with `git checkout --` after each run.

## Next action

The owner reviews http://localhost:4201 in both themes. If the direction is accepted: rerun the full Playwright suite once on the final tree, look at the "still to look at by eye" list in the route checklist, then commit. When committing, leave out `docs/tooling.md` and `.anchor/`.

## Skills Review

- ✅ `frontend-design:frontend-design`: set the direction and the rule against generic defaults.
- ⚠️ `anthropic-skills:ui-ux-pro-max`: its generated system (navy, Plus Jakarta Sans) was generic and was not adopted; its checklist items were used.
- ✅ `design-skills:design-systems`: token tiers and the component table in DESIGN.md.
- ✅ `audit-suite:web-animation-design`: easing and duration choices, reduced-motion handling.
- ⚠️ `anthropic-skills:cinematic-gsap-lenis-motion-system`: choreography and timings used; its GSAP and Lenis implementation was not.
- ⚠️ `design-skills:accessibility-audit`: automated layer and a partial visual layer done; keyboard walk-through and screen reader not done in this pass.
- ⚠️ `zizi-skills:i18n`: key parity (unit test), logical properties and mirrored icons checked; no pseudo-locale sweep.
- ❌ `design-skills:interaction-design`, Refero, GetLayers: not used. Refero needs sign-in; GetLayers is not installed.
- ❌ Self-assessment: arrows were double-mirrored in Arabic and white text was placed on a light red in dark; screenshots and the axe scan caught both, not review.

---

# Handoff: Remediation of independent QA findings (2026-11-09)

**Candidate:** the local commit that introduces this text, on branch `stabilization-and-ledger-redesign`, based on `36c69d8`. Find its SHA with `git log -1 --format=%H -- HANDOFF.md`. Not pushed, merged or deployed.

## Service classification control (product decision taken 2026-11-09)

The earlier hold reproduced physical goods declared as a service reaching CLOSED with no shipment or receipt, unseen by approvers (`docs/qa/remediation-36c69d8/commit-hold/`). Options A and B were chosen together (D-10):

- **Approvers see it.** The queue row, decision pane, one-tap page and record state how many service lines a request has and that they skip shipment and receipt. The count covers all lines, including those beyond the first five shown.
- **Purchasing decides it.** Before award, a buyer or purchasing manager confirms each service line or marks it as physical goods, which sends it through shipment and receipt. The decision is audited (`LINE_CLASSIFICATION_DECIDED`), and the request's author cannot make it (403 `own-classification`). Award and purchase-order creation return 409 `classification-unconfirmed` until it is recorded.
- **It cannot be undone quietly.** A line approved as goods stays goods after a purchasing return (422 `line-kind-locked`). Changing a confirmed service line's description clears the confirmation.

**Remaining risk, accepted by this decision:** a requester and a purchasing user who both misdescribe goods as a service can still skip receipt. Service acceptance stays one person's action under S7-AC-05.

Findings and original evidence: [docs/qa/independent-qa-report.md](docs/qa/independent-qa-report.md), `docs/qa/independent-qa-36c69d8/` (unchanged). Remediation evidence: `docs/qa/remediation-36c69d8/`.

## Finding-by-finding disposition

All findings are fixed by the developer and **await independent retest**. Independent QA is pending.

| ID | Sev. | Disposition | Change | Regression evidence |
|---|---|---|---|---|
| QA-001 | Major | Fixed, with the classification control above | A line approved as goods can no longer be reclassified after a purchasing return, which skips re-approval (422 `line-kind-locked`). Migration V20 adds `line_kind` (GOODS, SERVICE; existing lines GOODS). Service acceptance needs a service line and covers all of them. Goods need a shipment, a confirmed receipt and full acceptance or a shortfall decision; closure checks each line, and neither a waiver nor an old service acceptance replaces a confirmed receipt. Service lines cannot be shipped or received. Mixed orders work per line. UI: line type on the request form, and service acceptance on the record. | Backend `goodsCannotBeAccepted…`, `anOldServiceAcceptance…`, `aServiceOrder…`, `aMixedOrder…`, `anApprovedGoodsLine…`, `approversAreTold…`, `anOrderCannotBeAwarded…`, `purchasingCanSend…`, `theRequesterCannotDecide…`, `changingAConfirmed…`; browser `goods cannot…`, `a service order…`, `approvers see service lines…` |
| QA-002 | Major | Fixed | Accepted follows Received until edited. A valid lower manual value is kept. An invalid value shows a specific inline error and blocks submission. Zero lines are still left out. | Unit `Received and Accepted`; browser `a partial receipt…` |
| QA-003 | Medium | Fixed | A receipt line is tied to one delivered shipment line (DELIVERED milestone has an actual date) and limited to what remains on it. Unshipped and in-transit lines are refused (409 `line-not-shipped`). The form has one row per delivered shipment line. | Backend `anUnshippedLine…`, `repeatedReceipts…`, `receiptsWithoutAShipmentLine…`; browser `an unshipped line…`; R1c, R1b |
| QA-004 | Medium | Fixed | Status stays NON_CONFORMING while any problem is open. Each affected receipt line shows Open issue or Resolved (fully accepted, or shortfall accepted). | Backend `anEarlierProblem…`; unit |
| QA-005 | Medium | Fixed | Errors clear when invoice, confirm or close starts. | Unit |
| QA-006 | Medium | Fixed | After recording, focus moves to the next line to receive, or to the panel heading. | Unit; browser `a partial receipt…` |
| QA-007 | Low | Fixed | Close is disabled and described by the list of what is missing, except when a manager can waive with a note. The backend still returns 409. | Unit; browser `a service order…`; Z.22 |
| QA-008 | Low | Fixed | `untilText` counts calendar days. Two days away reads "in 2 days". | `relative-time.spec.ts` |

Also corrected: the `quantity-exceeded` message, which wrongly mentioned shipping for receipts.

**Older data.** Nothing historical is changed. Queries and actions: [docs/decisions/receipt-history-reconciliation.md](docs/decisions/receipt-history-reconciliation.md). Decisions D-08 and D-09 are in the decision log.

**Points for review.**
- API compatibility (reviewed): the Angular UI always sends the chosen `shipmentLineId`; the only other callers are the repository's tests. No caller change is needed. Omission works when exactly one delivered shipment line is open. Several open lines give 422 with `errors[0] = { field: "lines", code: "shipment-line-required" }`. Any future integrator must send `shipmentLineId` for split deliveries.
- The requester proposes the line type; purchasing decides it before award. After the order it cannot be changed in the application.
- Service acceptance remains one person's action (S7-AC-05); D-03 applies to goods receipts.
- `docs/qa/independent-qa-36c69d8/qa-independent.spec.ts`: the U1–U3 and R2 scenarios must be updated for shipment-line field IDs and the new rules before reuse.

## Test evidence

Run from a clean export of `36c69d8` overlaid with this working tree, outside OneDrive, confirmed identical to the workspace. New databases `procurement_rem_app` and `procurement_rem_test`; synthetic data only. Edge 157 through Playwright; Java 21; SQL Server 2025.

| Check | Result |
|---|---|
| Backend `verify -Pdb-it` | Passed, 193/193 |
| Frontend lint | Passed |
| Frontend unit tests | Passed, 245/245 |
| Production build | Passed; initial bundle 6.50 kB over budget (warning) |
| Playwright full suite | 62 of 63 passed on the first run. The one failure was the new test looking at a row hidden in the split view. After correcting only that test, `receipt-rules.spec.ts` passed 5/5 on the same application code. |
| Full journey through payment and closure, split delivery, authorization matrix | Passed, 3 tests. Three recorded mismatches are expected: Z.9, Z.14, Z.17 (correct 404 refusals). |

Evidence: `docs/qa/remediation-36c69d8/final/`.

Not run: closure journeys repeated 8×, screen reader, real touch device, native FR/AR review.

## Release gates (tracked separately)

| Gate | Status |
|---|---|
| Independent QA | Failed for `36c69d8`; **pending** on this candidate |
| Penetration test | Not passed; no evidence of completion |
| MFA verification | Not passed; no evidence of completion |
| Restore drill | Not passed; no evidence of completion |

Pilot and production remain no-go until every required gate passes with evidence.

## Next action

Return this commit to independent QA, with `docs/qa/independent-qa-report.md` as the retest list. Pushing, merging and deployment are separate decisions.

**Scenario updates for the independent reviewer** (`docs/qa/independent-qa-36c69d8/qa-independent.spec.ts`):
- **U1:** form fields are keyed by shipment line. Read `purchaseOrder.lines[n].shipmentLines[0].id` from the dossier and use it for `#rcpt-received-*`, `#rcpt-accepted-*` and `#rcpt-error-*`. U1.3 now expects a recorded partial with Accepted following Received; U1.4 expects no error.
- **U2:** U2.5 now expects Close disabled, with `#closure-why` naming what is missing. Remove the U2.6 press-Close-early step (Close cannot be clicked). U2.3 expects no stale error.
- **U3:** use the shipment-line field IDs. U3.7 expects focus on `#receipt-title` after the last line is recorded.
- **R2:** R2.5 stays NON_CONFORMING. R2.6 uses goods lines and expects 409 `no-service-lines`. For a valid SERVICE-line case, call `POST /api/v1/purchasing/requests/{id}/line-classification` as the buyer before award.
- **R1b:** R1b.1 now expects 409 `line-not-shipped`.

---

# Earlier handoff: Ledger verification (2026-11-09)

## Status

- DONE: stabilization and Ledger redesign, commits `96da606` and `c67c733`.
- DONE: verification of both and a fix batch. Results in [docs/design/verification-report.md](docs/design/verification-report.md).
- DONE: the fix batch, committed on `stabilization-and-ledger-redesign` as the commit after `c67c733` and pushed to `enterprise` `main`. Re-verify: `git log --oneline -3` and `git ls-remote enterprise main`.
- DONE: D-03 and D-07 rules, committed after `c3adb10` and pushed to `enterprise` `main`; verified as below. Re-verify: `git log --oneline -3` and `git ls-remote enterprise main`.
- NOT STARTED: independent QA and review; layout redesign of sourcing, ordering, logistics, admin and messaging.

**Readiness:** internal demo, yes. Hand-over to independent QA, yes. Pilot or production, no.

## Selected direction

Ledger (Direction A), static sign-in. Defined in [DESIGN.md](DESIGN.md). Tokens: `FrontEnd/src/styles/_tokens.scss`. Approved preview: `docs/design/prototypes/index.html`.

## Decisions

- D-01: a purchasing manager may accept a shortfall, with a reason, once every receipt is confirmed. Why: a rejected unit otherwise stranded the request.
- D-02: purchasing marks an invoice paid; closure does not require payment. Why: matches sprint-7 closure rules.
- An invoice may be recorded without attestation and attested later. Why: S7-AC-06 makes it optional, while closure requires it.
- D-03: the person who recorded a receipt cannot confirm it. Why: one person should not both record and accept goods. Cost: a site needs a second eligible person.
- D-07: expired quotes cannot be awarded, and a quantity different from the request needs a recorded reason. Why: an award should not commit beyond what was approved without an explanation.
- Quotes stay out of the approval pane. Why: they come after approval in the real workflow.

## Changes

Branch `stabilization-and-ledger-redesign`. Commits `96da606`, `c67c733`, on `1b59c4e`.
Remotes: `enterprise` (private; its `main` is at `c67c733`) and `origin` (same branch at `c67c733`; its `main` lacks this work).

**Changed routes (no route was added or removed):**

| Route | Change |
|---|---|
| `/overview` | Next-approval band, compact lists |
| `/approvals` | Queue beside the decision from 64 rem |
| `/requests`, `/requests/:id` | Restyled; "Created" column fixed; rebuilt receipt panel |
| `/purchasing` | Secondary filters folded |
| `/sign-in` | Static restyle |

**API additions under `/api/v1/receipts/requests/{id}`:** `shortfall-acceptance`, `invoice/paid`, `invoice/match-attested`. Request list adds `createdAt`. Dossier adds `shortfallAccepted` and `purchaseOrder` with `receivable` per line.

**Migrations:** V18 and V19, applied only to `procurement_test`. `procurement_dev` migrates at next start.

## Verification

On `c3adb10` plus the working tree (2026-11-09), detail in the report:

- Passed: lint; unit 223/223; build; Playwright 58/58; closure journeys repeated 8/8; backend `verify -Pdb-it` 177/177 (run before the last change, which was one frontend line).
- UNVERIFIED: screen reader; real touch devices; runtime performance against the baseline; native FR and AR review; independent QA.
- Two fixes have no automated test: the last-page redirect after a decision, and the quote total overflow check.

Reports and screenshots: `docs/design/verification-report.md`, `docs/design/verification/`, `docs/design/design-qa.md`, `docs/design/after/`, `docs/design/baseline/`, `docs/reviews/stabilization-review.md`.

## Blockers

- Pilot needs independent QA, penetration test, MFA and restore drill.

## Open

D-05, D-06, PERF-04, and low items R-1 to R-6, all in the verification report.

## Next action

Hand the head of `enterprise` `main` to independent QA, using `docs/design/verification-report.md` as the starting point.

When committing, do not add `docs/tooling.md` (the owner's preflight) or `.anchor/` (a plugin's session file).

## Skills Review

- ✅ `supermatt:code-review`: three axes found four P1s in the receipt form that 171 passing backend tests had missed. Host-only; no external reviewer configured.
- ✅ `design-superpowers:design-review`: run at L1 by reading tokens and screenshots. Confirmed RTL, density and focus token conformance.
- ⚠️ `design-skills:accessibility-audit`: automated and keyboard layers done; screen reader layer not run.
- ✅ `superpowers:verification-before-completion`: repeated runs exposed two intermittent races one pass would have hidden.
- ❌ Earlier self-assessment: the receipt form was reported complete after happy-path tests only. Partial and split deliveries were broken.

**Environment findings**

- Automated checks: no CI gate runs Playwright. Propose a workflow that runs it on pull requests.
- Automated checks: the 20-step journey overwrites tracked evidence on every run. Propose writing to an ignored folder unless a flag is set.
- Standards: raw hex and `rgb()` in component styles are caught only in review. Propose a stylelint rule.
