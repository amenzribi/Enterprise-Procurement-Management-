# ADR-001: Microsoft SQL Server as the system database

- Status: Accepted (client decision, 2026-10-24); **reaffirmed by the owner on 2026-11-19**: development, Google Cloud UAT and the path to production continue on SQL Server 2022. A PostgreSQL migration is deferred to a future, dedicated experimental branch once the application baseline is stable (assessment of 2026-11-19: about 6 to 9 weeks, mainly native SQL, locking and case-insensitive comparison). Meanwhile new features avoid unnecessary new SQL Server-specific dependencies, without changing existing, tested code for that reason alone.
- Supersedes: specification §22 "Base de données : PostgreSQL, migrations Flyway"

## Context

The specification names PostgreSQL. The client confirmed Microsoft SQL Server as the database. Target clients
(banks, insurers, industrial groups in the region) frequently already operate SQL Server with existing DBA skills,
backup tooling and licences.

## Decision

- Use Microsoft SQL Server (development: SQL Server 2025 Developer Edition, version 17.0) for all transactional data.
- Keep Flyway for schema migrations, with the `flyway-sqlserver` database module.
- JDBC driver: Microsoft `mssql-jdbc` managed by Spring Boot.
- Connection configured only through environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
  Local development uses Windows integrated authentication, so no password exists in configuration.
- Text columns use `NVARCHAR` (French, and later Arabic). Development databases are created with a UTF-8,
  supplementary-character-aware collation `Latin1_General_100_CI_AS_SC_UTF8`.
- Money: `DECIMAL(19,4)` with an ISO-4217 currency code column. Timestamps stored in UTC.
- Prefer portable SQL through JPA; T-SQL-specific features (full-text search, row-level security, temporal tables)
  are allowed where they replace an infrastructure component (see ADR-003) and must be isolated in migrations or
  dedicated repository code.
- Instants are `DATETIME2(3)` holding UTC, mapped to `java.time.Instant`; calendar dates are `DATE` ↔ `LocalDate`.
- Keys are `BIGINT` drawn from SQL Server sequences (pooled optimiser, batch-insert friendly). Business numbers
  (request, PO) come from a `document_counter` table locked inside the issuing transaction.
- Integrated Windows authentication is a **development-only** convenience. Production uses SQL authentication with a
  secret store, Kerberos (`authenticationScheme=JavaKerberos`) or Entra ID. Non-development profiles fail fast when
  `DB_URL` is not set.
- Supported editions: SQL Server 2019 Standard or later; Express (with Advanced Services when full-text search is
  used) for small installations within its limits.

## Consequences

- The "no third-party licence" promise (§1) must be reworded (RC-02). SQL Server Express (free, 10 GB per database)
  may serve small installations.
- Docker is not available on the development laptop, so tests cannot use Testcontainers; database integration tests
  run against a dedicated local database `procurement_test` and are skipped (reported as skipped) when not configured.
  No other database (e.g. H2) is substituted in tests.
- SQL Server full-text search can defer OpenSearch (ADR-003).
