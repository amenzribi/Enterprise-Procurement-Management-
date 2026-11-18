# Sprint 1 hardening: independent review

**Date:** 2026-11-16. **Reviewed state:** working tree on `feat/internal-messaging` at `c7b901d` plus the uncommitted Sprint 1 changes (and the earlier uncommitted internal-messaging work, out of scope).

**How it was done.** A separate review context that had none of the implementation's reasoning, only the code, the audit and the sprint documents. Read-only: `git diff`, file reads and searches. It ran nothing. It was told not to accept the sprint documents as proof. The lead then checked each Major finding against the source and, where possible, against a running instance; that check is recorded in `docs/sprints/sprint-01-validation-report.md`.

**Result: no Blocker, 4 Major, 15 Minor, 3 Notes.** Under the project rule a sprint is not complete while a Major is open.

Paths are under `BackEnd/src/main/java/com/epi/procurement/` unless stated.

## Major

| ID | Area | Where | Finding | Scenario | Suggested fix |
|---|---|---|---|---|---|
| R-1 | Delegation (S-5) | `approval/Absences.java` (`belongsToACompanyOf`), `approval/ApproverResolver.java:139-140` | Only "delegate = acting administrator" is refused. With the override, any account of the absent person's company, or any holder of a grant without a company, is accepted | (a) A company administrator creates an account with an e-mail they control and names it delegate. (b) A supplier contact is created in the inviting buyer's company with a grant without a company (`messaging/MessagingService.java:217-223`), so an external contact can be named delegate | Require an active internal account for an override; limit the group-wide branch to `GROUP_MANAGEMENT`; refuse a delegate the acting administrator created or granted recently, or require a second administrator |
| R-2 | Role grants | `identity/UserAdminService.java` (`addGrantInternal`) | No check that the administrator is granting to themselves | A company administrator grants themselves `BUDGET_APPROVER`, `DEPARTMENT_MANAGER` or `MANAGEMENT_APPROVER` in their company and approves directly, with no delegation at all | Refuse self-grants by company administrators |
| R-3 | Peer administrators (S-4) | `identity/UserAdminService.java` (`addGrant`, `removeGrant`) | The peer-administrator check is only in `assertCanManage`; granting and removing roles do not use it | Administrator A removes or adds roles of peer administrator B in A's companies. Each change renews B's credential version, which signs B out, repeatedly if wanted | Apply the peer check in both methods |
| R-4 | Startup guard (S-15) | `platform/config/DeploymentConfigurationGuard.java` | The guard refuses a development profile only when the operator also declares an HTTPS address or a deployed environment. Both are optional | UAT behind a TLS-terminating proxy, started with the documented `dev,demo` command and no `APP_PUBLIC_BASE_URL`, starts with insecure cookies, testing endpoints and API documentation. Also: `demo` with a deployed environment seeds demo accounts including a global administrator; `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` is not seen; environment names outside the fixed list are not treated as deployed; unscanned downloads and empty trusted proxies are not checked | Invert the default: development profiles start only against a local database or with an explicit development declaration; refuse `demo` in a deployed environment; check the effective cookie setting |

## Minor

| ID | Area | Where | Finding |
|---|---|---|---|
| R-5 | Isolation (S-7) | `approval/ApprovalQueries.java:294-304` | `GET /admin/approvals/awaiting-circuit` is filtered by group only: a company administrator sees other companies' legacy submitted requests (number, title, company) |
| R-6 | Isolation (S-7) | `identity/UserDirectory.java` (scope clause) | "Holders of a group-wide role" includes every supplier contact and global administrator: names and e-mails of contacts invited by other companies are listed |
| R-7 | Quarantine (S-1) | `FrontEnd/.../documents/attachments.ts`, `document/ScanPolicy.java` | In a deployment without scanner the interface says "virus check in progress" forever. A scanner that returns nothing gives 422 `file-infected`, not 503 as documented |
| R-8 | Uploads | `document/Attachments.java` | The file is written to disk before the database insert; a failure or the new timeouts leave an orphan file |
| R-9 | Uploads | `document/Attachments.java` (`sanitize`) | A name over 200 characters with a 200-character "extension" raises an exception, answered 500 instead of 415. Existing code |
| R-10 | Proxy (S-3) | `platform/web/ClientAddressResolver.java` | `X-Forwarded-For` is preferred and `Forwarded` used when it is absent: a proxy that manages only one of them lets the client supply the other. Trusted proxies are exact strings (no ranges). Empty trusted proxies behind a proxy makes all users share one rate-limit key, silently |
| R-11 | Search (S-24) | `request/RequestWorkflow.java:186-187`, `request/RequestLogisticsQueries.java:64` | Request search does not escape `%`, `_`, `[`; logistics search does not escape `[` |
| R-12 | Translations | four new error codes | `peer-administrator`, `cannot-set-own-password`, `delegate-is-administrator`, `scan-unavailable` have no text in EN, FR or AR; the user page still offers actions the server refuses |
| R-13 | Authorization matrix | `AuthorizationMatrixIntegrationTests` | The outsider is a plain requester, so administration, purchasing, supplier and dashboard cells are role refusals reached before any object: they do not prove isolation between two privileged users of different companies. Actuator and documentation paths are not enumerated. No `OK`, `INPUT` or `SKIP` cell was found to hide an exposure |
| R-14 | Timeouts (DB-3) | `platform/web/ApiExceptionHandler.java` | Every transaction or query timeout is answered "busy, retry in 2 s". A deterministically slow operation (large import, full export) fails the same way every time, with no trace of the statement |
| R-15 | Database scripts | `scripts/db/` | The runtime user can still advance the audit chain head and append events. The deny on the migration history is skipped silently when the table does not exist. Refusal checks accept error numbers that also mean "object does not exist". Migrations run at application start, so the migration password sits in the application's environment |
| R-16 | Audit (S-6) | `approval/ApprovalStore.java:101-105` | Activating a rule version silently retires the previous one, with no event of its own |
| R-17 | Delivery | `V23`, matrix file, translations | Sprint 1 cannot be committed without the messaging work: V23 follows the untracked V22 and the matrix lists the messaging endpoints |
| R-18 | Actuator | `identity/SecurityConfiguration.java` | `/actuator` and `/actuator/info` are open to any signed-in user; a future exposed endpoint would be too |
| R-19 | CI | `.github/workflows/ci.yml` | `npm audit --omit=dev` fails the job on an advisory of any severity |

