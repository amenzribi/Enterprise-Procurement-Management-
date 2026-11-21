# 06. Code quality and duplication

Scope: backend and frontend source. Counts are from scripted searches on 2026-11-15. Simplification is recommended only where the gain can be stated; section 4 lists abstractions that were examined and should stay.

## 1. Summary

The code is consistent and readable: classes carry comments that explain why, there are no `TODO` or `FIXME` markers, only 4 broad `catch` blocks in the backend, and no dead frameworks. Duplication is local and mechanical, not structural. The items worth acting on are few:

- Hand-built JSON for audit payloads (a correctness risk).
- The same small rule written several times with different behaviour (page-size checks, wildcard escaping).
- Four near-identical numbering classes.
- A handful of code paths nobody calls.

No large refactoring is justified before UAT.

| Measure | Value |
|---|---|
| Backend main code | 209 files, 24,141 lines |
| Frontend code (non-spec) | 117 files, 20,822 lines TS; 1,837 HTML; 4,716 SCSS |
| `TODO` / `FIXME` / `XXX` | 0 |
| `catch (Exception/Throwable/RuntimeException)` in backend | 4 |
| Backend classes over 500 lines | 6 |
| Frontend components over 500 lines | 9 |
| Interfaces with exactly one implementation | 5 |
| Endpoints not called by the frontend | 4 (plus 2 testing hooks used by the browser tests) |

## 2. Findings

### Q-1. Audit and error payloads are JSON built by string concatenation — Medium — Verified

