# Sprint 0 Code Review

- Revision: git tree `4aa5f61ae224c6a312e2ae3b017cdcbe00d2e03f` (staged index, no commit yet)
- Reviewer: Reviewer agent. **Static review only**: files read and grepped; no build, test or server was run, so
  QA runs the app against this same revision without interference.
- Scope: `BackEnd/`, `FrontEnd/`, root `.gitignore`. Baseline: `docs/product/sprint-0.md` (S0-AC-01 to 14),
  `docs/analysis/design-review-response.md` (design-review items due in S0), decision log, architecture.

## Summary

The foundation is solid and smaller than expected, which is good. The schema, audit chain, fail-fast
configuration and frontend shell all follow the design decisions, with real tests behind most of them. No blockers.
There are three major findings:

- **CR-01:** the demo data is likely to break the normal development workflow.
- **CR-02:** the "planned sprint" labels shown to users disagree with the roadmap.
- **CR-03:** the root README (S0-AC-14) is missing.

The remaining findings are hardening points to schedule into Sprint 1.

Design-review items due in Sprint 0:

| Item | Status | Note |
|---|---|---|
| DR-03 | Applied | Chain-head lock and unique `(chain_key, chain_seq)` are correct. See CR-04 for a verification gap. |
| DR-14 | Applied | Covered by `StartupConfigurationTests`. See CR-05 for the `local` profile. |
| DR-15 | Applied | The `expose-details=false` path has no test (CR-08). |
| DR-16 | Applied | |
| DR-17 | Applied | |
| DR-18 | Applied | |
| DR-24 | Applied | |
| DR-28 | Applied | |

## Findings

