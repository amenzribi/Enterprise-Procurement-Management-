# Sprint 1 validation report

**Date:** 2026-11-16. **For:** the owner's approval. **Validated state:** branch `feat/internal-messaging`, base commit `c7b901d`, uncommitted working tree (hardening sprint 1 on top of the uncommitted internal-messaging work).

## 1. Recommendation

**No-Go for closing Sprint 1. No-Go for UAT.** Go for a short correction batch, then a re-check of the four items below.

- All regression suites pass. Nothing that worked before the sprint is broken.
- The independent review found **4 Major defects**, all confirmed on a running instance. Two of them mean a claim of the sprint was wrong: "S-5 closed" (an administrator routing approvals to themselves) and "a deployment cannot start with a development profile".
- The project rule is that a sprint is not complete while a Major defect is open.
- CI has not run: nothing has been pushed. Pushing waits for your approval (section 6).

## 2. Regression testing

| Suite | Result | Skipped | Evidence |
|---|---|---|---|
| Backend `verify -Pdb-it` on SQL Server | **253 passed, 0 failed** | 0 | `C:\qa\s1\val-backend.log` |
| Frontend lint | Passed | — | `val-fe-lint.log` |
| Frontend unit tests | **253 passed, 0 failed** (32 files) | 0 | `val-fe-test.log` |
| Frontend production build | Succeeded, 2 budget warnings that predate the sprint (initial bundle 524.60 kB against 500 kB; `sign-in.scss`) | — | `val-fe-build.log` |
| Browser suite (Playwright, Edge), isolated instance, fresh database | **69 tests: 67 passed, 2 failed** in the full run; both passed when rerun. See below | 0 | `C:\qa\s1\e2e\pw-full.log`, `pw-rerun*.log` |

**The browser suite in detail.** 66 existing tests plus 3 new ones written for this validation (`FrontEnd/e2e/sprint-01-hardening.spec.ts`).

| Failure in the full run | Cause | Outcome |
|---|---|---|
| `accessibility AR phone` | The laptop suspended during the run (`ERR_NETWORK_IO_SUSPENDED`; the run shows 3.5 h of wall time) | Passed on rerun |
| New test "company administrator sees only their own companies" | Defects in the new test, not in the product: the company list opens only after "New rule", and shows names, not codes | Corrected; 3 of 3 pass |

One more thing to state: in one rerun, `accessibility EN desktop` failed at sign-in and passed on the next run with no change. It had passed in the full run. Treated as a slow first sign-in on a busy machine, not investigated further. **There is no single uninterrupted run with 69 of 69.**

Coverage of the areas you named:

| Area | Covered by | Result |
|---|---|---|
| Temporary-password administration | New spec, test 1; backend `AdministratorLimitsIntegrationTests` | Pass |
| File attachments | New spec, test 2; backend sniffer, policy and API tests | Pass |
| Company administration listings | New spec, test 3; backend | Pass |
| Request-to-closure workflow | `request-to-closure-20-step`, `request-journey`, `receipt-rules` | Pass |
| Supplier quotation workflows | `purchasing-journey`, `ordering-logistics-journey`, `messaging-journey`; backend sourcing and quote-link tests | Pass |
| Approval and delegation workflows | `approval-journey` (includes absence delegation), `approval-split-pane`; backend | Pass |

After the run: test instance stopped; the screenshots the suite overwrites in `docs/qa/evidence` and `docs/design/verification` were restored (81 files); the database `procurement_qa_s1val` remains on the local server and can be dropped.

## 3. Scope of the diff

The working tree holds 88 changed or new entries. Checked against the approved scope:

- **In scope and documented:** the Sprint 1 files listed in `sprint-01-security-changes.md` and `sprint-01-database-changes.md`.
- **Added by this validation:** `FrontEnd/e2e/sprint-01-hardening.spec.ts`, five ADRs, `docs/reviews/sprint-01-independent-review.md`, this report, and the documentation updates of section 8.
- **Not Sprint 1:** the internal-messaging work that was already uncommitted (code, migration V22, its tests and documents), `docs/tooling.md`, `.anchor/`, `docs/superpowers/`.
- **Undocumented until now:** the test profile lowers the lock timeout to 5 s; `SecurityConfiguration` permits asynchronous dispatches (messaging work, standard rule).
- **Dependency:** Sprint 1 cannot be committed without the messaging work. Migration V23 follows the untracked V22, and the authorization matrix lists the 15 messaging endpoints.

