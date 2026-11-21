# 09. Prioritized remediation roadmap

Built from reports 02 to 08. Finding ids: **S** security (03), **P** performance (04), **DB** database (05), **Q** code quality (06), **UI** interface (07), **D** deployment (08), **A** architecture (02). Complexity: S = up to 1 day, M = 2 to 5 days, L = more than a week. Nothing here has been started; each workstream needs the approval named in section 9.

## 1. Order and dependencies

```
WS1 Security and data integrity ─┬─> WS5 Regression testing ──> WS6 UAT deployment ──> WS7 Documentation
WS2 Architecture and performance ─┤            ▲
        │                         │            │
        └──> WS3 Load testing ────┘            │
WS4 Premium UI/UX (after WS1 and WS2 foundations; before WS5 final run) ─┘
```

- WS1 and the foundations of WS2 (observability, pipeline, timeouts) can run in parallel and are the next sprint.
- WS3 needs WS2's metrics and the RCSI decision, or its results cannot be interpreted.
- WS4 must not start before WS1, so that security fixes are not mixed with visual changes in the same files; it needs the component splits (UI-3) first.
- WS6 needs WS1, the WS2 foundations and WS5.

## 2. WS1: critical security and data-integrity remediation

| # | Item | Findings | Complexity | Acceptance criteria |
|---|---|---|---|---|
| 1.1 | Tie file extension to detected type | S-2 | S | A script renamed `.bat` with a PDF header is refused (415); existing uploads still download |
| 1.2 | Scanner: restrict the no-op to dev/test; store the real result; integrate the client's antivirus or record a written risk acceptance | S-1 | M (needs ADR) | Outside dev, startup fails without a scanner; EICAR upload gives 422; status column reflects the scan |
| 1.3 | Correct client-address resolution behind proxies | S-3 | S | With a trusted proxy, forged `X-Forwarded-For` values do not reset limits (integration test) |
| 1.4 | Remove administrator-chosen passwords; forbid security actions on peer administrators | S-4 | S | ADMIN gets 403/404 on a peer ADMIN; reset happens only by link; owner is notified |
| 1.5 | Absence rules: no self-delegation by the acting administrator; override limited to the absent person's companies | S-5 | S | 422 on self-delegation; cross-company delegate refused |
| 1.6 | Audit events for rules, rates and absences | S-5, S-6 | S | One audit event per change, same transaction (tests) |
| 1.7 | Company scope on administrator listings; exchange-rate writes for GLOBAL_ADMIN only | S-7 | S | Cross-company tests: another company's users, absences and companies are not returned |
| 1.8 | Quote link: single use or versioned revisions (business decision) | S-8 | S | Second submission refused or stored as an audited version; buyer-entered quotes never overwritten |
| 1.9 | Invoice separation of duties (business decision) | S-9 | S | Recorder cannot attest; payment needs the agreed role |
| 1.10 | JSON payloads through the mapper; CSV formula neutralisation; shared CSV reader | S-13, S-14, S-17, Q-1, Q-5 | S | Tests with quotes, backslashes and `=1+1` |
| 1.11 | Startup guard against `dev` in non-development; explicit mail mode | S-15, S-16 | S | Starting with `dev` and an HTTPS public URL fails with a clear message |
| 1.12 | Two database logins; runtime login cannot alter the schema | DB-1, D-3 | S | `DISABLE TRIGGER` as the runtime login fails; suites pass with it |
| 1.13 | Scheduled audit verification and daily export of chain heads | S-12 | M | A deliberately altered event in a test database raises an alert; exported heads match |
| 1.14 | Purge jobs: idempotency records, expired tokens | DB-4 | S | Rows older than the retention disappear (adjustable-clock test) |
| 1.15 | Sign-in hardening: per-account delay, password maximum length and breached-password check, throttled change-password | S-10 | M | Unit tests on throttle and policy |
| 1.16 | One automated authorization sweep over all endpoints (other company → 403/404; supplier contact → 403) | A-4, 03 §5 | M | The test enumerates all mappings (180 today) and passes; a new unchecked endpoint makes it fail |

**Risks:** 1.2 depends on the client's antivirus choice; 1.8 and 1.9 change business behaviour and need the client's decision; 1.12 may expose code that relied on owner rights (run the full suites).

**Deferred to before production, not UAT:** MFA or single sign-on (S-10), one-tap approval ceiling (S-11), independent penetration test.

## 3. WS2: architecture and performance improvements