## Notes

| ID | Area | Finding |
|---|---|---|
| R-20 | Request id | Correct. No URL path carries a secret. For the event stream, duration and final status are not logged |
| R-21 | Token purge | Safe. An error on the first table skips the other two until the next run |
| R-22 | Lock timeout, V23 | Valid and persistent. A migration on a busy database is also subject to the 10 s wait |

## Claims of the sprint documents

| Claim | Verdict |
|---|---|
| S-1 reduced | Confirmed (R-7, R-8 as caveats) |
| S-2 closed | Confirmed: double extensions, trailing dot or space, case, `a.pdf:x.bat` and look-alike characters all fail closed |
| S-3 closed in code | Partly (R-10) |
| S-4 closed for company administrators | Partly (R-3) |
| S-5 closed | **Not supported** (R-1, R-2) |
| S-6 closed | Partly (R-16) |
| S-7 reduced | Partly; R-5 and R-6 were not in the list of what remains |
| S-13, S-14, S-16 closed | Confirmed |
| S-15 closed; "a deployment cannot start with a development profile" | **Not supported** (R-4) |
| S-24 closed | Partly (R-11) |
| Metrics restricted, nothing else exposed | Partly (R-18) |
| DB-1 prepared, DB-2 done for 4, DB-3 reduced, DB-4 reduced | Confirmed |

## Undocumented or out-of-scope changes

- `SecurityConfiguration`: asynchronous dispatches are permitted. It belongs to the messaging work and is a standard rule, but no sprint document mentions it.
- The test profile lowers the lock timeout to 5 s; not mentioned.
- `SessionTimeouts.java` was listed as changed and is not.
- Otherwise none.

## CI: expected on the first run

No certain breaker found in the workflow. Risks: `npm audit` strictness (R-19); first run on SQL Server 2022; a date computed in the Tunis time zone in the matrix test may fail between 23:00 and 24:00 UTC (hypothesis); demo-user seeding under the new 30 s transaction limit on a slow runner (hypothesis); the job runs as `sa` and so never exercises the runtime login.

## Not checked

Nothing was executed. Internal messaging code. `scripts/db/enable-read-committed-snapshot.sql`. Arabic translation quality. Each remaining string placed in audit JSON.

---

# Re-review of R-1 to R-4 after remediation (2026-11-17)

**How it was done.** A new separate review context, with no access to the implementation's reasoning, given the four findings and the author's description of the fixes, and told to break them. Read-only, nothing executed. Each new finding was then fixed and tested by the developer, or left open as stated below. The developer's tests are listed in `docs/sprints/sprint-01-remediation-report.md`.

## Verdicts of the re-review, and what followed

