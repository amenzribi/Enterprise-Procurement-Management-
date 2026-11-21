# 04. Performance, scalability and load-test plan

Static review of the code and configuration, plus the measurements already taken on 2026-11-14 for internal messaging. **No load test was run in this audit.** Every capacity figure below is an estimate from reading the code and is labelled as such; section 4 is the plan to replace estimates with measurements.

## 1. Summary

At the scale asked for (25 to 100 concurrent users) the design should hold on one node: requests are short, lists are paginated and capped, there is no polling from the browser, and the only measured path (message send) answers in about 30 ms.

The risks are not raw speed. They are **unbounded waits**, **unmeasured contention on one row per company**, and **defaults never sized**:

- A reader can wait forever behind a writer (database setting plus no timeouts).
- Every audited write of a company queues on one row.
- The connection pool is the default 10, with no evidence that it fits.
- Nothing is measured in the running application: no metrics, no request timing, no slow-query log.

The application runs on **one node only** today; four pieces of in-memory state block a second node.

## 2. What the code shows

| Topic | Evidence | Assessment |
|---|---|---|
| Connection pool | HikariCP defaults: 10 connections; only `connection-timeout: 5000` is set (`application.yml`) | Unsized (P-2) |
| Request threads | Tomcat defaults (200 threads); no `spring.threads.virtual` setting | 200 threads compete for 10 connections; waiters fail after 5 s |
| Isolation | `READ_COMMITTED_SNAPSHOT` off; JDBC `lockTimeout=-1`; no query or transaction timeout except the status probe | Unbounded waits (P-1) |
| Per-request overhead | 2 queries to load the user and roles on every authenticated request (`identity/CurrentUserProvider.java:37-54`, `UserDirectory.java:176-201`), plus `CurrentUserRefresh` | Acceptable; cacheable later (P-6) |
| Lists | Server-side pagination, size capped at 100 (200 for the workflow list); sort by allow-list | Sound |
| Request list | JPA specification with a page query and a count query (`request/ScopedRequests.java:29-30`); lines fetched in batches of 50 (`hibernate.default_batch_fetch_size`) | Sound; cost of the visibility predicate unmeasured |
| Writes | Each state change locks the company's audit chain head until commit (`audit/AuditLog.java:52-88`) | Throughput ceiling per company (P-3) |
| Document numbers | `sp_getapplock` with a 2 s timeout, then 503 `busy` (`platform/persistence/TransactionLocks.java:28`); released early after first-of-year creation | Sound; visible to users as 503 under bursts |
| Deadlocks | Submission retries only on SQL Server error 1205 (`request/RequestService.java:184-197`); messaging uses a fixed lock order | Sound where applied; other write paths unverified |
| Files | Upload reads the whole file into memory (`document/AttachmentController.java:57`), download too (`document/FileStorage.java:41`); limit 20 MB | Memory risk under concurrent large files (P-5) |
| Jobs | Reminders every minute, outbox every 15 s (20 e-mails per batch), purge every 6 h, stream heartbeat every 25 s | Light; outbox drains at most 80 e-mails per minute |
| Browser load | No polling timers; one Server-Sent Events stream per signed-in employee tab; no automatic HTTP retry | Idle users cost almost nothing |
| Caching | None on the server (no cache library, no `@Cacheable`). Static assets are content-hashed (`outputHashing: all`), so they can be cached for a year by the web server | Reference data re-queried each time (P-6) |
| Observability | No metrics registry, no request timing, no correlation id, 21 log statements in the whole backend | Cannot diagnose in UAT (P-4) |
| Frontend | 34 of 35 routes lazy; initial bundle 524.60 kB (budget 500 kB); no list virtualisation | See report 07 |

**Measured on 2026-11-14 (internal messaging, one node, normal load):** send API median 30.5 ms, p95 55.4 ms; live event 2–3 ms after the response; 0 of 40 events missed. A deadlock on simultaneous retries of one message was found by a stronger test and fixed. This is the only part of the system with measurements.

## 3. Findings

### P-1. Requests can wait without limit — High — Verified

