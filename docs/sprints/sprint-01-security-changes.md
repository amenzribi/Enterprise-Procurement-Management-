# Sprint 1: security changes

What changed, why, and the test that proves it. Finding ids are those of `docs/audit/03-security-assessment.md`. Test results are in `sprint-01-test-results.md`. Paths are under `BackEnd/src/main/java/com/epi/procurement/` unless stated.

**Status words.** *Closed*: the behaviour is changed and a test proves it. *Reduced*: part of the finding is fixed; what remains is stated. *Open*: not changed in this sprint.

> **Correction, 2026-11-16.** The independent review and live probes showed that two statements below were wrong: **S-5 is not closed** (an administrator can still obtain another person's approvals, findings R-1 and R-2) and **S-15 is only partly closed** (R-4). S-3, S-4, S-6, S-7 and S-24 are partly closed. The current status of every item is in `sprint-01-validation-report.md`.

## Summary

| Finding | Severity | Status | Proof |
|---|---|---|---|
| S-1 Files never scanned, recorded clean | High | **Reduced**: nothing is recorded or served as clean without a scan; no scanner product yet (gate G3-a) | `ScanPolicyTests`, `ApprovalIntegrationTests.attachmentsAreSniffedLimitedAndScoped` |
| S-2 Extension not tied to type | High | **Closed** | `ContentSnifferTests`, same integration test |
| S-3 Rate limits bypassed behind a proxy | High | **Closed in code**; behaviour behind a real proxy still to verify at deployment | `ClientAddressResolverTests` |
| S-4 Administrator takes over an account | High | **Closed** for company administrators; a global administrator can still set a temporary password (decision G2-a) | `AdministratorLimitsIntegrationTests` |
| S-5 Administrator routes approvals to themselves | High | **Closed** | `AdministratorLimitsIntegrationTests` |
| S-6 Rules, rates, absences without audit | Medium | **Closed** | `AdministratorLimitsIntegrationTests` |
| S-7 Company administrators see group-wide data | Medium | **Reduced**: listings are scoped; exchange-rate writes still open to company administrators (decision G2-c) | `AdministratorLimitsIntegrationTests` |
| S-13 Audit payload by concatenation | Medium | **Closed** for the three user-supplied values | `AuditRecordJsonTests`, `AdministratorLimitsIntegrationTests` |
| S-14 CSV formula injection | Medium | **Closed** | `ErpExportCsvTests` |
| S-15 Development profile in production | Medium | **Closed** | `DeploymentConfigurationGuardTests`, `StartupConfigurationTests` |
| S-16 E-mail defaults to files | Medium | **Closed** | same |
| S-24 Supplier search wildcards | Low | **Closed** (no dedicated test) | code review only |
| S-8, S-9, S-10, S-11, S-12, S-17 to S-23, S-25, S-26 | | **Open** | see `sprint-01-final-report.md` |

## 1. Files

### S-2: the extension must match the detected type

- `document/ContentSniffer.java`: a type detected from its signature (PDF, PNG, JPEG, GIF, WebP) is accepted only under its own extensions. A script named `invoice.bat` that starts with `%PDF-` is refused with 415 `file-type-not-allowed`. Office and text types were already tied to their extension.
- Existing files are not touched and still download.
- **Behaviour change for users:** a PDF or image uploaded with no extension or a wrong one is now refused.

### S-1: scanning fails closed

- `document/AttachmentScanner.java`: the no-op scanner that reported every file clean is removed. The interface stays as the port for a real antivirus.
- `document/ScanPolicy.java` (new): the rules in one place.

| Situation | Stored status | Download |
|---|---|---|
| Scanner reports clean | `CLEAN` | Yes |
| Scanner reports infected | Upload refused, 422 `file-infected` | — |
| Scanner fails or returns nothing | Upload refused, 503 `scan-unavailable` | — |
| No scanner configured | `PENDING` | **No (quarantine)**, unless `app.attachments.allow-unscanned-downloads=true` |

- The setting is `true` only in the `dev`, `local` and `test` profiles. Outside development it is `false` unless `APP_ALLOW_UNSCANNED_DOWNLOADS=true` is set, which is the written risk acceptance the audit asks for, expressed as configuration.
- The upload audit event now records the scan status.
- API: attachments carry a new field `downloadable`. Frontend: `features/documents/attachments.ts` uses it and shows "not virus-checked" (EN, FR, AR) for an unscanned file that may be downloaded.
- **Not done:** no antivirus adapter exists. Until gate G3-a is decided, a deployment outside development either quarantines every upload or accepts unscanned downloads explicitly.
- **Existing data:** files uploaded before this sprint keep the status `CLEAN` although they were never scanned.

## 2. Reverse proxy and rate limits (S-3)

- `platform/web/ClientAddressResolver.java`: behind a trusted proxy the forwarded list is read from the right, skipping trusted proxies; the first other address is the client. Values the client put on the left are ignored. Several header lines are read as one list; the `Forwarded` header is handled the same way.
- Unchanged: without a trusted proxy the headers are ignored.
- **To verify at deployment:** `APP_TRUSTED_PROXIES` must list the proxy's address as the application sees it. Test with 60 failed sign-ins and forged headers through the real proxy.

## 3. Administrator powers

### S-4: passwords and peer administrators (`identity/UserAdminService.java`)

- **Temporary passwords:** only a global administrator may set one; never on their own account; the password policy applies. A company administrator gets 403 and uses the reset link, which shows the secret only to the account owner.
- **Peer administrators:** a company administrator can no longer reset, deactivate, edit or sign out an account that holds the ADMIN role (403 `peer-administrator`). A global administrator can.
- Frontend: `features/admin/users-admin.ts` shows the temporary-password form to global administrators only.
- **Behaviour change:** in a company with two administrators, one can no longer help the other recover an account; a global administrator must.
- **Not done:** no e-mail tells the owner that an administrator set a temporary password (the reset link does reach the owner).

### S-5: delegation (`approval/Absences.java`)

- An administrator cannot name themselves delegate, with or without the override (422, `delegate-is-administrator`).
- With the override the delegate must belong to one of the absent person's companies (home company or a role there) or hold a group-wide role (422 `delegate-not-eligible` otherwise).
- **Not done:** delegated decisions are not yet labelled as such on the request page beyond the existing "on behalf of".

### S-6: audit events (same transaction as the change)

| Action | Entity type | Events |
|---|---|---|
| Absence recorded or cancelled, by the person or an administrator | `USER_ABSENCE` | `ABSENCE_CREATED`, `ABSENCE_CANCELLED` |
| Approval rule created, draft edited, new version, activated, retired | `APPROVAL_RULE` | `APPROVAL_RULE_CREATED`, `_UPDATED`, `_VERSION_CREATED`, `_ACTIVATED`, `_RETIRED` |
| Exchange rate added | `EXCHANGE_RATE` | `EXCHANGE_RATE_ADDED` (global chain: a rate applies to the group) |

Rule events hold the values after the change and, for an edit, before it (amount bounds, stages, approvers).

### S-7: company scope

- `GET /api/v1/admin/companies`: a company administrator gets only the companies they administer.
- `GET /api/v1/admin/users`: only users of those companies (home company or a role there) and holders of a group-wide role; a `companyId` outside the scope gives 404.
- `GET /api/v1/admin/absences`: only absences of users of those companies.
- A global administrator still sees the group.
- **Not done:** any ADMIN can still add an exchange rate for the group (decision G2-c).

## 4. Output safety

- **S-14** `dashboard/ErpExportService.java`: a cell starting with `=`, `+`, `-`, `@`, tab or carriage return is prefixed with an apostrophe. A negative amount is not affected: amounts are written by another function.
- **S-13** `audit/AuditRecord.java`: `jsonString()` builds a JSON string literal. Used for the user e-mail, the supplier code and the shipment number and kind. E-mail addresses are validated with a pattern instead of "contains @".
- **S-24** `supplier/SupplierService.java`: `%`, `_` and `[` typed in the search are literal.

## 5. Configuration that cannot be unsafe by accident (S-15, S-16)

`platform/config/DeploymentConfigurationGuard.java` (new) stops the application before any bean is created and lists every problem:

| Situation | Result |
|---|---|
| `dev`, `local` or `test` profile with a public HTTPS address | Refused |
| `dev`, `local` or `test` profile with `APP_ENVIRONMENT` = uat, staging, preprod, production | Refused |
| Outside those profiles: insecure cookies, testing hooks, API documentation enabled | Refused |
| Outside those profiles: `MAIL_MODE` missing, or `failing` | Refused |
| Outside those profiles: `APP_PUBLIC_BASE_URL` not HTTPS | Refused |
| Any profile: unknown `MAIL_MODE` | Refused |

- `MAIL_MODE` has no default outside development (`application.yml`); `dev` and `local` default to `file`.
- **Not done:** the testing endpoints still accept a company ADMIN (they exist only with the `dev` profile, which the guard now keeps out of deployments). No `uat` profile file was added: a deployment runs with no development profile and the variables of `sprint-01-final-report.md`, section 5.

## 6. Observability that supports security

- `platform/web/RequestIdFilter.java` (new): every answer has an `X-Request-Id` header; every log line of a request has the request id and the user id. The id is generated by the server, never taken from the client. It is deliberately not written into error bodies: two refusals that must be indistinguishable (unknown link, another company's object, wrong password) keep identical bodies, which existing tests assert.
- The access line holds method, path, status, duration and user id. It never holds the query string, headers, cookies or bodies.
- `/actuator/metrics` is restricted to global administrators; anonymous callers get only `{"status": ...}` from `/actuator/health` (unchanged); no other actuator endpoint is exposed.
- `approval/Absences.java`: cancelling an absence through the administrator path now checks the caller's role before looking the absence up, so a non-administrator gets 403 for any id and cannot tell which absence ids exist. Found by the authorization matrix.

## 7. Authorization matrix

`AuthorizationMatrixIntegrationTests` reads the endpoints from the running application (179 in the test profile; the two testing endpoints exist only with `dev` and are not covered) and calls each as an anonymous caller, a supplier contact and a requester of another company using the identifiers of the other company's objects. Expected answers are the reviewed file `BackEnd/src/test/resources/authorization-matrix.txt`; a new endpoint that is not in the file fails the build.

| Check | Result |
|---|---|
| Anonymous on the 170 non-public endpoints | 401 on all |
| Supplier contact outside the portal | 403 on all |
| Other company's objects (requests, tasks, attachments, orders, shipments, receipts, conversations, rules, accounts) | 403 or 404 on all; no 2xx |
| Privilege escalation (self-granted roles, hidden fields in a profile update, company administrator granting ADMIN or acting on a global administrator) | Refused; database state unchanged |

**Limits of the matrix:**

- It proves refusals. It does not prove that each permitted role sees only what it should inside its own company; the existing module tests cover that in part.
- The test builds its own objects of the other company: a request waiting for approval with an attachment, and a second request taken through RFQ, award, order, shipment, delivery and receipt. Refusals on those endpoints are refusals of existing objects. Supplier conversations, supplier contacts, imports, absences and notifications are not built by the test: it uses what other classes left in the database, or an id that does not exist, which proves less for those five.
- `POST /api/v1/imports` accepts only multipart: the matrix does not reach its role check.
- It is a static set of callers, not a penetration test.
