# 02. Architecture review

Scope: backend structure (Spring Boot 4.1.2, Java 21, Spring Modulith 2.1.1, 20 modules, 209 files, 24,141 lines), frontend structure (Angular 22, 117 files, 20,822 lines), configuration, error handling, observability and tests. Status: **Verified** = read in code or counted by script; **Hypothesis** = needs runtime evidence. The inventory of modules, classes and endpoints is in report 10.

## 1. Summary

The architecture is appropriate for the product and should be kept: one deployable modular monolith, one database, no extra infrastructure, a single-page frontend with lazy routes. The decisions are written down (ADR-001 to ADR-012) and mostly followed. Nothing here calls for a rewrite, a new framework or a split into services.

Four structural weaknesses matter for enterprise readiness:

1. **Module boundaries are enforced in Java but bypassed in SQL.** Modules read, and in one case write, each other's tables.
2. **The system cannot be observed in operation**: no metrics, no request ids, almost no logging.
3. **Operational safety relies on the `dev` profile**, which every instance has used so far.
4. **Authorization is correct by convention, not by construction**: each service method must remember its own check.

## 2. What is sound

| Aspect | Evidence |
|---|---|
| Module structure | 20 top-level packages, each with a `package-info.java`; `ModularityTests` runs `ApplicationModules.verify()` on every build; a real cycle was avoided during the messaging work rather than suppressed |
| Dependencies | Nine starters plus Modulith, springdoc and the SQL Server driver; nothing unused found; versions managed by the Spring Boot parent |
| API style | REST under `/api/v1`; RFC 9457 errors from one handler (`platform/web/ApiExceptionHandler.java`); no stack traces or internal messages returned |
| Configuration | Environment variables for everything deployment-specific; startup fails without `DB_URL` outside dev (`platform/config/DatabaseConfigurationGuard.java`); `open-in-view: false`; `ddl-auto: validate`; Flyway `clean-disabled` |
| Side effects | E-mail through a transactional outbox (ADR-009); events delivered after commit; idempotency keys on 37 write call sites |
| Concurrency | Optimistic versions on 18 tables; application locks for numbering with a short timeout; fixed lock order documented where it matters |
| Ports for later infrastructure | `FileStorage`, `AttachmentScanner`, `MailTransport`, `AssistantModel` (ADR-003): the right places are abstracted |
| Frontend | Standalone components, signals, zoneless, OnPush; 34 of 35 routes lazy; one HTTP interceptor for the session; CSRF configured centrally; no state-management library and no need for one |
| Tests | 37 backend test files with 211 tests, most against a real SQL Server; 32 frontend unit specs; 18 Playwright specs (66 tests) including three accessibility specs |

## 3. Findings

### A-1. Modules read and write each other's tables through SQL — Medium — Verified

- **Location (readers outside the owning module, counted by grep of `FROM|JOIN|UPDATE|INTO <table>`):**

| Table (owner) | Other modules with SQL on it |
|---|---|
| `purchase_order` (ordering) | dashboard, logistics, messaging, receipt, request |
| `supplier` (supplier) | identity, messaging, ordering, sourcing |
| `approval_task` (approval) | identity, messaging, request |
| `company` (organization) | identity, messaging, purchasing |
| `user_account`, `user_role` (identity) | messaging, notification |
| `shipment` (logistics) | receipt, request |
| `rfq` (sourcing) | messaging, ordering, request |
| `goods_receipt` (receipt) | request |

  **Writes:** `messaging/MessagingStore.java:285-302` inserts into `user_account` and `user_role` and updates `user_account.credentials_version` when a supplier contact is invited.
- **Explanation:** 40 classes use `JdbcTemplate` and only 5 entities exist, so most data access is SQL, and SQL is invisible to the Modulith test. The module diagram shows fewer dependencies than really exist. The identity write is the important case: accounts are created outside the identity module, so its rules (e-mail validation, audit event `USER_CREATED`, password setup, last-admin checks) can be skipped without anyone noticing.
- **Business impact:** schema changes break other modules at runtime; account creation has two code paths with different guarantees.
- **Recommendation:**
  1. Move supplier-contact account creation behind a public method of `identity` (one small method; no new abstraction).
  2. For reads, accept the pattern where it is a deliberate read model (`request/RequestLogisticsQueries`, dashboards) and name those classes consistently (`*Queries`).
  3. Elsewhere call the owning module's public query.
  4. Add a test that fails when a module's SQL names a table it does not own and is not on an allow-list.
- **Complexity:** S for the identity write; M for the test and clean-up.
- **Verification:** the new test passes with an explicit allow-list; `grep "INSERT INTO user_account"` matches only `identity/`.

