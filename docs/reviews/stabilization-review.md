# Stabilization review (before the UI redesign)

- Date: 2026-11-08. Scope: request-to-closure lifecycle, security boundaries, performance baseline.
- Reviewer: Developer session (Claude Code). **Self-review, not independent QA or reviewer sign-off.**
- Code state: `main` at `1b59c4e` plus uncommitted changes. `git diff -- BackEnd` SHA-256 `9F91C80C…D6832F9`; V18 SHA-256 `E4E71EDF…1A1675`. Line numbers below refer to `1b59c4e`.
- Stack as installed: Java 21.0.12, Spring Boot 4.1.2, Angular 22.2, SQL Server 2025 RTM (17.0.1000.7), Node 24.20, npm 11.19.

## Verdict

| Decision | Verdict |
|---|---|
| Phase 2 (visual redesign) | **Conditional go.** No open finding is in the presentation layer. The redesign must keep D-01 to D-06 visible and must not imply those paths work. |
| Sprint closure under `CLAUDE.md` | **No open Major.** D-01 and D-02 were resolved afterwards; see "Resolved after this review". Independent QA and review are still outstanding. |
| Pilot or production | **No-go.** Pen-test, MFA, restore drill and capacity on production-like hardware have not been done. |

## Tests actually run

| Check | Code | Result |
|---|---|---|
| `mvnw verify -Pdb-it` in the VS Code tree | `1b59c4e` | Invalid: 122/156 errors. The VS Code Java language server rewrote `target/classes` during the run (`NoClassDefFoundError`; class files timestamped mid-run). |
| `mvnw verify -Pdb-it`, isolated worktree | `1b59c4e` | **152/156.** All four failures are in `RequestSubmissionIntegrationTests`; see D-06. |
| `RequestSubmissionIntegrationTests` alone ×3 | `1b59c4e` | 3/3 runs failed one test (`parallelSubmissions…`, 503 `busy`). |
| New regression tests on old code (red) | `1b59c4e` | 8 failed as intended. See "Regression coverage" for those that did not. |
| `mvnw verify -Pdb-it`, isolated copy | fixed | **167/167, BUILD SUCCESS.** |
| `RequestSubmissionIntegrationTests` alone ×3 | fixed | 3/3 runs passed. |
| `npm run lint` · `npm test` · `npm run build` | `1b59c4e` (no frontend change since) | Lint clean · 212/212 · build OK, with a budget warning (504.87 kB initial, budget 500 kB). |

**Not run:** Playwright end-to-end and axe suites, penetration test, multi-node or production-like load, MFA, restore drill.

Running Maven in the VS Code workspace is unreliable while the Java extension is active. Use a copy outside it, or disable automatic builds.

## Lifecycle status

| Step | Status | Note |
|---|---|---|
| Request, approval, self-approval prevention | Implemented; tested | Authors excluded at routing, one-tap link and delegation (`ApproverResolver:71`, `ApprovalLinks:139`). |
| Purchasing assignment | Implemented; tested | Manager assigns, buyer accepts. |
| Sourcing, RFQ, quotation | Implemented; tested | Supplier link is token-based. Re-submission while open is accepted by design (S4SL-01). |
| Technical and commercial validation | Implemented; tested | `MANAGEMENT_VALIDATION` is accepted by award but **nothing enters it**: planned. |
| Award, purchase order | Implemented; tested | Snapshots quote values. |
| Shipment | Implemented; tested | Capped at PO quantity. |
| Receipt, acceptance | Implemented, **dead end on any rejection** | D-01. |
| Service acceptance | Implemented | Attestation path by design (S7-AC-05); no separate SERVICE type yet. |
| Invoice | Manual capture | Three-way match deferred (FR-RCV-03). |
| Payment | **Capture only** | D-02. No bank or ERP payment. |
| Closure | Implemented; tested | Never automatic. |
| Assistant | **Demo only** | Deterministic adapter under `demo`; disabled otherwise; no live provider (ADR-012). Reuses user-scoped queries, so `securitymaxxing:llm-app-security`-style concerns about a live model remain unverified. |
| Session revocation | Implemented; read and tested | Roles and credentials version reloaded every request (`CurrentUserRefresh`). |
| Company isolation | Implemented; tested | 404 across companies. Department scope applies through `DEPARTMENT_MANAGER` grants. |