| ID | Severity | Location | Finding | Recommended fix |
|---|---|---|---|---|
| CR-01 | Major | `BackEnd/src/main/resources/application-demo.yml:5`, `db/demo/R__demo_organization.sql` | Demo data is a *repeatable* migration in an extra location. Once `dev,demo` has run against `procurement_dev` (the documented default), starting with `dev` only very likely fails Flyway validation ("applied migration not resolved locally"), because the default `ignore-migration-patterns` is only `*:future`. Even where validation passes, the demo rows stay in the database, so "no demo data without the demo profile" only holds on a fresh database. No test covers either case. | Choose one: (a) `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"` in `application.yml`; or (b) keep demo data in a separate database (`procurement_demo`). Document that disabling `demo` does not remove rows already loaded. QA should confirm the failure (QA plan S0-AC-05). |
| CR-02 | Major | `FrontEnd/src/app/layout/navigation.ts:34-53`; `FrontEnd/src/app/features/overview/overview.ts:11-18` | The planned-sprint labels shown to users disagree with `docs/product/roadmap.md`. Roadmap: Requests and Organisation S1, Approvals S2, Purchasing inbox S3, Sourcing and Suppliers S4, Purchase orders S5, Receipts S6, Dashboard S7. Code: Approvals 1, Purchasing 2, Sourcing and Suppliers 3, POs 4, Receipts 5. The numbers are also duplicated in two files. Telling users the wrong delivery dates goes against the "honest placeholders" rule. Logistics (S5) and Dashboard (S7) are missing from the navigation. | Keep one source of truth: derive `JOURNEY` from `NAVIGATION`, or from a shared `ROADMAP` constant. Align it with the roadmap and add the Logistics and Dashboard entries. Add a unit test that the overview stages and the navigation agree. |
| CR-03 | Major | repository root | S0-AC-14: there is no root `README.md` with start-up steps (database creation, backend, frontend). `FrontEnd/README.md` is the unmodified Angular CLI boilerplate and says `ng serve`, but there is no global `ng`. | Lead to write the root README, using `mvnw.cmd` and PowerShell commands (the Developer noted that Git Bash `curl` fails the revocation check). Replace the FrontEnd README with a short pointer to the root README. |
| CR-04 | Minor | `BackEnd/src/main/java/com/epi/procurement/audit/AuditLog.java:98-133` | `verifyChain` reads the events first and the chain head afterwards, under READ COMMITTED, with no consistent snapshot. If an append commits between the two reads, the head is ahead of the verified events and the chain is reported **invalid** (a false "truncated" alarm). There is no caller yet, but a scheduled verification would raise false tamper alerts. | Read the head first, then verify events with `chain_seq <= head.last_seq`. Or run the verification in a `SNAPSHOT` transaction. Add an integration test that verifies while another thread appends. |
| CR-05 | Minor | root `.gitignore:20` (`application-local.yml`) vs `BackEnd/src/main/resources/application-local.yml` | The root ignore rule for local secrets also matches the `local` *profile* file, so that file is not tracked. On a clean checkout, `--spring.profiles.active=local` falls through to the fail-fast guard. The guard message, the comment in `application.yml:5` and DR-14 all mention "dev/local/test". | Either drop the `local` alias (keep `dev`) and update the comments, or narrow the ignore rule to `BackEnd/config/application-local.yml`. |
| CR-06 | Minor | `BackEnd/src/main/resources/application.yml:40-57` | In every profile, including production: Swagger UI (`/api/v1/docs`) and OpenAPI (`/api/v1/openapi`) are exposed without authentication, and health `show-components: always` lists the infrastructure components. Acceptable on a laptop; it is information disclosure in shared environments. The Developer flagged this too. | Set `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` by default, enabled in `dev`. Set `show-components: when-authorized` once security lands (S1-AC-13). |
| CR-07 | Minor | `BackEnd/src/main/java/com/epi/procurement/system/JdbcDatabaseProbe.java:42` | `flyway.info()` runs on every unauthenticated status request. It scans the classpath migrations and reads the history table, and it is not covered by the 3-second query timeout. This is cheap amplification on a public endpoint, and the status page polls it. | Read `SELECT TOP 1 version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC` through the timed `JdbcTemplate`, or cache the version after migration. |
| CR-08 | Minor | `BackEnd/src/test/java/com/epi/procurement/system/SystemStatusControllerTests.java`; `SqlServerIntegrationTests.java:213-220` | The DR-15 behaviour (product version hidden when `app.status.expose-details=false`) is not tested anywhere. The controller tests mock the probe, so `productVersion doesNotExist` on DOWN is true by construction. The integration test runs with `expose-details: true`. `JdbcDatabaseProbe` itself has no unit test, including the DOWN path. | Add a unit test for `JdbcDatabaseProbe` with a mocked `JdbcTemplate`/`Flyway` covering expose on/off and an exception mapping to DOWN. Or add an integration test with the property overridden to false. |
| CR-09 | Minor | `FrontEnd/src/app/app.config.ts:26`; `FrontEnd/src/app/shared/ui/language-select.ts:77` | If loading `/i18n/<lang>.json` fails at start-up, the app initialiser rejects and Angular never bootstraps: a blank page with no message. When switching language, `use(...).subscribe()` has no error handler. The `current` signal and `<html lang>` change to the new language while the texts stay in the old one. | In `init()`, `catchError` and fall back to `en` (or to the keys), so the shell still renders. In `use()`, set the signal and `lang` only after the load succeeds, and surface an error toast/state on failure. |
| CR-10 | Minor | `FrontEnd/src/app` (tests) | No automated EN/FR key-parity test. I checked statically: 105 keys each side, identical sets, matching `{{placeholders}}`. Nothing prevents drift in S1, when forms add many keys. QA plan item. | Add a Vitest spec that loads both JSON files and asserts identical flattened key sets and placeholders. Consider also a check that literal keys used in templates exist. |
| CR-11 | Minor | `FrontEnd/src/app/features/system-status/system-status.html:131` | Health component names are translated with `'systemStatus.health.components.' + name`. Any component without a key shows the raw key string. Only six keys exist; any new indicator will appear (e.g. mail in S2 or a custom one). | Fall back to the component name when the translation is missing, e.g. check the key with `translate.instant` against the key, or use a `MissingTranslationHandler`. |
| CR-12 | Minor | `FrontEnd/src/app/features/system-status/system-status.html:11, 35-90` | (a) The `<dl class="facts">` directly contains `<app-skeleton>` and `<p>` elements, which is invalid content for a `dl` and confuses screen readers that announce list structure. (b) On error, the summary is announced twice: once by the `aria-live` paragraph and once by `app-error-state` (`role="alert"`). | Render skeletons and notes outside the `<dl>`, or wrap them in `div > dd`. Keep a single live announcement: drop `role="alert"` when the page already has a live summary, or skip the live text in the error state. |
| CR-13 | Minor | `BackEnd/src/main/resources/db/migration/V1__initial_schema.sql:159-166, 197-202` | The append-only trigger does not fire on `TRUNCATE TABLE audit_event`, which is allowed because no FK references the table. `audit_chain_head` is fully writable. A user with the app's credentials can delete the tail and rewind the head without detection. This is the DR-04 limitation, already accepted until S1/S9, but nothing in the code records it. | Record it in the `AuditLog` Javadoc. In S1, grant the runtime principal only INSERT/SELECT on `audit_event` and only UPDATE on `audit_chain_head` (no TRUNCATE/ALTER), and run migrations as a separate principal. The external anchoring in S9 closes the head-rewind gap. |
| CR-14 | Minor | `BackEnd/pom.xml:18, 128-147` | The `mssql-jdbc_auth` version is hard-coded and must equal the Boot-managed `mssql-jdbc` version; a Boot upgrade silently breaks integrated auth ("DLL version mismatch"). The x64 Windows DLL is also downloaded on every build and OS, including Linux CI and ARM Windows. | Move the DLL copy into a `windows-dev` Maven profile activated by `<os><family>windows</family></os>`. Add a comment, or an enforcer rule, that ties the version to `${mssql-jdbc.version}`. |
| CR-15 | Minor | `BackEnd/config/.env.example` | Spring Boot does not read `.env` files, so copying this file has no effect unless the user exports the variables or configures an `envFile` in VS Code. Developers will believe they configured a database URL that is never read. | State how to use it: PowerShell `$env:DB_URL=...`, or `"envFile"` in `.vscode/launch.json`. Or import it explicitly with `spring.config.import=optional:file:./config/.env[.properties]`. |
| CR-16 | Minor | `BackEnd/src/test/java/com/epi/procurement/SqlServerIntegrationTests.java:85-91` | The test asserts the schema version is exactly `"2"` and lists the tables exactly, so every future migration breaks it. That pushes developers to edit the test instead of the behaviour. | Assert that `flyway.info().pending()` is empty and `current()` is not null, and that the expected tables are *contained*. |
| CR-17 | Minor | `V1__initial_schema.sql:51, 110-122` | `cost_center` lacks `UNIQUE (company_id, id)`, unlike `site` and `department`, so the S1 request table cannot use the same-company composite-FK pattern for cost centres. `company.parent_company_id` can point to a company of another group. | Add `uq_cost_center_company_id` in the next migration. Enforce same-group parenthood with a composite FK `(group_id, parent_company_id)` → `company (group_id, id)`, which needs a unique `(group_id, id)`. |
| CR-18 | Minor | `FrontEnd/src/app/app.config.ts:23`; `angular.json` assets | Translation files load from the absolute path `/i18n/`, which breaks if the SPA is served under a sub-path (common behind enterprise reverse proxies). Files under `public/` are not content-hashed, so browsers can keep serving old translations after a deployment. | Use the relative prefix `i18n/` (it resolves against `<base href>`), and add a version query, e.g. `suffix: '.json?v=' + buildVersion`. Or configure cache headers in deployment (S9). |
| CR-19 | Suggestion | `V1__initial_schema.sql:26-31` with `Company.java:27-29` | The same sequences are used both by SQL `DEFAULT NEXT VALUE FOR` (demo/import scripts) and by Hibernate's pooled optimiser (`allocationSize = 50`, `INCREMENT BY 50`). The value ranges do not collide, but no test proves it. | Add an integration test that inserts one row through SQL and one through JPA and asserts distinct ids. Or document that SQL-side inserts are for migrations only. |
| CR-20 | Suggestion | `FrontEnd/src/styles/_tokens.scss` (dark block) | Dark tokens are inactive today. When enabled, `--color-text-inverse` on `--color-accent` is 2.37:1, which fails WCAG 1.4.3 for primary buttons and the active language option. The light theme passes; computed: muted text 6.0:1, accent text 5.0:1, nav muted 6.2:1, focus ring 5.0:1. | Darken the dark-theme accent, or use dark text on it, before exposing a theme switch. |
| CR-21 | Suggestion | repository root | There is no root `.gitattributes`. With `core.autocrlf=true` on this laptop, files go into the repository with LF but are checked out with CRLF (Git printed many warnings). Prettier's `endOfLine: lf` and checkouts on other platforms will disagree. `BackEnd/.gitattributes` covers only `mvnw` and `*.cmd`. | Add a root `.gitattributes` with `* text=auto eol=lf`, `*.cmd text eol=crlf`, `*.bat text eol=crlf`, `*.dll binary`. |
| CR-22 | Suggestion | `BackEnd/src/main/java/com/epi/procurement/organization/CompanyRepository.java` | The repository is a public type in the module's base package, so any module can query companies unscoped. This conflicts with ADR-007's central scoped-query rule once S1 lands. | In S1, move repositories to an internal package and expose a scoped `OrganizationQueries` service. The architecture test from S1-AC-14 then enforces it. |
| CR-23 | Suggestion | `BackEnd/src/main/java/com/epi/procurement/platform/web/ApiExceptionHandler.java:22-31` | Problem details have no stable, translatable `code` property (architecture §2), so the frontend cannot localise backend errors. | Add `code` (e.g. `internal-error`) now, so S1 validation errors follow the same contract. |
| CR-24 | Suggestion | start-up behaviour | Flyway migrates at start-up, so the app does not start when the database is unreachable at that moment. The "DB DOWN" status path (S0-AC-06) is therefore only reachable when the database is lost *after* start. That is fine, but QA must test it that way. | QA note: start the backend, then stop SQL Server or block port 1433, then check the status page. |