- **Location:** database option `READ_COMMITTED_SNAPSHOT` off (catalog); `lockTimeout=-1`; no `queryTimeout`, no `@Transactional(timeout)` (grep of `BackEnd/src/main`).
- **Explanation and impact:** one stuck write transaction blocks every reader of the same rows; blocked requests keep their connection; with 10 connections the application stops answering in seconds and reports nothing. Detail and the change procedure are in report 05, DB-3.
- **Recommendation:** enable RCSI; `lockTimeout=10000` in the JDBC URL; a default transaction timeout (30 s); Hikari `leak-detection-threshold` in UAT.
- **Complexity:** S to configure, M to re-verify.
- **Verification:** recovery scenario R-2 in section 4.

### P-2. Connection pool and threads were never sized — Medium — Verified (effect: Estimate)

- **Location:** `application.yml` (`spring.datasource.hikari`: only `pool-name` and `connection-timeout`).
- **Explanation:** 10 connections serve up to 200 request threads and 4 scheduled jobs. Outside a transaction each `JdbcTemplate` statement borrows and returns a connection, so reads hold one briefly; a write holds one for its whole transaction. *Estimate:* at 100 users with 8–12 s think time (about 10 requests per second) and 30–60 ms per request, average use is under 1 connection and the pool is ample. It becomes the limit only when requests block (P-1, P-3) or files are large (P-5).
- **Recommendation:** do not raise the pool by guesswork. Measure `hikaricp.connections.pending` and acquisition time in the 100-user test; raise only if waiting appears while SQL Server CPU has headroom. Consider Java 21 virtual threads (`spring.threads.virtual.enabled=true`) only after P-1 is fixed, since they remove the thread limit and make pool pressure worse.
- **Complexity:** S.
- **Verification:** pool metrics during test L-3.

### P-3. All writes of one company queue on one row — Medium — Verified (ceiling: Estimate)

- **Location:** `audit/AuditLog.java:52-88`; chain key per company or `global` (`audit/AuditHasher.java:73-75`).
- **Explanation:** see report 05, DB-5. *Estimate:* if the part of a transaction after its audit call takes 20–40 ms, one company sustains roughly 25–50 audited writes per second. A realistic 100-user mix produces 1–3 writes per second.
- **Business impact:** none at 100 users; the first limit reached in a single large company at 500 users with bursts, and the multiplier of any slow transaction.
- **Recommendation:** measure with scenario C-1; keep the audit call last in each transaction.
- **Complexity:** M.

### P-4. The running system cannot be measured — High — Verified

- **Location:** no `MeterRegistry`, `@Timed` or Prometheus dependency in `pom.xml` or code; actuator exposes `health` and `info` only; no logging configuration file; no MDC or request id; 21 `log.info/warn/error` calls in 24,000 lines.
- **Explanation:** when UAT users report "it is slow" there is no request latency, no pool usage, no slow-query trace and no way to tie a browser error to a server log line. The 25-second send investigated on 2026-11-14 could only be explained by external monitoring of the workstation.
- **Business impact:** incidents in UAT and production cannot be diagnosed; load-test results cannot be attributed to a cause.
- **Recommendation:** Micrometer is already on the classpath through the actuator starter. Expose `metrics` (and `prometheus` if the client has a collector) to administrators only; enable HTTP server timing histograms, Hikari and JVM metrics; add a request-id filter that sets an MDC value, returns it as a response header and includes it in `ProblemDetail`; write access logs with duration; log statements slower than a threshold. No new infrastructure.
- **Complexity:** M.
- **Verification:** every error shown in the browser carries an id found in the server log; a dashboard or a metrics dump shows p95 per endpoint after test L-1.

### P-5. Files are held fully in memory — Medium — Verified