| Finding | Re-review verdict | Gap found | After the second correction |
|---|---|---|---|
| R-1 Delegation | Partly fixed | A supplier contact could still be a named approver in a rule (RR-2) or receive an approver role (RR-5); an override delegate from a holding company could receive another company's approvals (RR-8) | Fixed and tested (RR-2, RR-5, RR-8). Residual RR-4 below |
| R-2 Self-granted approval authority | Partly fixed | A company administrator could write themselves, or an account they had just created, as named approver of a rule and activate it alone (RR-1) | Fixed and tested |
| R-3 Peer administrators | **Fixed** | — | — |
| R-4 Startup guard | Partly fixed | A server with its own local database, started with the developer command and no declaration, was still accepted (RR-6). A local-looking JDBC URL with `serverName=` could point elsewhere (RR-9, hypothesis). A zero four-eyes period was not refused | Fixed and tested: development profiles now also require listening on 127.0.0.1 without a declaration; `serverName=`, `server=`, `failoverPartner=` and a separate pool URL are refused; the four-eyes period must be at least one day when deployed |

## New findings of the re-review

| ID | Severity | Finding | Status |
|---|---|---|---|
| RR-1 | Major | Company administrator names themselves or a fresh account as `NAMED_USER` approver | **Fixed**: refused at rule creation and edit (`approver-is-administrator`, `approver-created-by-administrator`) |
| RR-2 | Major | Supplier contact as named approver; approves through the e-mail link | **Fixed**: refused in rule validation; also excluded at routing and eligibility, for rules that already exist |
| RR-3 | Major (owner decision) | The four-eyes rule covers approval roles only; a fresh account may receive `BUYER`, `RECEIVER`, `PURCHASING_MANAGER`, `REQUESTER` from its creator | **Open, owner decision.** The code comment that overstated the protection is corrected |
| RR-4 | Minor | The four-eyes rule lasts 30 days and does not cover accounts created before V24 | **Open, residual**, documented in `README.md`. A zero period is now refused when deployed |
| RR-5 | Minor | Approver role granted to a supplier contact account | **Fixed**: no internal role for a supplier contact account |
| RR-6 | Major | Developer command on a server with a local database | **Fixed** (listening address) |
| RR-7 | Minor | A company administrator could grant a role to an account of another company, which signed that user out and returned their details | **Fixed**: 404 before any change (ADR-007) |
| RR-8 | Minor | Override delegate from another company of the absent approver | **Fixed** at routing: the task stays with the approver, marked `ABSENCE_UNRESOLVED` |
| RR-9 | Minor (hypothesis) | `localhost;serverName=remote` | **Fixed** (refused without a declaration) |
| RR-10 | Note | Absence recorded for a peer administrator | Open, note |
| RR-11 | Note | `USER_UPDATED` audit event has no before and after values | Open, note |
| RR-12 | Note | Exchange rates by any company administrator | Open: ADR-017, direction approved, not implemented |
| RR-13 | Note | In the development profile any administrator can move the clock | Open: development only; RR-6 keeps development instances off the network |
| RR-14 | Note | Documents described the old guard | **Fixed**: `README.md`, `CLAUDE.md`, `AGENTS.md` |
| RR-15 | Note | The two new absence error codes are not shown by any screen (no administrator absence screen exists) | Open, note |

**Residual by design:** two administrators acting together (one creates, the other grants) can still create an approver. That is the purpose of the rule: it requires a second person.

## Not re-reviewed after the second correction

The second correction (RR-1, RR-2, RR-5 to RR-9) was verified by the developer's tests, not by a third independent pass.

---

# Independent review of RR-3 and re-check of R-1 to R-4 (2026-11-17)

**How it was done.** A fresh separate context, read-only, nothing executed, given the owner's eight RR-3 requirements and the author's claimed design, and asked to break it. The developer then fixed the code-only findings and wrote tests; the three findings that change business rules wait for the owner.

**Verdicts.** R-1, R-2, R-3, R-4: **fixed** (R-4 core; two settings remain unchecked, see F-R4 below). RR-1, RR-2, RR-5 to RR-9: still fixed. Owner requirements: 5, 6, 7 met; 1, 2, 3, 4, 8 **partly** met before the fixes below.