## Acceptance-criteria verdicts (static)

| AC | Verdict | Reason |
|---|---|---|
| S0-AC-01 | Met (per Developer evidence) | `mvnw verify` configuration is sound; DB tests are gated by `@EnabledIfSystemProperty` and reported as skipped. Not re-run by the Reviewer. |
| S0-AC-02 | Partially | No credentials in tracked files (grep clean apart from placeholders), and the fail-fast guard is present. However, the `local` profile file is untracked (CR-05) and `.env.example` is not consumed (CR-15). |
| S0-AC-03 | Met | `migratesToTheInitialSchemaAndRerunIsANoOp`. |
| S0-AC-04 | Met | All entities are present. Same-company composite FKs exist for site/department/user. Append-only hash-chained audit. Small gaps in CR-17. |
| S0-AC-05 | Partially | The demo data is labelled and loaded only by the `demo` profile. Switching the profile off afterwards is likely to break start-up and leaves rows behind (CR-01), and there is no absence test. |
| S0-AC-06 | Met | Health includes `db`, the status payload is complete, DOWN returns 200, and there are no stack traces (`include-stacktrace: never`, generic handler). The real probe's DOWN path and detail hiding are untested (CR-08). |
| S0-AC-07 | Met | `ModularityTests` runs `ApplicationModules.verify()`. |
| S0-AC-08 | Met (per Developer evidence) | `npm ci`, lint, Vitest and build scripts are present; the lockfile is committed. |
| S0-AC-09 | Met | Skip link, `inert` closed drawer, CDK focus trap, Escape closes the drawer and returns focus to the trigger, focus moves to `h1[tabindex=-1]` on navigation, collapsed rail keeps labels for assistive technology, and the drawer breakpoint is at 64 rem. Minor semantic issue in CR-12. |
| S0-AC-10 | Met | Runtime switch without reload, saved in `localStorage` inside try/catch, browser default, English fallback, `<html lang>` updated, French locale data registered, page titles translated. Robustness gap in CR-09. |
| S0-AC-11 | Met | Distinct loading (skeleton, `aria-busy`), success, database-down, empty-schema and error-with-retry states, all localised and unit-tested. |
| S0-AC-12 | Met | Tokens plus button, panel, page header, status badge, skeleton, empty state, error state and icon, used consistently. |
| S0-AC-13 | Met | Global `prefers-reduced-motion` override plus component-level overrides (skeleton, route animation). Transitions are 120–200 ms. |
| S0-AC-14 | Not met | No root README yet (CR-03). |

