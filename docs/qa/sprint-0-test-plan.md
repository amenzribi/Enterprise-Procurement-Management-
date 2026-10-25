# Sprint 0 — QA Test Plan

> **Status: DESIGNED, NOT EXECUTED.** None of these checks has been run yet. Results will be recorded in
> `docs/qa/sprint-0-test-report.md` against a single integrated revision.

Source of truth: [`docs/product/sprint-0.md`](../product/sprint-0.md) (S0-AC-01..14), spec §22, §24, §25, §27 (Lot 0).
Derived independently by QA; not copied from developer tests.

## Test environment (this machine)

| Item | Value |
|---|---|
| OS / shell | Windows 11, PowerShell 7 and Git Bash |
| JDK / Node | OpenJDK 21.0.12, Node 24.20, npm 11.19 |
| Database | SQL Server 2025 Developer (17.0.1000.7), `localhost:1433`, **Windows authentication only** |
| Databases | `procurement_dev` (app), `procurement_test` (integration tests); collation `Latin1_General_100_CI_AS_SC_UTF8` |
| Docker | Not installed → no Testcontainers; DB tests use the real local instance |
| Browser automation | Playwright MCP (Chromium) for UI checks; manual browser as fallback |
| Throwaway DB for destructive tests | `procurement_qa_scratch` — created and dropped by QA only |

Common commands (paths relative to repo root):

```powershell
# Backend
cd BackEnd; .\mvnw.cmd -B verify
# Frontend
cd FrontEnd; npm ci; npx ng build; npx ng test --watch=false
# DB inspection
sqlcmd -S localhost -E -C -d procurement_dev -Q "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
```

Severity scale used below: **Blocker** (sprint cannot close) · **Major** · **Minor** · **Cosmetic**.

---

## S0-AC-01 — Backend build is green on a clean checkout

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-01.1 | Automated | Fresh clone into a temp dir; no `BackEnd/target`; no DB env vars set | `.\mvnw.cmd -B verify` | BUILD SUCCESS. Surefire report lists DB-dependent tests as **skipped** (not passed, not failed). |
| QA-01.2 | Automated | `procurement_test` exists; DB test profile/env enabled as documented in README | `.\mvnw.cmd -B verify` with DB enabled | BUILD SUCCESS; DB integration tests **executed and passed** (count > 0 in surefire/failsafe XML). |
| QA-01.3 | Exploratory | — | Inspect surefire XML for `skipped` vs `passed` counts in both runs | Skipped count in QA-01.1 equals executed DB-test count in QA-01.2 (no test silently disappears). |
| QA-01.4 | Automated | Maven wrapper present | `.\mvnw.cmd -v` | Wrapper downloads/uses pinned Maven; Java 21 reported. |

