# Sprint 0 — Foundation

Status: **accepted** on revision tree 02cd9b6 (QA re-test 27/27 passed, 0 open defects; review verdict: accept) · Sprint length: 2 weeks · Spec reference: Lot 0 "Socle" (§27), §22, §24, §25

## Goal

A runnable, tested foundation on which the first procurement journey (Sprint 1) can be built:
Spring Boot modular monolith + Angular shell + SQL Server schema via Flyway, bilingual EN/FR, with an
end-to-end connectivity check. No business features are claimed in this sprint.

## Out of scope (explicitly)

Authentication/SSO (Sprint 1), procurement requests, workflows, AI, ERP, file storage, search, messaging.
Navigation entries for future modules are shown as "Planned" with their target sprint — never as fake screens.

## Technical decisions fixed for Sprint 0

| Topic | Decision |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2, Maven Wrapper, package root `com.epi.procurement` |
| Modularity | Spring Modulith; top-level packages = modules; `ApplicationModules.verify()` test |
| Database | Microsoft SQL Server 2025 (dev instance: localhost:1433, Windows auth only); Flyway + `flyway-sqlserver` |
| DB connection | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars. Local default = Windows integrated auth (`integratedSecurity=true`) using the `mssql-jdbc_auth` native DLL fetched by Maven — no password anywhere. |
| Tests without Docker | Docker is not installed → no Testcontainers. Unit/slice tests run without DB. DB integration tests run against the real local SQL Server database `procurement_test`, enabled by profile/env; they are skipped (not faked) when unavailable. No H2/other-database substitution. |
| API | REST under `/api/v1`, errors as RFC 9457 `ProblemDetail`, OpenAPI via springdoc if a Boot-4-compatible release exists |
| Frontend | Angular 22 (standalone, signals, zoneless), Vitest, Angular CDK; custom design system (CSS custom properties, logical properties for future RTL); no Angular Material theme, no React |
| i18n | Runtime EN/FR switching with `@ngx-translate/core` 18; preference persisted in `localStorage` (safe fallback); `<html lang>` updated; locale-aware dates/numbers |
| Fonts/assets | Self-hosted (`@fontsource-variable/inter`) — on-prem installs must not depend on public CDNs |
| Dev wiring | Angular dev server proxies `/api` and `/actuator` to `http://localhost:8080` (no CORS needed in dev) |

## Acceptance criteria

| ID | Criterion |
|---|---|
| S0-AC-01 | `BackEnd/mvnw verify` succeeds on a clean checkout (DB-dependent tests skipped when DB is not configured, and reported as skipped). |
| S0-AC-02 | Backend starts with env-var configuration; no credentials are hardcoded or committed; example config is provided. |
| S0-AC-03 | On startup Flyway migrates an empty SQL Server database to the initial schema; re-running is a no-op. |
| S0-AC-04 | Initial schema contains organisation (group, company, site, department, cost centre), reference data (country, currency), user account, and an append-only hash-chained audit table, with company-scoped keys ready for multi-company isolation. |
| S0-AC-05 | Demonstration data is loaded only in the `demo` profile and is labelled as demonstration data. |
| S0-AC-06 | `GET /actuator/health` reports UP/DOWN including DB; `GET /api/v1/system/status` returns app name, version, server time, DB status, product version and current schema version. When the DB is down the endpoint still responds with DB = DOWN (no stack traces leaked). |
| S0-AC-07 | Module boundaries verified by an automated Spring Modulith test. |
| S0-AC-08 | `FrontEnd` installs from lockfile (`npm ci`), `ng build` and `ng test` succeed, lint passes if configured. |
| S0-AC-09 | Responsive application shell: header, collapsible sidebar navigation, main content; usable at 360px, 768px, 1280px+; keyboard navigable with visible focus and a skip link. |
| S0-AC-10 | Language selector EN/FR visible in the shell; switching updates all visible text without reload; choice survives reload; default from browser language, fallback English. |
| S0-AC-11 | A "System status" page performs the frontend→backend→database check and shows distinct loading, success, error (with retry) and empty states, all localised. |
| S0-AC-12 | Reusable UI foundations exist and are used: design tokens, button, card/panel, status badge, loading skeleton, empty state, error state with retry. |
| S0-AC-13 | Motion is subtle and disabled under `prefers-reduced-motion`. |
| S0-AC-14 | Root README gives exact local start-up steps (DB creation, backend, frontend) for Windows + VS Code. |

## Ownership (to avoid conflicting edits)

| Role | Owns |
|---|---|
| Lead | `docs/` (except `docs/qa`, `docs/reviews`), root `README.md`, `CLAUDE.md`, `.gitignore`, `.vscode/` |
| Developer | `BackEnd/`, `FrontEnd/` |
| QA Tester | `docs/qa/` (read-only elsewhere; may run builds/tests and the app) |
| Reviewer | `docs/reviews/` (read-only elsewhere) |

## Closure record

Accepted on revision tree 02cd9b6 plus the QA-10.9 test (see below). Evidence: `docs/qa/sprint-0-test-results.md`,
`docs/qa/sprint-0-defects.md`, `docs/reviews/sprint-0-code-review.md`.

### Remaining non-blocking issues (carried forward)

| ID | Severity | Issue | Planned |
|---|---|---|---|
| CR-06 (part) | Minor | Anonymous callers can see health component names and states (no authentication yet). | S1 (S1-AC-13) |
| CR-19 | Minor | No test yet that JPA and SQL inserts share sequences without collisions (no writable entity existed). | S1 |
| CR-22 | Minor | Scoped repository component not yet built (ADR-007). | S1 (S1-AC-14) |
| CR-25 | Minor | Audit verification ignores events beyond the chain head (needs schema-altering rights to exploit). | S1 |
| CR-26 | Minor | 1.5 s connection timeout applies to the whole pool, not only the status probe. | S1 |
| CR-27 | Suggestion | Document JDBC `socketTimeout` for production. | S1 |
| CR-28 | Suggestion | `repeatable:missing` ignored in every profile; restrict to dev/local or justify. | S1 |
| CR-18 | Minor | No cache-busting for translation files. | S9 |
| CR-13 / DR-04 | Accepted limit | `db_owner` can still drop audit constraints; separate DB principals and external anchoring planned. | S1 decision, S9 |
| QA-01.4 | Blocked check | Git-history secrets scan impossible before the first commit; run after the initial commit. | S1 QA |
| QA-10.9 | Formerly not executed | Required a temporary source edit; replaced by a permanent automated missing-key test, verified by QA. | Closed at S0 commit |
