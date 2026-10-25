# QA Test Strategy — Enterprise Procurement Intelligence

> **Status: DESIGNED, NOT EXECUTED.** Strategy document; refined each sprint.

## 1. Principles

- QA derives tests from the specification and acceptance criteria, **independently** of developer tests.
- Evidence over opinion: a check counts only when executed and its output recorded. Agreement between agents is not evidence.
- Executed, blocked and proposed checks are always reported separately.
- The same integrated revision is used by QA and the Reviewer; fixes are re-tested on a new integrated revision.
- No database substitution: integration tests run on SQL Server, never on H2 or another engine.

## 2. Test levels

| Level | Tooling | Scope | Runs |
|---|---|---|---|
| Unit (backend) | JUnit 5, AssertJ, Mockito | Domain rules: state transitions, money/currency, approval-circuit resolution, closure conditions | Every build |
| Module/slice | `@WebMvcTest`, Spring Modulith `@ApplicationModuleTest`, `ApplicationModules.verify()` | Controller contracts, ProblemDetail errors, module boundaries | Every build |
| Integration (DB) | `@SpringBootTest` against `procurement_test` on local SQL Server | Repositories, Flyway migrations, constraints, optimistic locking, audit chain, company isolation in queries | Every build when DB enabled; skipped (reported) otherwise |
| Unit (frontend) | Vitest (Angular 22 default) | Services, pipes, guards, component states (loading/empty/error/success) | Every build |
| API acceptance | REST calls (curl/HTTP files or JUnit + RestClient) per role | Permissions, company isolation, workflows end-to-end via API | Per sprint |
| UI end-to-end | Playwright (Chromium) | Core journeys, EN/FR, responsive (360/768/1280), keyboard | Per sprint on running app |
| Accessibility | axe-core via Playwright, manual keyboard/screen-reader spot checks | WCAG 2.2 AA | Per sprint for new screens |
| Non-functional | Simple load script (k6 or JMeter if introduced), timing assertions | Spec §25: common screens < 2 s | From Sprint 3, and before release |
| Security | Grep for secrets, `npm audit`, OWASP dependency check, authorisation matrix tests | Spec §24 | Per sprint; pen test before external portals (Phase 2) |

## 3. Mandatory every sprint (for features touched)

| Area | What to test | Spec |
|---|---|---|
| Permissions | Role × action matrix: allowed actions succeed, every other role gets 403 (API) and the action is hidden/disabled (UI). Test by API, not only UI. | §20 |
| Company isolation | User of company A cannot read, list, search, export, count, or infer data of company B: by ID guessing (IDOR), list filters, dashboards, search, document download, audit history, error messages. Group management sees all. External clients never see purchase prices or suppliers. | §3.6, §17 guardrails, §20 |
| EN/FR | Key parity, no hard-coded strings, locale formats (dates, numbers, currency), server-side messages (validation, ProblemDetail) localised by `Accept-Language`, text expansion at 360px. | Confirmed decision |
| Audit | Each state change / validation / config change writes one audit event with user, timestamp, action, comment, old/new status; chain verifies; audit rows immutable. | §7.2, §23, §24 |
| State transitions | Allowed transitions succeed; **every** illegal transition rejected (e.g. approve a Draft, close without conditions); per-line status independent of header where spec requires. | §5 |
| Money & currency | `DECIMAL(19,4)` storage; rounding per currency minor units (TND = 3 decimals, USD/EUR = 2, JPY = 0); conversion uses the **recorded** rate at the stated date, not today's; totals = sum of rounded lines (rule documented); no floating point in JSON (string or exact decimal). | §6.1, §9.1, §15 |
| Concurrency | Two users edit/approve the same request simultaneously → second gets 409 conflict (optimistic locking), no lost update, no double approval; unique request number under parallel creation. | §1 "numéro unique" |
| Rejection / revision / delegation / information request | Reject requires reason; "return for revision" goes back to requester and resubmission restarts the right step; "request information" suspends SLA per rule; delegation only to authorised delegate within dated window, traced. | §7.2 |
| Partial receipts & exceptions | Partial quantity, damaged, missing documents, refused delivery; line status updates; closure blocked until conditions met. | §13 |
| Failure recovery | DB unavailable, backend restart mid-workflow, network failure in UI (retry, no duplicate submission — idempotency on submit), ERP/file import partial failure with error queue. | §21.5, §25 |
| AI (when introduced) | Proposals never auto-committed; source + confidence shown; respects user scope; measured precision/recall on a labelled sample set. | §17 |

## 4. Test data on SQL Server without Docker

- Databases: `procurement_dev` (manual/exploratory), `procurement_test` (automated integration), `procurement_qa_scratch*` (destructive QA tests, created/dropped per session).
- Schema always created by **Flyway** (same migrations as production); never by Hibernate `ddl-auto`.
- Isolation between tests: each integration test creates its own data with unique codes (e.g. suffix from a UUID) inside a transaction rolled back at the end, or cleans up by company. Tests must not depend on execution order or on demo data.
- Fixture builders per aggregate (company, user with role, request with lines) — readable, minimal.
- Reference data (countries, currencies) comes from migrations; demo data only via `demo` profile and never used as test oracle.
- Multi-company scenarios always use **at least two companies** (one central purchasing, one subsidiary) plus one external client.
- Authentication: Windows integrated auth for local runs; CI will need a SQL login via env vars (documented, not committed).

## 5. Defect reporting

File: `docs/qa/defects/S<sprint>-D<nn>.md` (or a section in the sprint test report).

```markdown
### S0-D01 — <short title>
- Severity: Blocker | Major | Minor | Cosmetic
- AC / requirement: S0-AC-xx / §n / US-xx
- Revision: <git commit or "working tree at <date time>">
- Environment: Windows 11, JDK 21, Node 24, SQL Server 17.0, Chromium <ver>, viewport, language
- Preconditions:
- Steps to reproduce:
  1.
  2.
- Expected:
- Actual:
- Evidence: <command + output excerpt / screenshot path / log lines>
- Notes / suspected area (optional, not a diagnosis):
- Status: Open | Fixed (pending re-test) | Verified on <revision> | Won't fix (reason)
```

Severity guide:
- **Blocker** — AC not met, data loss/leak across companies, security exposure, build broken.
- **Major** — feature works only partially, wrong calculation, missing localisation of a flow, a11y barrier.
- **Minor** — workaround exists, cosmetic-plus issues in edge cases.
- **Cosmetic** — visual polish only.

## 6. Reporting per sprint

`docs/qa/sprint-<n>-test-report.md`: revision tested, table of checks with result **Passed / Failed / Blocked / Not run**, evidence links, defect list, re-test results, and an explicit QA go/no-go.
