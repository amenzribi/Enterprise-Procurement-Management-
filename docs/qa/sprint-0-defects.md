# Sprint 0 — Defects (QA execution)

Revision under test: git tree `4aa5f61ae224c6a312e2ae3b017cdcbe00d2e03f` (staged index; no unstaged changes in
`BackEnd/` or `FrontEnd/` at start of testing). Tester: QA agent. Date: 2026-10-24.
Results: [sprint-0-test-results.md](sprint-0-test-results.md). Evidence: [evidence/](evidence/).

Severity: **Blocker** (no workaround, sprint cannot ship) · **Major** (acceptance criterion not met or significant
risk) · **Minor** (limited impact, workaround exists) · **Trivial** (cosmetic).

| ID | Severity | Title | AC / scenario | Owner |
|---|---|---|---|---|
| QA-D-01 | Major | Audit trail can be erased without trace (TRUNCATE + chain-head delete) | S0-AC-04 / QA-04.5 | Developer |
| QA-D-02 | Major | Backend fails to start without `demo` on a database that was seeded with `demo` | S0-AC-05 / QA-05.4 | Developer |
| QA-D-03 | Major | Skip link reloads the app and jumps to `/overview#main`; focus lands on `<body>` | S0-AC-09 / QA-09.4 | Developer |
| QA-D-04 | Minor | Planned-sprint numbers in sidebar and overview do not match the roadmap | S0-AC-09 / QA-09.7 | Developer |
| QA-D-05 | Minor | Missing optional status fields render as blank values; missing schema version shows a misleading "no schema yet" message | S0-AC-11 / QA-11.7 | Developer |
| QA-D-06 | Minor | Status endpoint runs Flyway on every request (logs JDBC URL per call); DB-down response takes ~5 s | S0-AC-06 / QA-06.3, QA-06.7 | Developer |
| QA-D-07 | Minor | Swagger UI and OpenAPI document are served unauthenticated in every profile | S0-AC-06 / QA-06.6 | Developer |
| QA-D-08 | Minor | `demo` profile alone enables `app.status.expose-details` (DB product version disclosed) | S0-AC-06 | Developer |
| QA-D-09 | Minor | `application-local.yml` was git-ignored at the tested revision, so the documented `local` profile is absent from a fresh checkout | S0-AC-02 / QA-01.1 | Lead (.gitignore) |
| QA-D-10 | Minor | CLAUDE.md inaccuracies (i18n path, "key parity is tested"); README.md and CLAUDE.md not part of the tested revision | S0-AC-14 / QA-14.3 | Lead (docs) + Developer (parity test) |
| QA-D-11 | Trivial | Mobile navigation drawer has no `role="dialog"` / `aria-modal` (focus trap itself works) | S0-AC-09 / QA-09.3 | Developer |
| QA-D-12 | Trivial | 404 problem detail exposes framework wording "No static resource api/v1/…" | S0-AC-06 / QA-06.5 | Developer |

No Blocker found. Three Major defects must be fixed and re-verified before Sprint 0 can be reported complete.

---


## Re-test status (tree 02cd9b6, 2026-10-24)

| ID | Severity | Status | Evidence |
|---|---|---|---|
| QA-D-01 | Major | Verified fixed | RT-08 (all seven tamper attempts rejected), RT-03 |
| QA-D-02 | Major | Verified fixed | RT-10 |
| QA-D-03 | Major | Verified fixed | RT-13 |
| QA-D-04 | Minor | Verified fixed | RT-14 |
| QA-D-05 | Minor | Verified fixed | RT-25 |
| QA-D-06 | Minor | Verified fixed | RT-12, RT-25 (DB DOWN in about 1.5 s) |
| QA-D-07 | Minor | Verified fixed (API docs); health component list for anonymous callers deferred to S1 (S1-AC-13) | RT-09, RT-11 |
| QA-D-08 | Minor | Verified fixed | RT-11 |
| QA-D-09 | Minor | Verified fixed | RT-27 |
| QA-D-10 | Minor | Verified fixed | RT-27, RT-06 |
| QA-D-11 | Trivial | Verified fixed | RT-19 |
| QA-D-12 | Trivial | Verified fixed | RT-09 |

