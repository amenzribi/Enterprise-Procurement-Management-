# 03. Security assessment

Authorized, non-destructive, static review of the backend (`BackEnd/src/main/java/com/epi/procurement`), configuration and frontend, against OWASP ASVS and the OWASP API Security Top 10. No attack was run against any environment.

**How to read the status.** *Verified* means the code that proves the point was read; for findings S-1 to S-14 the lead re-read the cited lines after the review agent reported them. *Hypothesis* means the finding depends on deployment or needs a running system. **This review does not show that the application is secure.** It covers what can be read in code; section 5 lists what was not tested.

## 1. Summary

The security foundations are careful: session handling, CSRF, token design, parameterised SQL, central request visibility and safe file storage all hold up to reading. No endpoint was found without an authentication or scope check, and no injection or secret was found.

The serious gaps are in three places:

1. **Files:** uploads are never scanned yet are marked clean, and a file's name is not tied to its real type.
2. **Privileged users:** a company administrator can take over another account or route approvals to themselves, and several administrative changes leave no audit record. For a procurement system this is the main fraud path.
3. **Deployment-dependent protections:** rate limits can be bypassed behind a common proxy setup, and nothing stops a production instance from running with the development profile.

| Severity | Count | Findings |
|---|---|---|
| Critical | 0 | — |
| High | 5 | S-1, S-2, S-3, S-4, S-5 |
| Medium | 12 | S-6 to S-17 |
| Low | 9 | S-18 to S-26 |

No finding is rated Critical because none allows an anonymous or ordinary user to read or change another company's data. S-4 and S-5 need an administrator account; they are rated High because the person best placed to commit procurement fraud is an insider with that role.

## 2. Findings

### S-1. Uploaded files are never scanned and are recorded as clean — High — Verified

- **Location:** `document/AttachmentScanner.java:21-30` (the only implementation, an unconditional `@Component`, logs "Attachment not scanned" and returns `CLEAN`); `document/Attachments.java:155` (the insert writes the literal `"CLEAN"`); `Attachments.java:167` (download refused unless the status is `CLEAN`, which it always is).
- **Explanation:** the scan port exists but has no real adapter in any profile, and the stored status does not come from the scanner.
- **Business impact:** a requester, or anyone with a requester's account, can deliver malware to approvers, buyers and finance through a document the system presents as checked.
- **Recommendation:** restrict the no-op scanner to the `dev` and `test` profiles; add an adapter to the client's antivirus (ClamAV `clamd` INSTREAM is the usual choice and needs an ADR under ADR-003 as new infrastructure); store the scanner's result; refuse to start outside dev without a real scanner.
- **Complexity:** M.
- **Verification:** start without the dev profile and no scanner: startup fails. Upload the EICAR test file: 422.

### S-2. A file's extension is not tied to its detected type — High — Verified

- **Location:** `document/ContentSniffer.java:26-42` (PDF, PNG, JPEG, GIF and WebP are accepted on magic bytes alone, whatever the extension); the user's file name is kept and returned on download (`Attachments.java:214-239`, `AttachmentController.java:70-73`).
- **Explanation:** a file named `invoice.bat` whose first bytes are `%PDF-` is accepted as a PDF and later downloaded as `invoice.bat`. Windows runs the script lines that follow the header when it is opened. `X-Content-Type-Options: nosniff` does not help: the file is executed from disk, not rendered by the browser.
- **Business impact:** code execution on a colleague's workstation through a normal-looking attachment.
- **Recommendation:** an allow-list from detected type to permitted extensions; refuse a mismatch, or replace the extension with the one matching the detected type.
- **Complexity:** S.
- **Verification:** upload `%PDF-` followed by script text as `x.bat`: 415.

### S-3. Rate limits can be bypassed behind a proxy that appends to X-Forwarded-For — High — Verified in code; exploitability depends on deployment (Hypothesis)

