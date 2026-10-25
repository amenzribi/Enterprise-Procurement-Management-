# Sprint 0 — Test Results (QA execution)

| Item | Value |
|---|---|
| Revision | git tree `4aa5f61ae224c6a312e2ae3b017cdcbe00d2e03f` (staged index). `git diff --stat -- BackEnd FrontEnd` empty at start (no unstaged changes). No commits exist yet. |
| Method | Index exported with `git checkout-index -a` to a clean directory ("fresh clone") under the session scratchpad; all builds and runs used that copy. |
| Environment | Windows 11, JDK 21.0.12 (Temurin), Maven 3.10.0 via wrapper, Node 24.20 / npm 11.19, SQL Server 2025 Developer 17.0.1000.7 (localhost:1433, Windows auth), Chromium via Playwright, axe-core 4.10.3 (installed in scratchpad only) |
| Databases | `procurement_test` (DB integration tests), `procurement_dev` (dev run), scratch `procurement_qa_scratch` and `procurement_qa_scratch_demo` (created and dropped by QA) |
| Date / tester | 2026-10-24 · QA agent (independent of the Developer's report) |
| Defects | [sprint-0-defects.md](sprint-0-defects.md) |

## Summary

| Scenarios in plan | Executed | Pass | Fail | Blocked | Not executed |
|---|---|---|---|---|---|
| 80 | 78 | 72 | 6 | 1 | 1 |

Defects: 0 Blocker · 3 Major · 7 Minor · 2 Trivial. **QA exit criteria not met** (Major defects open):
QA-D-01, QA-D-02, QA-D-03.

## Results by scenario

Legend: P = pass, F = fail, B = blocked, NE = not executed.

### S0-AC-01 — Backend build

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-01.1 | Executed | P | Clean clone, no `DB_URL`: `mvnw.cmd -B clean verify` → BUILD SUCCESS, Tests run 22, Failures 0, Errors 0, Skipped 10 (all `SqlServerIntegrationTests`). Error-level log lines are from intentional negative tests. |
| QA-01.2 | Executed | P | `mvnw.cmd -B verify -Pdb-it` → BUILD SUCCESS, 22 run, 0 skipped. Per-test list: 7 AuditHasher, 1 Modularity, 10 SqlServer (migrate+rerun no-op, UTC round-trip, cross-company FK, append-only, tamper detection, 160 concurrent appends, minor units, demo labels, status, health), 1 StartupConfiguration, 3 SystemStatusController — all pass. |
| QA-01.3 | Executed | P | Skipped count (10) in 01.1 = executed SQL Server tests (10) in 01.2. |
| QA-01.4 | Executed | P | `mvnw.cmd -v` → Apache Maven 3.10.0, Java 21.0.12.1. |

### S0-AC-02 — Configuration and secrets

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-02.1 | Executed | P | `git ls-files` grep for password/secret/token assignments: only `${DB_PASSWORD:}`, `change-me` placeholder in `config/application.example.yml`, and Maven wrapper variable handling. JDBC URLs contain no credentials (integrated auth). |
| QA-02.2 | Executed | P | `.env`, `BackEnd/.env`, `BackEnd/config/.env`, `BackEnd/config/application.yml`, `application-local.yml` all git-ignored (see QA-D-09 about the latter's side effect). |
| QA-02.3 | Executed | P | `config/.env.example` and `config/application.example.yml` cover `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, integrated auth and SQL auth. |
| QA-02.4 | Executed | P | `DB_URL` → port 1499: fails fast, exit 1, cause "TCP/IP connection to host localhost, port 1499 … refused" (driver message in OS language, French); no credentials in log. |
| QA-02.5 | Executed | P | `databaseName=procurement_nope`: exit 1, `Cannot open database "procurement_nope" requested by the login`. No secrets. |
| QA-02.6 | — | B | No commits exist; history check not possible. Re-run after the first commit. |

### S0-AC-03 — Flyway migration

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-03.1 | Executed | P | Empty scratch DB, profile `prod` + `DB_URL`: "Successfully applied 2 migrations … now at version v2". Status: DB UP, schema "2", `productVersion: null` (details off outside dev — DR-15 OK). |
| QA-03.2 | Executed | P | Restart: "Schema [dbo] is up to date. No migration necessary." |
| QA-03.3 | Executed | P | Checksum of V1 altered → startup exit 1, "Migration checksum mismatch for migration version 1". |
| QA-03.4 | Executed | P | V1 reviewed: NVARCHAR text, DATETIME2(3) UTC, BIGINT sequence keys, named PK/FK/UK/CK constraints, composite same-company FKs, filtered unique ERP code, ISJSON check. No PostgreSQL syntax. |
| QA-03.5 | Executed | P | Scratch databases dropped; only `procurement_dev`, `procurement_test` remain. |

### S0-AC-04 — Schema content

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-04.1 | Executed | P | `organization_group`, `company` (type, country, currency, language, time zone, tax id, ERP code, purchasing mode), `site`, `department`, `cost_center`. |
| QA-04.2 | Executed | P | Non-demo DB: 13 countries, 11 currencies (TND 3, EUR 2, USD 2, JPY 0, LYD 3 …) as reference data; 0 organisation rows. |
| QA-04.3 | Executed | P | `user_account`: company FK, unique email, filtered unique external subject, `preferred_language`; no password column. |
| QA-04.4 | Executed | P | `company_id NOT NULL` FKs; codes unique per company; composite FKs (`company_id, site_id` etc.). Also covered by DB test `sameCompanyForeignKeysRejectCrossCompanyReferences` (pass). |
| QA-04.5 | Executed | **F** | UPDATE and DELETE blocked (Msg 51000). **TRUNCATE succeeds and chain head can then be deleted → full erasure.** → QA-D-01. Principal: dev Windows login (sysadmin = 1). |
| QA-04.6 | Executed | P | DB test `auditEventsAreChainedStoredInUtcAndAppendOnly` executed in QA-01.2; hasher unit tests (7) pass. |
| QA-04.7 | Executed | P | DB test `tamperingIsDetectedByChainVerification` executed in QA-01.2. (Truncation is not covered — see QA-D-01.) |
| QA-04.8 | Executed | P | DB test `concurrentAppendsProduceOneLinearChain` (160 parallel appends) executed in QA-01.2; `uq_audit_event_chain_seq` prevents forks by construction. |
| QA-04.9 | Executed | P | `version INT` on every mutable organisation table and `user_account`. |

### S0-AC-05 — Demonstration data

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-05.1 | Executed | P | Non-demo scratch: groups 0, companies 0, sites 0, departments 0, cost centres 0, users 0. |
| QA-05.2 | Executed | P | `prod,demo` on second scratch: 3 migrations incl. repeatable "demo organization". |
| QA-05.3 | Executed | P | Group "Atlas Industrial Group (Demo)", 4 companies (Holding TN/TND, Sourcing & Trading = purchasing centre, Sfax plant TN/TND MIXED, Lyon plant FR/EUR CENTRALIZED), sites suffixed "(Demo)", codes `DEMO-*`. Realistic, clearly labelled. |
| QA-05.4 | Executed | **F** | Restart without `demo` on demo-seeded DB → exit 1, "Detected applied migration not resolved locally: demo organization". → QA-D-02. |

### S0-AC-06 — Health and status

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-06.1 | Executed | P | `/actuator/health` 200, `status UP`, components db/diskSpace/livenessState/ping/readinessState/ssl UP. |
| QA-06.2 | Executed | P | `/api/v1/system/status` 200: application, version 0.1.0-SNAPSHOT, serverTime ISO-8601 UTC, DB UP, productVersion 17.0.1000.7 (dev/demo), schemaVersion 2. |
| QA-06.3 | Executed (variant) | P | Service stop not used (admin); instead scratch DB set OFFLINE while app running: status 200 `DOWN`, schemaVersion null, no stack trace (~5 s, QA-D-06); health 503 with db DOWN. DB set ONLINE → status UP again without restart. |
| QA-06.4 | Executed | P | Wrong `DB_URL` → startup refused (fail fast), so runtime calls not applicable; acceptable per plan. |
| QA-06.5 | Executed | P | Unknown path 404 `application/problem+json`; POST on GET-only → 405 problem+json; no stack traces (wording: QA-D-12). |
| QA-06.6 | Executed | P | `/actuator` 200, `/actuator/info` 200, `env`/`beans`/`heapdump`/`configprops`/`loggers` 404. Note `/api/v1/docs` and `/api/v1/openapi` 200 → QA-D-07. |
| QA-06.7 | Executed | P | 20 calls: min 259, median 298, p95 350, max 420 ms (< 500 ms). Flyway per request → QA-D-06. |

### S0-AC-07 — Module boundaries

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-07.1 | Executed | P | `ModularityTests.moduleBoundariesAreRespected` passes. |
| QA-07.2 | Executed | P | Mutation in clone: `organization.QaMutant` calling `audit.internal.QaHidden` → test fails with "Module 'organization' depends on non-exposed type … within module 'audit'". Mutation reverted. |
| QA-07.3 | Executed | P | Modules `platform` (OPEN), `system`, `audit`, `organization` — only modules with code (DR-24). |

### S0-AC-08 — Frontend build

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-08.1 | Executed | P | `npm ci` exit 0; `package-lock.json` hash unchanged. Install scripts of `lmdb`, `msgpackr-extract` skipped by npm 11 (optional). |
| QA-08.2 | Executed | P | `ng build` exit 0, initial 344 kB raw / 92.5 kB transfer, lazy chunks per page, no budget warnings. |
| QA-08.3 | Executed | P | Vitest: 3 files, 17/17 pass (language service 6, system status 6, shell 5). |
| QA-08.4 | Executed | P | `npm run lint` → "All files pass linting." |
| QA-08.5 | Executed | P | `npm audit --omit=dev` → 0 vulnerabilities. |
| QA-08.6 | Executed | P | No googleapis/gstatic/jsdelivr/unpkg/cdnjs URLs in `dist`. |

### S0-AC-09 — Responsive, accessible shell

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-09.1 | Executed | P | 1280×800: header, expanded sidebar, main; no horizontal scroll. `evidence/qa-09-1-overview-1280-en.png` |
| QA-09.2 | Executed | P | 768×1024: navigation moves to drawer (menu button), no overflow. `evidence/qa-09-768-*-status.png` |
| QA-09.3 | Executed | P | 360×740 EN/FR: no horizontal scroll, no target < 24 px, drawer opens with focus on close button, focus trapped (16 Tabs), Escape closes and returns focus to menu button, navigating closes drawer and focuses page `h1`. `evidence/qa-09-3-360-drawer-open.png` (semantics: QA-D-11) |
| QA-09.4 | Executed | **F** | First Tab = visible skip link ✓; tab order logical; focus ring on every control ✓. **Skip link reloads app to `/overview#main`, focus on body** → QA-D-03. |
| QA-09.5 | Executed | P | axe-core (wcag2a/aa, 21a/aa, 22aa, best-practice): 0 violations on overview, system status (EN and FR, 1280), status at 360, planned page, not-found page. |
| QA-09.6 | Executed (approx.) | P | 640×400 viewport (≈ 1280 at 200 %): no horizontal scroll. `evidence/qa-09-6-zoom200-equivalent.png`. True browser zoom not tested. |
| QA-09.7 | Executed | **F** | Planned modules shown as "Sprint N" links to honest planned pages (no fake screens) ✓, but sprint numbers contradict roadmap → QA-D-04. `evidence/qa-09-7-planned-module.png` |

### S0-AC-10 — EN/FR

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-10.1 | Executed | P | `public/i18n/en.json` / `fr.json`: 105 = 105 keys, none missing either way, no empty values. Identical EN=FR values are legitimate (product name, "Version", "Administration", "Organisation", language names, format strings). |
| QA-10.2 | Executed | P | No hard-coded user-visible text found in templates (matches were code only). |
| QA-10.3 | Executed | P | Browser `fr-FR`, empty storage → `<html lang="fr">`, French UI. |
| QA-10.4 | Executed | P | Browser `de-DE` → English. |
| QA-10.5 | Executed | P | Switch without document reload (window marker kept); `lang` and title update; dates/times re-formatted ("Oct 24, 2026, 12:09:17 PM" ↔ "24 oct. 2026, 12:09:18"). `evidence/qa-11-1-status-success-1280-{en,fr}.png` |
| QA-10.6 | Executed | P | Choice stored as `epi.language`; survives reload. |
| QA-10.7 | Executed | P | `Storage.prototype` methods throwing, and `window.localStorage` getter throwing: app loads, switching works, no page errors. |
| QA-10.8 | Executed | P | Stored `"xx"` → browser language (en-US → EN, fr-FR → FR). |
| QA-10.9 | Executed (closure) | P | Replaced by a permanent automated test (`fallback-missing-translation-handler.spec.ts`, 5 tests, EN+FR, real i18n files, translate pipe). Verified 2026-10-25, see "QA-10.9 closure verification". |
| QA-10.10 | Executed | P | Selector is `role="group"` with accessible name ("Langue d'affichage"), buttons with `aria-pressed` and `lang` attributes, labels "English"/"Français", keyboard operable. |
| QA-10.11 | Executed | P | No overflow of French labels at 360 px; product name truncates with ellipsis in header (cosmetic). |

### S0-AC-11 — System status states

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-11.1 | Executed | P | Success: three hops connected, version, server time, response time, schema 2, SQL Server version, health table — localised. |
| QA-11.2 | Executed | P | 3 s delayed response: skeleton bars, `section[aria-busy=true]`, polite live region "Checking the connection…". Page grows when health table appears (744→924 px; below the fold, acceptable). `evidence/qa-11-2-loading.png` |
| QA-11.3 | Executed | P | Real backend stopped (proxy 502): "The application server did not respond", Unavailable / Unknown states, no raw HTTP text. `evidence/qa-11-3-backend-stopped-real.png` |
| QA-11.4 | Executed | P | Backend restarted; "Check again" → "Everything is connected" without document reload. `evidence/qa-11-4-recovered-after-retry.png` |
| QA-11.5 | Executed | P | DB DOWN payload (+ health 503): "The server is running but cannot reach the database", distinct from server failure. `evidence/qa-11-5-db-down.png` |
| QA-11.6 | Executed | P | 500 problem+json and malformed JSON → server-unavailable error state, no page errors. `evidence/qa-11-6-http500.png` |
| QA-11.7 | Executed | **F** | Missing fields: no `undefined`/`null`, but blank "Version"/"Server time" and misleading "no schema yet" → QA-D-05. `evidence/qa-11-7-missing-fields.png` |
| QA-11.8 | Executed | P | Switching to FR in error state translates message and button. `evidence/qa-11-8-error-fr.png` |

### S0-AC-12 — UI foundations

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-12.1 | Executed | P | `src/styles/_tokens.scss`: 52 colour, 9 spacing, 7 text-size, radius, weight, leading tokens. One stray literal colour (`shell.scss` overlay scrim `rgb(18 32 43 / 0.45)`). |
| QA-12.2 | Executed | P | Status page uses `app-page-header`, `app-panel`, `app-status-badge`, `app-skeleton`, `app-empty-state`, `app-error-state`, `appButton`, `app-icon`. |
| QA-12.3 | Executed | P | axe colour-contrast rule: 0 violations; badges carry text + dot, not colour alone. |
| QA-12.4 | Executed | P | 17 logical-property uses, 0 physical left/right margins/paddings; `dir="rtl"` moves sidebar to the right with no horizontal scroll (informational). `evidence/qa-12-4-rtl-spotcheck.png` |

### S0-AC-13 — Motion

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-13.1 | Executed | P | Normal: transitions 0.12 s; `prefers-reduced-motion: reduce`: all durations 1e-05 s. |
| QA-13.2 | Executed | P | Motion 120 ms, used on interactive state changes; no looping animation apart from loading. |
| QA-13.3 | Executed | P | No GSAP/Lenis/Motion/anime in `package.json`. |

### S0-AC-14 — Documentation

| ID | Status | Result | Evidence |
|---|---|---|---|
| QA-14.1 | Executed (working-tree README) | P | README is **untracked (outside tree 4aa5f61)**. Following it: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"` responds after 12 s with DB UP; `npm ci` + `npm start` serve on 4200 with proxy (status through proxy 200). DB-creation command not re-run (DBs pre-existed). |
| QA-14.2 | Executed | P | Covers prerequisites, DB creation, integrated auth + DLL, SQL auth alternative, env vars, `demo`, ports, test commands, Git Bash caveat. Does not mention QA-D-02 behaviour. |
| QA-14.3 | Executed | **F** | CLAUDE.md present and concise, but i18n path wrong and claims an automated parity test that does not exist → QA-D-10. |

## Cross-cutting exploratory notes (not defects)

- Startup time ~20 s (jar), 12 s via `spring-boot:run` on warm machine.
- Fresh-clone build reproducible from the index alone; Maven wrapper downloads to `~/.m2/wrapper`.
- "Everything is connected" text appears twice in DOM (heading + live region); intended for screen readers.
- Full-page screenshots show the sidebar background ending at viewport height; in normal scrolling the sidebar is
  sticky and fills the viewport, so this is a screenshot artefact.

## Clean-up performed

All QA-started processes (backend jars on 8080/8090, `spring-boot:run`, Angular dev server) stopped; ports 8080, 8090,
4200 free. Scratch databases dropped. QA files written only to `docs/qa/` (evidence under `docs/qa/evidence/`);
working copies, logs and axe-core installation are in the session scratchpad outside the repository.

## Re-test on 02cd9b6

| Item | Value |
|---|---|
| Revision | git tree `02cd9b698f20223b820eca45869e6266d8429db3` (staged index); `git diff --stat` empty for BackEnd/ and FrontEnd/ at start |
| Date | 2026-10-24 |
| Run from | Working tree (identical to index at start) |
| Environment | As above; axe-core 4.10.3 from the session scratchpad; Chromium via Playwright |

| ID | Check | Status | Result | Evidence |
|---|---|---|---|---|
| RT-01 | Revision integrity (no unstaged changes in BackEnd/FrontEnd) | Executed | P | `git write-tree` = 02cd9b6; `git diff --stat` empty |
| RT-02 | `BackEnd\mvnw.cmd verify` | Executed | P | 33 tests, 0 failures, 0 errors, 16 skipped (SQL Server tests), BUILD SUCCESS. Includes the 1500 ms timeout change. |
| RT-03 | `BackEnd\mvnw.cmd verify -Pdb-it` against `procurement_test` | Executed | P | 33 tests, 0 failures, 0 skipped, BUILD SUCCESS |
| RT-04 | `npm ci` (clean `node_modules`) | Executed | P | Exit 0; `package-lock.json` unchanged. A first attempt collided with a second QA-started `npm ci` (ENOTEMPTY); that was a test-harness error, re-run clean. |
| RT-05 | `npm run lint` | Executed | P | "All files pass linting." |
| RT-06 | `npm test` | Executed | P | 5 files, 33/33 tests pass (includes `translations.spec.ts` key/placeholder parity) |
| RT-07 | `npm run build` | Executed | P | 93.13 kB initial transfer, no warnings |
| RT-08 | Audit tamper attempts on a scratch DB with V1-V3 applied (QA-D-01) | Executed | P | TRUNCATE `audit_chain_head` and TRUNCATE `audit_event` both rejected (FK-referenced); DELETE head rejected (51001); rewind head rejected (51003); DELETE/UPDATE event rejected (append-only); new head with `last_seq` <> 0 rejected (CHECK). Rows intact (1 event, 1 head). Scratch DB dropped. |
| RT-09 | Live `dev,demo` (jar) | Executed | P | Status DB UP, schema "3", product version shown; health 200 with db UP; OpenAPI 200, Swagger UI 302; unknown path returns `{"detail":"The requested resource does not exist.","code":"not-found",...}` |
| RT-10 | Live `dev` alone after `dev,demo` (QA-D-02) | Executed | P | Starts; Flyway "Schema [dbo] is up to date"; status UP |
| RT-11 | Live `local,demo` on :8090 (QA-D-07, QA-D-08) | Executed | P | OpenAPI 404, Swagger UI 404; `productVersion: null` (details hidden with demo) |
| RT-12 | JDBC URL not logged per request (QA-D-06) | Executed | P | Only 2 occurrences in the `dev,demo` log, both at startup, after several status calls |
| RT-13 | Skip link (QA-D-03) | Executed | P | Tab then "Skip to main content"; Enter moves focus to `main#main`, URL unchanged (`/system-status`), same document (JS marker kept) |
| RT-14 | Sidebar planned sprints vs roadmap (QA-D-04) | Executed | P | Requests S1, Approvals S2, Purchasing inbox S3, Sourcing S4, Purchase orders S5, Logistics S5, Receipts S6, Dashboards S7, Organisation S1, Suppliers S4: matches `docs/product/roadmap.md` |
| RT-15 | EN/FR switch, `<html lang>`, persistence | Executed | P | FR switch updates headings and `lang="fr"`; `epi.language=fr` stored; survives reload |
| RT-16 | Failed language switch (CR-09) | Executed | P | `fr.json` returns HTTP 500, click Français: stays English, `lang="en"`, EN still pressed, message "Français could not be loaded. The previous language is kept."; stored value stays `en` |
| RT-17 | Failed translation load at startup (CR-09) | Executed | P | Stored `fr`, `fr.json` returns 500, reload: page renders in English (`lang="en"`), not blank |
| RT-18 | axe (wcag2a/aa, 21a/aa, 22aa, best-practice) at 1280, EN and FR, system status | Executed | P | 0 violations each |
| RT-19 | Drawer at 360 px (QA-D-11) | Executed | P | Closed: inert, hidden, no `aria-modal`. Open: `role="dialog"`, `aria-modal="true"`, name "Application sidebar", focus moves to "Close navigation" (mouse and keyboard open), focus trapped over 30 Tabs, Escape closes and returns focus to "Open navigation". Screenshot `evidence/retest-360-drawer-open.png` |
| RT-20 | axe at 360 px; no horizontal scroll at 360/768/1280 | Executed | P | 0 violations; `scrollWidth <= innerWidth` at all three widths |
| RT-21 | Reduced motion | Executed | P | Drawer `transition-duration` 1e-05 s under `prefers-reduced-motion: reduce` |
| RT-22 | Planned-module page and unknown planned key | Executed | P | `/planned/logistics` shows "Planned for sprint 5"; `/planned/doesNotExist` shows "Page not found" |
| RT-23 | Backend stopped: status page error state (CR-12) | Executed | P | "The application server did not respond"; database "Cannot be checked while the server is unreachable"; exactly one live region (polite). `evidence/retest-backend-down.png` |
| RT-24 | Retry after backend restart | Executed | P | "Check again" leads to "Everything is connected". `evidence/retest-recovered.png` |
| RT-25 | Database offline after startup, timing (QA-D-05, QA-D-06) | Executed | P | Backend on a scratch DB taken OFFLINE: status 200 with DB DOWN in 1.54 s / 1.51 s, health 503 in 1.55 s, no stack traces. UI: "The server is running but cannot reach the database", no "no schema" message. `evidence/retest-db-down.png`. Scratch DB dropped. |
| RT-26 | README backend command as a new developer (`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"`) | Executed | P | Status responds with DB UP, schema 3 |
| RT-27 | Lead files and static checks (QA-D-09, QA-D-10) | Executed | P | README.md, CLAUDE.md, .gitignore, .gitattributes tracked in the index; `application-local.yml` tracked, no credentials; CLAUDE.md i18n path `FrontEnd/public/i18n` correct; parity test exists; `build/` ignored; secrets grep of tracked files clean (only `mvnw` variable names) |

**Re-test totals:** 27 executed, 27 passed, 0 failed. Still blocked: QA-01.4 git-history secrets scan (no commits
exist). QA-10.9 was not executed in this re-test; it was closed afterwards (see below).

**Notes (not defects):** health `components` are still listed for anonymous callers; this is deferred to S1 by
design (CR-06, S1-AC-13). Uncommitted changes by other roles exist outside `docs/qa/` (`AGENTS.md` untracked;
`docs/product/backlog.md` and `docs/reviews/sprint-0-code-review.md` modified after staging); they do not affect the
code under test.

**Clean-up:** all QA-started processes stopped; ports 8080, 8090, 4200 free. Scratch databases
`procurement_qa_scratch_rt` and `procurement_qa_scratch_dn` dropped; `procurement_dev` and `procurement_test`
ONLINE. The root `.playwright-mcp/` folder created by the browser tool was deleted.

## QA-10.9 closure verification (2026-10-25)

Change under test: `FrontEnd/src/app/core/i18n/fallback-missing-translation-handler.ts` (dev-only `console.warn`
guarded by `isDevMode()`) and the new `fallback-missing-translation-handler.spec.ts`. QA did not modify `FrontEnd/`
in the repository.

| Step | Result | Evidence |
|---|---|---|
| `npm test` in the repository | P | 6 test files, 38/38 passed (33 before + 5 new). |
| Mutation A in a scratch copy: `console.warn` removed | P (tests detect it) | 4 new tests failed (the "warns in dev mode" and "translate pipe" tests, EN and FR); 34 passed. |
| Mutation B in a scratch copy: handler returns `''` | P (tests detect it) | All 5 new tests failed, plus the system-status tests that rely on the fallback label. |
| Production logging | P | The warning is inside `if (isDevMode())`, so it is not emitted in production builds. Returned text is unchanged: the fallback parameter, else the key, never empty. |
| Clean-up | — | Scratch copy and its `node_modules` junction deleted; repository `node_modules` intact. |

**Updated Sprint 0 totals:** all planned scenarios executed except QA-01.4 (git-history secrets scan), which stays
blocked until the first commit exists and is scheduled in Sprint 1 QA.
