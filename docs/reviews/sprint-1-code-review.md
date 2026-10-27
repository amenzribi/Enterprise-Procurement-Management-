# Sprint 1 Code Review

- Revision: git tree `be1f311` (staged index on top of commit `77d22b1`)
- Type: static review (no builds, tests or servers; QA executes concurrently)
- Reviewed against: `docs/product/sprint-1.md` (criteria, design review outcomes, QA decisions),
  `docs/reviews/sprint-1-design-review.md`, `CLAUDE.md`, ADR-001/006/007

## Summary

No Blocker or Major findings. Security-sensitive paths (CSRF with the SPA, explicit context save and session-id
rotation, server-side idle/absolute timeouts, uniform 401 with dummy hashing, demo gating, central scope component,
mass-assignment rejection, server-derived charged/buying company, lock order with per-attempt transactions,
audit in the same transaction) are implemented as designed. The findings below are hardening and maintainability
items; none blocks Sprint 1 acceptance from a review standpoint, subject to QA's execution results.

## Findings

| ID | Severity | Location | Finding | Recommended fix |
|---|---|---|---|---|
| S1CR-01 | Minor | `identity/LoginThrottle.java:110,157` | The in-memory counter map is never evicted (only the account key is removed on success). Failures from many addresses or e-mails accumulate entries for the life of the process; a distributed spray slowly grows memory. | Drop counters whose window and block have expired (on access when the map exceeds a size, or with a scheduled sweep) and cap the map size. |
| S1CR-02 | Minor | `identity/AuthController.java:203-207` | Role authorities are frozen in the session at sign-in. Business endpoints reload roles per request (`CurrentUserProvider`), but URL rules that use authorities (API docs `hasRole(ADMIN)`, actuator health `roles: ADMIN`) keep a revoked ADMIN role until the session ends (max 8 h). | Reload authorities per request (e.g. refresh the `Authentication` in the timeout filter from `UserDirectory`), or document the 8 h revocation delay for these two diagnostics endpoints. |
| S1CR-03 | Minor | `request/RequestService.java:175`; `platform/persistence/TransactionLocks.java:237` | The submit retry catches `PessimisticLockingFailureException`, which includes `CannotAcquireLockException` from the 10 s application-lock timeout. A stuck lock therefore makes a submit hang up to ~30 s (3 × 10 s) before failing. Retrying a deadlock victim in a **new** transaction per attempt is correct. | Retry only deadlock victims (`DeadlockLoserDataAccessException` / SQL error 1205); lower the app-lock timeout (2–3 s) and map a timeout to 409/503 with a retryable code. |
| S1CR-04 | Minor | `request/RequestService.java:155-157`; `RequestValidator.java:92` | On update, an omitted `requesterId` means "the editing user". When the entered-by user (assistant) edits a draft entered on behalf of someone and the client omits `requesterId`, the requester silently becomes the assistant. The current frontend always sends it for on-behalf drafts, so this is an API semantics trap rather than a live UI defect. | On update, treat a missing `requesterId` as "unchanged"; only create uses "self" as the default. Add a test. |
| S1CR-05 | Minor | `FrontEnd/src/app/core/auth/session-timer.service.ts:81-97`; `auth.service.ts:122` | The idle timer is per tab and only reset by that tab's API responses. With two tabs open, the idle tab sends the user to sign-in (and clears client session state) while the server session is still alive thanks to the other tab. | Before acting on client-side expiry, confirm with one user-visible `GET /me` (not polling), or share the last-activity time between tabs (`storage` event / `BroadcastChannel`). |
| S1CR-06 | Minor | `FrontEnd/src/app/core/form-snapshots/form-snapshot-storage.ts` | Snapshots (business data: titles, justifications, prices) stay in `sessionStorage` after a session expiry by design (Q6). If a different user signs in in the same tab, the previous user's snapshot is not shown but remains readable in developer tools until the tab closes. | At sign-in, clear snapshots that belong to other user ids; add a time-to-live (e.g. 8 h) to each snapshot. |
| S1CR-07 | Minor | `request/PurchaseRequestRepository` usage; draft line handling in `RequestService.java:314-336` | Lines are matched to the payload **by position**: removing line 2 of 3 rewrites line 2 with line 3's content and deletes line 3. Harmless now, but S2 adds line-level attachments (FR-REQ-02) and later line statuses; those would follow the wrong line. | Before S2, include line `id` in the payload and match existing lines by id (new lines without id; missing ids deleted). |
| S1CR-08 | Minor | `src/test/.../ArchitectureTests.java:35-46` | The rule protects `PurchaseRequestRepository`, but direct SQL (`JdbcTemplate`) against `purchase_request` from another class or module (e.g. future dashboards, exports) bypasses the visibility rule without failing the test. | Add a rule that only `request`-module classes may use `JdbcTemplate` on request tables (e.g. forbid `JdbcTemplate` in the `request` package except `RequestNumbering`, and require dashboards/exports to call `ScopedRequests`); add it to the review checklist. |
| S1CR-09 | Suggestion | `identity/CredentialStore.java:227` | `LOWER(u.email) = LOWER(?)` prevents index use on `uq_user_account_email`; the database collation is case-insensitive, so `LOWER` is unnecessary. | Compare `u.email = ?` (input already lower-cased). |
| S1CR-10 | Suggestion | `request/RequestController.java` (`q` parameter) | The text search parameter has no length limit. | Reject `q` longer than 100 characters with `invalid-parameter`. |
| S1CR-11 | Suggestion | `FrontEnd/src/app/features/requests/request-form*.ts` | Quantities and prices are sent as JSON numbers. Exact within the enforced caps (≤ 15 significant digits), but fragile if caps change. | Send decimal values as strings (Jackson binds them to `BigDecimal`). |

