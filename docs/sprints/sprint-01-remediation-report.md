# Sprint 1 final report: R-1 to R-4, RR-3, F-1, F-2, F-5 and final validation

**Date:** 2026-11-17. **For:** the owner's Go/No-Go. **State:** branch `feat/internal-messaging`, base `c7b901d`, uncommitted working tree (internal messaging, then hardening sprint 1 and its remediation). Nothing committed, pushed or deployed; Sprint 2 not started; no database-wide or infrastructure change.

Earlier documents: `sprint-01-final-report.md` (sprint), `sprint-01-validation-report.md` (first validation), `sprint-01-security-changes.md`, `sprint-01-database-changes.md`, `sprint-01-test-results.md`, `docs/reviews/sprint-01-independent-review.md` (three reviews).

## 1. Recommendation

<!-- recommendation:start -->
**GO for closing Sprint 1** (security hardening, R-1 to R-4, RR-3, and the business security decisions F-1, F-2,
F-5). **NO-GO for UAT and production**, for the infrastructure reasons that predate this work.

- No Blocker or Major defect is open. The fourth independent review (F-1, F-2, F-5) found 5 Major issues: four are
  fixed and tested; the fifth (finding 2) is fixed for the approved requirement and leaves one stricter option for
  you (section 6).
- All required checks pass on the same, final code: backend **298/298** on SQL Server; frontend lint, **255/255**,
  build; **browser suite 69/69 in one uninterrupted run** (2026-11-18).
- Browser runs on this laptop are sensitive to load (OneDrive and other applications kept it at 60 to 100% CPU);
  six attempts before the clean one failed on test defects (fixed) or time limits, never on a wrong server answer.
  CI on a dedicated runner is the reliable place for this suite.
- F-1, F-2, F-5 were implemented as approved, with **no migration, no new table and no new business state**.
- UAT stays No-Go: no antivirus scanner (ADR-013), one database login that can alter schema and audit (ADR-015), no
  backup, restore drill or monitoring. These are infrastructure decisions awaiting your separate approval.
<!-- recommendation:end -->

## 2. What was fixed

### R-1 to R-4 (Major findings of the first independent review)

| Finding | Fix | Proof |
|---|---|---|
| R-1 Override delegation to a created account or a supplier contact | Delegate must be an active internal employee, never a supplier contact, never the recorder or an account the recorder created; with the override, belong to the absent person's company, and at routing to the request's company, recorded by an administrator of that company | `AdministratorLimitsIntegrationTests` |
| R-2 Self-granted approval authority | No administrator changes their own roles (company) or gives themselves a sensitive role (global); nobody names themselves as approver in a rule or as delegate | same |
| R-3 Peer administrators | A company administrator cannot add or remove roles of, reset, deactivate, edit or sign out another administrator | same |
| R-4 Unsafe startup | `APP_ENVIRONMENT` mandatory without a development profile; development profiles only with a local database, listening on 127.0.0.1, no `serverName=` redirection, never in a declared deployed environment; `demo` only with `APP_ENVIRONMENT=demo`; effective cookie setting checked | `DeploymentConfigurationGuardTests` (15), `StartupConfigurationTests` (5 real starts) |

### Re-review findings (second review)