Dependencies: the licences of the direct runtime dependencies are permissive: Angular, CDK, ngx-translate and
`@fontsource-variable/inter` (OFL font) are MIT/OFL; `lucide` is ISC; Spring is Apache-2.0; Flyway Community is
Apache-2.0; `mssql-jdbc` is MIT. Nothing is React-based. No unnecessary infrastructure was introduced.

---

## Fix verification on 02cd9b6

Static verification of the changes between trees `4aa5f61` and `02cd9b6` (`git diff 4aa5f61 02cd9b6`). No builds or
servers were run; runtime behaviour relies on QA's re-test.

### Status of earlier findings

| ID | Status | Evidence / remark |
|---|---|---|
| CR-01 | Resolved | `application.yml` `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"`; DB-IT test added; comment states demo rows remain. See CR-28 on scope. |
| CR-02 | Resolved | `navigation.ts` is the single source (`plannedSprint`), values match roadmap; Logistics S5 and Dashboards S7 added; `JOURNEY` points at nav items; `navigation.spec.ts` locks values. |
| CR-03 | Resolved | Root `README.md` in the index with DB, backend, frontend steps; `FrontEnd/README.md` now points to it. |
| CR-04 | Resolved | `AuditLog.verifyChain` reads the head first and checks only `chain_seq <= head.last_seq`. See new CR-25. |
| CR-05 | Resolved | Root `.gitignore` no longer ignores `application-local.yml`; the file is tracked and holds no secret. `BackEnd/.gitignore` still ignores `config/application.yml`. |
| CR-06 | Partially resolved | springdoc disabled by default, enabled in `application-dev.yml` only. Health `show-components` deferral is covered by roadmap S1-AC-13. |
| CR-07 | Resolved | `JdbcDatabaseProbe` reads `flyway_schema_history` directly with a 2 s query timeout; logs only the exception class. See new CR-26. |
| CR-08 | Resolved | `JdbcDatabaseProbeTests` covers details hidden/shown, no schema, failure → DOWN. |
| CR-09 | Resolved | `LanguageService.init()` falls back preferred → English → keys and never rejects; `use()` only commits after load, otherwise keeps the language and sets `switchFailure`; loader uses `failOnError: true`. |
| CR-10 | Resolved | `translations.spec.ts` checks key sets, placeholders and empty values. |
| CR-11 | Resolved | `FallbackMissingTranslationHandler` + `translate: { fallback: component.name }`. |
| CR-12 | Resolved | `<dl>` contents cleaned; the page live region is the only announcement (`[announce]="false"` on the error state). |
| CR-13 | Resolved within the DR-04 limit | V3: FK `audit_chain_head.last_event_id → audit_event` blocks `TRUNCATE audit_event`; `INSTEAD OF DELETE` and forward-only triggers on the head; CHECK ties `last_seq`/`last_event_id`. `TRUNCATE audit_chain_head` is still possible for ALTER-capable principals (no FK references the head, and TRUNCATE skips triggers); it is detected (missing head) unless combined with a rewind — see CR-25. |
| CR-14 | Resolved | DLL copy moved to a Windows-activated Maven profile; version coupling documented in `pom.xml`. No enforcer rule (acceptable). |
| CR-15 | Resolved | `.env.example` explains that Spring Boot does not read it and shows PowerShell / VS Code `envFile` usage. |
| CR-16 | Resolved | Tests assert `contains(...)` for tables and compare the schema version with `flyway.info().current()`. |
| CR-17 | Resolved | V3 `uq_cost_center_company_id`, `uq_company_group_id`, `fk_company_parent_same_group`. |
| CR-18 | Partially resolved | Relative `i18n/` prefix done. Cache-busting deferred to S9 but **not recorded** in backlog/roadmap. |
| CR-19 | Deferred | Reasonable (no JPA-writable entity yet) but **not recorded** for Sprint 1. |
| CR-20 | Resolved | Dark `--color-text-inverse: #0b141b` on `#4fb8b4`; light active option white on `#0e7c7b` (≈5:1, passes AA). |
| CR-21 | Resolved | Root `.gitattributes`: `text=auto eol=lf`, CRLF for `.cmd/.bat/.ps1`, binaries marked. |
| CR-22 | Deferred | Covered in substance by roadmap S1-AC-14 (scoped-query component + architecture test). |
| CR-23 | Resolved | `code` property (`internal-error`, `not-found`) in `ApiExceptionHandler`. |
| CR-24 | No change needed | Behaviour documented; QA used start-then-take-down method. |