- **Location:** 50 lines in 17 files build JSON with `"{\"…\":" + value + "}"`; 21 of them insert a **string** value. Examples: `identity/UserAdminService.java:91` (e-mail), `:180`, `:226`, `:288`; `approval/ApprovalEngine.java:489` (channel); `importdata/ImportService.java:128` (hash); `identity/ProblemResponses.java:22-23` (title, detail and code of an error response).
- **Explanation:** numeric and enum values are safe. A free-text value containing a quote or backslash produces invalid or altered JSON. The e-mail case is reachable from user input (report 03, S-13). A `JsonMapper` is already injected elsewhere (`platform/web/Idempotency.java`).
- **Measurable value:** removes a class of malformed audit records; one helper replaces 50 hand-written literals.
- **Recommendation:** a small `AuditPayload.of("key", value, …)` helper in `audit` that serialises with the mapper; use it wherever a string is inserted first, the rest opportunistically.
- **Complexity:** S.
- **Verification:** unit test with a value containing `"` and `\`; stored payload parses as JSON.

### Q-2. The same rule implemented several times with different behaviour — Medium — Verified

| Rule | Copies | Difference |
|---|---|---|
| Page-size validation | 8 places | 4 reject an invalid size with 400 (`approval/ApprovalQueries.java:86`, `notification/NotificationController.java:51`, `request/RequestAdminService.java:120`, `request/RequestController.java:84`); 4 silently fall back to a default (`dashboard/DashboardService.java:102`, `logistics/LogisticsService.java:94`, `request/RequestWorkflow.java:78` with a cap of 200 instead of 100, `supplier/SupplierService.java:45`) |
| LIKE wildcard escaping | 7 places | Three techniques: backslash with `ESCAPE` (`request/RequestController.java:188`, `request/RequestAdminService.java:769`, `messaging/InternalMessagingStore.java:404`, `identity/UserDirectory.java:115`), bracket escaping (`request/RequestLogisticsQueries.java:64`), and none (`supplier/SupplierService.java:56`) |
| "Is administrator" check | `Absences.requireAdmin` called from 4 classes, including the testing controller | An absence helper used as a general role check; `identity` has its own role helpers |
| Private `audit(...)` helper | 8 copies | Same shape per service: build an `AuditRecord` from user, request and action |

- **Explanation:** each copy is a few lines, so line count is not the issue. The issue is that the API behaves differently from one list to another, and that one copy (supplier search) is missing the rule.
- **Measurable value:** one behaviour for all lists; one place to fix a bug; about 60 lines removed.
- **Recommendation:** a `PageRequests.of(page, size)` validator and a `SqlLike.contains(text)` helper in `platform`; move the administrator check to `identity`. Leave the per-service `audit(...)` helpers: they are short and specific.
- **Complexity:** S.
- **Verification:** one parameterised test over all list endpoints: size 0 and size 1,000 give the same documented answer.

### Q-3. Four near-identical numbering classes — Low — Verified

- **Location:** `request/RequestNumbering.java` (72 lines), `ordering/PoNumbering.java` (64), `receipt/ReceiptNumbering.java` (58), `logistics/ShipmentNumbering.java` (58). After replacing the document name, `PoNumbering` and `ReceiptNumbering` differ in 14 lines, `ReceiptNumbering` and `ShipmentNumbering` in 8.
- **Explanation:** each locks, reads or creates a `document_counter` row, increments it and formats a number. They live in four modules because each module owns its document.
- **Measurable value:** about 180 lines down to about 80; one place for the locking logic, which has already needed careful fixes (S1CR-03, S1DR-10 in the comments).
- **Recommendation:** one `DocumentNumbering` in `platform.persistence` taking the counter kind and a formatter; the four classes become one-line delegates or disappear. Low priority: the code works and is tested.
- **Complexity:** S.
- **Verification:** existing concurrency tests for numbering pass unchanged.

### Q-4. Code that nothing calls — Low — Verified

| Item | Location | Evidence | Action |
|---|---|---|---|
| `Idempotency.purge()` | `platform/web/Idempotency.java:104` | Only caller is a test | Schedule it (report 05, DB-4) |
| `AuditLog.verifyAll()` | `audit/AuditLog.java:92` | No caller in main code | Schedule it and expose the result to administrators (report 03, S-12) |
| `GET /api/v1/admin/approvals/awaiting-circuit` | `approval/ApprovalAdminController.java:134` | No reference in `FrontEnd/src/app`, `e2e` or backend tests | Build the screen or remove |
| `GET /api/v1/assistant/threads` | `assistant/AssistantController.java:46` | Same | Same |
| `GET /api/v1/admin/requests/action-matrix` | `request/RequestAdminController.java:110` | Same | Same |
| `GET /api/v1/requests/statuses` | `request/RequestController.java:57` | No frontend reference; one backend test | Keep if it is a public API promise, else remove |
| `internal_participant.removed_at` | V22 | No screen sets it; server rules exist and are tested | Keep; build removal when the feature is scheduled |

- **Explanation:** unused endpoints are attack surface without a user. Three of them are also untested.
- **Complexity:** S.

### Q-5. Hand-rolled CSV in two directions — Low — Verified

- **Location:** export `dashboard/ErpExportService.java:149-158` (quoting only; report 03, S-14); import `importdata/ImportService.java:185` (`split(",")`, as reported by the security review).
- **Measurable value:** one shared, tested CSV reader and writer (about 60 lines, no dependency) removes two defects.
- **Complexity:** S.

### Q-6. Large files — Low — Verified

- **Backend:** see report 02, A-6.
- **Frontend:** see report 07, UI-3. `features/requests/request-form.ts` at 1,308 lines is the one file where size is already a cost: it is the most-edited screen and has the highest regression risk.
- **Recommendation:** split when next changed, not as a project of its own.

### Q-7. Repository hygiene — Low — Verified

- **Location:** `tools/` holds 14 tracked output files of manual runs (1.2 MB: `_e2e_full_out.txt`, `_verify-be-full.log`, a Python verification script and its report); `FrontEnd/proxy.conf.8081.json` is tracked beside `proxy.conf.json` and belongs to one past QA instance; `README.md` describes Sprint 3; `build/` and `scripts/` exist at the root, the latter empty.
- **Recommendation:** remove run outputs from the repository (keep evidence under `docs/qa/` when it is evidence); delete the instance-specific proxy file; refresh the README.
- **Complexity:** S.

### Q-8. Frontend duplication — Low — Hypothesis

- **Evidence:** not measured line by line in this audit. Indicators: inline templates and styles in most components; filter bars, pagination pills and action dialogs appear in several features (`requests-list`, `approvals-inbox`, `purchasing-inbox`, `request-admin`); shared UI exists (`shared/ui/button`, `icon`, `skeleton`, `stage-ring`).
- **Recommendation:** before the UI phase, list repeated patterns from the screens (filter strip, data table with states, confirm dialog) and promote them to `shared/ui`. This is the precondition for a consistent redesign and is cheaper than restyling each screen.
- **Verification:** a duplication scan (`jscpd` run locally, not added as a dependency) on `FrontEnd/src/app`.

### Q-9. Dependency versions — Low — Verified

- **Backend:** Spring Boot 4.1.2 parent manages everything except Spring Modulith 2.1.1, springdoc 3.1.1 and the SQL Server authentication DLL 13.4.0 (needed only for Windows integrated authentication, that is, local development; a server deployment with SQL logins does not need it). No unused dependency found. Not scanned for vulnerabilities (report 03, section 5).
- **Frontend:** Angular 22.2, TypeScript 6.0, ESLint 10, Vitest 5. Production dependencies: 0 known advisories. Development tooling: 11 (1 high in `vite`, fix available through `npm audit fix`; the `typescript-eslint` ones need a major version change).
- **Recommendation:** apply the non-breaking fixes; add both scans to CI.

## 3. Error handling and logging

| Aspect | Evidence | Assessment |
|---|---|---|
| Central handler | `platform/web/ApiExceptionHandler.java`: validation, optimistic lock (409), lock failure (503 `busy`), unexpected (500 with a reference id) | Sound |
| Error codes | Kebab-case `code` in every problem answer, translated by the frontend (`errors.<code>`) | Sound; three keys were added for messaging |
| Broad catches | 4 in the backend | Sound |
| Security errors | JSON answers instead of redirects (`identity/ApiSecurityHandlers.java`), but `identity/ProblemResponses.java:22-23` builds the JSON by concatenation | Use the mapper (Q-1) |
| Logging | 21 statements; no request id | Too little (report 02, A-2) |

## 4. Abstractions examined and judged justified

| Abstraction | Implementations | Why it should stay |
|---|---|---|
| `FileStorage` | 1 | Port for object storage when a second node or cloud hosting arrives (ADR-003) |
| `AttachmentScanner` | 1 (no-op) | Port for the antivirus adapter that S-1 requires |
| `MailTransport` | 3 modes behind one interface | File, SMTP and failing transports are all used |
| `AssistantModel` | 1 | Provider choice is pending (ADR-012) |
| `NoticeLinkResolver` | 2 | Real polymorphism |
| `CredentialStore` | 1 | Small; isolates password storage from user administration. Could be inlined, with no measurable gain |
| `DatabaseProbe` | 1 | Lets the status endpoint be tested without a database. Keep |
| `ScopedRequests` + `RequestAccessPolicy` | — | The central visibility rule; the most valuable abstraction in the code |
| `Idempotency`, `TransactionLocks`, `RequestRateLimiter` | — | Each used from many modules |
| `*Views` record holders | one per module | Keep DTOs out of entities; consistent |

Nothing was found that qualifies as over-engineering: no generic repository layers, no mapper frameworks, no event bus beyond Spring's, no speculative interfaces beyond the ports above.

## 5. Not verified

- Unused private methods, parameters and CSS classes (needs IDE inspection or a coverage run; not done).
- Unused translation keys among the 1,494 (the parity test checks equality between languages, not use).
- Frontend duplication percentage (Q-8).
- Test quality beyond counts: assertions were read only for the messaging tests.