RR-1 (self or fresh account as named approver), RR-2 (supplier contact as named approver), RR-5 (internal role on a supplier contact account), RR-6 (development profile on a server with a local database), RR-7 (grant to another company's account), RR-8 (override delegate of another company), RR-9 (`serverName=` redirection), RR-14 (documents): fixed and tested.

### RR-3 (owner approval 2026-11-17)

**Rule:** whoever created an account never gives it a sensitive role (DEPARTMENT_MANAGER, BUDGET_APPROVER, MANAGEMENT_APPROVER, BUYER, RECEIVER, PURCHASING_MANAGER), never names it as named approver, never names it as delegate, never reactivates it while it holds a sensitive role. Permanent; for every administrator, global ones included.

| Owner requirement | How |
|---|---|
| 1. No single administrator creates and empowers an account | Refused at creation (roles array) and afterwards (`four-eyes`); the account is not created when its creation call asks for a sensitive role |
| 2. Second independent administrator | The grant itself must be made by an administrator other than the creator. No pending-approval queue (see section 6, option) |
| 3. No bypass through later changes, indirect assignment, delegation, new accounts | Permanent rule (the 30-day window was removed); delegation (absence and task), named approvers, reactivation; supplier-contact accounts now record their inviter and cannot be converted; accounts created before V24 recognised from the `USER_CREATED` audit event |
| 4. No self-approval, no circular approval | Self-grant, self as named approver, self as delegate refused. Two administrators each empowering the other's creations is two-person collusion (residual). **Partly**: F-1 and F-5 below |
| 5. Legitimate roles and processes kept | No data changed; ordinary roles (REQUESTER, ENTER_ON_BEHALF) stay the creator's work; demo and seeded accounts unaffected |
| 6. Single-administrator company | A global administrator grants the role (`README.md`, "Administration rules") |
| 7. Backend enforcement | All checks in services; the interface only hides what the server refuses |
| 8. Regression tests | `AdministratorLimitsIntegrationTests`: 28 tests |

**No new table, no new workflow.** One additive migration from the earlier remediation (V24, `user_account.created_by_user_id`) is reused; RR-3 added none.

### Third review (RR-3), fixed

F-3 temporary password refused on accounts holding a sensitive or administrator role; F-4 supplier-contact accounts; F-6 named approver of another company; F-7 `removeGrant` 404 across companies; F-8 reactivation by the creator; F-9 override scope; F-10 listings; F-11 texts and translations; F-12 task delegation. Detail: `docs/reviews/sprint-01-independent-review.md`.

### F-1, F-2, F-5 (owner approval 2026-11-17)

Mapped to the existing workflow first: no migration, new table or state was needed, so the work went ahead within
the approval.

| Rule | How (existing structures reused) | Tests |
|---|---|---|
| **F-1** A new account is inactive until another administrator activates it; not the creator, not anyone in its creation chain, not an administrator created by that chain | `is_active` and `POST /admin/accounts/{id}/active`; creator from `created_by_user_id` (V24) or the `USER_CREATED` audit event, followed five levels deep; set-up link sent at activation; audit `USER_CREATED`, `ROLE_GRANTED`, `USER_ACTIVATED`, `PASSWORD_SETUP_ISSUED`; no temporary password before activation or by the creator; existing accounts untouched | `AdministratorLimitsIntegrationTests` (36), `UserAdminIntegrationTests`, browser `sprint-01-hardening` |
| **F-2** ADMIN and GLOBAL_ADMIN never granted by the creator (or its chain) nor to oneself; bootstrap of two global administrators held by two people | `Role.requiresFourEyes`; `GLOBAL_ADMIN_BOOTSTRAP_SECOND_EMAIL` (set-up link by mail), audit `GLOBAL_ADMIN_BOOTSTRAPPED`; README "Bootstrap" | same; cross-company refusals 404 |
| **F-5** Requester never approves (pre-existing); never buys: assignment, acceptance, sourcing, award, order, logistics; the buyer (assigned or PO issuer) never records or confirms the receipt, accepts services or a shortfall, or waives a quantity; the invoice recorder never attests or pays, and recording does neither; payment only by a purchasing manager of the buying company who did not buy the order | Checks in `PurchasingService`, `SourcingService`, `OrderingService`, `LogisticsService`, `ReceiptService`; codes `buyer-is-requester`, `buyer-cannot-receive`, `invoice-recorder`, `payment-needs-manager`, `payment-not-independent` (EN/FR/AR) | `ReceiptIntegrationTests` (38), `PurchasingIntegrationTests`, browser `request-to-closure-20-step`, `receipt-rules` |

The interface follows: the invoice form no longer offers "attested"; attest and pay buttons are hidden from the recorder.

## 3. Independent reviews

| Review | Scope | Result |
|---|---|---|
| 1 (2026-11-16) | Sprint 1 | 0 Blocker, 4 Major (R-1 to R-4), 15 Minor, 3 Notes; all 4 Major confirmed on a running instance |
| 2 (2026-11-17) | R-1 to R-4 fixes | R-3 fixed; R-1, R-2, R-4 partly; RR-1 to RR-15 |
| 3 (2026-11-17) | RR-3 and R-1 to R-4 | R-1 to R-4 fixed; RR-3 requirements partly met; F-1 to F-13 |
| 4 (2026-11-17) | F-1, F-2, F-5 as implemented | 0 Blocker, 5 Major, 3 Minor, 1 Info; Major 1, 3, 4, 5 fixed; Major 2 fixed except attester ≠ payer (owner option); Minor 6 fixed, 7 and 8 documented; Info 9 accepted |

Each review ran in a separate context, read-only, without the implementation's reasoning. The fixes after review 4
were verified by tests, not by a fifth review.

## 4. Test results

<!-- tests:start -->
| Check | Result |
|---|---|
| Backend `verify -Pdb-it`, SQL Server, final code | **298 passed, 0 failed, 0 skipped** (208 before the sprint) |
| Frontend lint | Passed |
| Frontend unit tests | **255 passed, 0 failed** (32 files) |
| Frontend production build | Succeeded; 2 budget warnings that predate the sprint |
| Browser suite, final code, fresh database, 2026-11-18 08:56 to 09:22 | **69 passed, 0 failed, 0 skipped, 0 flaky, 25.7 min; uninterrupted** (no sleep event, no clock leap). Six earlier attempts failed: three test defects (fixed) and time limits under machine load (section 4 of `sprint-01-test-results.md`) |

All ran on the same code. Logs in `C:\qa\s1` (`r4-full.log`, `e2e\pw-final.log`, `e2e\pw-results-final.json`).
<!-- tests:end -->

Browser history: 2026-11-16 67/69 (laptop suspended; one test defect). 2026-11-17 15:37 and 16:20: interrupted by *Sleep* chosen in the Start menu; the 16:20 run gave 65/69, causes in `sprint-01-test-results.md` section 7 (one test race in the sign-in helper and one in a new test, both fixed; two failures across the sleep).

**Slow sign-in, investigated:** load on the laptop, not a defect. Idle: median 104 ms, maximum 175 ms; during browser runs every request slows down together.

## 5. Remaining security risks

| Risk | Severity | Status |
|---|---|---|
| The same purchasing manager may attest an invoice match and authorise its payment (recorder and buyer are excluded) | Medium | Owner option, section 6 |
| Two administrators colluding; a second administrator deceived into activating a fake identity | Residual | Outside any four-eyes control; single sign-on (option C) reduces it |
| No antivirus scanner; uploads quarantined | High | ADR-013 approved direction, not implemented |
| Application database login can alter the schema and the audit trail | High | ADR-015 approved direction, not applied |
| No backup, restore drill, external monitoring | High | Infrastructure, not started |
| Readers wait for writers (up to 10 s) | Medium | ADR-014: test on a copy |
| Password-only sign-in; temporary passwords still possible for ordinary, active accounts not created by the administrator | Medium | MFA or SSO before production; ADR-016 |
| Supplier-contact invitations active without a second person (portal-only accounts) | Low | Documented exception |
| Second-administrator bootstrap not covered by an automated test | Low | Documented; operator trusted |
| Quote-link reuse, unbounded imports, one-tap approval without ceiling, audit chain not anchored | Medium | Audit backlog |
| `APP_ALLOW_UNSCANNED_DOWNLOADS=true` and empty trusted proxies accepted in a deployed environment | Low | Documented |

## 6. Decisions for the owner

1. **Go/No-Go** (section 1).
2. **Attester ≠ payer (option).** Requiring a third person per invoice closes the last finding of review 4, but a
   company with one buyer and one purchasing manager could then never close an invoice. Recommended only where every
   buying company has two purchasing managers; otherwise keep the current rule.
3. **Commit and push plan** (section 7).
4. **Single sign-on** before production (identities from the client's identity provider; also MFA).
5. ADR-016 full removal of temporary passwords; ADR-017 exchange rates for global administrators; ADR-013, ADR-014
   (copy), ADR-015 per their plans (`docs/decisions/ADR-013-to-017-implementation-plans.md`). Each needs your separate
   approval.

## 7. Commit and push proposal (for approval, nothing done)

<!-- commit:start -->
**Branch and remote.** Current branch `feat/internal-messaging` (local only), base `c7b901d` = `enterprise/main`. Proposed target: remote `enterprise` (`amenzribi/Enterprise-Procurement-Management-`), **new remote branch `feat/internal-messaging`**, not `main`. A pull request to `main` would follow only on your instruction.

**Migration order.** V21 is the last committed migration. Commit 1 adds V22 (internal messaging); commit 2 adds V23 (indexes) and V24 (account creator). F-1, F-2, F-5 added no migration. No gap, no change to an applied migration.

| Commit | Content |
|---|---|
| 1. `feat(messaging): internal employee messaging` | The messaging work that awaited your review (unchanged since the earlier plan): `messaging/Internal*`, `request/RequestMessagingQueries.java`, `V22__internal_messaging.sql`, its tests, `FrontEnd/src/app/features/internal-messaging/`, `e2e/internal-messaging.spec.ts`, the shell, navigation, routes, guards, icon and request-detail changes, `support/ApiClient.java`, `docs/architecture/internal-messaging.md`, `docs/architecture/architecture.md`, `docs/design/messaging/` (13 screenshots) |
| 2. `Harden security, administration and separation of duties (Sprint 1).` | Every other `BackEnd/` and `FrontEnd/` change, including F-1, F-2, F-5 (`identity/`, `purchasing/`, `sourcing/`, `ordering/`, `logistics/`, `receipt/`, the receipt actions screen, i18n keys, tests and browser specs), `scripts/db/`, `.github/workflows/ci.yml`, V23, V24 |
| 3. `Record the readiness audit, Sprint 1 reports, reviews and ADR-013 to ADR-017.` | `docs/audit/` (11), `docs/sprints/` (8), `docs/reviews/sprint-01-independent-review.md`, `docs/decisions/` (6), `README.md`, `CLAUDE.md`, `AGENTS.md`, `STATUS.md`, `HANDOFF.md` |
| Left out | `.anchor/` (local session state), `docs/tooling.md` (as earlier handoffs say), `docs/superpowers/` (planning notes; include only if you want them), **`FrontEnd/angular.json`** (an Angular CLI analytics ID written by a tool run, not project work) |

The working tree has 165 changed or new files (counted per file, 2026-11-18). Secret scan of the changes repeated on 2026-11-18: none found (the only match is a usage comment with `%MIGRATION_PASSWORD%` placeholders in `scripts/db/create-application-logins.sql`). Before executing, I will show the exact file list of each commit.

**Four files mix both pieces of work.** Commit 1 receives their messaging part only; commit 2 adds the Sprint 1 part:

- `identity/SecurityConfiguration.java`: asynchronous dispatch rule in commit 1; metrics and health rules in commit 2.
- `FrontEnd/public/i18n/en.json`, `fr.json`, `ar.json`: messaging keys in commit 1; Sprint 1 keys in commit 2.
- `messaging/InternalMessageStream.java`: commit 1 without the `registerMetrics` method; the method in commit 2.

**Checks before pushing:**

- **Commit 1 has never been built on its own:** every test above ran on the combined tree. I would build and test it in a temporary worktree (backend suite and frontend checks) before pushing.
- **No secret in the changes:** re-scanned before committing. The only password-like strings are fixtures on throwaway test accounts; the demo password of the runs exists only outside the repository (`C:\qa\s1\e2e`).
- **No generated output:** `target/`, `dist/`, `node_modules/` and `e2e/.results` are ignored.

Commit messages end with the attribution line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**After the push.** The workflow runs on the pushed branch. The GitHub CLI is not installed here, so I would ask you to check the Actions tab, or authorize installing `gh` to read the result. I will not report CI as passing until it has run successfully.

**Nothing is executed until you approve this plan.**
<!-- commit:end -->
