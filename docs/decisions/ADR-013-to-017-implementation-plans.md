# Implementation plans for ADR-013 to ADR-017

**Date:** 2026-11-16. **Status:** plans only. The owner approved the direction of the five decisions on 2026-11-16; each plan below still needs its own go-ahead where it says "Approval". Nothing here has been installed, enabled or applied.

Approved direction:

| ADR | Direction |
|---|---|
| ADR-013 | ClamAV for UAT |
| ADR-014 | Snapshot isolation tested on a database copy first |
| ADR-015 | Separate SQL runtime and migration accounts for UAT |
| ADR-016 | Reset links instead of administrator-defined temporary passwords |
| ADR-017 | Exchange-rate management for global administrators only |

Order: ADR-016 and ADR-017 are code only and can be done first. ADR-015 and ADR-013 need the UAT host. ADR-014 needs a database copy and both test suites.

## ADR-016: reset links only

| # | Step | Where |
|---|---|---|
| 1 | Confirm outgoing e-mail works in the target environment (a reset link must arrive) | UAT |
| 2 | Remove `POST /admin/accounts/{id}/temporary-password`, its service method and request type | `identity/UserAdminController`, `UserAdminService`, `UserAdminViews` |
| 3 | Remove the form and its translations; remove the matrix line | `FrontEnd/.../users-admin.ts`, `i18n/*.json`, `authorization-matrix.txt` |
| 4 | E-mail to the account owner when a reset is issued by someone else (new notice kind, EN/FR/AR) | `notification` |
| 5 | Runbook entry "user without mailbox access": a global administrator corrects the address (audited), then sends a link. This needs an e-mail change endpoint for global administrators, which does not exist today | `identity`, runbook |
| 6 | Tests: endpoint gone (404); reset still single use; owner notified | backend, browser spec |

- **Effort:** about 1 day. **Risk:** low. **Rollback:** revert the commit.
- **Approval:** none beyond the direction, except step 5 (a new endpoint).
- **Until step 1 is proven,** temporary passwords stay as they are now: global administrators only.

## ADR-017: exchange rates for global administrators

| # | Step | Where |
|---|---|---|
| 1 | `addRate` answers 403 unless the caller is a global administrator; reading unchanged | `approval/ApprovalRulesAdmin` |
| 2 | Hide the rate form from company administrators; keep the page readable | `FrontEnd/.../exchange-rates.ts` |
| 3 | Move the tests and browser flows that add a rate as `admin@demo.local` to the global administrator | `ApprovalIntegrationTests`, `AdministratorLimitsIntegrationTests`, `accessibility-approvals.spec.ts` |
| 4 | Missing-rate message for a requester says whom to ask (EN/FR/AR) | `i18n/*.json` |
| 5 | Optional: warn when a new rate differs from the previous one of the same pair by more than a set percentage | `approval/ApprovalRulesAdmin` |

- **Effort:** half a day. **Risk:** low; a subsidiary can no longer unblock itself when a rate is missing. **Rollback:** revert.
- **Approval:** none beyond the direction.

## ADR-015: separate database accounts for UAT

| # | Step | Who |
|---|---|---|
| 1 | Server in mixed authentication mode | DBA |
| 2 | `scripts/db/create-application-logins.sql` with passwords from the secret store | DBA |
| 3 | `scripts/db/verify-runtime-permissions.sql`: 12 PASS expected | DBA or developer |
| 4 | Run the full backend suite with the two accounts against a test database on that server (`DB_TEST_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD`). The tests clean the database, so the migration account needs that right on the test database only | Developer |
| 5 | Migrations as a release step: run Flyway with the migration account before starting the application, and start the application with `SPRING_FLYWAY_ENABLED=false`, so the migration password is never in the running application's environment (review R-15) | Release procedure |
| 6 | Tighten the check script: assert that each object exists before a "must be refused" check (review R-15) | Developer |
| 7 | In CI, the SQL Server container accepts SQL logins: create the two accounts there and run the suite with the runtime account | Developer |

- **Effort:** 1 day plus DBA time. **Risk:** code that relied on owner rights fails at step 4, which is where it should be found.
- **Rollback:** point the variables back to the previous account; drop the two users and logins.
- **Approval needed before step 1:** it changes the authentication mode of a server.
- **Not covered:** the runtime account can still append to the audit chain. External anchoring of chain heads (audit S-12) is separate.

## ADR-013: ClamAV for UAT

| # | Step | Who |
|---|---|---|
| 1 | Install `clamd` and `freshclam` on the UAT application host; `clamd` on the loopback interface only; `StreamMaxLength` above 20 MB | Operations |
| 2 | Adapter `ClamAvScanner` behind `document/AttachmentScanner`: `INSTREAM` over a socket, connect and read timeouts, any error throws (the existing policy then refuses the upload with 503). Active only when `APP_CLAMAV_HOST` is set. No new library | Developer |
| 3 | Unit tests against a fake socket server: clean, `FOUND`, size limit, timeout, connection refused. One optional integration test with a real `clamd` and the EICAR file, run only where `clamd` exists | Developer |
| 4 | Startup guard: in a deployed environment, refuse to start with neither a scanner nor `APP_ALLOW_UNSCANNED_DOWNLOADS=true` | Developer |
| 5 | Interface text for a quarantined file when no scanner exists ("waiting for a virus check" is wrong today, review R-7); a scanner that returns nothing gives 503 | Developer |
| 6 | Rescan of the files stored `CLEAN` before Sprint 1 (never scanned): a one-off job, results audited | Developer |
| 7 | Scanner health in the health details and a metric for refused and failed scans | Developer |
| 8 | Runbook: install, signature updates, what users see when the scanner is down | Operations |

- **Effort:** 2 days of development plus installation. **Risk:** uploads are refused when `clamd` is down, by design.
- **Rollback:** unset `APP_CLAMAV_HOST`; files go back to quarantine.
- **Approval needed before step 1:** installing software on the host.

## ADR-014: snapshot isolation, on a copy first

| # | Step | Who |
|---|---|---|
| 1 | Restore a copy of a representative database under another name | DBA or developer |
| 2 | `scripts/db/enable-read-committed-snapshot.sql` on the copy | Developer |
| 3 | Review the read-then-write code on unversioned tables under snapshot reads: `buyer_assignment`, `quotation_line`, `shipment_line`, and the quantity checks of receipts and shipments, which read a sum and then insert. Add a lock hint or an application lock where two transactions could both pass the check | Developer |
| 4 | Full backend suite and full browser suite against the copy. The concurrency tests (`RequestSubmissionIntegrationTests`, concurrent receipt, shipment, award and quote tests) are the ones that matter | Developer |
| 5 | Add a test that holds a write open and asserts a concurrent read answers at once | Developer |
| 6 | Record the results; only then ask for approval to enable it on a database people use | Owner |
| 7 | On approval: maintenance window, backup, enable, smoke test. Rollback: the same statement with `OFF` in the same window | DBA |

- **Effort:** 1 to 2 days for steps 1 to 5. **Risk:** step 3 may find a real race; that is the purpose of doing it on a copy.
- **Approval:** steps 1 to 5 touch only a copy. **Step 7 needs a separate approval per database.**
- The test database `procurement_test` can serve as the copy for steps 2 to 5; the suite recreates its schema but not database options.