## S0-AC-02 — Env-var configuration, no committed secrets

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-02.1 | Automated (grep) | From repo root: `git ls-files -z \| xargs -0 grep -nIiE "(password\|pwd\|secret\|apikey\|api_key\|token)\s*[:=]\s*[^\s\$\{]" ` and `grep -rnIE "jdbc:sqlserver://[^\"']*password=" --exclude-dir={node_modules,target,dist,.angular}` | No real secret values. Only placeholders (`${DB_PASSWORD}`, `changeme`/`<your-password>` in `*.example*`). |
| QA-02.2 | Automated | `git check-ignore -v .env BackEnd/.env FrontEnd/.env application-local.yml` (whichever local files README tells users to create) | Every local secrets file the README instructs to create is ignored. |
| QA-02.3 | Manual | Read example config(s) | Example exists, covers `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and documents Windows integrated auth (no password). |
| QA-02.4 | Negative | Start backend with `DB_URL` pointing to `localhost:1499` (closed port) | App either fails fast with a clear, non-secret message, or starts and reports DB DOWN (see QA-06.4). Connection string password, if any, is **not** printed in logs. |
| QA-02.5 | Negative | Start with `DB_URL` naming non-existent database `procurement_nope` | Clear error naming the database; no stack trace containing credentials. |
| QA-02.6 | Automated | `git log -p` grep for the same patterns as QA-02.1 (once commits exist) | No secret ever committed in history. |

## S0-AC-03 — Flyway migration on empty DB, idempotent re-run

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-03.1 | Automated | Create empty `procurement_qa_scratch` | Start backend with `DB_URL` → scratch DB; stop | `flyway_schema_history` rows all `success = 1`; tables from S0-AC-04 exist. |
| QA-03.2 | Automated | After QA-03.1 | Start backend again | Log reports "Schema … is up to date. No migration necessary"; history row count unchanged. |
| QA-03.3 | Negative | After QA-03.1 | Manually alter checksum of one history row (`UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version='1'`) and restart | Startup fails on validation (migration tampering detected); not silently repaired. Restore after test. |
| QA-03.4 | Exploratory | — | Review migration SQL | Uses SQL Server types (`NVARCHAR`, `DATETIME2`/`DATETIMEOFFSET`, `DECIMAL(19,4)` for money, `BIT`), explicit PK/FK/unique names, no PostgreSQL syntax. |
| QA-03.5 | Cleanup | — | `DROP DATABASE procurement_qa_scratch` | — |

## S0-AC-04 — Initial schema content

Query used: `SELECT t.name, c.name, ty.name, c.max_length, c.is_nullable FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id ORDER BY t.name, c.column_id` plus `sys.foreign_keys`, `sys.indexes`, `sys.triggers`.

| ID | Type | Check | Expected |
|---|---|---|---|
| QA-04.1 | Automated (SQL) | Organisation tables | Group, company (with type mother/subsidiary/central-purchasing, country, currency, default language, timezone, ERP company code), site, department, cost centre exist with FKs up the hierarchy. |
| QA-04.2 | Automated (SQL) | Reference data | Country (ISO 3166 alpha-2) and currency (ISO 4217 code + minor units) tables exist and are seeded with **reference** data (not demo). |
| QA-04.3 | Automated (SQL) | User account | Table exists, linked to a company (and optionally site/department), unique login/email, no plaintext password column. |
| QA-04.4 | Automated (SQL) | Company-scoped keys | Every company-owned table has a non-null `company_id` FK; unique constraints on business codes are scoped by company (e.g. `UNIQUE(company_id, code)`). |
| QA-04.5 | Automated (SQL) | Audit table append-only | As the **application's** DB principal: `UPDATE` and `DELETE` on the audit table fail (trigger, permission DENY, or ledger table). INSERT succeeds. |
| QA-04.6 | Automated | Hash chain valid | Insert 3 audit events via the application service; recompute hash of each row = H(previous_hash ‖ canonical payload); verification routine (if exposed) returns OK. |
| QA-04.7 | Negative | Tamper detection | With elevated rights (bypassing protections, e.g. disabling trigger in scratch DB), modify the payload of event #2; run verification | Verification reports a break at event #2 (and not before). Restore. |
| QA-04.8 | Negative | Concurrent appends | Append 50 audit events from parallel threads (integration test) | Chain remains linear and valid — no two events share the same previous hash (no forks). |
| QA-04.9 | Exploratory | Optimistic locking readiness | Mutable entities carry a `version`/`rowversion` column. |

## S0-AC-05 — Demonstration data only in `demo` profile

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-05.1 | Automated | Migrate scratch DB **without** `demo` profile; count rows in company/site/department/user tables | 0 demo rows (reference countries/currencies allowed). |
| QA-05.2 | Automated | Start with `SPRING_PROFILES_ACTIVE=demo` on a second scratch DB | Demo organisations present. |
| QA-05.3 | Manual | Inspect demo rows and UI where shown | Each demo record is visibly labelled as demonstration data (e.g. name suffix / `is_demo` flag shown as a badge). Names are realistic, not "foo/test1". |
| QA-05.4 | Negative | Restart non-demo app against a DB previously seeded with demo | Documented behaviour (demo rows remain but are flagged); no crash; migration history not inconsistent (`flyway validate` passes, e.g. demo in a separate location or repeatable migration handled via `ignoreMigrationPatterns`). |

## S0-AC-06 — Health and system status

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-06.1 | Automated | Backend + DB up | `curl -s http://localhost:8080/actuator/health` | HTTP 200, `status: UP`, DB component UP (or details available under documented group). |
| QA-06.2 | Automated | Backend + DB up | `curl -s http://localhost:8080/api/v1/system/status` | HTTP 200 JSON with: application name, version, server time (ISO-8601 with offset), DB status UP, DB product/version (SQL Server 17.x), current schema version = latest Flyway version. |
| QA-06.3 | Negative | Backend up, then stop SQL Server service (`Stop-Service MSSQLSERVER` — requires admin; alternative: point a second instance at a closed port) | Call both endpoints | `/actuator/health` → 503, DB DOWN. `/api/v1/system/status` → responds (200 or 503 as documented) with DB `DOWN`; **no stack trace, SQL, host credentials, or exception class names** in the body. Restart service afterwards. |
| QA-06.4 | Negative | Backend started with wrong `DB_URL` (if startup is allowed) | Same calls | Same as QA-06.3. |
| QA-06.5 | Negative | Backend up | `curl -i /api/v1/does-not-exist` and a malformed request | RFC 9457 `application/problem+json` with `type`, `title`, `status`; no stack trace. |
| QA-06.6 | Exploratory | — | Check which actuator endpoints are exposed | Only health (and info if intended); `env`, `beans`, `heapdump` etc. **not** exposed over HTTP. |
| QA-06.7 | Automated | — | Response time of `/api/v1/system/status`, 20 calls | p95 < 500 ms locally (spec §25 "< 2 s" for common screens). |