- **Location:** `document/AttachmentController.java:57` (`file.getBytes()`), `document/FileStorage.java:41` (`Files.readAllBytes`), `document/ContentSniffer.java:63` (zip inspection in memory); limit 20 MB per file (`application.yml`).
- **Explanation:** each upload or download allocates the whole file, and the scan and hash work on the array. Twenty concurrent 20 MB transfers need about 400 MB of heap at once. No heap size is set anywhere.
- **Business impact:** out-of-memory restart under concurrent large uploads; every signed-in user loses their session (sessions are in memory).
- **Recommendation:** stream downloads (`InputStreamResource`), hash and store uploads as a stream, sniff from the first bytes; set an explicit heap size in the deployment.
- **Complexity:** M.
- **Verification:** scenario F-1; heap stays flat.

### P-6. Reference data is read from the database on every request — Low — Verified

- **Location:** user and roles per request (`CurrentUserProvider`); form options, categories, units, companies and approval rules on each use.
- **Explanation:** two indexed queries per request are cheap, and reloading roles on each request is a deliberate security property (a revoked role takes effect at once).
- **Recommendation:** leave the user load as it is. Cache truly static reference lists (countries, currencies, units) in memory with a short lifetime if the load test shows them in the top statements. Add `Cache-Control` for hashed static assets at the web server (report 08).
- **Complexity:** S.

### P-7. One node only — Medium — Verified

| Node-local state | Location | Needed for a second node |
|---|---|---|
| HTTP sessions | Servlet container memory | Sticky sessions (users lose their session if their node restarts), or a shared session store (Spring Session JDBC on the existing SQL Server; no new infrastructure) |
| Login throttle | `identity/LoginThrottle.java:36` | Limits become per node; acceptable with sticky sessions, otherwise move counters to the database |
| Rate limiter | `platform/web/RequestRateLimiter.java:22` | Same |
| Live message streams | `messaging/InternalMessageStream.java:65` | Sticky sessions plus a per-node poll, or a broker (documented in `docs/architecture/internal-messaging.md`) |
| Cleanup preview sessions | `request/RequestAdminService.java:73` | Sticky sessions |
| File storage on local disk | `app.storage.directory` | Shared volume or object storage behind the existing `FileStorage` port |
| Scheduled jobs | No scheduler lock | Outbox claim is safe on two nodes; reminders are protected by a primary key; purge is idempotent. Safe, with duplicate work |

- **Business impact:** no high availability; a restart signs everyone out and drops live streams.
- **Recommendation:** stay on one node for UAT and the first production; decide the session store and file storage before promising availability targets.
- **Complexity:** M to L.

### P-8. Write endpoints depend on the client sending an idempotency key — Low — Verified

- **Location:** `platform/web/Idempotency.java:61-65` ("Without a key the action simply runs"); the record is written after the action commits (report 05, DB-11); stored records are never purged (DB-4).
- **Explanation:** the Angular client has an idempotency helper (`core/api/idempotency.ts`) and does not retry automatically, so duplicates come only from a user's double click or a manual retry after a timeout.
- **Recommendation:** make the key mandatory on creation endpoints; purge records.
- **Complexity:** S.

## 4. Load-test plan

### 4.1 Tool

**k6** (Grafana k6, open source, one executable, runs on Windows).

- Scripts are JavaScript, close to what the team already writes for Playwright.
- Cookies and the CSRF header are simple to handle.
- Thresholds turn a run into pass or fail.
- Results export to CSV or JSON without a server.

Gatling is equally capable; it was not chosen because it adds a Scala or Java DSL and a second build. JMeter was not chosen because its XML test plans are hard to review in git. The Playwright suite stays the functional check; it is not a load tool.

k6 is a tool on the tester's machine, not a dependency of the application. Scripts would live in a new `perf/` folder at the repository root.

### 4.2 Environment (never shared, never production)

| Item | Requirement |
|---|---|
| Application | A dedicated instance built from the commit under test, run as a jar with production-like settings: not the `dev` profile, secure cookies behind the same reverse proxy as UAT, a fixed heap (for example `-Xms1g -Xmx1g`) |
| Database | A dedicated `procurement_perf` database on a SQL Server sized like the UAT target, with RCSI as decided in P-1; restored from a prepared backup before each run so runs are comparable |
| Data volume | Seeded to a realistic first-year size before measuring: 3 companies, 500 users, 50,000 requests with 3 lines on average, 150,000 approval tasks, 20,000 orders, 300,000 audit events. Empty-database results are not valid |
| Load generator | A separate machine on the same network; its CPU must stay under 70% |
| Disk | Test output outside OneDrive or any synced folder (lesson of 2026-11-14) |
| Mail | `MAIL_MODE=file` to a local folder, or an SMTP sink; never a real mail server |