| # | Item | Findings | Complexity | Acceptance criteria |
|---|---|---|---|---|
| 2.1 | Request id with MDC, access log with duration, error answers carry the id | A-2, P-4 | M | An error shown in the browser is found in the log by its id |
| 2.2 | Metrics: HTTP timings, pool, JVM, outbox backlog, open streams; admin-only exposure | A-2, P-4 | M | p95 per endpoint readable after a run |
| 2.3 | RCSI on, lock timeout, transaction timeout, mapped to 503 | DB-3, P-1 | S + M verification | A held write lock no longer blocks readers; a blocked writer gets 503 within the timeout; suites pass |
| 2.4 | CI pipeline: build, tests with SQL Server, lint, scans, versioned artefacts | A-9, D-1 | M | Every push shows a result; artefacts carry the commit hash |
| 2.5 | About 20 foreign-key indexes (one migration) | DB-2 | S | Plans on the seeded database use seeks; no missing-index suggestion after the load test |
| 2.6 | CHECK constraints on amounts, quantities and closed status sets | DB-6, DB-7 | S | Migration applies on existing data; negative values rejected |
| 2.7 | Streamed file upload and download; explicit heap | P-5 | M | Heap flat in the file scenario |
| 2.8 | Shared page and LIKE helpers; consistent list behaviour; bounded page number | Q-2, DB-8, DB-9 | S | One parameterised test over all lists |
| 2.9 | Supplier-contact account creation through `identity`; cross-module SQL test with allow-list | A-1, DB-12 | S + M | `INSERT INTO user_account` only in `identity`; the test passes |
| 2.10 | Test independence: own data per test, deltas not absolutes, parallel-ready browser users | A-8 | M | Suites pass three times in random order |
| 2.11 | Remove or build the 4 unused endpoints; one numbering class; repository hygiene; README | Q-3, Q-4, Q-7, A-10 | S | No endpoint without a caller or a test |
| 2.12 | Collation check at startup | DB-10 | S | Startup fails on a database with another collation |

**Risks:** 2.3 changes concurrency semantics (mitigation: full suites and the contention scenarios of WS3); 2.5 adds write cost (mitigation: only the listed indexes).

## 4. WS3: load testing and scalability validation

Plan in report 04, section 4. Preconditions: 2.1, 2.2, 2.3 done; a dedicated environment and seeded database.

| # | Item | Acceptance criteria |
|---|---|---|
| 3.1 | Performance environment, seed script (500 users, 50,000 requests), k6 scripts for five profiles | Smoke run L-0 passes; data checks pass |
| 3.2 | Runs at 25, 50, 100 users | Thresholds of report 04 §4.6 met at 100 users |
| 3.3 | Contention, concurrent-update and duplicate-submission scenarios | Exactly one winner per conflict; no 500; audit chains verify; no duplicate numbers |
| 3.4 | File, search and reporting scenarios | No missing-index suggestion; heap flat |
| 3.5 | Soak (6 h) and recovery tests | No memory or connection growth; clean recovery from each injected fault |
| 3.6 | Optional stress at 200 and 500 users | Capacity figure recorded; overload gives 503 or 429, never hangs |

**Risks:** results on an under-sized or shared machine mislead (use the UAT-class server); test data that is too small hides index problems.

## 5. WS4: premium UI/UX implementation

Direction in report 07, section 6. No GSAP, Lenis or React.

| # | Item | Findings | Complexity | Acceptance criteria |
|---|---|---|---|---|
| 4.1 | Split the three largest components without changing behaviour | UI-3 | M each | Existing unit and browser tests pass unchanged |
| 4.2 | Token clean-up: breakpoints, z-index scale, raw colours | UI-2, UI-5 | S | Only named values remain; screenshots at five widths in three languages |
| 4.3 | Shared patterns promoted to `shared/ui` (filter strip, data table with states, confirm dialog) | Q-8 | M | Each pattern used from one component |
| 4.4 | Motion vocabulary in CSS: route cross-fade, list stagger, overlay easing, status change | UI-6 | S to M | Nothing over 420 ms in the app; all motion off under reduced motion; verified in Arabic and dark mode |
| 4.5 | Large-form performance: stable tracking, line component, deferred rendering | UI-1 | M | Interaction-to-next-paint under 200 ms with 100 lines |
| 4.6 | Bundle back under budget; Arabic font loaded on demand | UI-7 | S | Initial bundle under 500 kB |
| 4.7 | Accessibility pass: contrast measured in both themes, screen-reader walkthrough | 07 §7 | M | No serious axe issue on any route; manual checklist signed |

