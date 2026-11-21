# 01. Executive summary

**Procurement 360 — Phase 1 enterprise readiness audit.** Completed 2026-11-15. Audit and planning only: no application code, dependency or environment was changed.

**Audited state:** branch `feat/internal-messaging`, base commit `c7b901d`, plus the uncommitted internal-messaging work verified on 2026-11-14. Stack: Angular 22, Spring Boot 4.1.2 on Java 21, Microsoft SQL Server; English, French and Arabic.

## 1. Overall readiness

| Target | Assessment |
|---|---|
| Continued development and internal demonstration | **Ready** |
| UAT with client users on a public HTTPS URL | **Not ready.** About one focused sprint away (report 09, section 10) |
| Production | **Not ready.** Needs UAT plus MFA or single sign-on, an independent penetration test, a restore drill and a load test at target volume, all of which were already listed as open release gates in `STATUS.md` |

**Why not ready, in one paragraph.** The application itself is well built: a clear modular structure, a carefully constrained schema, sound session and token handling, parameterised SQL, central access rules, a disciplined design system and a real test base (208 backend, 253 frontend unit and 66 browser tests passing on 2026-11-14). What is missing is what turns a well-built application into an operable, defensible service:

- protection against insider misuse by administrators;
- safe handling of uploaded files;
- bounded waits in the database;
- any way to observe the running system;
- the whole deployment chain (pipeline, production-like profile, backup, monitoring).

None of the gaps requires redesign.

## 2. Findings by severity

| Area | Critical | High | Medium | Low | Report |
|---|---|---|---|---|---|
| Security | 0 | 5 | 12 | 9 | 03 |
| Database | 0 | 2 | 5 | 5 | 05 |
| Performance and scalability | 0 | 2 | 4 | 2 | 04 |
| Architecture | 0 | 2 | 5 | 4 | 02 |
| Code quality | 0 | 0 | 2 | 7 | 06 |
| UI/UX and motion | 0 | 0 | 2 | 7 | 07 |
| Deployment | 0 | 5 | 2 | 2 | 08 |

Several findings appear in more than one report from different angles (for example the database wait limits: DB-3 and P-1; observability: A-2, P-4, D-5). No finding is rated Critical: nothing found lets an anonymous or ordinary user read or change another company's data, and no injection or leaked secret was found.

## 3. Top 10 technical risks

| # | Risk | Evidence | Report |
|---|---|---|---|
| 1 | **An administrator can act as someone else.** A company administrator can set any colleague's password, or make themselves a delegate for an approver, and then approve; absences, approval rules and exchange rates change without audit records | `identity/UserAdminService.java:201-219`; `approval/Absences.java:84-97`; no audit call in three classes | 03: S-4, S-5, S-6 |
| 2 | **Uploaded files are never scanned yet marked clean, and a script can be uploaded under a document's signature** | `document/AttachmentScanner.java:21-30`; `document/Attachments.java:155`; `document/ContentSniffer.java:26-42` | 03: S-1, S-2 |
| 3 | **One stuck transaction can freeze the application.** Readers wait for writers with no time limit; the pool has 10 connections | Database option and driver setting; no timeouts in code | 05: DB-3; 04: P-1 |
| 4 | **The audit trail can be rewritten by anyone holding the application's database login**, which can also alter the schema | Single datasource; `V3__audit_hardening_and_org_keys.sql:9-11` | 05: DB-1; 03: S-12 |
| 5 | **Rate limits and login throttling can be bypassed behind a normal reverse proxy** | `platform/web/ClientAddressResolver.java:70` | 03: S-3 |
| 6 | **The system cannot be observed.** No metrics, no request ids, 21 log statements; a slow request cannot be explained | Code search | 02: A-2; 04: P-4 |
| 7 | **No pipeline, no production profile, no backup or rollback.** Every instance so far ran with the development profile, which disables secure cookies and enables testing endpoints | `.github/`; `application-dev.yml` | 08: D-1 to D-5; 03: S-15 |
| 8 | **Capacity is unknown.** No load test has ever been run; all writes of one company queue on one audit row; 117 of 213 foreign keys have no index | `audit/AuditLog.java:52-88`; SQL Server catalog | 04; 05: DB-2, DB-5 |
| 9 | **Single node, in-memory sessions.** Any restart signs everyone out; no high availability | Four in-memory maps and servlet sessions | 04: P-7 |
| 10 | **Sign-in is password only**, with a thin policy and no account-wide lockout; supplier quote links can be reused; invoices have no separation of duties | `identity/LoginThrottle.java`; `sourcing/SourcingService.java:372-386`; `receipt/ReceiptService.java:279-300` | 03: S-8, S-9, S-10 |

## 4. Top 10 recommended improvements

| # | Improvement | Effort | Closes |
|---|---|---|---|
| 1 | Close the administrator paths: reset by link only, no self-delegation, audit every rule, rate and absence change, company scope on administrator listings | S | S-4 to S-7 |
| 2 | Tie file extension to detected type now; add a real scanner (or a written risk acceptance) before UAT | S + M | S-1, S-2 |
| 3 | Enable read-committed snapshot, set lock and transaction timeouts, rerun the suites | S + M | DB-3, P-1 |
| 4 | Two database logins (migration and runtime); scheduled audit-chain verification with exported heads | S + M | DB-1, S-12 |
| 5 | Fix client-address resolution; add a startup guard against the development profile; make the mail mode explicit | S | S-3, S-15, S-16 |
| 6 | Request ids, access log with duration, metrics for HTTP, pool, JVM and the outbox | M | A-2, P-4, D-5 |
| 7 | Continuous integration: build, tests against SQL Server, scans, versioned artefacts | M | A-9, D-1 |
| 8 | One automated authorization sweep over all 180 endpoints | M | A-4 |
| 9 | Load-test programme with k6 on a seeded database: 25, 50, 100 users, contention, soak, recovery; then the 20 indexes it confirms | M to L | P-2, P-3, DB-2 |
| 10 | UAT deployment kit: reverse-proxy configuration with security headers, UAT profile, backup and restore drill, runbook | M | D-2 to D-8 |