## Design review (S1DR) verification

| Item | Status | Evidence |
|---|---|---|
| S1DR-01 CSRF SPA | Applied | `SecurityConfiguration` `csrf.spa()`; `/auth/csrf` renders the deferred token; cookie cleared on logout |
| S1DR-02 Login context save / rotation | Applied | `AuthController.login`: `ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`, explicit `saveContext` |
| S1DR-03 Uniform 401, throttling | Applied | `SignInService` always hashes (dummy hash), single failure path; `LoginThrottle` per (e-mail hash, IP) and per IP; see S1CR-01 |
| S1DR-04 Audit of failures | Applied | Failures logged with hashed e-mail; chain gets sign-in, sign-out, throttle activation only |
| S1DR-05 Demo gating | Applied | `is_demo`; checked at sign-in and on every request (`CurrentUserProvider`); seeder refuses non-demo companies |
| S1DR-06 Lost updates | Applied | Explicit client-version comparison; `touch()` always dirties the header so `@Version` increments on line-only edits |
| S1DR-07 Server-derived companies | Applied | `buyingCompany()` from purchasing mode; client values rejected `not-editable` |
| S1DR-08 Status model | Applied | Single ranked `RequestStatus`; DB check lists aligned; `IN_TRANSIT` absent |
| S1DR-09 Transition table | Applied | `purchase_request_transition` written on create and submit |
| S1DR-10 Lock order | Applied | Request row (`PESSIMISTIC_WRITE`) → counter → audit head; app lock for first rows; retry per new transaction; see S1CR-03 |
| S1DR-11 Expiry UX | Applied | Client warning, snapshot restore; see S1CR-05/06 |
| S1DR-12 Absolute timeout source | Applied | `AUTHENTICATED_AT` attribute set at sign-in, not session creation time |
| S1DR-13 session-expired signal | Applied | `ApiSecurityHandlers` uses `isRequestedSessionIdValid()` |
| S1DR-14 Department-scoped roles | Applied | `user_role.department_id` with same-company FK and group-wide check |
| S1DR-15 Requesting company / submit by entered-by | Applied | `requestingCompany()`; `canEdit` allows requester or entered-by |
| S1DR-16 §6.1 fields | Applied | origin, budget line, urgency justification, equipment and maintenance-order refs |
| S1DR-17 Validation rules | Applied | Company time zone for number year and required-date checks |
| S1DR-18 API gaps | Applied | `actions`, discard, `permissions`, `scope` |
| S1DR-19 Unscoped access | Applied (partly) | ArchUnit rule on the repository; see S1CR-08 |
| S1DR-20 Draft-save auditing | Applied | Edits not chained; tracked by `updated_by/updated_at` |
| S1DR-21 Language precedence | Applied | Frontend saves the sign-in-page choice to the account; account preference otherwise |
| S1DR-22 Health exposure | Applied | `show-components/details: when-authorized`, `roles: ADMIN`; status requires sign-in |
| S1DR-23 Gap-free comment | Applied | Numbering documented as gap-free per company and year |
| S1DR-24 Line limit | Partly | Configurable (`app.request.max-lines`), default kept at 100 pending client input |
| S1DR-25 Browser channel | Accepted | System Edge channel used; revisit with CI platform decision |

## Verdict per acceptance criterion (static)