**Risks:** visual work in the request form can break business rules (mitigation: 4.1 first, and the 66-test browser suite on every change); scope creep toward decorative effects (mitigation: the approved direction in report 07).

## 6. WS5: functional regression testing

| # | Item | Acceptance criteria |
|---|---|---|
| 5.1 | Backend suite on SQL Server with RCSI and the runtime login | All pass |
| 5.2 | Frontend lint, unit tests, build | All pass; bundle within budget |
| 5.3 | Full Playwright suite in isolation on a fresh database | All pass, twice in a row |
| 5.4 | New tests from WS1 and WS2 (authorization sweep, file types, audit events, list behaviour) | Included in 5.1 to 5.3 |
| 5.5 | Manual acceptance of the 20-step request-to-closure journey in EN, FR and AR, light and dark | Signed record in `docs/qa/` |
| 5.6 | Independent QA and reviewer pass on the same revision (project rule) | No open Blocker or Major defect |

## 7. WS6: UAT deployment

Plan in report 08. Preconditions: WS1 items 1.1 to 1.12, WS2 items 2.1 to 2.4, WS5.

| # | Item | Acceptance criteria |
|---|---|---|
| 6.1 | Hosting decision, domain, certificate | HTTPS URL answers; headers verified with `curl -I` |
| 6.2 | Reverse proxy configuration in the repository (TLS, headers, static files, stream path, upload limit) | Live messages arrive through the proxy; policy violations: none |
| 6.3 | UAT profile and environment variables; no `dev` | `actuator/info` shows the commit; testing endpoints answer 404 |
| 6.4 | Database created with the required collation, RCSI, two logins; migration as a release step | Runtime login cannot alter schema |
| 6.5 | Backup schedule and a recorded restore drill | Restore time recorded; application starts on the restored copy |
| 6.6 | Health check, alerts, log retention | A stopped service raises an alert within 5 minutes |
| 6.7 | Named UAT users through setup links; working e-mail | Each tester signs in with their own account |
| 6.8 | Runbook: release, rollback, incident contacts | Rehearsed once |

## 8. WS7: final technical documentation

| # | Item |
|---|---|
| 7.1 | README and architecture document brought up to the current state; module list corrected |
| 7.2 | ADRs for the decisions listed in report 02, section 5 |
| 7.3 | Operations runbook (deploy, backup, restore, rollback, monitoring) |
| 7.4 | Security notes: roles and permissions matrix (the French one exists), data retention, known accepted risks |
| 7.5 | Load-test report with the capacity figure |
| 7.6 | This audit's reports updated with the status of each finding |

## 9. Approval gates

No application code changes before gate G1. Each gate is the owner's decision.

| Gate | Decision needed | Unblocks |
|---|---|---|
| **G1** | Approve this audit and the scope of the next sprint (section 10) | WS1, WS2 foundations |
| **G2** | Business rules: quote-link reuse (S-8), invoice separation of duties (S-9), administrator powers (S-4, S-5) | Items 1.4, 1.5, 1.8, 1.9 |
| **G3** | Infrastructure ADRs: antivirus, RCSI and timeouts, two database logins | Items 1.2, 1.12, 2.3 |
| **G4** | Hosting option, domain, budget, SQL Server licensing | WS6 |
| **G5** | Load-test thresholds and environment | WS3 |
| **G6** | UI direction of report 07 (CSS-only motion, no GSAP/Lenis/React) | WS4 |
| **G7** | Go for UAT: WS1 (1.1–1.12), WS2 (2.1–2.4), WS5 complete; no open Blocker or Major | First UAT user |
| **G8** | Go for production: MFA or SSO, penetration test, restore drill, load test at target, retention decisions | Production |

## 10. Recommended next sprint

**Goal: make the system safe to expose and possible to observe.** About two weeks for one developer with review.

1. Security quick wins, all complexity S: 1.1, 1.3, 1.4, 1.5, 1.6, 1.7, 1.10, 1.11.
2. Database safety: 1.12 (two logins), 1.14 (purge jobs), 2.3 (RCSI and timeouts, with the suites rerun).
3. Observability and pipeline: 2.1, 2.2, 2.4.
4. The authorization sweep test 1.16, so every later change is checked.
5. Decisions to obtain in parallel: G2, G3, G4.

Not in this sprint: UI work, load testing (it needs items 2 and 3 first), MFA.

**Sprint acceptance:** all new tests pass with the existing 208 backend, 253 frontend and 66 browser tests; the pipeline is green on the sprint's last commit; findings S-2 to S-7, S-13 to S-16, DB-1, DB-3 and DB-4 are closed with a test each.