No new defects found in the re-test. Open defects: none. QA exit criteria for Sprint 0 are met, apart from the
check still blocked (QA-01.4, needs a first commit) and the one not executed (QA-10.9).

## QA-D-01 — Audit trail can be erased without trace

- **Severity:** Major
- **Expected:** The audit table is append-only (S0-AC-04, spec §24, ADR-006). Any removal of events is either
  prevented or detectable by chain verification.
- **Actual:** `UPDATE` and `DELETE` are blocked by `trg_audit_event_append_only`, but `TRUNCATE TABLE audit_event`
  succeeds (triggers do not fire on TRUNCATE). After truncation no row references `audit_chain_head`, so the head
  row can also be deleted. The result is an empty audit trail with nothing left to detect the removal.
- **Repro (scratch DB `procurement_qa_scratch`, migrated V1–V2, connected as the dev Windows login):**
  1. `INSERT audit_chain_head('global',1,0x01)`; `INSERT audit_event(... chain_seq 1 ...)`.
  2. `UPDATE audit_event SET comment='x'` → Msg 51000 (correct). `DELETE FROM audit_event` → Msg 51000 (correct).
  3. `TRUNCATE TABLE audit_event` → succeeds; `SELECT COUNT(*)` = 0.
  4. `DELETE FROM audit_chain_head WHERE chain_key='global'` → succeeds; heads_left = 0, events_left = 0.
- **Evidence:** command output recorded in test results, QA-04.5.
- **Note:** ADR-006 already plans separate DB principals (S1) and external anchoring (S9); those mitigate a privileged
  actor. Cheap schema-level hardening is still available now, e.g. a foreign key that references `audit_event`
  (TRUNCATE is refused on a referenced table) and a trigger blocking DELETE on `audit_chain_head`.

## QA-D-02 — Startup fails without `demo` on a demo-seeded database

- **Severity:** Major
- **Expected:** QA-05.4 / S0-AC-05: restarting without the `demo` profile against a database previously seeded with
  demo data works (demo rows remain, clearly labelled) — no crash.
- **Actual:** Startup aborts: `Validate failed: Migrations have failed validation — Detected applied migration not
  resolved locally: demo organization.` Exit code 1.
- **Repro:** start with `SPRING_PROFILES_ACTIVE=prod,demo` (or `dev,demo`) on an empty DB → OK; stop; start again with
  `prod` (or `dev`) → fails. This is the normal developer path on `procurement_dev` after following the README
  (which starts with `dev,demo`) and then omitting `demo`, as the README invites.
- **Evidence:** `qa05-4.out` log excerpt in test results, QA-05.4.
- **Suggested fix:** e.g. `spring.flyway.ignore-migration-patterns: "*:missing"` for the repeatable demo migration,
  or document and enforce one profile per database.

## QA-D-03 — Skip link reloads the app

- **Severity:** Major (WCAG 2.4.1 Bypass Blocks; keyboard users lose their page)
- **Expected:** Activating "Skip to main content" moves focus to `<main id="main">` on the current page.
- **Actual:** On `/system-status`, Tab → skip link → Enter triggers a full document navigation to
  `http://localhost:4200/overview#main` (the `#main` href resolves against `<base href="/">`, then the root route
  redirects to overview). The window context is destroyed and focus ends on `<body>`.
- **Repro:** Open `/system-status`, press Tab once, press Enter. Observe URL and `document.activeElement`.
- **Evidence:** Playwright result `urlAfterSkip: http://localhost:4200/overview#main`, `sameDocument: false`,
  `focusAfterSkip: BODY`. The unit test "offers a skip link to the main content" only checks presence, not behaviour.
- **Suggested fix:** handle activation in code (`preventDefault`, focus `main` with `tabindex="-1"`), or use a
  fragment link relative to the current URL.

## QA-D-04 — Planned-sprint numbers do not match the roadmap

- **Severity:** Minor
- **Expected (roadmap.md):** Requests S1, Organisation S1, Approvals S2, Purchasing inbox S3, Sourcing S4, Suppliers S4,
  Purchase orders S5, Receipts S6.
