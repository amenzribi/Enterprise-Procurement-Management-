# Decision Log

Architecture decisions. Significant ones have their own ADR file; smaller ones are recorded here.

| ID | Date | Decision | Status |
|---|---|---|---|
| ADR-001 | 2026-10-24 | SQL Server instead of PostgreSQL — [ADR-001-sql-server.md](ADR-001-sql-server.md) | Accepted |
| ADR-002 | 2026-10-24 | Modular monolith with Spring Modulith | Accepted |
| ADR-003 | 2026-10-24 | Introduce infrastructure components only when needed | Accepted |
| ADR-004 | 2026-10-24 | Angular custom design system on Angular CDK; runtime i18n with ngx-translate | Accepted |
| ADR-005 | 2026-10-24 | Approval circuits as rule table + domain state machine (no BPMN engine in MVP) | Proposed |
| ADR-006 | 2026-10-24 | Hash-chained, append-only audit log in the database | Accepted |
| ADR-007 | 2026-10-24 | Company visibility model, centrally enforced scope, verified by tests | Accepted |
| ADR-008 | 2026-10-24 | Spring Boot 4.1 / Java 21 | Accepted |
| ADR-009 | 2026-10-24 | E-mail notifications through a transactional outbox | Accepted |
| ADR-010 | 2026-10-25 | Adopt Java 25 LTS | Rejected (client kept Java 21) |
| ADR-011 | 2026-10-31 | Supplier messaging + SUPPLIER_CONTACT identity — [ADR-011-supplier-messaging.md](ADR-011-supplier-messaging.md) | Accepted |
| ADR-012 | 2026-10-31 | AI assistant hosting options; provider-neutral port; demo/disabled default — [ADR-012-ai-assistant-hosting.md](ADR-012-ai-assistant-hosting.md) | Accepted (provider pending) |

## ADR-002 Modular monolith with Spring Modulith

One deployable Spring Boot application. Each business module (`organization`, `identity`, `request`, `approval`,
`purchasing`, `sourcing`, `ordering`, `logistics`, `receiving`, `supplier`, `document`, `dashboard`, `integration`,
`audit`, `system`) is a top-level package under `com.epi.procurement` with a public API package and internal
implementation. Spring Modulith verifies there are no illegal dependencies or cycles in a test that runs on every
build. Modules communicate through public services or application events. AI and ERP integrations may later become
separate processes behind ports, as the specification recommends (§22).

Rejected: microservices (operational cost for on-prem clients; no team-scaling need); a single unstructured package
(boundaries erode quickly).

## ADR-003 Infrastructure on demand

See the table in `docs/analysis/specification-review.md` §5. MVP runs with Spring Boot + SQL Server + a file
directory only. Each deferred component has an explicit trigger for introduction. Abstractions (storage port,
notification port, AI provider port, integration port) keep later introduction local.

## ADR-004 Frontend foundations

- Angular 22 standalone components, signals, zoneless change detection, OnPush.
- Angular CDK for accessibility primitives (focus management, overlays, layout breakpoints); no Angular Material
  theme, so the visual identity is our own design-token system.
- React-based catalogues (21st.dev, React Bits) may inspire visuals only; nothing React is installed.
- Runtime language switching with `@ngx-translate/core` (Angular's built-in i18n compiles one bundle per locale and
  cannot switch without reloading a different build). Locale data registered for EN and FR for dates and numbers.
- Self-hosted Inter Variable font (no public CDN dependency for on-prem installs).
- Motion: CSS transitions only, ≤200 ms, disabled under `prefers-reduced-motion`. No animation library (GSAP etc.)
  until a concrete need is shown.

## ADR-005 Approval circuits without a BPMN engine (MVP)

US-04 requires per-company, per-category, per-amount circuits configurable without development. A rule table
(company × category × purchase type × amount band → ordered **stages**, each holding one or more approvers with an
`ALL`/`ANY` policy, an optional condition, SLA and escalation)
plus an explicit domain state machine covers sequential circuits, delegation, rejection, revision and information
requests. Flowable (spec §22) is reconsidered when a client needs parallel/conditional circuits that the table
cannot express. Status: Proposed — confirm with first pilot client.

## ADR-006 Audit log

Append-only `audit_event` table with one hash chain per company (plus a system chain for non-company events). Each
row stores the SHA-256 of a fixed canonical serialisation of its content (explicit field order, UTF-8, ISO-8601 UTC
with milliseconds, fixed decimal scale, explicit null marker) concatenated with the previous hash of the same chain.
The chain-head row is locked (`UPDLOCK, HOLDLOCK`) inside the same transaction as the business change, so concurrent
writers cannot fork the chain and no event exists without its change. A verification routine recomputes each chain.
Production uses separate database principals: a migration owner (DDL) and a runtime user with INSERT/SELECT only on
`audit_event` (decided in S1). The chain head is periodically anchored outside the database (HMAC with an external
key or WORM storage, S9). This is tamper-evident, not tamper-proof (RC-10).

## ADR-007 Company visibility and isolation

Company-scoped tables carry `company_id`; requests carry several company roles (requesting, charged, buying,
processing). Visibility per role is defined in `docs/analysis/design-review-response.md` (visibility model). The
user's scope is resolved once per request and applied by a mandatory scoped-query component; an architecture test
forbids controllers from using unscoped repositories. Out-of-scope resources return 404; in-scope but forbidden
actions return 403. Automated tests attempt cross-company access for every endpoint. A time-boxed S1 spike evaluates
SQL Server Row-Level Security with `SESSION_CONTEXT` as defence in depth.

## ADR-008 Spring Boot 4.1 / Java 21

The spec names Spring Boot 3. The 3.x line's open-source support has ended; Spring Boot 4.1.2 is the current
supported release (start.spring.io default on 2026-10-24) and supports Java 21, the LTS installed and named in §22
at the time of this decision. ADR-010 (Java 25) was proposed and rejected; Java 21 remains the target.

## ADR-010 Adopt Java 25 LTS — Rejected

Status: Rejected on 2026-10-25. The client chose to stay on Java 21: it is installed on the development machine,
named in the specification (§22), and all Sprint 0/1 checks were validated on it. Java 25 can be reconsidered as a
separate upgrade with its own build and test run on JDK 25. Original proposal kept below for the record.


Update ADR-008's backend runtime and compiler target from Java 21 to Java 25 LTS while retaining Spring Boot 4.1,
the Maven Wrapper, and Microsoft SQL Server as the only database. Java 25 is the current LTS target; the existing
Spring Boot 4.1 line supports Java 25, so no framework or database migration is required. Builds and tests must
run on JDK 25 before this runtime target is considered validated.

## ADR-009 Notifications

E-mail (SMTP) from S2 via a transactional outbox and scheduled sender, localised in the recipient's stored language,
with deep links to the action. In-app notifications in S3. Push/PWA later.