## Fixed

| ID | Severity | Location | Evidence | Impact | Fix | Verification |
|---|---|---|---|---|---|---|
| F-01 | Major | `SourcingService:181-183`, `:303` | Anonymous submit accepted `freightAmount: -500`; no check in code or database. | A supplier could lower the landed cost and win the comparison. | Charges must be ≥ 0 and rounded HALF_UP to minor units. | Red → green `quoteCharges…` |
| F-02 | Major | `ReceiptService:278`, `:399` | Spec reserves waivers for the purchasing manager; the requester's close with a waiver returned 200. | Requester could waive a shortfall. Currently reachable only after data correction, since a shortfall blocks `ACCEPTED` (D-01). | Waiver honoured only for the manager. | Red → green `onlyAPurchasingManager…` |
| F-03 | Major | `SourcingService:170-187` | Two first submissions raced on `uq_quotation_rfq_supplier`. | 500 on a supplier double-click. | Take the request lock before reading, as the buyer path does. | Red → green `concurrentFirstSupplier…` |
| F-04 | Minor | `SourcingService:176`, `RequestWorkflow:372` | Link submission during evaluation returned 409 "not in sourcing". | Reveals workflow state to anonymous callers (S4SL-08). | Neutral 404. | Red → green `supplierLinkIsNeutral…` |
| F-05 | Major | `ReceiptService:178`, `:186`; `OrderingService:243`, `:252` | Status read before the row lock; update count ignored. | Concurrent confirmations could duplicate audit events and notices (ADR-006). | Check the update count. | Guarded by `concurrentReceiptConfirmation…`; race did not reproduce on old code. |
| F-06 | Minor | `SourcingStore:149`, `SourcingService:228` | Closing twice wrote two audit events. | Audit noise. | Update only `OPEN`; audit only on change. | Red → green `closingAnRfqTwice…` |
| F-07 | Minor | `ReceiptService:125-130` | Negative or sub-precision quantities hit the database CHECK. | 500 instead of a validation error. The CHECK prevented bad data. | Validate; 422. | Red → green `invalidReceiptQuantities…` |
| F-08 | Minor | `ReceiptService:252-263` | Unknown currency returned 500; amount not rounded. | Error, and precision outside the money rule. | Validate the code; round to minor units. | Red → green `invoiceRejects…` |
| F-09 | Minor | `RequestWorkflow:118,140,143,147` | `?status=NOPE` returned 500. | Bad input reported as a server fault. | 400 `invalid-parameter`. | Red → green `unknownQueueFilter…` |
| F-10 | Major | `UserAdminService:48-50` | Company scope applied after `OFFSET/FETCH`. | Company admins could get short or empty pages and miss users. | Scope in SQL. | Read and retested; see "Regression coverage". |
| F-11 | Minor | `UserAdminService:146-171` | No global-admin check on removal, unlike the other actions. | A company admin could remove a company role from a global admin. | Same 403 as the other actions. | Code read; no dedicated test. |
| PERF-01 | Major | `RequestService` list; V4 indexes | Plan: clustered scan of 50,120 rows plus Sort. | Default list cost grew with all rows: about 513 ms SQL CPU per call. | Filtered index in V18. | Load test below. |
| PERF-02 | Major | `RequestWorkflow.snapshot:582`; `application.yml` | Lazy `lines` loaded per request with no batch size. | N+1 per list page, up to 5,000 per dashboard. | `default_batch_fetch_size: 50`. | Load test below. |
| PERF-03 | Major | `LogisticsService:93-100` | Loaded every confirmed PO in the group, then paged in Java: about 16,000 reads per call at 5,000 POs. | Cost grew with the group on every inbox open. | Scope and page in SQL. | About 2 reads per statement; `logisticsInboxPages…`. |

## Resolved after this review

Decided by the product owner on the developer's recommendation, 2026-11-08. Final state: backend 171/171, frontend 218/218.