## 5. Premium UI and motion: conclusion

- GSAP, ScrollTrigger, Lenis and React are **not** in the application. They exist only as assistant skills or connectors in this workspace.
- React Bits is a React library and cannot be used without adding React, which the project rules forbid.
- The cinematic GSAP/Lenis approach is designed for marketing sites. Smooth-scroll hijacking, pinned scenes, custom cursors and split-text reveals would harm long forms, data tables, accessibility and Arabic text.
- **Recommendation: do not add them.** The existing token-based design system is strong (656 logical CSS properties against 7 physical, tokenised motion, global reduced-motion support, identical key sets in three languages). A premium result is reachable with CSS and Angular's built-in features: consistent breakpoints, a route transition, restrained list and overlay motion, and performance work on the 100-line request form.

Detail and the proposed direction are in report 07.

## 6. Skills and tools actually used

| Item | Status |
|---|---|
| Review agent `grill:security` | Used; resumed after the interruption and completed. 25 findings; the 17 rated High or Medium were re-read in source by the lead, except S-17 (import parsing), which is marked as not re-read |
| Agents for database, architecture, frontend and performance | Interrupted by the usage limit before producing anything; **not** rerun. Those reviews were done by the lead with targeted searches and scripts |
| Skill `anthropic-skills:cinematic-gsap-lenis-motion-system` | Loaded and read; assessed in report 07 |
| Skills available but not invoked | `frontend-design`, `design-superpowers:*`, `anthropic-skills:ui-ux-pro-max`, `design-skills:accessibility-audit`, `audit-suite:web-animation-design`, `vectorlab-ux-skills:motion`, `securitymaxxing:*`, `backend-design:*` |
| React Bits skill | Does not exist in this workspace |
| SQL Server catalog queries (read-only, local verification database) | Used for every database figure |
| `npm audit` | Run: 0 advisories in production dependencies, 11 in development tooling |
| Scripted inventory (endpoints, tables, classes, style metrics) | Used; scripts kept in `C:\qa\audit\` outside the repository |
| Connectors that failed or need authorization (Context7, Playwright MCP, Motion Dev UI, Refero) | Not used |
| Load-testing tools, dynamic scanners, backend dependency scanner | **Not run** (planned in reports 03 and 04) |

## 7. Recommended next sprint

**"Safe to expose, possible to observe"**, about two weeks (report 09, section 10):

1. Security fixes of small size: file type check, proxy address, administrator powers, missing audit events, company scope, safe JSON and CSV, startup guard.
2. Database safety: two logins, purge jobs, snapshot isolation with timeouts and a full rerun of the suites.
3. Request ids, metrics and a CI pipeline.
4. The automated authorization sweep.

In parallel, obtain the decisions of gates G2 to G4 below.

## 8. Approval gates before any implementation

| Gate | Decision |
|---|---|
| **G1** | Approve this audit and the next sprint's scope. **No application code changes before this.** |
| G2 | Business rules: quote-link reuse, invoice separation of duties, limits on administrator powers |
| G3 | Infrastructure decisions (ADRs): antivirus, snapshot isolation and timeouts, two database logins |
| G4 | Hosting option, domain, budget, SQL Server licensing for UAT |
| G5 | Load-test thresholds and environment |
| G6 | UI direction: CSS-only motion, no GSAP, Lenis or React |
| G7 | Go for UAT |
| G8 | Go for production |

## 9. What this audit did not verify

- **No dynamic security test, no penetration test, no backend dependency scan.** The security report is a code review.
- **No load test.** Every capacity statement is an estimate; only internal messaging has measurements (send API about 30 ms, 2026-11-14).
- **No execution plans on realistic data.** The databases hold a few hundred rows.
- **No browser session in this audit.** Contrast, rendering performance and screen-reader behaviour were not measured.
- **No deployment.** Proxy behaviour, headers, Linux and Azure SQL compatibility are untested.
- **Authorization** was traced for most services but not line by line for approval read views, dashboards, the purchasing queue and each request-administration action.
- **Hosting costs** are indicative, not quotes.

## 10. Reports

| # | Report |
|---|---|
| 01 | Executive summary (this document) |
| 02 | [Architecture review](02-architecture-review.md) |
| 03 | [Security assessment](03-security-assessment.md) |
| 04 | [Performance and load-test plan](04-performance-and-load-test-plan.md) |
| 05 | [Database review](05-database-review.md) |
| 06 | [Code quality and duplication](06-code-quality-and-duplication.md) |
| 07 | [UI/UX and motion audit](07-ui-ux-and-motion-audit.md) |
| 08 | [Deployment readiness](08-deployment-readiness.md) |
| 09 | [Prioritized remediation roadmap](09-prioritized-remediation-roadmap.md) |
| 10 | [System inventory](10-system-inventory.md) |
| — | [Audit progress and evidence log](AUDIT-PROGRESS.md) |