### A-2. No operational visibility — High — Verified

- **Location:** no metrics dependency or `MeterRegistry` use; actuator exposes `health` and `info` only (`application.yml`); no `logback-spring.xml`; no MDC or request id; 21 `log.info/warn/error` statements in the backend; lock failures are logged as a class name only (`ApiExceptionHandler.java:65`).
- **Explanation and impact:** see report 04, P-4. An unexpected error returns a reference id to the user (`ApiExceptionHandler.java:71-79`), which is good, but requests that succeed slowly or fail with a handled status leave no trace.
- **Recommendation:** request-id filter with MDC; access log with duration and user id (never bodies); Micrometer metrics for HTTP, pool, JVM and the outbox backlog; a structured (JSON) log format option for the client's log platform.
- **Complexity:** M.
- **Verification:** an error in the browser can be found in the log by its id; p95 per endpoint is readable after a test run.

### A-3. Production safety depends on not choosing the `dev` profile — Medium — Verified

- **Location:** `application-dev.yml`; `approval/TestingController.java:24-25`; every documented start command uses `dev,demo` (`CLAUDE.md`, `README.md`, `HANDOFF.md`).
- **Explanation:** there is no `uat` or `prod` profile and no guard against `dev` on a public address (report 03, S-15). The testing controller sits in the `approval` module and its permission check is `Absences.requireAdmin`, an absence-management helper reused as the general "is administrator" test in four classes.
- **Recommendation:** add `application-uat.yml` (or document the exact environment variables), a startup guard, and move the administrator check to `identity` where the other role checks are.
- **Complexity:** S.

### A-4. Authorization is by convention — Medium — Verified

- **Location:** no `@PreAuthorize` or method security; URL rules distinguish only anonymous from authenticated (`identity/SecurityConfiguration.java:73-88`); each of 180 endpoints relies on a call inside its service.
- **Explanation:** the central request policy (`request/RequestAccessPolicy.java`) is a strong point and most modules go through it. The risk is the next endpoint added without a check: nothing fails. `CLAUDE.md` requires a cross-company test per endpoint; this audit found group-wide administrator listings that such tests would have caught (report 03, S-7).
- **Recommendation:** do not introduce annotations everywhere now. Add one automated test that enumerates all request mappings and asserts, for each, that a user of another company gets 403 or 404 and a supplier contact gets 403. New endpoints are then covered by default.
- **Complexity:** M.
- **Verification:** the test lists 180 mappings and passes; adding an unchecked endpoint makes it fail.

### A-5. Two persistence styles in one aggregate — Low — Verified (risk: Hypothesis)

- **Location:** JPA for the purchase request (5 entities, 4 repositories, `request/PurchaseRequest.java` 625 lines); `JdbcTemplate` for everything else (40 classes), including code that updates request-related rows in the same transaction (`requests.touchForAudit`, `requests.currentLocked` in `receipt/ReceiptService.java:290-292`).
- **Explanation:** mixing is legitimate, and the code re-reads state after SQL updates. The hazard is a JPA entity loaded earlier in the transaction that does not see a later SQL update, or a pending flush ordered after a SQL statement.
- **Recommendation:** no change now. When a defect of this kind appears, move that aggregate to one style rather than adding flush calls. Document the rule in `docs/architecture/architecture.md`.
- **Complexity:** none now.

### A-6. Large service classes mix reads, rules and writes — Low — Verified

- **Location:** `request/RequestAdminService.java` 810 lines (about 44 methods: listing, visibility, archive, cancel, corrections, removal, cleanup preview sessions); `receipt/ReceiptService.java` 702; `messaging/InternalMessagingService.java` 689; `request/RequestWorkflow.java` 644; `approval/ApprovalStore.java` 520; `approval/ApprovalEngine.java` 499.
- **Explanation:** each is coherent around one business area, so this is a maintainability cost, not a defect. `RequestAdminService` also keeps cleanup previews in a `ConcurrentHashMap` (`:73`), which is node-local state inside a business service.
- **Recommendation:** split only when touched: read side (`*Queries`) from commands, as `approval` already does. Move preview sessions to the database or the HTTP session if a second node is planned.
- **Complexity:** M each, opportunistic.

### A-7. SQL in a controller — Low — Verified

- **Location:** `notification/NotificationController.java:54-93` builds and runs four queries.
- **Recommendation:** move to a store class, like every other module.
- **Complexity:** S.

### A-8. Integration tests share one database and depend on its state — Medium — Verified