| ID | Resolution | Verification |
|---|---|---|
| D-01 | `POST /receipts/requests/{id}/shortfall-acceptance`: purchasing manager only, reason required, every receipt must be confirmed first. Stored in `shortfall_acceptance` (V19), audited, then the normal invoice and closure path applies. The receipt form now captures quantities per line; before, it could only record a full receipt. | `aManagerAcceptsARejectedUnit…`, `shortfallAcceptanceWaits…`, including 403 and cross-company 404 |
| D-02 | `POST /receipts/requests/{id}/invoice/paid`: purchasing only, once, audited. Closure still does not require payment, so payment may follow closure. | `aPendingInvoiceCanBeMarkedPaidOnce…`, including 403, 404 and 409 |
| D-04 | The form sends the purchase-order currency and starts from its total. There is still no server rejection of a different currency. | `invoices in the purchase-order currency…` |
| New | The invoice and receipt forms were pre-filled with demo values (`INV-DEMO-1`, amount 35, an attested match, rating 5). All removed; the attestation is now a deliberate tick. Receipt errors now show translated text, not codes. | Unit tests; both closure journeys |

| D-03 | The person who recorded a receipt cannot confirm it (403 `self-confirmation`). | `theRecorderOfAReceiptCannotConfirmIt` |
| D-07 | An expired quote cannot be awarded (409 `quote-expired`). A quantity that differs from the request needs a reason, kept in the award's audit comment (422 without one). | `anExpiredQuoteCannotBeAwarded`, `aQuantityDifferentFromTheRequestNeedsARecordedReason` |

The rows for D-01, D-02, D-03, D-04 and D-07 below record the findings as first reported.

**Correction, 2026-11-09:** the receipt form described above as resolving D-01 was incomplete. It failed on lines that did not arrive, partial receipts and split shipments, and an unattested invoice could not be closed. Fixed and retested; see [verification-report.md](../design/verification-report.md).

## Open findings

| ID | Severity | Location | Evidence | Impact | Proposed fix | Verification needed |
|---|---|---|---|---|---|---|
| D-01 | **Major** | `ReceiptService:204-205`, `:246`, `:314`; `LogisticsService:167` | Reproduced: receive 1, reject 1, confirm → `NON_CONFORMING`; then invoice, close with waiver, re-receipt and re-shipment all return 409. | Any rejected unit strands the request. Only an administrative cancel ends it. | **Decision:** a manager "accept with shortfall", or allowing replacement shipments for rejected quantity. | API test for the chosen path. |
| D-02 | **Major** | `ReceiptStore:155` (no callers); `ReceiptService:257-265` | Payment is set once at invoice time. | A `PENDING` invoice can never become `PAID`. Closure does not require payment. | **Decision:** add a payment update, and say whether closure requires it. | API test. |
| D-03 | Medium | `ReceiptService:383-388` | A RECEIVER may confirm their own receipt; S7-AC-04 says "after requester confirmation". | One person can record and accept goods. | **Decision:** restrict to the requester, or require a different confirmer. | Role test. |
| D-04 | Medium | `receipt-actions.ts:251`; `ReceiptService` | Invoice currency defaults to the request currency; the PO uses the quote currency. No server comparison. | An invoice can be recorded in the wrong currency. | Default to the PO currency and reject mismatches. Server check held back until the UI is corrected. | UI and API test. |
| D-05 | Medium | `DashboardService:152`; `ErpExportService:120` | Reads stop silently after 25 pages of 200. | Dashboards and exports understate beyond 5,000 requests. | SQL aggregates; explicit export limit. | Test above 5,000 rows. |
| D-06 | Medium | `RequestService:262-307`; `TransactionLocks:28` | Number and audit locks are held through approval planning, with a 2 s wait. Failed 4/4 on baseline, passed 4/4 after the fixes. Batch fetching may explain it; **not proven**. | A burst in one company can get 503 `busy` (retryable). Thin margin. | Shorten the locked section or number last. | Burst test on target hardware. |
| D-07 | Medium | `OrderingService:94-108`; `SourcingService:285` | Expired quotes can be awarded; quote quantity may differ from the request. Spec not conclusive. | Possible commitment beyond what was approved. | Confirm the rule. | Unverified against spec. |
| D-08 | Low | `AssistantService:104` | `looksLikeInjection` has an empty branch. | No effect in demo mode. | Resolve before any live provider. | Assistant review with ADR-012. |
| PERF-04 | Medium | Request list count | Now the top query: about 97 ms CPU, 2,657 reads per call. Missing-index DMV still suggests `(archived_at, removed_at)`. | Limits the worst-case list. | Test a covering index for the count. | Plan and load test. |
| PERF-05 | Low | `MessagingService:96,110` | `countUnread` per conversation. Code reading only. | Grows with conversations. | Join in the list query. | Measure with volume. |
| PERF-06 | Low | Frontend build | 504.87 kB initial against a 500 kB budget. | Slightly over budget before the redesign. | Address in Phase 2. | Build output. |

