# Architecture

Status: Sprint 3 baseline (2026-10-29). Decisions referenced: [decision log](../decisions/decision-log.md).
Java 21 (ADR-008); Java 25 rejected (ADR-010).

## 1. Context

```
 Requesters, approvers, buyers,      ┌──────────────────────────────────┐      SQL Server
 logistics, finance, management ───► │  Angular SPA  (FrontEnd/)        │      (procurement DB)
          (browser / phone)          │  EN/FR/AR (RTL), responsive      │            ▲
                                     └───────────────┬──────────────────┘            │ JDBC + Flyway
                                                     │ HTTPS /api/v1 (JSON)          │
                                     ┌───────────────▼──────────────────────────────┴───┐
                                     │  Spring Boot modular monolith  (BackEnd/)        │
                                     │  modules present: organization · identity ·      │
                                     │  request · approval · document · notification ·  │
                                     │  audit · platform · system · purchasing ·        │
                                     │  supplier · importdata · orgadmin                │
                                     │  planned: sourcing · order · logistics …         │
                                     └───────┬───────────────────────┬──────────────────┘
                                             │ file store            │ ports (later)
                                             ▼                       ▼
                                       Attachments dir        ERP files / AI provider / OIDC IdP
```

## 2. Backend

- Java 21 LTS, Spring Boot 4.1, Maven Wrapper. Root package `com.epi.procurement`.
- Module = top-level package; `ApplicationModules.verify()` runs in the test suite (ADR-002).
- Layering inside a module: `api` (REST controllers, DTOs) → application services (transactions, authorisation,
  audit) → domain (entities, state machines, rules) → persistence (Spring Data JPA repositories).
- Errors: RFC 9457 `ProblemDetail`, no stack traces, stable `type` URIs and a translatable `code`.
- Validation: Jakarta Bean Validation at the API boundary; domain invariants in the domain.
- Concurrency: optimistic locking (`rowversion`/`@Version`) on mutable aggregates; state transitions are
  conditional updates so two approvers cannot both act on one step.
- Money: `BigDecimal` + ISO-4217 code. Quantity `DECIMAL(19,4)` with unit; amounts `DECIMAL(19,4)`; FX rate
  `DECIMAL(19,8)` = quote-currency units per 1 base unit, stored with date and source on each converted document;
  rounding `HALF_UP` to the currency's minor units at line and document totals.
- Idempotency: state-changing POSTs accept an `Idempotency-Key` (result kept 24 h) from S2; ERP export batches use
  their batch id.
- Time: `Instant` in UTC; per-company time zone for display and SLA calendars.
- Async work (notifications, ERP exports): transactional outbox table polled by a scheduler (ADR-003).
- Live updates: internal messaging pushes ids-only events over Server-Sent Events (`SseEmitter`, session cookie)
  after commit; clients resynchronise from the database on every (re)connection. Single node: see
  [internal-messaging.md](internal-messaging.md) before running several backend nodes.

## 3. Data

- Flyway migrations in `BackEnd/src/main/resources/db/migration` (`V{n}__description.sql`).
- Demonstration data in a separate location loaded only with the `demo` Spring profile.
- Company-scoped tables carry `company_id` (ADR-007). Reference data (countries, currencies) is global.
- Each aggregate synchronisable with an ERP carries an `erp_code`; a generic mapping table arrives with the first import.
- Audit: append-only, hash-chained `audit_event` (ADR-006).

## 4. Frontend

- Angular 22, standalone components, signals, zoneless, OnPush; lazy-loaded feature routes per module.
- `core/` (shell, i18n, http, error handling), `shared/ui/` (design system components), `features/<module>/`.
- Design tokens as CSS custom properties; logical properties (RTL-ready); dark-mode-ready tokens.
- i18n: `@ngx-translate/core`, `public/i18n/{en,fr,ar}.json`, key parity enforced by a unit test. Arabic is RTL.
- Dev: `ng serve` proxies `/api` and `/actuator` to `localhost:8080`. Production: SPA served behind the same
  reverse proxy as the API (same origin; no CORS).

## 5. Security (target; built from Sprint 1)

- Spring Security. Dev/demo: local accounts with Argon2/BCrypt hashes. Production: OIDC resource server against the
  client's IdP (Keycloak or Entra ID); MFA enforced by the IdP for management, purchasing, finance (§24).
- Authorisation = role × scope (company, department, country, category), applied by a central scoped-query
  component (ADR-007). Out-of-scope resource → 404; in scope but action not permitted → 403.
- Supplier prices, budgets and strategic data are field-level restricted (§20).
- Messaging: supplier conversations (ADR-011) and internal employee messaging
  ([internal-messaging.md](internal-messaging.md)) are separate tables and endpoints. Internal conversations are
  readable by participants only (no administrator override); request discussions also require current request access.
- TLS in transit; SQL Server TDE and encrypted file store at rest (client infrastructure).

## 6. Quality gates

- Backend: `mvnw verify` (unit, slice, Modulith verification; DB integration tests against `procurement_test`).
- Frontend: `npm ci`, lint, unit tests (Vitest), production build; axe-core accessibility checks from S1.
- Per sprint: QA acceptance run against the integrated revision; independent review; defects fixed and re-verified.
