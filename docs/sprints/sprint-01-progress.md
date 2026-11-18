# Sprint 1 progress

Working file so another session can continue without repeating work. Plan: `sprint-01-plan.md`.

_Last updated: 2026-11-16, all work packages worked; final verification recorded in `sprint-01-test-results.md`._

## State of the work packages

| WP | State | Where it is described |
|---|---|---|
| WP1 Quick wins (S-2, S-3, S-13, S-14, S-15, S-16, S-24) | Done, tested | security changes §1, §2, §4, §5 |
| WP2 Scanning fail-closed (S-1, part) | Done, tested; scanner product waits for G3-a | security changes §1 |
| WP3 Administrator powers (S-4, S-5) | Done, tested | security changes §3 |
| WP4 Audit events (S-6) | Done, tested | security changes §3 |
| WP5 Company scope on listings (S-7, part) | Done, tested; rate writes wait for G2-c | security changes §3 |
| WP6 Purge of expired tokens (DB-4, part) | Done, tested | database changes §3 |
| WP7 Lock and transaction timeouts (DB-3, part) | Done, tested; RCSI waits for G3-b | database changes §1 |
| WP8 Two database logins (DB-1) | Scripts written, grants verified; not applied (G3-c) | database changes §4 |
| WP9 Foreign-key indexes (DB-2) | Migration V23 (4 indexes with query evidence) | database changes §2 |
| WP10 Request id, access log | Done, tested | security changes §6 |
| WP11 Metrics | Done, tested | security changes §6 |
| WP12 CI pipeline | Written (`.github/workflows/ci.yml`); **never executed** | final report §4 |
| WP13 Authorization matrix | Done, 179 endpoints | security changes §7 |

## How to run the tests

Maven must run from a copy outside the VS Code workspace (project rule). The helper `C:\qa\s1\run.ps1` mirrors `BackEnd/` to `C:\qa\s1\BackEnd` and runs Maven there:

```powershell
& C:\qa\s1\run.ps1 <log-name> verify -Pdb-it                       # full suite, uses procurement_test
& C:\qa\s1\run.ps1 <log-name> test -Pdb-it "-Dtest=ClassA,ClassB"  # selected classes
```

Logs: `C:\qa\s1\<log-name>.log`. Never run two at once: they share `procurement_test`.

## Notes for the next session

- **Error bodies must stay identical between two refusals.** Several existing tests compare whole bodies ("indistinguishable" answers). Adding a per-request value to the problem body broke them; the request id is in the `X-Request-Id` header only.
- **`/actuator/health` body is asserted exactly** (`{"status":"UP"}`) by `AuthenticationIntegrationTests`. Enabling health probes adds a `groups` field and breaks it.
- **A failed test in `AuthenticationIntegrationTests` leaves the sign-in throttle tripped**, and every later class then fails at sign-in. Look at the first failures only.
- The baseline had 2 timing failures in `RequestSubmissionIntegrationTests` on a loaded machine; they passed in later runs.
- Edits to Java files with `\r\n` or `\"` inside string literals: use the editor tool, not a shell heredoc (the shell expands the escapes).
- Tests share one database and one Spring context: a test that adds a current EUR/TND exchange rate breaks `ApprovalIntegrationTests`. Use an old rate date.
- Adding an endpoint requires a line in `BackEnd/src/test/resources/authorization-matrix.txt`.
- Nothing has been committed. The sprint sits in the working tree on top of the uncommitted internal-messaging work.

## RR-3 remediation (started 2026-11-17)

Owner approval 2026-11-17: four eyes for BUYER, RECEIVER, PURCHASING_MANAGER and the three approver roles.

**Design (no new table, no new workflow, no new migration):**

1. `Role.isSensitive()`: DEPARTMENT_MANAGER, BUDGET_APPROVER, MANAGEMENT_APPROVER, BUYER, RECEIVER, PURCHASING_MANAGER.
2. The administrator who created an account can never give it a sensitive role, name it as named approver, or name it as delegate. **Permanent**: the 30-day window of the first remediation is removed, with `app.security.four-eyes-period` and its startup check (re-review RR-4).
3. Applies to **every** administrator, global ones included. Nobody gives themselves a sensitive role or names themselves approver or delegate.
4. Creator = `user_account.created_by_user_id` (V24) or, for accounts created before V24, the actor of the account's `USER_CREATED` audit event (new read method `AuditLog.firstActor`). No data migration.
5. The "approval by a second administrator" is the grant itself: it must be made by an administrator other than the creator. Single-administrator company: a global administrator grants. A pending-approval queue for every sensitive grant would need a new table and screens; it is offered as an option, not built.

Files: `identity/Role.java`, `identity/UserDirectory.java`, `identity/UserAdminService.java`, `audit/AuditLog.java`, `approval/Absences.java`, `approval/ApprovalRulesAdmin.java`, `platform/config/DeploymentConfigurationGuard.java`, `application.yml`, tests in `AdministratorLimitsIntegrationTests`, `DeploymentConfigurationGuardTests`.