## S0-AC-07 — Module boundaries

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-07.1 | Automated | Run Modulith verification test | Passes. |
| QA-07.2 | Negative (mutation) | In a scratch copy, add a reference from one module to another module's `internal` package; run the test | Test **fails**, proving the check is real. Revert. |
| QA-07.3 | Exploratory | Review package layout | Top-level packages map to spec modules/platform concerns; no `util` dumping ground crossing modules. |

## S0-AC-08 — Frontend reproducible build and tests

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-08.1 | Automated | Delete `node_modules`; `npm ci` | Succeeds from `package-lock.json`; no lockfile change (`git diff --exit-code package-lock.json`). |
| QA-08.2 | Automated | `npx ng build` (production config) | Succeeds; no budget errors; warnings noted. |
| QA-08.3 | Automated | `npx ng test --watch=false` | All pass; count > 0 and covers language service, status page states, shell. |
| QA-08.4 | Automated | `npx ng lint` (if configured) | 0 errors. If not configured → logged as finding (Minor). |
| QA-08.5 | Automated | `npm audit --omit=dev` | No high/critical vulnerabilities in runtime deps (report, do not block on moderate). |
| QA-08.6 | Exploratory | Check `dist` for external CDN URLs (`grep -r "fonts.googleapis\|cdn" dist`) | None — assets self-hosted (on-prem requirement). |

## S0-AC-09 — Responsive, accessible shell

| ID | Type | Viewport | Steps | Expected |
|---|---|---|---|---|
| QA-09.1 | Manual/Playwright | 1280×800 | Load app | Header, sidebar (expanded), main content visible; no horizontal scroll. |
| QA-09.2 | Manual/Playwright | 768×1024 | Load app; toggle sidebar | Sidebar collapsible; content readable; no overlap. |
| QA-09.3 | Manual/Playwright | **360×740** | Load app; open/close navigation | Nav accessible via menu button; no horizontal page scroll (`document.documentElement.scrollWidth <= 360`); touch targets ≥ 44px; language selector still reachable. |
| QA-09.4 | Manual | any | **Keyboard only**: Tab from page load | First focusable = "Skip to main content" link; activating it moves focus to `<main>`. All nav items, toggle, language selector reachable in logical order; **visible focus ring** on every element; Escape closes mobile nav and returns focus to the toggle; no focus trap. |
| QA-09.5 | Automated | any | axe-core scan (Playwright `browser_evaluate` with injected axe, or Lighthouse) on shell and status page | 0 serious/critical violations; landmarks `header`, `nav`, `main` present; `aria-current="page"` on active nav item. |
| QA-09.6 | Manual | any | Zoom 200% at 1280px | Content reflows without loss (WCAG 1.4.10). |
| QA-09.7 | Manual | any | Planned-module nav entries | Shown as "Planned — Sprint N" (localised), not as fake working screens; not presented as functional. |

## S0-AC-10 — EN/FR language selection

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-10.1 | Automated (script) | Compare key sets of `en.json` and `fr.json` (flatten nested keys; diff both directions) | Identical key sets; no empty values; no FR value identical to EN for sentences (flag for review, allow proper nouns). |
| QA-10.2 | Automated (grep) | Grep component templates for hard-coded user-visible text (e.g. `>[A-Za-z][^<{]{3,}<` in `*.html`) | Every user-visible string goes through translation (exceptions: brand name). |
| QA-10.3 | Manual/Playwright | Browser language `fr-FR`, clear storage, load | UI in French; `<html lang="fr">`. |
| QA-10.4 | Manual/Playwright | Browser language `de-DE`, clear storage | Falls back to English. |
| QA-10.5 | Manual/Playwright | Switch EN→FR via selector | All visible text changes **without reload** (check network: no document reload); `<html lang>` updates; dates/numbers on status page reformat (e.g. `24 oct. 2026` / decimal comma). |
| QA-10.6 | Manual/Playwright | Choose FR, reload | Still FR. |
| QA-10.7 | Negative | Block localStorage (Chrome: block site data for localhost, or Playwright `addInitScript` making `localStorage.setItem` throw) | App loads without errors; switching still works for the session; console shows no uncaught exception. |
| QA-10.8 | Negative | Set stored preference to invalid value `"xx"` | App falls back to browser language/English; no broken keys shown. |
| QA-10.9 | Negative | Temporarily request a missing key (inspect behaviour) | Missing key is logged in dev and never renders a blank; acceptable to render the key name in dev only. |
| QA-10.10 | Manual | Language selector a11y | Has accessible name; current language announced; operable by keyboard; each option labelled in its own language ("English", "Français"). |
| QA-10.11 | Manual | FR text expansion | French labels (≈ 20–30% longer) do not overflow/truncate in header, nav, buttons at 360px. |