| ID | Severity | Finding | Status |
|---|---|---|---|
| F-1 | Major, single person | An administrator can create accounts with mailboxes they control and ordinary roles (REQUESTER, ENTER_ON_BEHALF), then (a) approve a request submitted by such an account, if they hold an approval role given by someone else, or (b) use two such accounts as recorder and confirmer of a receipt | **Owner decision** (identity of new accounts) |
| F-2 | Major, single person | A global administrator can alone create an administrator identity whose mailbox they control, then use it as the "second administrator" | **Owner decision** |
| F-3 | Major, single person | A global administrator could set a temporary password on any approver, buyer or administrator and act as them | **Fixed**: refused (`use-reset-link`) for accounts holding a sensitive, ADMIN or GLOBAL_ADMIN role |
| F-4 | Major | Supplier-contact accounts had no recorded creator; once their role was removed they were employee accounts outside the four-eyes rule | **Fixed**: the inviting buyer is recorded as creator; SUPPLIER_CONTACT cannot be removed through user administration |
| F-5 | Major | Procurement separation of duties: one buyer records, attests and pays an invoice; a purchasing manager can assign themselves to their own request; a buyer can receive their own order | **Owner decision** (audit S-9, already pending) |
| F-6 | Minor | A named approver could belong to another company | **Fixed**: must belong to the rule's company (`not-in-company`) |
| F-7 | Minor | `removeGrant` on another company's account answered 403, revealing it | **Fixed**: 404 |
| F-8 | Minor | The creator could reactivate an account holding a sensitive role | **Fixed**: refused (`four-eyes`) |
| F-9 | Minor | An administrator of the absent approver's home company could redirect that approver's approvals in other companies | **Fixed** at routing: an override applies only to requests of companies its recorder administers |
| F-10 | Minor | Administrator listings included every supplier contact and global administrator | **Fixed**: only GROUP_MANAGEMENT among group-wide roles |
| F-11 | Note | Outdated texts; two error codes without translation | **Fixed** (EN, FR, AR; documents) |
| F-12 | Note | Explicit task delegation did not check the creator | **Fixed**; not covered by a dedicated test |
| F-13 | Note | Two administrators each granting the other's creations | Accepted residual (collusion of two people) |
| F-R4 | Note | `APP_ALLOW_UNSCANNED_DOWNLOADS=true` and empty trusted proxies are not refused in a deployed environment | Open; documented in `README.md` |

**Test weakness found and fixed:** no test showed a second administrator successfully granting BUYER, RECEIVER or PURCHASING_MANAGER; now covered.

# Independent review of F-1, F-2 and F-5 (2026-11-17, fourth review)

**Scope:** the owner-approved business security decisions, as implemented (uncommitted tree). **Method:** a separate,
read-only agent given the requirements and the key files, without the implementation's reasoning; code reading only,
no build or test. **Result:** 0 Blocker, 5 Major, 3 Minor, 1 Info. Every requirement not listed below was verified as
met with code references.

| # | Severity | Finding | Outcome |
|---|---|---|---|
| 1 | Major | A purchasing manager could source, award, order and handle logistics on their own request (only *assignment* refused the requester) | **Fixed.** The requester or the person who entered the request is refused (`buyer-is-requester`) in `SourcingService` (opening and every sourcing action), `OrderingService` (award, purchase order) and `LogisticsService`. Test `aRequesterWhoIsAlsoPurchasingManagerCannotSourceTheirOwnRequest` |
| 2 | Major | The same person could attest an invoice and pay it; the payer need not be independent of the purchase | **Partly fixed.** Payment is refused to the buyer of the order: the assigned buyer and whoever issued the purchase order (`payment-not-independent`). Attester ≠ payer is **not** enforced: with recorder ≠ attester and recorder ≠ payer it needs three people per invoice, which blocks a company with one buyer and one manager (the demo has exactly that). Owner option. Test `aPurchasingManagerWhoBoughtTheOrderNeitherAcceptsItsShortfallNorPaysIt` |
| 3 | Major | A two-step chain (A creates B, B creates C) let A activate or empower C | **Fixed.** "Created by" follows the creation chain, five levels (`UserDirectory.creationChain`), for activation, sensitive and administrator roles, named approvers and delegations. Test `theWholeCreationChainCountsAsTheCreator` |
| 4 | Major | The creator could set a temporary password on a new account before a second administrator activated it, then use it | **Fixed.** No temporary password on an inactive account, nor on one in the actor's creation chain (`use-reset-link`). Tests `aTemporaryPasswordIsNeverTheFirstCredentialNorChosenByTheCreator`, browser `sprint-01-hardening.spec.ts` |
| 5 | Major | The buyer, also a manager, could accept a shortfall or waive a quantity at closure on their own order | **Fixed.** `acceptShortfall` and a closure waiver refuse the buyer of the order (`buyer-cannot-receive`); `requireNotBuyer` also covers the purchase order issuer. Test above |
| 6 | Minor | Roles given in the creation call were not audited | **Fixed.** `ROLE_GRANTED` written for each. Test `rolesGivenAtCreationAreAudited` |
| 7 | Minor | Supplier-contact invitations create active accounts without a second person | **Documented** in README as an exception: the account only ever holds `SUPPLIER_CONTACT`, which cannot be removed or combined with an internal role |
| 8 | Minor | The creator can never reactivate an account they created (stricter than required) | **Documented** in README; kept deliberately |
| 9 | Info | The second-administrator bootstrap is not exercised by a test; an existing account at that address would be promoted | **Accepted**, documented: the test context runs the demo profile, where the bootstrap does not run; the operator setting the variables is trusted |

**Residual:** a sibling pair (two accounts created by the same person) can still empower each other only through a
second, deceived administrator's activation; two administrators colluding remain outside any four-eyes control. The
fixes were verified by tests, not by a fifth review.