- **Actual:** `FrontEnd/src/app/layout/navigation.ts` and `JOURNEY` in `features/overview/overview.ts`: Approvals 1,
  Purchasing inbox 2, Sourcing 3, Suppliers 3, Purchase orders 4, Receipts 5. Shown in sidebar, overview and planned
  pages ("Planned for sprint 1" on Approvals).
- **Evidence:** `evidence/qa-09-1-overview-1280-en.png`, `evidence/qa-11-2-loading.png` (sidebar).

## QA-D-05 — Blank values for missing status fields

- **Severity:** Minor
- **Expected (QA-11.7):** When optional fields are absent, show a localised "Not available" (no blank, `undefined`
  or `null`).
- **Actual:** With payload `{"application":"procurement","database":{"status":"UP"}}`, "Version" and "Server time"
  labels render with no value. Missing `schemaVersion` with DB UP shows "Schema version: None — The database has no
  schema yet … Restart the backend", which is a wrong diagnosis when the field is merely absent. No `undefined`/`null`
  was printed.
- **Evidence:** `evidence/qa-11-7-missing-fields.png`.

## QA-D-06 — Flyway executed per status request; slow DB-down response

- **Severity:** Minor
- **Expected:** Status endpoint is cheap and quiet; DB DOWN reported promptly.
- **Actual:** Every `GET /api/v1/system/status` logs `FlywayExecutor : Database: jdbc:sqlserver://localhost:1433;…`
  (21 log lines for 21 calls), i.e. a Flyway instance is created per request. Median latency ~300 ms (p95 350 ms,
  within budget). With the database offline the endpoint answers correctly (200, `DOWN`) but only after ~5 s
  (Hikari `connection-timeout: 5000`); `/actuator/health` likewise ~5 s.
- **Evidence:** `be8080.out` log count; QA-06.3 / QA-06.7 timings in test results.

## QA-D-07 — API documentation unauthenticated in all profiles

- **Severity:** Minor (no business endpoints yet; becomes Major once Sprint 1 adds them)
- **Actual:** `/api/v1/docs` (Swagger UI) and `/api/v1/openapi` return 200; `springdoc` settings in `application.yml`
  are not profile-gated. Developer's own report flags this.
- **Expected:** Disabled outside dev, or restricted to administrators from Sprint 1 (S1-AC-13 spirit).

## QA-D-08 — `demo` profile exposes DB product version

- **Severity:** Minor
- **Actual:** `application-demo.yml` sets `app.status.expose-details: true`. A run with `prod,demo` on scratch returned
  `"productVersion":"17.0.1000.7"`. Demo environments are often shared (sales demos), which is the case DR-15 targets.
- **Expected:** Details enabled only by `dev` (and `test`), or explicitly by environment variable.

## QA-D-09 — `local` profile file missing from a fresh checkout

- **Severity:** Minor
- **Actual:** At tree `4aa5f61`, root `.gitignore` contained `application-local.yml`, so
  `BackEnd/src/main/resources/application-local.yml` (no secrets, only the localhost default) was not in the index; the
  exported clone had only `application.yml`, `-dev`, `-demo`. A user starting with the `local` profile would hit the
  "set DB_URL" fail-fast. The lead's working tree now removes that ignore line (uncommitted, outside the revision) —
  re-verify on the next revision.

## QA-D-10 — Documentation inaccuracies

- **Severity:** Minor
- **Actual:** `CLAUDE.md` says strings go in `FrontEnd/src/assets/i18n/` (actual: `FrontEnd/public/i18n/`) and that
  "key parity is tested" — no automated parity test exists (QA verified parity manually: 105/105 keys, no empties).
  `README.md` and `CLAUDE.md` are untracked and therefore not part of tree `4aa5f61`.
- **Expected:** Accurate docs inside the revision; an automated en/fr key-parity test in the frontend suite.

## QA-D-11 — Drawer semantics

- **Severity:** Trivial
- **Actual:** At 360 px the navigation drawer traps focus, closes on Escape and restores focus correctly, but has no
  `role="dialog"`/`aria-modal="true"`, so screen readers are not told the rest of the page is inert.

## QA-D-12 — Framework wording in 404 problem detail

- **Severity:** Trivial
- **Actual:** `GET /api/v1/does-not-exist` → 404 `application/problem+json` with
  `"detail":"No static resource api/v1/does-not-exist."` — leaks implementation wording; no stack trace.