Read committed snapshot is off on both databases, so readers can block on writers. No lock waits appeared in the read-only mix; a mixed workload is unmeasured. The Hikari pool is at its default size of 10 (assumption from no override in configuration; not read at runtime).

## Load test (measured)

Environment: one laptop-class 4-core Windows machine running the app, SQL Server and the driver together. Throwaway database `procurement_loadtest`, since dropped. `-Xmx2g`, profiles `dev,demo`.

Dataset (synthetic): 50,080 requests, 150,080 lines, 5,000 confirmed POs. All bulk requests belong to one requester, which is deliberately pessimistic for the list. Purchase-order award references were disabled in that database.

Workload: closed loop, GET only, 30 s per level. Mix: request list 35%, purchasing queue 25%, `/me` 20%, logistics inbox 15%, pipeline 5%. The purchasing manager's grant is in a different buying company, so **the queue and pipeline did not exercise the bulk data** and their figures are not a capacity claim.

| Build | Concurrency | Throughput | Errors | p95 | p99 | Saturation |
|---|---|---|---|---|---|---|
| Before | 5 | 13.6 rps | 0% | 1,156 ms | 2,260 ms | Not captured (counter failure) |
| Before | 20 | 14.8 rps | 0% | 2,579 ms | 2,985 ms | Not captured |
| Before | 50 | 14.0 rps | 0% | 5,276 ms | 5,835 ms | Not captured |
| After | 5 | 39.4 rps | 0% | 517 ms | 971 ms | app 75%, SQL 99% of one core |
| After | 20 | 88.9 rps | 0% | 527 ms | 781 ms | app 95%, SQL 241% |
| After | 50 | 86.5 rps | 0% | 994 ms | 1,344 ms | app 98%, SQL 220% |

Sequential submission of 40 requests: before mean 119 ms, p95 209 ms; after mean 237 ms, p95 331 ms, with a 4,501 ms outlier. The sample is small and the "after" run was the first after Flyway applied V18 and a cold start, so **no conclusion is drawn about submission speed**.

Throughput levels off near 20 concurrent users on this machine. These figures do not describe production capacity. Writes, attachments, and the outbox under load are unmeasured.

Pagination is server-side on the lists reviewed. Large-table rendering in the browser and connection-pool wait times were not measured.

## Regression coverage

Added 11 tests. Not every one has a red run on old code:

- `concurrentReceiptConfirmationAuditsOnce` did not fail on old code; the race did not occur. It guards the behaviour without proving F-05.
- `companyAdminListIsScopedBeforePagination` did not fail on old code: the demo company admin's scope covers every user. **It cannot detect F-10 on demo data.**
- `logisticsInboxPages…` covers a behaviour-preserving refactor.

## Tools and fallbacks

Named in the brief but not installed in this session; the review applied the same concerns by hand: `agent-protocols`, `java-backend-skills`, `backend-design`, `securitymaxxing`, Ponytail Lite. `backend-performance-review` exists on disk but was not in the session list; its `SKILL.md` was read and its evidence rules followed, without its scripts or JSON report. Used directly: `superpowers:systematic-debugging`, `superpowers:verification-before-completion`. Context7 needs sign-in; no version-specific documentation lookup was necessary. SkillSpector was not applicable: no third-party skill or MCP configuration changed.