**Test users.** The demo profile has 14 accounts, far too few: one account used by many virtual users shares its session limits, its per-user send limit and its unread counts. A seed script for the performance database only (not a Flyway migration) creates `perf.requester.0001…`, approvers, buyers and receivers per company, all with one known password hash. One virtual user = one account = one session.

**Protections that affect the test, and how to respect them:**

- Sign-in throttle: 5 failures per account and 50 per address per 15 minutes. Successful sign-ins are not limited, so correct passwords are enough; do not disable the throttle.
- CSRF: `GET /api/v1/auth/csrf`, then send the `XSRF-TOKEN` cookie value as `X-XSRF-TOKEN` on every write; fetch it again after sign-in (the token is replaced).
- Internal messaging: 30 sends per minute per user.
- Password hashing costs CPU by design; sign in each virtual user once and keep the session, as real users do.

### 4.3 Workload profiles

Think time between actions: random 5–15 s (median about 8 s), 20–40 s while "reading" a request. One iteration per virtual user lasts 2–4 minutes. Share of virtual users:

| Profile | Share | Actions per iteration |
|---|---|---|
| Requester | 45% | Overview; request list (page 1, a filter, a search); open 2 requests; every third iteration create a draft with 3 lines, save, submit; check notifications |
| Approver | 20% | Approval inbox and count; open 2 tasks with the request and its tracker; approve (80%), return (10%) or ask a question (10%); every fifth iteration read and send an internal message |
| Buyer | 20% | Purchasing queue with filters; open a request; accept an assignment; create an RFQ or record a quote; every fourth iteration award and create an order; logistics inbox |
| Receiver | 10% | Receipts of a request; record a goods receipt; a second receiver confirms it |
| Administrator / manager | 5% | Dashboards (pipeline, management); user list with search; request administration list; CSV export once per iteration |

Every signed-in employee also holds one live stream open (`GET /api/v1/internal-messaging/stream`), as the browser does.

Expected request rate (*estimate, to be confirmed by the first run*): about 0.1 request per second per virtual user, so 100 users ≈ 10 requests per second, of which about 15% are writes.

### 4.4 Scenarios

| ID | Scenario | Users | Ramp-up | Steady | Purpose |
|---|---|---|---|---|---|
| L-0 | Smoke | 5 | 1 min | 5 min | Scripts and data are correct; baseline latencies |
| L-1 | Normal day | 25 | 5 min | 30 min | Expected daily load |
| L-2 | Busy day | 50 | 5 min | 30 min | Peak of a normal week |
| L-3 | Target peak | 100 | 10 min | 45 min | The contractual target; pass or fail on the thresholds below |
| X-1 | Stress (optional) | 200 | 10 min | 20 min | Where latency starts to degrade |
| X-2 | Stress (optional) | 500 | 15 min | 20 min | Breaking point and how it fails: errors must be 503 `busy` or 429, never hangs or data errors |
| K-1 | Morning sign-in | 100 sign-ins in 2 min, then profile mix | — | 10 min | Password-hash CPU, the `global` audit chain, session creation |
| C-1 | Contention, one company | 50 writers in one company, 2 s think time | 2 min | 15 min | Audit chain ceiling (P-3); submissions per second; 503 rate on numbering |
| C-2 | Concurrent updates | 20 pairs editing the same draft; 20 pairs deciding the same task; 20 pairs confirming the same receipt | 1 min | 10 min | Exactly one of each pair succeeds; the other gets 409; no lost update, no deadlock error to the user |
| C-3 | Duplicate submissions | Same idempotency key or client message id sent 8 times at once, 200 rounds | — | 5 min | One record per key; no 5xx (the messaging case already passes as integration test `t10`) |
| F-1 | Files | 20 users uploading and downloading 15–20 MB files | 2 min | 10 min | Heap and response times (P-5) |
| R-1 | Search and reporting | 30 users on list searches, deep pages (page 50+), dashboards and CSV export | 3 min | 20 min | Slow queries and missing indexes (report 05, DB-2, DB-8, DB-9) |
| K-2 | Soak | 50 | 5 min | 6 h (8 h if possible) | Memory growth, connection leaks, table growth, stream stability, job backlog |
| R-2 | Recovery | 50 steady, then inject faults | — | 30 min | See below |