Spot checks of other requested areas: the skip link (`shell.ts` `skipToContent`) prevents default and focuses
`main#main`, which has `tabindex="-1"` — correct. The drawer has `role="dialog"` in drawer mode, `aria-modal="true"`
only while open, `inert` while closed, and the CDK focus trap — correct. V3 is compatible with Flyway clean in tests
(DROP does not fire DELETE triggers; QA/Dev runs of `-Pdb-it` exercise this). The demo repeatable runs after V3 and
satisfies the new same-group parent FK because all demo companies share one group. `AuditLog.record` inserts a new head
at `last_seq = 0` and then updates it after inserting the event, which matches the V3 triggers and CHECK.

### New findings

| ID | Severity | Location | Finding | Recommended fix |
|---|---|---|---|---|
| CR-25 | Minor | `BackEnd/src/main/java/com/epi/procurement/audit/AuditLog.java:113-143` | Verification ignores committed events with `chain_seq > head.last_seq`. A rewound head therefore hides later events. A rewind is not possible for the runtime principal, thanks to the V3 triggers. It is possible for an ALTER-capable principal: `TRUNCATE audit_chain_head`, insert the head at 0, then update it to an earlier `seq`/`hash`. That chain then verifies as valid. The events cannot be deleted, but they become invisible to verification. | After checking up to the head, re-read the head. If it is unchanged and any event exists with `chain_seq > last_seq`, report the chain as tampered. Add a DB-IT test that rewinds the head as `db_owner`. Sprint 1. |
| CR-26 | Minor | `BackEnd/src/main/resources/application.yml:13` | `hikari.connection-timeout: 1500` applies to the whole pool, not just the probe. Under load, pool saturation now fails ordinary requests after 1.5 s instead of 5 s (or Hikari's default 30 s). | Restore a normal pool timeout (5–10 s) and bound only the probe: a small dedicated pool or `DataSource` with a short timeout, or a time-boxed probe call. Revisit with the S9 performance tests at the latest. |
| CR-27 | Suggestion | `README.md`, `BackEnd/config/application.example.yml` | The JDBC `queryTimeout` relies on the server answering the cancel request. On a dead network path (not a stopped service) a call can hang until TCP timeout. | Document `socketTimeout=` (e.g. 30000) in production URL guidance. |
| CR-28 | Suggestion | `BackEnd/src/main/resources/application.yml:27` | `repeatable:missing` applies in every profile, including production, where a missing repeatable migration should be investigated, not ignored. | Move `ignore-migration-patterns` to `application-dev.yml`/`application-local.yml`, or keep it global and document why. |

### Verdict

**Sprint 0 can be accepted from a review standpoint.** No Blocker or Major finding remains open. Before Sprint 1
planning, the lead should:
- record CR-18 (cache-busting, S9) and CR-19 (JPA + SQL sequence test with the first writable entity, S1) in the backlog;
- schedule CR-25 and CR-26 in Sprint 1.

The audit trail is tamper-evident against the runtime principal; against `db_owner` it stays limited as accepted
under DR-04, until the S1 principal split and S9 external anchoring.