- **Location:** `platform/web/ClientAddressResolver.java:70` (`return xff.split(",")[0].trim();`).
- **Explanation:** when the direct peer is a trusted proxy, the code takes the left-most address of the header. Most proxies (nginx `$proxy_add_x_forwarded_for`, IIS ARR, cloud load balancers) append the real client address to whatever the client sent, so the left-most value is the attacker's. That address keys the login throttle, the password-reset limits, the approval-link and quote-link limits and the assistant limit.
- **Business impact:** unlimited password guessing and token probing once the application is behind a reverse proxy, which the UAT deployment will be.
- **Recommendation:** read the header from the right and take the first address that is not a trusted proxy, or use Tomcat's `RemoteIpValve` (`server.forward-headers-strategy=native` with `internal-proxies`) and remove the custom parsing.
- **Complexity:** S.
- **Verification:** with a trusted proxy configured, 60 failed sign-ins each with a different forged header value from one client: blocked after the per-address limit.

### S-4. A company administrator can take over any account of their company — High — Verified

- **Location:** `identity/UserAdminService.java:201-219` (`setTemporaryPassword`: the administrator chooses the password; only its length is checked, not the password policy); `UserAdminService.java:314-327` (`assertCanManage` refuses only GLOBAL_ADMIN targets and accounts with grants outside the actor's companies).
- **Explanation:** an administrator sets a temporary password on a budget approver or a peer administrator, signs in as them, changes the password and acts. The audit trail shows `TEMPORARY_PASSWORD_SET` by the administrator, then actions attributed to the victim.
- **Business impact:** separation of duties is void: the person who administers users can approve as anyone. Approvals lose evidential value.
- **Recommendation:** remove administrator-chosen passwords and keep the reset link, which already exists (`UserAdminService.java:185-195`) and never shows the secret to the administrator; forbid security actions by a company administrator on another administrator; notify the account owner by e-mail on every reset.
- **Complexity:** S.
- **Verification:** integration test: ADMIN calling `temporary-password` gets 403 or 404 for a peer ADMIN; the endpoint is gone or restricted to GLOBAL_ADMIN.

### S-5. An administrator can route approvals to themselves, without any audit record — High — Verified

- **Location:** `approval/Absences.java:84-97`: the delegate must only belong to the same group; `adminOverride` skips the shared-role check; the only identity check is delegate ≠ absent user. `approval/ApproverResolver.java:139-140` accepts an override delegate; `approval/ApprovalEngine.java:277-302` then checks only that the actor is the assignee. `Absences.java` contains no audit call (0 matches for "audit").
- **Explanation:** an administrator records an absence for the budget approver, names themselves delegate with the override flag, and receives that approver's tasks for the period.
- **Business impact:** an administrator can approve their own or an accomplice's requests; nothing in the audit chain records how the task reached them.
- **Recommendation:** refuse delegate = acting administrator; with override, still require the delegate to hold a role in the absent person's companies; write `ABSENCE_CREATED` and `ABSENCE_CANCELLED` audit events in the same transaction; show delegated decisions as such on the request (the task already stores `original_user_id`).
- **Complexity:** S.
- **Verification:** `POST /admin/absences` with the administrator as delegate: 422; an audit row exists for every absence created or cancelled.

### S-6. Approval rules, exchange rates and absences change without audit — Medium — Verified

- **Location:** `approval/ApprovalRulesAdmin.java:91-197` (create, update, activate, retire, add rate), `organization/ExchangeRates.java`, `approval/Absences.java`: no reference to the audit log in any of the three files.
- **Explanation:** ADR-006 and `CLAUDE.md` require an audit event for every state change. Rule versions keep `created_by`, which is weaker than the hash chain and does not cover rates or absences.
- **Business impact:** thresholds and rates decide who must approve. They can be lowered, used and restored without a tamper-evident trace.
- **Recommendation:** audit each change with before and after values in the same transaction.
- **Complexity:** S.
- **Verification:** integration tests assert one audit event per change.

### S-7. Company administrators see and change group-wide data — Medium — Verified

- **Location:** `approval/ApprovalAdminController.java:55-63` (all companies of the group), `:68-80` with `identity/UserDirectory.java:98-101` (users of the whole group, with e-mail; the company filter is optional and not checked against the administrator's companies); `approval/Absences.java:56` (every absence of the group); `approval/ApprovalRulesAdmin.java:155-196` (any ADMIN adds an exchange rate used by all companies).
- **Explanation:** ADMIN became company-scoped in V17, but these four paths still use the group as the boundary. This contradicts ADR-007.
- **Business impact:** a subsidiary's administrator reads names, e-mails, roles and absence reasons of other subsidiaries, and can change the rates that decide approval thresholds across the group.
- **Recommendation:** filter by the administrator's companies unless GLOBAL_ADMIN; reserve exchange-rate writes for GLOBAL_ADMIN; add the cross-company tests that `CLAUDE.md` requires for every endpoint.
- **Complexity:** S.

### S-8. Supplier quote links can be reused until they expire — Medium — Verified

- **Location:** `sourcing/SourcingService.java:372-386` (`requireValidToken` checks expiry only, never `usedAt`); `:195` marks the token used after saving; `:46` lifetime 14 days; the save replaces the supplier's existing quote (`:319-320`, as reported by the review agent; not re-read by the lead).
- **Explanation:** whoever holds the link can submit again and overwrite the quote while the RFQ is open.
- **Business impact:** a quote can be changed after the fact, including by someone who received a forwarded e-mail. Comparison and award may rest on a figure that was replaced.
- **Recommendation:** decide the rule. Either single use (refuse when `usedAt` is set), or revisions allowed with every version kept, audited and shown to the buyer. Never overwrite a quote the buyer entered.
- **Complexity:** S.
- **Verification:** second submission with the same token: 404, or a new audited version.

### S-9. No separation of duties on invoices — Medium — Verified

- **Location:** `receipt/ReceiptService.java:279-294` (attest match), `:296-300` (mark paid), `:625-631` (`requireBuyer`: any BUYER or PURCHASING_MANAGER of the buying company).
- **Explanation:** recording an invoice, attesting its three-way match and marking it paid all need the same role, and nothing compares the people. Receipts, by contrast, require a second person (commit `36c69d8`).
- **Business impact:** one buyer can take a fictitious invoice to "paid".
- **Recommendation:** the attester must differ from the recorder; marking paid needs PURCHASING_MANAGER or a finance role. This is a business rule: confirm with the client before changing.
- **Complexity:** S.

### S-10. Sign-in has no account-wide limit, no second factor and a thin password policy — Medium — Verified

- **Location:** `identity/LoginThrottle.java:47,138-143` (blocking keyed by e-mail + address and by address); `identity/PasswordPolicy.java:15-36` (12 characters, one letter, one digit; no breached-password list, no maximum length); `identity/PasswordService.java:89-97` (change-password verifies the current password without throttling). No MFA code exists.
- **Explanation:** an attacker using many addresses can guess one account without ever being blocked. BCrypt ignores input beyond 72 bytes; what happens with a longer password here is untested.
- **Business impact:** accounts of approvers and administrators are protected by a password alone. STATUS.md already lists "MFA verification" as an open release gate.
- **Recommendation:** per-account progressive delay independent of address, with an alert; maximum length 128 and a breached-password check; throttle change-password; MFA (TOTP) at least for ADMIN, GLOBAL_ADMIN and approvers before production, or single sign-on with the client's identity provider, which would bring MFA with it.
- **Complexity:** M (L with MFA or SSO).

### S-11. One-tap e-mail approval is a single factor — Medium — Verified (by design)

- **Location:** `approval/ApprovalLinks.java:96-146`; lifetime 72 h (`application.yml`, `app.approval.link-ttl`).
- **Explanation:** possession of the e-mail approves as the user, without a session. Tokens are strong, hashed, single-use and re-checked against eligibility, so the design is sound for what it is.
- **Business impact:** a forwarded e-mail or a shared mailbox approves spend of any amount.
- **Recommendation:** an amount ceiling above which the link opens the sign-in page instead; a shorter lifetime; a configuration switch so a client can disable the feature.
- **Complexity:** M.

### S-12. The audit chain can be recomputed by anyone who controls the database — Medium — Verified

- **Location:** `audit/AuditHasher.java:61-62` (plain SHA-256, no key); `audit/AuditLog.java:92` (`verifyAll` is not called by any job); no anchoring code (only a comment). See also report 05, DB-1: the application's database login can alter the schema.
- **Explanation:** triggers stop accidental and application-level changes. A database owner can disable them, edit events and recompute every hash; verification then passes.
- **Business impact:** the audit log is tamper-evident against the application, not against a database administrator, which is the case an external auditor asks about.
- **Recommendation:** separate runtime and migration logins (DB-1); export chain heads daily to storage the application cannot rewrite; run `verifyAll` on a schedule and alert on failure. An HMAC key held outside the database is an alternative to anchoring.
- **Complexity:** M.

### S-13. Audit payloads are built by string concatenation — Medium — Verified

- **Location:** `identity/UserAdminService.java:91` (`"{\"email\":\"" + email + "\"}"`); e-mail validation checks only for an `@` (`:365-370`). The same pattern with numeric values is used elsewhere and is harmless there.
- **Explanation:** a quote in the e-mail breaks or extends the JSON stored in the audit event.
- **Business impact:** misleading or unparseable audit records.
- **Recommendation:** build payloads with the JSON mapper; validate e-mail addresses properly.
- **Complexity:** S.

### S-14. CSV exports allow formula injection — Medium — Verified

- **Location:** `dashboard/ErpExportService.java:149-158` (`csv()` quotes commas, quotes and newlines only).
- **Explanation:** a request title starting with `=`, `+`, `-` or `@` is executed as a formula when the export is opened in Excel.
- **Business impact:** data exfiltration or command execution on a finance user's workstation, triggered by any requester.
- **Recommendation:** prefix such values with an apostrophe (also for a leading tab or carriage return).
- **Complexity:** S.
- **Verification:** a request titled `=1+1` appears as text in the export.

### S-15. Nothing prevents the development profile from running in production — Medium — Verified

- **Location:** `application-dev.yml:9-22` (insecure cookies, Swagger, `/api/v1/testing`, adjustable clock); `approval/TestingController.java:24-25,46,54` (enabled by profile `dev` and a property; any company ADMIN may shift the global clock, which governs token expiry, SLAs and session timeouts); `platform/config/DatabaseConfigurationGuard.java:17-33` checks only `DB_URL`.
- **Explanation:** the dev profile is how every instance has been run so far, including the review and QA instances (`dev,demo`). A UAT or production start with the same command line inherits all of it.
- **Business impact:** session cookies sent over plain HTTP, API documentation exposed, and an administrator able to move time.
- **Recommendation:** a startup guard that refuses `dev` when the public base URL is HTTPS or a production flag is set; a dedicated `uat` profile; GLOBAL_ADMIN for the testing hooks.
- **Complexity:** S.

### S-16. E-mail defaults to files on disk that contain live links — Medium — Verified

- **Location:** `application.yml` (`app.mail.mode: ${MAIL_MODE:file}`); `notification/MailTransport.java:105-118` (any value other than `smtp` or `failing` writes complete messages, including password-setup and approval links, to `storage/mail`).
- **Business impact:** a deployment that omits `MAIL_MODE` sends no e-mail and leaves bearer links readable on the server.
- **Recommendation:** no default outside dev; unknown values fail startup.
- **Complexity:** S.

### S-17. Imports are unbounded and loosely parsed — Medium — Verified by the review agent; not re-read by the lead

- **Location:** `importdata/ImportService.java:84-117` (no row limit; one transaction), `:185` (`split(",")`, no quoted fields), `:158-159` (a purchasing manager of any company can create group-wide SHARED suppliers).
- **Business impact:** a large file holds locks for a long time (see report 05, DB-3); a comma in a name corrupts the row; one company's manager adds suppliers for the group.
- **Recommendation:** row cap, a proper CSV parser (hand-written state machine or the JDK; no new dependency needed), SHARED restricted to group-level roles.
- **Complexity:** S.

### Low severity

| ID | Finding | Location | Status |
|---|---|---|---|
| S-18 | Security headers rely on Spring defaults: no Content-Security-Policy or Referrer-Policy on API responses; nothing sets headers for the static frontend, whose hosting configuration does not exist yet | `identity/SecurityConfiguration.java` (no `headers()` customisation); `FrontEnd/src/index.html` | Verified (static); effective headers untested |
| S-19 | Live stream stays open after a password change or session revocation, up to 30 min. It carries identifiers only and each delivery re-checks the recipient | `messaging/InternalMessageStream.java:144-155` | Verified |
| S-20 | Rate limiter keeps one entry per key and sweeps only above 20,000 entries; fixed windows allow double bursts; authenticated writes, search and exports have no limit; no JSON body-size limit beyond the server default | `platform/web/RequestRateLimiter.java:33-38` | Verified |
| S-21 | Failed sign-ins are logged with an unsalted, truncated SHA-256 of the e-mail, reversible against a staff list | `identity/LoginThrottle.java:98-103` | Verified |
| S-22 | Forgot-password does more work for existing accounts; response time may reveal which e-mails exist | `identity/PasswordService.java:79-85` | Hypothesis |
| S-23 | Demo password printed at startup in demo environments | `identity/DemoUserSeeder.java:121-128` | Verified; acceptable for demo only |
| S-24 | Supplier search does not escape `%` and `_` | `supplier/SupplierService.java:56` | Verified |
| S-25 | Unsaved form content is kept in `sessionStorage` (this tab, per user). It is cleared at sign-out (`core/auth/auth.service.ts:102`); it remains readable on an unlocked shared workstation while the tab is open | `FrontEnd/src/app/core/form-snapshots/form-snapshot-storage.ts` | Verified; accepted risk to confirm with the client |
| S-26 | Development tooling has 11 known advisories (1 high in `vite`, used by the dev server and tests). Production dependencies have none | `npm audit` on 2026-11-14 | Verified |

## 3. Authorization model and endpoint coverage

There is **no method-level security** (`@PreAuthorize` or similar). URL rules separate anonymous from authenticated callers only; every role and scope check is in service code reached through `CurrentUserProvider.require()`. Three filters run on every authenticated request: `CurrentUserRefresh` (reloads roles and credential version), `MustChangePasswordFilter`, and `SupplierPortalRestrictionFilter` (supplier contacts may call only the portal endpoints and a few self-service ones).

This design is sound but depends on each service method remembering its check. All 180 endpoints are listed in report 10. Coverage by this review:

| Area | Where the check lives | Traced |
|---|---|---|
| Sign-in, CSRF, password change and reset | `SecurityConfiguration.java:73-77`; `SignInService`, `PasswordService` | Yes |
| User administration | `UserAdminService.java:307-333`, grant rules `:240-274` | Yes (S-4) |
| Requests: list, read, edit, submit, discard | `request/ScopedRequests.java`, `RequestAccessPolicy.java:42-146` | Yes |
| Request administration | `RequestAdminService.java:92-123` | Partly: role-per-action mapping not traced |
| Approval decisions | `ApprovalEngine.java:277-302` (assignee = actor) | Yes |
| Approval read views, awaiting-circuit | `ApprovalQueries` | Not line by line |
| Approval links (public) | `ApprovalLinks.java:117-146` | Yes |
| Approval administration, absences, rates | `Absences`, `ApprovalRulesAdmin` | Yes (S-5, S-6, S-7) |
| Attachments | `document/Attachments.java:94-203` | Yes (S-1, S-2) |
| Sourcing, quote links (public) | `SourcingService.java:78-123,372-410` | Yes (S-8) |
| Ordering | `OrderingService.java:65-328` | Yes |
| Receipts, invoices, closure | `ReceiptService.java:85-637` | Yes (S-9) |
| Logistics | `LogisticsService.java:123-313` | Yes |
| Purchasing queue and assignment | `PurchasingService.java:403-420` | Partly: not mapped per endpoint |
| Suppliers | `SupplierService.java:43-49,193-204` | Yes |
| Dashboards | `DashboardService` | Not line by line; exports yes |
| Imports | `ImportService.java:62-65,136,178-182` | Yes (S-17) |
| Organisation administration | `OrganizationAdmin.java:41-99,180-195` | Yes |
| Supplier messaging and portal | `MessagingService.java:106-168,300-391` | Yes |
| Internal messaging | `InternalMessagingService` (single gate `load`) | Yes, and by 15 integration tests |
| Assistant | `AssistantService.java:212-214,298-305` | Yes |
| Testing hooks | `TestingController.java:24-25,46,54` | Yes (S-15) |

No endpoint was found with no check at all. The three "partly" and "not line by line" rows are the gap to close with a cross-company test sweep (section 5).

## 4. Checked and found sound

- **Sessions:** id rotated and CSRF token replaced at sign-in; idle 30 min and absolute 8 h enforced by the application; sign-out invalidates the session; sessions end on password change, reset, deactivation or role change; roles reloaded on every request (`SecurityConfiguration.java:110-114`, `identity/SessionTimeouts.java:39-69`, `identity/CurrentUserRefresh.java:53-72`).
- **Cookies:** `HttpOnly`, `SameSite=Lax`, `Secure` by default (`application.yml`).
- **CSRF:** enabled for every state-changing call, including anonymous ones; no exemptions.
- **CORS:** none configured, so browsers refuse cross-origin calls; the frontend must be served from the API's origin (report 08).
- **Sign-in:** uniform failure answer; a dummy hash is compared for unknown accounts; hashing runs outside any transaction (`identity/SignInService.java:45-61`).
- **Password storage:** Spring's delegating encoder with bcrypt (`SecurityConfiguration.java:129`).
- **Link tokens:** 256-bit random, carried in the URL fragment and posted in the body (so they do not reach server logs or the `Referer` header), stored as SHA-256 only; reset and approval tokens are single-use; link responses are `no-store` and `no-referrer`.
- **Outbox:** e-mail bodies hold placeholders resolved at send time, so no token is stored in the outbox table.
- **SQL:** parameterised everywhere; the three dynamic fragments are an integer, a column chosen from two constants and a constant filter (re-verified by the lead; see report 05).
- **Request visibility:** one policy (`RequestAccessPolicy`) used by the other modules before they act on orders, receipts, shipments, RFQs and attachments.
- **File storage:** random storage keys, path containment check, sanitised names, `attachment` disposition, size and count limits under a lock; SVG, HTML and macro-enabled Office files refused.
- **Supplier portal restriction:** exact-match allow-list that fails closed; the default strict firewall rejects encoded slashes, `;` and `..` before it.
- **Async dispatch permitted** (`SecurityConfiguration.java:72`): safe, an async dispatch only follows an authorised request.
- **Errors:** RFC 9457 answers without stack traces or internal messages; actuator exposes `health` and `info` only, details to ADMIN.
- **Secrets:** none found in tracked source, configuration, documents or scripts; the review password used on 2026-11-13/14 is in no file (searched, tracked and untracked).
- **Frontend:** no `innerHTML`, `bypassSecurityTrust` or `eval`; return URLs validated against open redirects (`core/auth/safe-return-url.ts`); `localStorage` holds theme, language, sidebar state and last-activity time only.
- **Assistant:** no external model call is possible in the current code (live mode throws); retrieval goes through the scoped queries.

## 5. Not tested

| Check | Why it matters | How to do it |
|---|---|---|
| Backend dependency vulnerabilities | Only the frontend was scanned | OWASP Dependency-Check or `osv-scanner` on `BackEnd/pom.xml`, in CI |
| Dynamic testing | Static reading misses runtime behaviour | OWASP ZAP baseline and authenticated scan against UAT, never production |
| Cross-company sweep of every `{id}` route | `ApprovalQueries`, `DashboardService`, `PurchasingService`, `RequestAdminService` were not traced line by line | Automated test: two companies, every route, expect 404 |
| Effective response headers and TLS | No hosting configuration exists yet | `curl -I`, SSL Labs or `testssl.sh` on the UAT URL |
| Proxy behaviour for S-3 | Depends on the chosen proxy | Test on UAT with forged headers |
| Passwords longer than 72 bytes | BCrypt limit | Unit test |
| Timing differences (S-22) | Enumeration | Measure 200 requests for existing and unknown addresses |
| Penetration test | Listed as an open release gate in `STATUS.md` | Independent tester before production |
| Restore drill and MFA verification | Open release gates in `STATUS.md` | See reports 08 and 09 |
