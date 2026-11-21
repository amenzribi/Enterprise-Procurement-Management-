# Sprint 2 progress: P1, application readiness

**P1: CLOSED, GO (owner, 2026-11-21).** Google Cloud UAT: not yet approved (P2). Production: NO-GO.

**Date:** 2026-11-18, validation completed 2026-11-21. **Branch:** `sprint-2`, pushed to `enterprise/sprint-2` (HEAD
`f57f700`), **CI green** (run 44875958782). **State:** P1.1 to P1.6 implemented, stabilised, validated and pushed; not
merged, deployed or provisioned. Plan: `sprint-02-plan.md`.

**CI evidence (2026-11-21, run https://github.com/amenzribi/Enterprise-Procurement-Management-/actions/runs/44875958782):**
backend **351/351 as the restricted runtime identity** on SQL Server 2022; runtime permission verification **12/12
PASS**; application jar built; frontend lint clean, **268/268**, production build; production dependency advisories
**0 vulnerabilities**. Locally on the same commit: 351/351 as the emulated runtime identity (default order) and 351/351
in reverse-alphabetical order (no sleep during the run); permission script 12/12.

**Final validation (2026-11-19, one code state):** backend **337/337** on SQL Server; disposable snapshot-isolation
database **33/33** (dropped); frontend lint clean, **268/268**, build succeeds; browser suite **73/73 in one
uninterrupted run** on a fresh isolated database (24.8 min, 0 flaky, 0 retried, no server 5xx, no sleep). Secret scan
clean. Recommendation: **GO for committing P1** (after the owner approves the commit plan); UAT remains gated by P2
and the pending decisions below.

## Results

| Step | What | Tests (local, SQL Server) |
|---|---|---|
| P1.1 Fail-closed ClamAV (ADR-013) | `ClamAvScanner` (INSTREAM over TCP, no library), active when `APP_CLAMAV_HOST` is set; health indicator; scan outcome counters; startup refused in uat, staging, preprod, production without a scanner; `APP_ALLOW_UNSCANNED_DOWNLOADS=true` refused in every deployed environment | 8 scanner tests against a fake clamd (clean, infected, size limit, unknown reply, timeout, unreachable, ping, through the policy); guard tests |
| P1.2 Two database identities (ADR-015) | CI creates a migration and a runtime identity and runs the suite as the runtime identity, Flyway as the migration identity, then re-grants and verifies; grant script safe on an empty database; verification script proves probed objects exist; tamper tests use the schema owner's connection | SqlServerIntegrationTests 20/20; 12/12 permission checks on a disposable restored copy (users without login); the empty-database case fails correctly. **The suite as the runtime identity runs only in CI (needs a push)** |
| P1.3 Audit Center | Module `auditcenter`; `/api/v1/admin/audit/**` (global administrators only, URL rule and service); filters, keyset paging, event detail, CSV export (50,000 rows, formulas neutralised), background chain verification in batches of 2,000; each search, export and verification recorded in the trail; migration V25 (two indexes); permission `AUDIT_VIEW`; page `/admin/audit` in EN, FR, AR | AuditCenterIntegrationTests 6/6 (access, paging, validation, CSV, tamper detection, batched equals single reading); frontend spec 4/4 |
| P1.4 Private monitoring endpoint | Prometheus registry; `/actuator/prometheus` without signing in only on the management port (`MANAGEMENT_SERVER_PORT`, private address), global administrators on the application port; `/healthz` on the application port (same answer as `/actuator/health`; System status page uses it); guard requires a private management port in uat and later | ManagementPortIntegrationTests 3/3 (separate port, as in UAT); observability, authentication, guard; matrix 186 endpoints |
| P1.5 Links only (ADR-016) | Temporary-password endpoint, form and translations removed (404); self-service reset e-mail says the owner asked; **nine Arabic e-mail texts were literal question marks since Sprints 10 and 11, now translated** | One regression test replaces four; PasswordMailTests (bundles identical and readable; who asked); browser spec rewritten (not run in this phase) |
| P1.6 Snapshot isolation (ADR-014) on a disposable database only | Code review: every read-decide-write path locks the request row first. Test `ReadIsolationIntegrationTests` checks either mode | Normal mode: reader waits (pass). Disposable database with READ_COMMITTED_SNAPSHOT on: full suite **321/324**; findings below. Database dropped; no other database changed |

**Full regression, normal mode:** backend **324/324** (`C:\qa\s1\p1-full2.log`). Frontend lint clean, **259/259**,
build succeeds (two size warnings that predate Sprint 1). Browser suite not run in this phase.

## Findings

| # | Finding | Status |
|---|---|---|
| 1 | **Snapshot isolation breaks two numbering guarantees.** The document counter's "create if missing" check (`INSERT ... WHERE NOT EXISTS`) reads the last committed data, misses a concurrent uncommitted counter row and fails with a duplicate key; concurrent submissions of one request advanced the counter by 5 instead of 1 (numbers no longer gap-free). The audit chain head uses the same pattern. `DatabaseWaitsIntegrationTests` expects blocked reads, which no longer block (intended) | **ADR-014 must not be enabled** until those checks read with an explicit lock (`READCOMMITTEDLOCK` or `UPDLOCK, HOLDLOCK`); harmless under today's isolation. Proposed, not implemented |
| 2 | Under load, the first events of a brand-new audit chain, or the first submissions of a year, can answer 503 `busy`: a second writer holds the application lock while it waits on the first writer's uncommitted row, so others exceed the 2 s lock wait | Retryable by design; proposed fix: release the application lock before waiting on the row (read the existence check without waiting). Not implemented |
| 3 | The long-used `procurement_test` database failed the submission concurrency test twice; fresh databases passed with both the committed and the current code. Recreated; the full suite then passed | Environment, not code |
| 4 | Local SQL Server is 2025 (Standard Developer), Windows authentication only; CI runs 2022 | D17 keeps 2022 for UAT; the local difference is noted |
| 5 | My new management-port test first wiped the shared test database by starting a second context (id collisions); fixed with `test.database.clean=false` | Fixed |

## P1 stabilisation and validation (owner-approved 2026-11-18)

| # | Item | Change | Evidence |
|---|---|---|---|
| S1 | Snapshot isolation: numbering and audit-chain creation (finding 1) | The "create if missing" checks for the document counter and the audit chain head read with `READUNCOMMITTED`, so a concurrent uncommitted row is seen and no duplicate is attempted; the increment itself still locks the counter row (`UPDLOCK`), under either isolation | Reproduced first: duplicate key on a disposable RCSI database. After: `FirstWriterConcurrencyIntegrationTests` 3/3, `RequestSubmissionIntegrationTests` 4/4, `SqlServerIntegrationTests` 20/20, `ReadIsolationIntegrationTests` 1/1, both in normal mode and on the disposable RCSI database (dropped afterwards). RCSI is **not** enabled on any other database |
| S2 | First-writer 503 (finding 2) | Same change: the second writer no longer waits on the first writer's uncommitted row while holding the application lock; at most 3 bounded create-then-lock attempts; no timeout raised | Reproduced first (503 `busy` with 3 first writers); after: no 503, no duplicate, no fork in the chain |
| S3 | Gap-free request numbers | Claimed only for submitted requests: the increment commits or rolls back with the submission; increments are serialised on the counter row; numbered requests are never deleted | `aRolledBackNumberIsReusedSoCommittedNumbersStayContiguous` |
| S4 | E-mail correction (ADR-016 step 5) | Global administrators only; never their own account or a supplier contact; sensitive accounts need a second, independent global administrator; nothing changes until the owner opens the single-use link sent to the new address; while open, no activation and no set-up or reset link; on confirmation earlier links expire, sessions end, the old address is told; every step and refusal audited. Migration V26; page section in Users; public page `/e` | `EmailChangeIntegrationTests` 5/5 (the link is taken from the e-mail actually written); frontend specs 6; browser spec 2 |
| S5 | **Defect found by the browser run: the application did not start with background jobs on** (every real instance) | The Audit Center asked for "the" executor; with jobs on, the task scheduler is one too. Now asks for the application executor by name | `AuditCenterWiringTests` (red before the fix). The test suite turns jobs off, so it could not see this |
| S6 | **Defect found by the browser run, present since Sprint 10: set-up and reset e-mails carried no usable link** | The sender's placeholder pattern accepted neither a hyphen in the kind (`password-reset`, `email-change`) nor a code argument (`SETUP`); the e-mail kept the raw `{{link:password-reset:...}}` text. One definition of kind and argument now serves both the placeholder and the sender, and an unmatched placeholder is refused when it is made | `MailLinkIntegrationTests` 3/3 (set-up, administrator reset, self-service reset, all redeemed from the e-mail file; red before the fix); every earlier test built its own token and passed |
| S7 | Defect found by the browser run: two simultaneous first "mark as read" calls of a supplier contact, the second answered 500 (duplicate participant), present since the messaging delivery | Check and insert are one statement under a key-range lock | `concurrentFirstReadsByTheSupplierAddOneParticipantWithoutError` (red before) |

**Browser run 1 (22:21 to 22:59): 68 passed, 3 failed, not valid as evidence.** The machine went into sleep from 22:29:34 to
22:39:58 because my run script's keep-awake call failed (a PowerShell cast error); one failure was that sleep. The new
e-mail test failed on defect S6. One sign-in failed at 22:25 while every request on the server was slow (simple calls
up to 1 s, sign-in 17.8 s, two queue reads stopped at the 30 s limit); there was no lock refusal or deadlock. It looks
like host-level starvation before the sleep; it was not reproduced, and the cause is not proven.

**Browser run 2 (23:50 to 00:23): 70/71**; the only failure was a locator in the new e-mail test (it also matched the
notification live region), fixed. Run 3 was stopped at the owner's request (no result). The keep-awake call of the run
script was fixed and now records its result.

**Added for the owner's check that the e-mails people receive work:** `e2e/sprint-02-mail-links.spec.ts` (2 tests):
a new account is set up from its set-up e-mail, an administrator's reset e-mail and a self-service reset e-mail are
followed, a password is chosen and the person signs in with it. No browser test had followed these links before.

**Browser run 4 (2026-11-19 08:15 to 08:40): 73/73 passed** (71 earlier tests and the 2 above), one uninterrupted run,
fresh database `procurement_qa_s1val`, keep-awake confirmed, no sleep event, no server 5xx; 187 e-mails written, none
with an unresolved placeholder. Background jobs on (as in every real instance), so S5 is confirmed at run time.
Logs and screenshots outside OneDrive: `C:/qa/s1/e2e/pw-run4-73of73.{log,json}`, `backend-run4.log`, `evidence/`,
`verification/`.

### Remaining risks

| Risk | Note |
|---|---|
| The CI database-identity job (P1.2) has run only on a disposable local copy | Proven only after a push (CI) |
| Micrometer stops tagging new URIs after 100 (`MaximumAllowableTagsMeterFilter` warning at the end of the browser run) | Some endpoints may be missing from per-URI metrics in Cloud Monitoring; review the limit in P2 (monitoring) |
| The 22:25 slowness of browser run 1 is unexplained | Not seen in runs 2 and 4; watch in UAT |
| Snapshot isolation (ADR-014) | Now safe for numbering and audit-chain creation in tests; still **not enabled** anywhere; enabling it is a separate decision |
| E-mail delivery has only been proven in file mode | D9 (mail relay) still open; UAT must prove real delivery (ADR-016 step 1) |
| No antivirus scanner on development instances | Expected; fail-closed in uat and later (P1.1) |

## Commits (local `sprint-2`, 2026-11-19, owner-approved; not pushed)

| # | Commit | Content |
|---|---|---|
| 1 | `794e1b3` | Fix e-mail links and supplier read concurrency defects (S6, S7) |
| 2 | `e4e8cef` | Sprint 2 P1: ClamAV, SQL identities, Audit Center, monitoring and reset links (P1.1 to P1.6, S5); migration V25 |
| 3 | `cfcccd4` | First writers of numbering and audit chains safe under contention (S1 to S3) |
| 4 | `3235287` | Global administrator e-mail correction, ADR-016 step 5 (S4); migration V26 |
| 5 | (this documentation commit) | Progress report, plan, ADR-018, ADR-001/014/016 notes, README, STATUS, HANDOFF, CLAUDE.md/AGENTS.md database line |

Each intermediate state was checked in a copy outside the workspace: commit 1, targeted tests; commit 2, full backend
**329/329** (337 minus the 8 tests of commits 3 and 4), frontend lint, 259/259, build; commit 3, 74/74 targeted
(numbering, submissions, receipts, audit trail, isolation, modularity); commit 4, the tree is byte-for-byte the
validated code (337/337, 268/268, browser 73/73). Secret scan of each commit's content: no secret.

**Observation at commit 1 only:** `ApprovalIntegrationTests.usdRequestGoesThroughManagerByEmailLinkThenBudgetApprover`
failed in 2 of 4 combined runs of commit 1's tree (the manager's task e-mail was not found right after sending; once an
older run's e-mail for the same request number was taken instead). It passed 5 of 5 on `72a2280`, 3 of 3 plus every
full suite on the final tree, and in the full suite of commit 2. Cause not established. The test's lookup is fragile in
any case: it takes the newest `.eml` matching recipient and request number, while request numbers restart with each
clean database and `target/test-mail` is never cleared. Follow-up: match the e-mail by its outbox id (test-only change).

## Database decision (owner, 2026-11-19)

SQL Server 2022 stays for development, the Google Cloud UAT and the path to production. A PostgreSQL migration is
deferred to a future, dedicated experimental branch once the application baseline is stable (assessment: about 6 to 9
weeks; the native SQL in 44 files, the locking and error-number handling, and case-insensitive comparison are the main
work). New features avoid unnecessary new SQL Server-specific dependencies without destabilising tested code. Recorded
in ADR-001 and `CLAUDE.md`/`AGENTS.md`.

## CI stabilisation (2026-11-19 to 2026-11-21, owner-approved step by step)

The first CI runs on `sprint-2` (now running the suite as the restricted runtime identity, P1.2) and adverse local test
orders exposed test-infrastructure faults, one configuration defect and one security defect. Each was reproduced before
it was fixed.

| Commit | Kind | What |
|---|---|---|
| `794e1b3` .. `7b6207e` | P1 | The five P1 commits (see "Commits" below) |
| `e8bec4e` | test | E-mail helpers pick the e-mail actually sent (outbox id, numeric order) |
| `87d2ae1` | test | Privileged fixtures (triggers) through the owner connection; `APPLOCK_TEST` instead of `sys.dm_tran_locks` |
| `e819d6b` | test | The quote-link security context no longer wipes the shared test database (sequence resets, duplicate keys) |
| `05dddd6` | test | Supplier-portal authorization probed with another supplier's conversation; cross-access test (404 x4, no leak, no state change) |
| `1a56275` | test | On-behalf journey finds its request by a unique title, not its page position |
| `4e42f22` | **production** | `app.jobs.enabled=false` disables all six application jobs (Spring Modulith moments enabled scheduling globally) |
| `226d1da` | test | Isolation test restores the configured 5 s lock timeout on the pooled connection |
| `d0ba358` | **production, security** | Import jobs scoped to their company or creator (ADR-007); safe re-upload reuse; no client-supplied scope for supplier imports |
| `5561151` | test | Import access test removes the sites it creates |
| `bfe2f08` | test | Approval/rejection race checks the winner's exact outcome; both orders tested every run |
| `f57f700` | **production, security** | Correction of `d0ba358`: global administrators read import jobs but do not create imports |

### Import-job authorization policy (owner decision, 2026-11-20)

- **Create** (`POST /api/v1/imports`): ADMIN and PURCHASING_MANAGER only (the holders of `IMPORT_RUN`); organisation
  imports only for a company the administrator administers. GLOBAL_ADMIN gets 403. Role model and frontend unchanged.
- **Read** (`GET /api/v1/imports/{id}`): company-scoped jobs by global administrators and that company's
  administrators (the creator while they keep the right); group-level supplier jobs by global administrators and
  their creator while still allowed to import; everyone else 404, identical to an unknown id, no status or counts.
- **Re-upload** of the same file reuses only a job of the same scope that the caller may read.
- Note: the message of `d0ba358` wrongly says the interface offers imports to global administrators; `f57f700`
  corrects it (history not rewritten).

### Remaining risks

| Risk | Note |
|---|---|
| Live internal messaging works on one node only | Deploy a single node until the multi-node option is decided |
| Possible duplicate e-mail after recovery | The outbox reclaims a message stuck in SENDING after 5 minutes (crash or very slow relay); the Message-ID is stable |
| Prometheus per-URI metric cardinality | Micrometer stops tagging new URIs after 100 (seen in the browser run); review the limit with monitoring in P2 |
| Spring Modulith moments | Unused; its hourly and daily tasks still run on every instance (harmless) |
| E-mail delivery proven only in file mode | D9 (mail relay) open; UAT must prove real delivery |
| Snapshot isolation (ADR-014) | Safe for numbering and audit-chain creation in tests; still not enabled |

## Decisions for the owner

1. ~~Close Sprint 2 P1~~: **closed, GO, 2026-11-21** (CI green on `f57f700`, P1.2 proven).
2. Findings 1 and 2 and ADR-016 step 5 are **done** (S1 to S4).
3. Still pending from P0: D8 (Developer edition licensing), D9 (mail relay), D16 (domain), D18 (trial account).
4. P2 (UAT provisioning on Google Cloud) not started; it needs its own approval.
