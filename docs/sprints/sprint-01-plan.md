# Sprint 1 plan: security and infrastructure hardening

Approved by the owner on 2026-11-15 (gate G1 of the Phase 1 audit). Source of truth: `docs/audit/` reports 01, 03, 05, 08 and 09. Finding ids (S-, DB-, D-, A-) and roadmap item numbers (1.x, 2.x) are those of the audit.

**Goal:** make the system safe to expose and possible to observe. No public deployment in this sprint.

**Working state:** branch `feat/internal-messaging`, base commit `c7b901d`, on top of the uncommitted internal-messaging work. Nothing is committed by the sprint; the owner decides when to commit.

## Rules of the sprint

- Each finding is re-read in the source before it is changed.
- Small batches. After each batch: run the relevant tests, check for regressions, record the result, continue only if stable.
- A finding is reported closed only with a test or a recorded check that proves it.
- Work stops before any action behind an approval gate (section 3).

## 1. Work packages

| WP | Content | Audit items | Gate |
|---|---|---|---|
| WP1 | File type tied to extension; proxy client address; CSV formula neutralisation; safe JSON in audit payloads; supplier search wildcards; startup guard against unsafe configuration; explicit mail mode | 1.1, 1.3, 1.10 (part), 1.11; S-2, S-3, S-13, S-14, S-15, S-16, S-24 | None |
| WP2 | Malware scanning made fail-closed: real scan result stored, unscanned files quarantined, scanner failure refuses the upload | 1.2 (part); S-1 | Scanner product: G3 |
| WP3 | Administrator powers: chosen passwords reserved to global administrators, no power over a peer administrator, no self-delegation, override limited to the absent person's companies | 1.4, 1.5; S-4, S-5 | In the sprint mission; remaining choices in section 3 |
| WP4 | Audit events for absences, approval rules, exchange rates | 1.6; S-6 | None |
| WP5 | Company scope on administrator listings (companies, users, absences) | 1.7 (part); S-7 | Rate writes: section 3 |
| WP6 | Retention: scheduled purge of idempotency records and expired tokens | 1.14; DB-4 | Retention periods beyond the documented ones: section 3 |
| WP7 | Database waits: lock timeout, transaction timeout, 503 mapping | 2.3 (part); DB-3 | RCSI itself: G3 |
| WP8 | Two database logins: configuration support and provisioning script (not executed) | 1.12; DB-1, D-3 | Executing it on a database: G3 |
| WP9 | Foreign-key indexes: evidence gathered, migration prepared | 2.5; DB-2 | Applying without load evidence: section 3 |
| WP10 | Request correlation id, access log, structured logging without sensitive data | 2.1; A-2, P-4 | None |
| WP11 | Metrics and health checks, administrator-only | 2.2; A-2, D-5 | None |
| WP12 | CI pipeline: backend tests with SQL Server, frontend lint, tests and build | 2.4; D-1 | None (file in the repository; it runs once pushed) |
| WP13 | Authorization test matrix over every endpoint | 1.16; A-4 | None |

Not in this sprint (audit, section 10): UI work, load testing, MFA or single sign-on, quote-link reuse (S-8), invoice separation of duties (S-9), UAT deployment.

## 2. Order

WP1 → WP2 → WP3, WP4, WP5 → WP6 → WP7 → WP10, WP11 → WP13 → WP12 → WP8, WP9 (prepared, not applied).

## 3. Decisions that wait for the owner

| # | Decision | Why it is not decided by the sprint |
|---|---|---|
| G2-a | Remove administrator-chosen passwords entirely, or keep them for global administrators (current sprint state) | Business rule on account recovery |
| G2-b | Quote link single use or versioned revisions (S-8); invoice separation of duties (S-9) | Business rules; not in the sprint mission |
| G2-c | Reserve exchange-rate writes for global administrators (S-7, last part) | Changes who maintains rates today |
| G3-a | Antivirus product (ClamAV is the usual choice) or a written acceptance of unscanned downloads | New infrastructure (ADR-003) |
| G3-b | Enable `READ_COMMITTED_SNAPSHOT` on each database | Database-wide change; needs backup, rollback plan and a full rerun |
| G3-c | Create the two database logins and switch the application to the runtime login | Changes database security; needs the DBA |
| G3-d | Apply the foreign-key index migration | Audit asks for query evidence on a loaded database first |
| G4 | Hosting, domain, SQL Server licensing | Hosting configuration |

## 4. Definition of done

- New tests pass together with the existing suites (baseline in `sprint-01-test-results.md`).
- Every closed finding names its test.
- `sprint-01-final-report.md` lists the remaining risks and the UAT readiness status.