No change outside the approved scope was found.

## 4. Independent review

Method and full list: `docs/reviews/sprint-01-independent-review.md`. The reviewer worked in a separate context with read-only access and ran nothing. Result: 0 Blocker, 4 Major, 15 Minor, 3 Notes.

Each Major was then tested on the running instance with a probe that undoes its own changes (`C:\qa\s1\e2e\probe.mjs`, output in `probe.log`):

| ID | Finding | Probe result |
|---|---|---|
| R-1 | With the override, an administrator can name as delegate (a) an account they just created, or (b) a supplier contact | **Confirmed.** Both absences were accepted (201) for the budget approver |
| R-2 | A company administrator can grant themselves an approver role | **Confirmed.** `BUDGET_APPROVER` granted to self (200); roles afterwards: `BUDGET_APPROVER, ADMIN` |
| R-3 | A company administrator can add and remove roles of a peer administrator, which also signs the peer out | **Confirmed.** Add 200, remove 200, the peer's session answered 401 afterwards |
| R-4 | The startup guard only works when the operator declares an HTTPS address or a deployed environment | **Confirmed by reading the code.** The test instance itself started with `dev,demo`, as any server would without those two variables |

R-2 is the most serious: it needs no delegation at all and existed before the sprint. The audit did not list it; the sprint did not find it.

Minor findings confirmed by the lead in the source: R-5 (one administrator listing still group-wide), R-11 (two searches without wildcard escaping), R-18 (`/actuator` and `/actuator/info` open to any signed-in user: probe 200, 200; metrics 403 as intended). The other Minor findings are accepted as reported and not individually re-tested.

## 5. Verified corrections

Status after the review. "Verified" means the reviewer confirmed it in the code and the tests pass.

| Item | Status |
|---|---|
| S-2 Extension tied to file type | **Verified closed.** Double extensions, trailing dot or space, case, `a.pdf:x.bat`, look-alike characters all refused |
| S-1 Nothing recorded clean without a scan; quarantine | **Verified (reduced).** No scanner exists. The interface wrongly says "virus check in progress" for a quarantined file (R-7) |
| S-13 Audit payload, S-14 CSV, S-16 mail mode | **Verified closed** |
| S-6 Audit events for absences, rules, rates | **Verified, with one gap:** a rule version retired by the activation of the next one has no event of its own (R-16) |
| S-3 Proxy address | **Partly.** Forged left-hand values are ignored. Open: header choice, exact-match proxy list, silent shared key when no proxy is declared (R-10) |
| S-4 Administrator takeover | **Partly.** Chosen passwords, reset, deactivation and sign-out of a peer are refused. Role changes on a peer are not (R-3) |
| S-7 Company scope | **Partly.** Companies, users, absences scoped. One listing still group-wide (R-5); supplier contacts and global administrators appear in the user list (R-6) |
| S-24 Search wildcards | **Partly.** Supplier search fixed; two others not (R-11) |
| **S-5 Self-delegation** | **Not closed** (R-1, R-2). The sprint documents said "closed"; they are corrected |
| **S-15 Development profile in deployment** | **Not closed** (R-4). Same correction |
| DB-3 Bounded waits; DB-2 four indexes; DB-4 token purge | **Verified.** Lock timeout and 503 answer confirmed by test |
| DB-1 Two logins | **Verified as prepared.** Scripts run on the test database, 12 of 12. Never run with a real login |
| Authorization matrix, 179 endpoints | **Verified for what it tests:** anonymous and supplier-contact callers, and a requester of another company against real objects. **It does not prove** isolation between two privileged users of different companies (R-13) |
| Metrics for global administrators only | **Verified.** Two other actuator paths are open to any signed-in user (R-18) |
| Observability | **Verified.** No secret in any URL path; request id generated by the server |

## 6. CI status

**Not run. Not reported as passing.** Reviewed by the independent reviewer: no certain breaker; risks listed in the review (strict `npm audit`, first run on SQL Server 2022 and on Linux, one time-zone-sensitive date in the matrix test, demo-user seeding under the new 30 s limit).

The repository has two remotes; pushing is possible but not authorized yet. Proposal for your approval:

