# Sprint 1 final report: security and infrastructure hardening

Completed 2026-11-16. Scope and rules: `sprint-01-plan.md`. Detail: `sprint-01-security-changes.md`, `sprint-01-database-changes.md`. Evidence: `sprint-01-test-results.md`.

> **Correction, 2026-11-16.** The independent review and live probes showed that two statements below were wrong: **S-5 is not closed** (an administrator can still obtain another person's approvals, findings R-1 and R-2) and **S-15 is only partly closed** (R-4). S-3, S-4, S-6, S-7 and S-24 are partly closed. The current status of every item is in `sprint-01-validation-report.md`.

## 1. Outcome

**UAT readiness: not ready yet, but the code-side blockers of the audit are closed or reduced.** What remains before a first UAT user is decisions and infrastructure (section 6), not open High findings in application code, with one exception: no antivirus scanner exists.

| Audit High finding | Before | After the sprint |
|---|---|---|
| S-1 Files recorded clean without a scan | Open | **Reduced.** Nothing is recorded or served as clean unscanned. No scanner product yet |
| S-2 Extension not tied to type | Open | **Closed** |
| S-3 Rate limits bypassed behind a proxy | Open | **Closed in code.** To confirm behind the real proxy |
| S-4 Administrator takes over accounts | Open | **Closed** for company administrators |
| S-5 Administrator self-delegates approvals | Open | **Closed** |
| DB-1 One database principal | Open | **Prepared.** Scripts verified; not applied |
| DB-3 Unbounded lock waits | Open | **Reduced.** Waits bounded at 10 s and answered 503. Readers still block on writers (RCSI not enabled) |
| D-1 No pipeline | Open | **Written, never run** |
| D-2 No profile between dev and production | Open | **Closed** by the startup guard: a deployment cannot start with a development profile |
| D-3 Migrations with the application login | Open | **Prepared** (same as DB-1) |
| D-4 No backup or restore | Open | **Open** |
| D-5 No monitoring | Open | **Reduced.** Request ids, access log, metrics. No external check, no alerting |
| A-2 No observability | Open | **Reduced** (same) |

Medium findings closed: S-6, S-13, S-14, S-15, S-16. Reduced: S-7, DB-2, DB-4. Low closed: S-24.

**Checks run:** backend 253 tests on SQL Server, frontend lint, 253 unit tests and production build: all passed. **Not run:** the 66 browser (Playwright) tests, any load test, any dynamic security test, the CI pipeline. Detail in `sprint-01-test-results.md`.

## 2. What changed for users and operators

| Who | Change |
|---|---|
| Everyone | A PDF or image must have the matching extension. Uploads are shown as "not virus-checked" until a scanner exists |
| Everyone | A request blocked by a database lock gets "busy, try again" after at most 10 s instead of hanging |
| Company administrators | Cannot set a temporary password (reset link only); cannot manage another administrator; cannot name themselves delegate; see only their own companies, users and absences |
| Global administrators | Temporary passwords must meet the password policy; metrics at `/actuator/metrics` |
| Operators | Startup is refused on unsafe configuration; `MAIL_MODE` and an HTTPS `APP_PUBLIC_BASE_URL` are mandatory outside development; every response has `X-Request-Id`; every log line has the request and user ids |

## 3. Things the sprint found that the audit did not, or had wrong

- **Idempotency records are already purged** every hour (`Idempotency.purge()` is scheduled). The audit said nothing called it.
- **Only 4 of the ~20 proposed foreign-key indexes are backed by a query.** The others are not searched by.
- **Absence ids could be probed:** a non-administrator got 404 for an unknown absence id and 403 for an existing one on the administrator cancel endpoint. Fixed (always 403). Found by the authorization matrix.
- **The audit trail's JSON is protected by a database CHECK** (`ISJSON`), so S-13 could cause a failed write, not a corrupted record. Fixed anyway.

## 4. Open points in what was delivered

Stated so nothing here is taken for more than it is.

1. **The CI pipeline has never run.** `.github/workflows/ci.yml` was written without a push. Its first run is also the first run of the backend on Linux and against SQL Server 2022 in a container.
2. **The browser suite was not run.** Three changes touch screens it covers: the temporary-password form (hidden from company administrators), the attachment label, and administrator listings. `docs/qa/evidence/` was not touched.
3. **No test runs the application with the runtime database login.** The grants are verified by impersonation only.
4. **The proxy fix is unit-tested, not tested behind a proxy.**
5. **`POST /api/v1/imports`:** the matrix does not reach its role check (multipart only).
6. **Files uploaded before the sprint stay `CLEAN`** although never scanned.
7. **Independent QA and review have not happened.** The project rule requires both on the same revision before a sprint is called complete. Nothing is committed, so there is no revision to hand them yet.
8. **`README.md`, `CLAUDE.md`, `AGENTS.md`, `STATUS.md`, `HANDOFF.md` were not updated** with the new mandatory variables and behaviours.
9. Two timing-sensitive tests in `RequestSubmissionIntegrationTests` failed once on a busy machine before any change, and passed in every later run. They are flaky under load, independent of this sprint.

## 5. Configuration of a deployed instance

No development profile. These variables; startup fails with a message naming any that is missing or unsafe.

| Variable | Value |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Runtime login |
| `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` | Migration login (release step) |
| `APP_PUBLIC_BASE_URL` | The public HTTPS address |
| `APP_ENVIRONMENT` | `uat` or `production` |
| `APP_TRUSTED_PROXIES` | The reverse proxy's address as the application sees it |
| `MAIL_MODE` | `smtp` (with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`), or `file` |
| `APP_ALLOW_UNSCANNED_DOWNLOADS` | Leave unset. `true` only as a recorded risk acceptance while no scanner exists |
| `DB_LOCK_TIMEOUT_MS`, `DB_TRANSACTION_TIMEOUT` | Optional; defaults 10000 and 30s |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Optional; `ecs` for JSON log lines |

## 6. Decisions waiting for the owner

Nothing below was done. Each is ready to start once decided.

| # | Decision | Recommendation | Ready material |
|---|---|---|---|
| G3-a | Antivirus | ClamAV (`clamd`) on the application host, behind the existing port; needs an ADR (new infrastructure) | Port and fail-closed rules exist; the adapter is about one class |
| G3-b | Enable `READ_COMMITTED_SNAPSHOT` | Yes, on a copy first, with both suites rerun, then on each database in a window | `scripts/db/enable-read-committed-snapshot.sql` with backup and rollback steps |
| G3-c | Two database logins | Yes, with UAT | `scripts/db/` (verified) |
| G2-a | Temporary passwords: remove entirely, or keep for global administrators | Remove unless support needs them without e-mail | Small change |
| G2-b | Quote link reuse (S-8); invoice separation of duties (S-9) | Decide with the client | Not started |
| G2-c | Exchange-rate writes for global administrators only | Yes | Small change plus hiding the form |
| — | Retention of notifications and messages | Decide with the client | Purge pattern exists |
| G4 | Hosting, domain, SQL Server licensing | See audit report 08 | — |

## 7. Remaining risks

| Risk | Severity | Why it remains |
|---|---|---|
| No malware scanning | High | No scanner product. Uploads are quarantined, or downloadable unscanned by explicit setting |
| Application login can alter the schema and the audit trail | High | Logins not created (G3-c) |
| Readers block on writers; one long transaction still stalls others for up to 10 s each | Medium | RCSI not enabled (G3-b) |
| No backup, restore drill or rollback procedure | High | Infrastructure, not started (D-4) |
| No external health check or alerting; single node, in-memory sessions | Medium | Infrastructure (D-5, P-7) |
| Password-only sign-in, no account-wide lockout, thin policy | Medium (High for production) | S-10, not in the sprint; MFA or SSO is a production gate |
| Audit chain can be recomputed by a database owner; no scheduled verification | Medium | S-12, depends on G3-c |
| Quote links reusable; one buyer can take an invoice to paid | Medium | Business rules S-8, S-9 |
| Imports unbounded and loosely parsed; one-tap approval without amount ceiling | Medium | S-17, S-11 |
| Security headers for the static frontend | Low to Medium | Proxy configuration does not exist yet (S-18, D-6) |
| Capacity unknown | Medium | No load test (audit report 04) |
| No dynamic or penetration test; no backend dependency scan | — | Not available in this sprint |

## 8. Recommended next steps

1. Commit the sprint (the owner's call), push, and make the pipeline pass on Linux.
2. Run the browser suite on a fresh database; fix what the three screen changes break.
3. Independent QA and review on that revision.
4. Decide G3-a, G3-b, G3-c and G4; then the UAT deployment kit of audit report 08 (proxy configuration with headers, backup and restore drill, external health check, runbook).
5. Decide G2-a to G2-c with the client.