- **Location:** all SQL Server tests use `procurement_test` and the demo seed (`support/SqlServerTestSupport.java`); tests of 2026-11-13/14 needed fixes for baselines left by other tests (unread counts, the in-memory rate limiter shared across tests). The Playwright suite shares demo accounts and needs `workers: 1`.
- **Explanation:** tests are realistic (real database, real HTTP), which is valuable. They are also order-sensitive and slow: backend 208 tests in about 10 minutes, browser suite 66 tests in 27 minutes, single-threaded. The earlier intermittent failures recorded in `HANDOFF.md` were of this kind.
- **Business impact:** a slow, occasionally flaky suite gets skipped under time pressure; there is no CI to run it anyway (A-9).
- **Recommendation:** each test creates the data it asserts on (most already do); assert on deltas, never absolute counts; reset in-memory limiters in a shared base class; let Playwright create its own users per worker so the suite can run in parallel.
- **Complexity:** M.
- **Verification:** the suites pass three times in a row in random order.

### A-9. No continuous integration — High — Verified

- **Location:** `.github/` contains only `modernize/` hook scripts; no workflow, no pipeline file of any kind; checks are run by hand from a copy of the repository outside the workspace (`CLAUDE.md`).
- **Explanation:** nothing guarantees that the committed code compiles, passes tests or builds. Results in `tools/_*.txt` (14 tracked output files) are snapshots of manual runs.
- **Business impact:** regressions reach UAT; "tested" depends on who ran what and when; no dependency scanning.
- **Recommendation:** a pipeline with: backend `verify` against a SQL Server service container; frontend lint, unit tests and build; dependency scans (report 03); the browser suite nightly; artefacts (jar and static bundle) built once and promoted to UAT. Detail in report 08.
- **Complexity:** M.

### A-10. Documentation drift — Low — Verified

- **Location:** `README.md:6` states "Current state: Sprint 3 locally implemented" while the code is past Sprint 13 with internal messaging; ADR-002 lists modules `receiving` and `integration` that do not exist (actual: `receipt`, `importdata`, `messaging`, `assistant`, `orgadmin`, `notification`, `platform`); 14 tool output files tracked under `tools/`.
- **Recommendation:** update the README and the module list; move run outputs out of the repository or into `docs/qa/evidence`.
- **Complexity:** S.

### A-11. Single node by construction — Medium — Verified

- See report 04, P-7 for the list of node-local state. Architecture note: this is consistent with ADR-003 (infrastructure on demand) and acceptable for UAT. It must be stated in the service-level expectations given to the client: a restart signs everyone out.

## 4. Frontend architecture

| Aspect | Evidence | Assessment |
|---|---|---|
| Routing | 35 routes, 34 `loadComponent`, shell eager; guards `authGuard`, `guestGuard`, `permissionGuard`, `anyPermissionGuard`, `employeeGuard`, `homeRedirectGuard` | Sound. Guards are convenience only; the server enforces (verified in report 03) |
| State | Signals in components and a few root services (auth, theme, language, notifications, approval count, messaging live); no global store | Sound for this size |
| HTTP | `provideHttpClient(withFetch(), withXsrfConfiguration, withInterceptors([sessionInterceptor]))`; no automatic retry; no request timeout | Sound; add a timeout for long calls if P-1 remains |
| Errors | `core/api/api-problem.ts` maps RFC 9457 answers to translation keys | Sound |
| Component size | 9 components over 500 lines; inline templates and styles | See report 07, UI-3 |
| Tests | 32 spec files; 11 feature folders without any unit spec | Gap; covered partly by browser tests |
| Subscriptions | 190 `subscribe(`, 38 `takeUntilDestroyed` | Review non-HTTP ones (report 07, UI-9) |

## 5. Decisions recommended for the record (ADRs)

| Decision | Why now |
|---|---|
| RCSI on, lock and transaction timeouts | Changes concurrency behaviour (report 05, DB-3) |
| Two database principals and audit anchoring | Makes the audit claim true (report 05, DB-1) |
| Antivirus adapter | New infrastructure under ADR-003 (report 03, S-1) |
| Session store and file storage for more than one node | Before any availability commitment (report 04, P-7) |
| MFA or single sign-on | Open release gate |
| Message retention; e-mail for messages | Already recorded as open in `STATUS.md` |
| No GSAP, Lenis or React; CSS-only motion | Confirms ADR-004 after report 07 |

## 6. Not verified

- Runtime behaviour of the JPA and SQL mix under concurrent edits.
- Whether every state change writes an audit event: confirmed missing for rules, rates and absences (report 03, S-6); not checked exhaustively for all 180 endpoints.
- Spring Boot 4.1.2 and Spring Modulith 2.1.1 patch-level currency (no network lookup was made for newer releases).