| | |
|---|---|
| Current branch | `feat/internal-messaging` (local only; base `c7b901d`, which is `enterprise/main`) |
| Target | `enterprise` (`amenzribi/Enterprise-Procurement-Management-`), **new branch `feat/internal-messaging`**, not `main` |
| Commit 1 | `feat(messaging): internal employee messaging` — the messaging work already awaiting your review (it must precede Sprint 1 because of migration V22) |
| Commit 2 | `Harden security, database waits and observability (Sprint 1).` — `BackEnd/` and `FrontEnd/` Sprint 1 changes, tests, `scripts/db/`, `.github/workflows/ci.yml` |
| Commit 3 | `Record the readiness audit, Sprint 1 documents, review and ADRs.` — `docs/audit/`, `docs/sprints/`, `docs/reviews/`, `docs/decisions/ADR-013` to `017`, `README.md`, `CLAUDE.md`, `AGENTS.md`, `STATUS.md`, `HANDOFF.md` |
| Left out | `.anchor/`, `docs/tooling.md` (as `HANDOFF.md` already says), `docs/superpowers/` unless you want it |

Two points to decide with the push: whether the Major findings are fixed before or after it, and that committing the messaging work is itself a decision that was waiting for you.

## 7. Remaining vulnerabilities

**Found or confirmed by this validation (new):**

| # | Vulnerability | Severity |
|---|---|---|
| R-2 | Company administrator grants themselves an approver role and approves | Major |
| R-1 | Override delegation to a created account or to a supplier contact | Major |
| R-3 | Company administrator changes a peer administrator's roles and signs them out | Major |
| R-4 | Development and demo profiles can start on a server | Major |
| R-5, R-6 | Two remaining cross-company exposures in administrator listings | Minor |
| R-10 | Rate-limit key still choosable with some proxy setups; shared key when no proxy is declared | Minor |
| R-13 | No automated proof of isolation between privileged users of different companies | Minor (test gap) |
| R-15 | Runtime database user could still extend the audit chain; migration password would sit in the application's environment | Minor |
| R-18 | Actuator index and build information readable by any signed-in user | Minor |

**Known before, unchanged:** no antivirus scanner (High); application database login can alter the schema until ADR-015 is applied (High); no backup, restore drill, external health check or alerting (High); readers block on writers up to 10 s until ADR-014 (Medium); password-only sign-in (Medium, High for production); audit chain not anchored (Medium); quote-link reuse, invoice separation of duties, unbounded imports, one-tap approval without ceiling (Medium); no load test, no dynamic or penetration test, no backend dependency scan.

## 8. Documentation and decisions

Updated: `README.md` (mandatory configuration outside development, known gap R-4), `CLAUDE.md` and `AGENTS.md` (kept identical: five new conventions, pointers, restore command), `STATUS.md`, `HANDOFF.md`. The sprint documents that overstated S-5 and S-15 carry a correction note.

ADRs prepared, **all Proposed, nothing activated**:

| ADR | Subject | Proposed |
|---|---|---|
| ADR-013 | Malware scanning | ClamAV daemon on the application host, fail closed |
| ADR-014 | Database isolation | Timeouts applied in Sprint 1; enable read committed snapshot after a rerun of both suites on a copy |
| ADR-015 | Database logins | Separate migration and runtime logins; scripts verified |
| ADR-016 | Password recovery | Reset links only; remove administrator-chosen passwords once e-mail is proven in UAT |
| ADR-017 | Exchange rates | Global administrators only now; a finance permission or automatic rates later |

## 9. Unresolved decisions

1. Fix R-1 to R-4 now (recommended; about one day including tests and a re-check), or accept them for a later sprint.
2. For R-1 and R-2, the rule to adopt: the recommendation is that a company administrator can never give themselves, or an account they created within a set period, an approver role or a delegation; and that an override delegate must be an active internal employee.
3. Approve, change or reject ADR-013 to ADR-017.
4. Commit and push as proposed in section 6.
5. From the audit, still open: quote-link reuse, invoice separation of duties, retention of notifications and messages, hosting and licensing.

## 10. UAT readiness

**Not ready.** Before a first UAT user, in order:

1. R-1 to R-4 fixed and re-verified.
2. CI green on the pushed revision.
3. One uninterrupted browser run on that revision.
4. ADR-013 (scanner or written risk acceptance), ADR-015 (two logins, suite run with the runtime login), ADR-014 decided.
5. The deployment kit of audit report 08: reverse proxy with security headers, backup and a recorded restore drill, external health check, runbook.

Sprint 2 was not started. Nothing was deployed.