| AC | Verdict | Note |
|---|---|---|
| S1-AC-01 | Met | Cookie flags per profile, rotation, CSRF, timeouts as properties, throttling replaces lock (S1DR-03) |
| S1-AC-02 | Met | Demo-only seeder, no password in repo, random password printed once |
| S1-AC-03 | Met | Fields, 1..100 lines, server totals HALF_UP to minor units |
| S1-AC-04 | Met | Actor rules server-side; charged/buying derived (S1DR-07); S1CR-04 to tighten |
| S1-AC-05 | Met | Number from locked counter, DRAFT → CREATED, audit in same transaction |
| S1-AC-06 | Met | Scoped list with allow-listed sort, filters, pagination |
| S1-AC-07 | Met | `ScopedRequests` on list/detail/update/submit/discard; 404 identical except `instance` |
| S1-AC-08 | Met | Explicit version check + `@Version`; second submit 409; parallel numbering tested by developer |
| S1-AC-09 | Met (static) | Error codes translated; key parity test present |
| S1-AC-10 | Met (static) | Depends on QA axe run |
| S1-AC-11 | Met | Owner/owner_since; transition table |
| S1-AC-12 | Met | `PUT /me/preferences`; applied at sign-in |
| S1-AC-13 | Met | Anonymous health status only; status needs sign-in; details/docs ADMIN (S1CR-02 caveat) |
| S1-AC-14 | Met | ArchUnit rules; S1CR-08 to strengthen |
| S1-AC-15 | Met | Guard + beforeunload + snapshots + expired-session return; S1CR-05/06 |
| S1-AC-16 | Met (static) | E2E specs exist; execution evidence from Developer/QA |

**Review verdict:** Sprint 1 can be accepted from a review standpoint once QA confirms execution; S1CR-01…08 should
be scheduled (S1CR-07 before line attachments in Sprint 2).

## Fix verification on af0bb38

Static verification of `git diff be1f311 af0bb38` (no builds run by the Reviewer; the lead reports `verify` 86/0/51 skipped,
`verify -Pdb-it` 86/0/0, frontend 148/148, e2e 10/10 on this tree).

### Status of earlier findings

| Finding | Status | Evidence |
|---|---|---|
| S1CR-01 throttle memory growth | Resolved | `LoginThrottle`: periodic sweep (every 500 records or at `MAX_ENTRIES`) drops expired, non-blocking counters; LRU eviction of the oldest 10 % above 10 000 keys; `LoginThrottleTests` added. |
| S1CR-02 roles cached in session | Resolved | New `CurrentUserRefresh` filter (after `SessionTimeouts`) reloads the user each request, invalidates the session if the account is gone/inactive or a demo account without the `demo` profile, and re-saves the context when authorities changed; `revokedRolesAndDeactivatedAccountsTakeEffectImmediately` test. One extra user query per request — acceptable at this scale. |
| S1CR-03 lock timeout treated as deadlock | Resolved | Retry only when `SqlServerErrors.isDeadlockVictim` (1205, walks causes and `getNextException`); app-lock timeout 2 s → 503 `busy`; `PessimisticLockingFailureException` also mapped to 503 `busy`. Retry still runs a new transaction per attempt. |
| S1CR-04 omitted requester on update | Resolved | `RequestService.update` substitutes the stored requester when `requesterId` is null; creation keeps the "self" default; test `omittedRequesterOnUpdateKeepsTheRequester`. |
| S1CR-05 per-tab idle timer | Resolved | `SessionTimerService` shares last activity through `localStorage` + `storage` event and re-checks shared activity before expiring (covers throttled background tabs); absolute deadline still applied by `schedule()`. |
| S1CR-06 snapshot left for next user | Resolved | 8 h TTL; `prepareFor(userId)` at sign-in removes other users' and expired snapshots; `clearUser` on explicit sign-out; tests added. |
| S1CR-07 positional line matching | Resolved | Lines matched by `lines[].id`; unknown/duplicate ids → 422 `invalid-value` on `lines[i].id`; kept lines keep their number, new lines take max+1, so the `(request_id, line_no)` unique constraint cannot collide even with Hibernate's insert-before-delete flush order. Frontend sends `id` for existing lines. |
| S1CR-08 architecture test too narrow | Resolved (heuristic) | `requestTablesAreOnlyQueriedInsideTheRequestModule` scans main sources for `purchase_request*` table names outside the `request` module. Text-based, so it can be evaded by building names dynamically — acceptable as a guard rail. |
| QA1-D-02 409 instead of 403 | Resolved (code) | Permission (`isEditor`) checked before state in edit/submit/discard; test `visibleButNotEditableRequestsAnswerForbidden`. |
| QA1-D-03 NG0200 cycle | Resolved (code) | Interceptor no longer injects `AuthService`; it signals `timer.serverRejected()`, and `AuthService` subscribes to `timer.expired`. `app-bootstrap.spec.ts` asserts no NG0200/fallback-load error. |
| QA1-D-04 default in-memory user | Resolved (code) | `noUserDetailsService` bean suppresses Boot's generated user and password log. |
| QA1-D-05 identical demo hashes | Resolved | `encode()` is called per user inside the loop → distinct salts. |
| QA1-D-06 OUTPUT without INTO | Resolved | `AuditLog` draws the id from `seq_audit_event` then inserts it; `RequestNumbering` uses `OUTPUT … INTO @issued`. |
| QA1-D-01 contrast | Resolved (tokens) | Accent `#0b6f6e` / hover `#085756`, documented ratios ≥ 5.07 on all light surfaces; QA to confirm by axe (lead reports e2e EN desktop passing). |