## S0-AC-11 — System status page states

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-11.1 | Manual/Playwright | Backend + DB up; open status page | Loading state (skeleton) then success: shows backend UP, DB UP, version, schema version, server time — localised labels. |
| QA-11.2 | Playwright (network throttle / route delay 3 s) | Open page | Loading state visible, announced to assistive tech (`aria-busy` or live region); no layout jump on completion. |
| QA-11.3 | Negative | **Backend stopped**, frontend dev server running | Error state: human message in current language ("Cannot reach the server"), **Retry** button; no raw HTTP error/JSON shown. |
| QA-11.4 | Negative | Start backend, click Retry | Recovers to success without page reload. |
| QA-11.5 | Negative | Backend up, DB down (QA-06.3 setup) | Page shows backend UP and database DOWN distinctly (not a generic failure). |
| QA-11.6 | Negative | Backend returns 500 / malformed JSON (Playwright `route.fulfill`) | Error state, no crash, console free of uncaught errors. |
| QA-11.7 | Edge | Backend returns payload with missing optional fields (route.fulfill) | Empty/"not available" state for those fields, localised; no `undefined`/`null` printed. |
| QA-11.8 | Manual | Switch language while in error state | Error text and Retry label translate. |

## S0-AC-12 — Reusable UI foundations

| ID | Type | Check | Expected |
|---|---|---|---|
| QA-12.1 | Exploratory (code) | Design tokens | Colours, spacing, radii, typography, shadows, motion durations defined as CSS custom properties in one place; components consume tokens, no stray hex values in component styles (grep `#[0-9a-fA-F]{3,6}` in component `.scss/.css`). |
| QA-12.2 | Exploratory (code) | Components exist and are **used** | Button, card/panel, status badge, skeleton, empty state, error state with retry each have a reusable component used by the status page or shell. |
| QA-12.3 | Automated | Colour contrast of tokens | Text/background pairs ≥ 4.5:1 (normal), ≥ 3:1 (large/UI); status badges not conveyed by colour alone (icon or text). |
| QA-12.4 | Exploratory | Logical CSS properties (`margin-inline-start` etc.) used for future RTL | Spot-check; set `dir="rtl"` on `<html>` in devtools — layout mirrors without breaking (informational, Arabic is not in scope). |

## S0-AC-13 — Restrained motion, reduced motion respected

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-13.1 | Playwright | `browser_emulate_media` `reducedMotion: 'reduce'`; toggle sidebar, load status page | Transitions/animations disabled or reduced to ≤ 0.01ms / opacity only; skeleton shimmer stops. |
| QA-13.2 | Manual | Normal motion | Motion short (≤ 250 ms), purposeful (nav toggle, state change); nothing loops indefinitely except loading indicators. |
| QA-13.3 | Code | No animation libraries added without justification (GSAP/Lenis absent from `package.json`) | As per lead's decision. |

## S0-AC-14 — Start-up documentation

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA-14.1 | Manual | Follow root README **literally** from a fresh clone in a new PowerShell window | Backend and frontend run, status page shows green, without needing knowledge outside README. Each deviation = defect. |
| QA-14.2 | Manual | README covers: prerequisites with versions, DB creation SQL, Windows integrated auth (DLL handling), SQL-auth alternative, env vars, profiles (`demo`), ports, test commands, troubleshooting for "DB down". | All present. |
| QA-14.3 | Manual | `CLAUDE.md` present, concise, with stack, commands, conventions | Present and accurate vs actual commands. |

---

## Cross-cutting / exploratory session (time-boxed 45 min)

- Start frontend before backend, then start backend: status page recovers via Retry.
- Kill backend mid-request: no infinite spinner (timeout ≤ 15 s then error state).
- Open two tabs, change language in one: other tab keeps its language until reload (document acceptable behaviour).
- Browser console and backend log clean of errors/warnings during happy path.
- `ng build` output size reported (initial bundle) for future performance baseline.

## Exit criteria for Sprint 0 (QA view)

- All Blocker/Major defects from this plan fixed and re-tested on the same integrated revision.
- Every S0-AC has at least one **executed** check with recorded evidence (command output, screenshot, or log excerpt).
- Checks that could not be run (e.g. stopping SQL Server without admin rights) are listed as **blocked**, with reason — not counted as passed.