**Recovery tests (R-2), each during steady load:**

1. Restart the application: users are signed out (expected today); after sign-in everything works; no duplicated e-mail; no half-written request.
2. Hold a write lock on one request row for 60 s from a SQL session: other requests keep working; readers of that row answer or fail within the lock timeout; the pool recovers without restart.
3. Stop SQL Server for 30 s: the application answers 503 quickly, `health` shows DOWN, and it recovers by itself when the database returns.
4. Fill the mail folder or stop the SMTP sink: user actions still succeed; e-mails are retried later.
5. Drop the network of the load generator for 20 s: live streams reconnect and resynchronise.

### 4.5 Measurements

| Layer | Metrics |
|---|---|
| Client (k6) | Per endpoint group: p50, p95, p99, max latency; requests per second; error rate by status code; iterations completed; time to first byte |
| Application | Tomcat busy threads; Hikari active, idle, pending and acquisition time; heap used, GC pause time and count; CPU; open live streams; outbox backlog (`notification_outbox` rows pending); count of 503 and 409 by endpoint |
| SQL Server | CPU; batch requests per second; lock waits (`LCK_M_*` in `sys.dm_os_wait_stats`); blocked sessions sampled every second; deadlocks (system health session); top statements by total time (`sys.dm_exec_query_stats`); missing-index DMVs; log and tempdb growth; page life expectancy |
| Host | CPU, memory, disk queue, network on the application and database machines |

P-4 (metrics) must be done first, or the application column cannot be filled.

### 4.6 Proposed acceptance thresholds (to agree with the client)

For L-1 to L-3, on the seeded database:

| Measure | Threshold |
|---|---|
| Read endpoints (lists, detail, inbox) | p95 ≤ 500 ms, p99 ≤ 1,500 ms |
| Write endpoints (save, submit, decide, receive) | p95 ≤ 800 ms, p99 ≤ 2,000 ms |
| Sign-in | p95 ≤ 1,000 ms |
| Dashboards and exports | p95 ≤ 3,000 ms |
| Errors | 5xx ≤ 0.1% of requests; no 500; 503 `busy` ≤ 0.5% of writes |
| Hikari pending connections | 0 for 99% of samples |
| Application CPU / database CPU | ≤ 70% average at 100 users |
| Data integrity after the run | Audit chains verify; no duplicate document numbers; request, task and order counts match what the scripts created |
| Soak | Heap after GC flat (slope under 5% per hour after the first hour); no growth of connections or threads |

For X-1 and X-2 there is no latency target: the result is the capacity figure and the proof that overload produces clean refusals.

### 4.7 Order of work

1. Add metrics and request ids (P-4). 
2. Decide RCSI and timeouts (P-1) and apply them to the performance environment.
3. Write the seed script and the k6 scripts; run L-0.
4. Run L-1, L-2, L-3; fix what the top-statement and missing-index queries show (report 05, DB-2); rerun.
5. Run C-1 to C-3, F-1, R-1; then K-2 and R-2.
6. Run X-1 and X-2 last, and record the capacity figure in the deployment document.

## 5. Not verified

- Every latency and capacity figure except the messaging measurements of 2026-11-14.
- The cost of the request-visibility predicate on a large table.
- Whether any write path other than submission and messaging can deadlock.
- Memory behaviour under file transfers.
- Behaviour with RCSI on.
- Frontend rendering performance on large forms (report 07).