### Lead's change: `TransactionLocks` (`IF @@TRANCOUNT = 0 BEGIN TRANSACTION`)

Root cause is correct: with auto-commit off, mssql-jdbc runs the session with `IMPLICIT_TRANSACTIONS ON`; an implicit
transaction starts only with a table-touching statement, so `sp_getapplock … @LockOwner='Transaction'` as the first
statement saw `@@TRANCOUNT = 0` and returned −999. Production only worked because callers happened to run a
SELECT/UPDATE first — a latent bug that would have surfaced as spurious 503 `busy` the first time a lock became the
first statement of a transaction.

The fix is sound under the current wiring:

- mssql-jdbc `commit()` / `rollback()` send `IF @@TRANCOUNT > 0 COMMIT TRAN` / `ROLLBACK TRAN`, so the explicitly
  opened transaction (count 1) is committed or rolled back by Hibernate's `JdbcResourceLocalTransactionCoordinator`
  exactly like an implicit one; the transaction-owned app lock is released with it.
- When an implicit transaction is already open (`@@TRANCOUNT ≥ 1`) the guard does nothing, so no nesting is created;
  later statements do not start a second implicit transaction while the count is > 0.
- `Propagation.MANDATORY` plus a `JdbcTemplate` on the same `DataSource` bean means the statement runs on the
  connection Hibernate exposes for the Spring transaction (auto-commit false).

The test guard in `aHeldApplicationLockFailsFastAsBusy` is correct: a 10 s bounded wait, then `holding.get(1 s)`
surfaces the holder's real exception (or a `TimeoutException`), and `finally` releases and shuts down the holder.

### New findings

| ID | Severity | Location | Finding | Recommended fix |
|---|---|---|---|---|
| S1CR-09 | Minor | `platform/persistence/TransactionLocks.java` `lockExclusively` | The explicit `BEGIN TRANSACTION` is only safe while the connection is the transaction-bound, auto-commit-off one. If the method were ever reached on an auto-commit connection (a second `DataSource`, a `TransactionTemplate` on a different manager, or a JTA setup), the open transaction and app lock would survive the statement and go back to the Hikari pool (Hikari rolls back on return only when auto-commit is off), leaking locks across requests. Nothing does this today. | Fail fast instead of opening a transaction on a non-transactional connection: e.g. `DataSourceUtils.isConnectionTransactional(con, dataSource)` / `!con.getAutoCommit()` check before the batch, or `IF @@TRANCOUNT = 0 AND @@OPTIONS & 2 = 2 BEGIN TRANSACTION` (implicit-transactions bit) and `THROW` otherwise. Add a DB-IT asserting `@@TRANCOUNT = 0` and no `APPLICATION` lock on the pooled connection after commit and after rollback. |
| S1CR-10 | Suggestion | `request/RequestService.java` line numbering | Line numbers grow monotonically (max+1) across edits; `SMALLINT` caps at 32 767 per request. Practically unreachable with ≤ 100 lines per save. | None needed now; renumber on submit if gap-free numbers are later wanted for ERP export. |
| S1CR-11 | Suggestion | `identity/CurrentUserRefresh.java` | One user lookup per authenticated request. | Fine for MVP; cache per request already used; revisit with a short-lived cache if `/me`-heavy traffic appears. |

### Verdict

All Sprint 1 review findings S1CR-01…08 and the code side of QA1-D-02…06 are resolved; QA1-D-01 is resolved in tokens
pending QA's axe confirmation. The lead's `TransactionLocks` fix is correct and addresses a real latent production
defect. No Blocker or Major finding is open. **Sprint 1 can be accepted from a review standpoint**; S1CR-09 should be
scheduled early in Sprint 2 because Sprint 2 adds more lock users.
