# CLAUDE.md

> `AGENTS.md` (read by other coding agents) mirrors `CLAUDE.md`. Change both together.

Enterprise Procurement Intelligence — procurement request platform for multi-company groups.
Functional reference: Solution Guide v3.0 (`docs/specification/…_Solution_Guide_EN_v3.0.md`, reviewed in `docs/analysis/solution-guide-v3-review.md`); detailed rules: French spec v2.1 in the same folder (both unchanged). Plan: `docs/product/roadmap.md`. Sprint 3: `docs/product/sprint-3.md`. Decisions: `docs/decisions/`.

## Stack (fixed — do not substitute)

- Backend: Java 21, Spring Boot 4.1, Spring Modulith, JPA/Hibernate 7, Flyway, Maven Wrapper — `BackEnd/`
- Frontend: Angular 22 (standalone, signals, zoneless, OnPush), Angular CDK, ngx-translate, Vitest — `FrontEnd/`
- Database: Microsoft SQL Server only (ADR-001). Never H2/PostgreSQL, including in tests.
- No React or second frontend framework. No new infrastructure (queues, search engines, BPMN engines) without an ADR (ADR-003).

## Commands (PowerShell)

| Task | Command |
|---|---|
| Run backend | `cd BackEnd; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"` |
| Backend tests | `.\mvnw.cmd verify` · with SQL Server: `.\mvnw.cmd verify -Pdb-it` (uses `procurement_test`) |
| Run frontend | `cd FrontEnd; npm start` (proxy to :8080) |
| Frontend checks | `npm run lint; npm test; npm run build` |

Use `mvnw.cmd`, not `./mvnw` from Git Bash (certificate revocation check fails).

## Conventions

- Modules = top-level packages under `com.epi.procurement`; create a module only when it has code; the Modulith verification test must pass.
- REST under `/api/v1`; errors as RFC 9457 `ProblemDetail`, never stack traces.
- Schema changes only through new Flyway migrations (`V{n}__*.sql`); never edit an applied migration. Demo data only in `db/demo`, loaded by the `demo` profile, labelled "(Demo)".
- Types: instants `DATETIME2(3)` UTC ↔ `Instant`; dates `DATE` ↔ `LocalDate`; money `DECIMAL(19,4)` + ISO-4217; quantity `DECIMAL(19,4)`; FX `DECIMAL(19,8)`; rounding HALF_UP to currency minor units; text `NVARCHAR`; keys `BIGINT` from sequences.
- Company scope enforced centrally (ADR-007): out of scope → 404, forbidden action → 403. Every endpoint gets a cross-company test.
- Every state change writes an audit event in the same transaction (ADR-006).
- Every user-visible string goes in `FrontEnd/public/i18n/en.json`, `fr.json` and `ar.json` (Arabic from Sprint 2; key parity is enforced by a unit test). Use logical CSS properties (Arabic is right to left), design tokens, shared UI components; handle loading/empty/error states; respect `prefers-reduced-motion`.
- No secrets in the repository. Configuration via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; local overrides in git-ignored files.

## Delivery rules

- Each sprint delivers vertical slices (migration, API, UI, permissions, audit, EN/FR, tests) against written acceptance criteria.
- Three roles: Developer (owns `BackEnd/`, `FrontEnd/`), QA (owns `docs/qa/`), Reviewer (owns `docs/reviews/`); the lead owns other docs and root files. QA and Reviewer check the same integrated revision.
- A sprint is not complete while a Blocker or Major defect is open. Report checks actually run, with results; label skipped/blocked checks as such.
- Communication and documentation in English.