Status: code and tests written 2026-11-17 (`Role.isSensitive`, `AuditLog.firstActor`, `UserDirectory.createdBy`, permanent rule in `UserAdminService`, `Absences`, `ApprovalRulesAdmin`; period and its guard check removed; 4 new tests in `AdministratorLimitsIntegrationTests`; `UserAdminIntegrationTests` fixture changed because a global administrator can no longer give itself BUYER). Next: run `AdministratorLimitsIntegrationTests,UserAdminIntegrationTests,ApprovalIntegrationTests,DeploymentConfigurationGuardTests,AuthorizationMatrixIntegrationTests`, then the full suite.

### RR-3 checkpoint 2026-11-17 (after the independent review)

- Tests: full backend suite on the RR-3 code **277 passed, 0 failed, 0 skipped** (`C:\qa\s1\final-rr3.log`). Frontend: lint clean, 253/253, build succeeds (two budget warnings that predate the sprint).
- Independent review of RR-3 and R-1 to R-4: R-1, R-2, R-3, R-4 fixed; RR-3 requirements 1 to 4 and 8 **partly** met. Findings F-1 to F-13 in `docs/reviews/sprint-01-independent-review.md` (third section).
- **Fixed 2026-11-17 (code only, no migration):** F-3 temporary password refused on accounts holding a sensitive, ADMIN or GLOBAL_ADMIN role; F-4 supplier-contact accounts get a recorded creator and SUPPLIER_CONTACT cannot be removed through user administration; F-6 named approver must belong to the rule's company; F-7 `removeGrant` answers 404 across companies; F-8 the creator cannot reactivate an account holding a sensitive role; F-9 an override applies only to requests of companies the recording administrator administers; F-10 administrator listings no longer include holders of group-wide roles other than GROUP_MANAGEMENT; F-11 texts and translations; F-12 explicit task delegation checks the creator; tests for a second administrator granting BUYER, RECEIVER, PURCHASING_MANAGER.
- **Waiting for the owner (business rules):** F-1 identities manufactured by an administrator; F-2 creation of administrator identities by one global administrator; F-5 separation of duties on invoices, buyer assignment and receipts (audit S-9).

### Checkpoint 2026-11-17 (review fixes)

- Review fixes F-3, F-4, F-6 to F-12 done. Affected classes: `fix1` 60 tests, 1 failure (a wrong assumption in a new test, corrected); `fix2` `AdministratorLimitsIntegrationTests` **28 passed, 0 failed**.
- Changed in this batch: `identity/UserAdminService.java` (`removeGrant` visibility, supplier-contact role, reactivation, temporary password), `identity/UserDirectory.java` (listing scope), `messaging/MessagingStore.java` and `MessagingService.java` (creator of contact accounts), `approval/ApprovalRulesAdmin.java` (named approver's company), `approval/ApproverResolver.java` (override scope, `createdBy`), `approval/ApprovalEngine.java` (task delegation), `FrontEnd/src/app/features/admin/approval-rules.ts` (two error codes), `FrontEnd/public/i18n/*.json` (texts), tests in `AdministratorLimitsIntegrationTests`.
- Done: backend 284/284, frontend lint, 253/253, build. Browser run started 19:17 (log `pw-final.log`, end marker `pw-final.exit`, both in `C:/qa/s1/e2e`). Next: its result, then the final report.
- Owner decisions pending: F-1, F-2, F-5 (see `docs/reviews/sprint-01-independent-review.md`, third section).

### Final checkpoint 2026-11-17 19:50

- Browser suite on the final code: **69 passed, 0 failed, 0 skipped, 0 flaky, 26.0 min, uninterrupted**.
- All approved work is done. Final report: `sprint-01-remediation-report.md`. Waiting for the owner: Go/No-Go, F-1, F-2, F-5, commit plan.

## F-1, F-2, F-5 (owner approval 2026-11-17, started)

Directions approved: F-1 two-person account activation; F-2 administrator roles never granted by the account's creator, documented bootstrap of the first global administrators; F-5 procurement separation of duties (requester not approver; buyer not assigned to own request; assigned buyer neither records nor confirms the receipt; invoice recorder neither attests nor authorises payment; payment by an independent purchasing manager).

Step 1 done: mapping. **No migration, no new table, no new state is needed**; implementation proceeds within the approval.

| Rule | Existing workflow, endpoint, data | Change |
|---|---|---|
| F-1 Account inactive until a second administrator activates it | `POST /admin/accounts` (create), `POST /admin/accounts/{id}/active`; `user_account.is_active`, `created_by_user_id` (V24); audit `USER_CREATED`, `USER_ACTIVATED`; setup link (`PasswordSetupRequested`) only redeemable on an active account | Create always inactive (`active` in the body ignored). Activation refused to the creator and to an account the creator created (indirect). The setup link is sent at activation instead of creation, so the mailbox owner can act only after the second person. History = audit events. Existing accounts unchanged. Supplier-contact invitations are out of scope (portal-only accounts, no internal role possible since F-4) |
| F-2 ADMIN / GLOBAL_ADMIN never from the account's creator | `addGrantInternal`; only global administrators grant ADMIN or GLOBAL_ADMIN | Creator rule extended to ADMIN and GLOBAL_ADMIN. Bootstrap: `GlobalAdminBootstrap` (first global administrator from `GLOBAL_ADMIN_BOOTSTRAP_EMAIL` / `_PASSWORD`) gains an optional second global administrator `GLOBAL_ADMIN_BOOTSTRAP_SECOND_EMAIL`, created active and sent a setup link to its own mailbox; documented in `README.md` |
| F-5a Requester never approves own request | `ApproverResolver.removeAuthors`, `isEligible` (requester and entered-by excluded) | Already enforced; regression test referenced |
| F-5b Buyer not assigned to own request | `PurchasingService.assign`, `accept` | Refuse a buyer who is requester or entered-by (`buyer-is-requester`) |
| F-5c Assigned buyer neither records nor confirms the receipt | `ReceiptService.createReceipt`, `confirmReceipt`, `acceptService`; `RequestSnapshot.assignedBuyerUserId` | Refuse the assigned buyer (`buyer-cannot-receive`) |
| F-5d Invoice recorder neither attests nor authorises payment | `ReceiptService.recordInvoice`, `attestInvoiceMatch`, `markInvoicePaid`; `supplier_invoice.recorded_by_user_id` | Recording never attests or pays (`matchAttested=true` or `PAID` at recording refused); attest and pay refused to the recorder (`invoice-recorder`) |
| F-5e Payment by an independent purchasing manager | `markInvoicePaid` (today BUYER or PURCHASING_MANAGER) | Requires PURCHASING_MANAGER of the buying company, not the recorder |

Interface: the invoice form loses the "match attested" checkbox; attest and mark-paid buttons shown only to people allowed (API adds `recordedByUserId` to the invoice). Tests and browser specs that had one buyer record, attest and pay are changed to use a second person (Mehdi, purchasing manager).

Step 2 done (code): `identity/Role.java` (`requiresFourEyes`), `identity/UserDirectory.java` (`creatorOf`), `identity/UserAdminService.java` (create inactive; activation by another person, not created by the creator; set-up link at activation; ADMIN/GLOBAL_ADMIN in the creator rule), `identity/GlobalAdminBootstrap.java` (`GLOBAL_ADMIN_BOOTSTRAP_SECOND_EMAIL`, audit events), `purchasing/PurchasingService.java` (assign, accept), `receipt/ReceiptService.java`, `ReceiptStore.java`, `ReceiptViews.java` (receipt and invoice rules, `recordedByUserId`), `FrontEnd/src/app/features/receipt/receipt-actions.ts` and `receipt.models.ts` (no attestation at recording; buttons by role and recorder), `users-admin.ts` (message), `i18n/*.json` (6 keys, parity 1514).

Step 3 (done): existing tests adapted to F-1/F-5 and new regression tests added; the affected classes pass (`f125-b`: UserAdmin 7/7, Receipt 36/36, AuthorizationMatrix 4/4; `f125-c`: AdministratorLimits 33/33; `f125-e`: Purchasing 6/6). Three tests first failed because inactive accounts or another company's buyer were refused earlier ("not eligible") than the rule under test; the fixtures now reach the intended rule. README, CLAUDE.md and AGENTS.md updated.

Step 4 (done): full backend 293/293; frontend lint, 255/255, build; fourth independent review: 5 Major, 3 Minor, 1 Info.

Step 5 (done): review fixes (findings 1, 3, 4, 5, 6 fixed; 2 fixed except attester ≠ payer, kept as owner option; 7, 8 documented; 9 accepted); 5 regression tests; affected classes 87/87; full backend **298/298** (`r4-full`); frontend lint, 255/255, build.

Step 6 (paused by the owner, 2026-11-18 00:05): browser suite. Attempts 1 to 4 on fresh databases: 67, 68, 67, 67 of 69; test defects fixed after attempts 1 and 2, time limits relaxed after attempt 3; attempt 4 failures were time limits in other tests, with OneDrive sync using a full core. Attempt 5 (screenshots outside the workspace) stopped by the owner. Resumed 2026-11-18: attempt 6 67/69 (time limits, machine at 60 to 100% CPU); **attempt 7: 69/69, uninterrupted, 25.7 min**. Final report completed. Step 6 done; waiting for the owner (GO/NO-GO, attester-versus-payer option, commit plan).
