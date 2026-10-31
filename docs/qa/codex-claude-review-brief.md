# Review brief for a separate Codex / Claude pass

| | |
|---|---|
| Purpose | Hand independent reviewers enough context to challenge this Core build |
| Author label | Developer packaging — **this brief is not itself an independent review** |
| Date | 2026-10-31 |

## Product purpose and scope

Enterprise Procurement Intelligence — multi-company procurement request platform (Functional **Core**). Journey: request → approval → purchasing → RFQ/quote → award → PO → shipment → receipt/invoice → closure, plus dashboards, org/import, GLOBAL_ADMIN, buyer–supplier messaging, and an internal assistant in **demo/disabled** modes only.

**Out of Core / not delivered as complete:** Trading & Enterprise add-ons, live ERP APIs, automated three-way match, OCR/AI document reading, live AI provider, MFA, messaging file attachments, grouping/buy-again/internal transfer (G12), PostgreSQL.

## Architecture / repo map

| Area | Path |
|---|---|
| Backend | `BackEnd/` Java 21, Spring Boot, Modulith packages under `com.epi.procurement.*` |
| Frontend | `FrontEnd/` Angular standalone/signals |
| Migrations | `BackEnd/src/main/resources/db/migration/V1`…`V15` (**do not edit applied**) |
| Specs | `docs/specification/`, analysis under `docs/analysis/` |
| ADRs | `docs/decisions/` (ADR-001 SQL Server … ADR-012 assistant hosting) |
| QA | `docs/qa/` |

## How to run

```powershell
# Backend (preferred ports)
$env:DEMO_USER_PASSWORD = (Get-Content -Raw "$env:TEMP\epi-e2e-pw.txt").Trim()
$env:SERVER_PORT = "8081"
cd BackEnd; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"

# Frontend
cd FrontEnd; npx ng serve --port 4300 --proxy-config proxy.conf.8081.json

# Tests
cd BackEnd; .\mvnw.cmd verify "-Pdb-it"
cd FrontEnd; npm run lint; npm test; npm run build; npm run e2e
```

DB: SQL Server `procurement_dev` (app), `procurement_test` (IT, cleaned by tests).

## Requirement / decision references

- Matrix: [requirements-implementation-matrix.md](requirements-implementation-matrix.md)
- Register: `docs/product/requirements-register.md`
- Roadmap: `docs/product/roadmap.md`
- System review: [sprint-13-system-review.md](sprint-13-system-review.md)
- AI hosting: ADR-012 + [ADR-012-hosting-decision-brief.md](../decisions/ADR-012-hosting-decision-brief.md)

## Commits and dirty files

- Delivery through Sprint 12: `b967b3e`, handover `fabfe96` (+ Sprint 13 commits when present).
- **Unrelated dirty tree:** purchasing/supplier/org/import/V7/frontend/docs — classified **CRLF-only**; do not normalize or commit in review fixes unless Mohamed asks. **Never edit applied V7.**

## Verified vs unverified

| Verified (developer automation) | Unverified / pending |
|---|---|
| Backend IT suite on SQL Server; FE unit/lint/build; full Playwright e2e including messaging + assistant demo | Manual UAT S5–13; independent QA/reviewer; pen-test; load; restore drill; live AI quality |
| Demo assistant isolation tests | Live provider security/quality |

## Open defects / accepted risks / gates

See [sprint-13-system-review.md](sprint-13-system-review.md). Gates unmet: §15 pilot, MFA, pen-test, NFR-01/10, live AI hosting.

## Areas needing independent review

1. Cross-company and supplier isolation on every new endpoint since S8.
2. Public `/a` and `/q` token handling + trusted-proxy rate limits.
3. Award/PO immutability and quantity invariants through receipt.
4. Messaging audience separation + revocation.
5. Assistant retrieval authz and prompt-injection posture (even in demo).
6. GLOBAL_ADMIN permission surface vs business data.
7. Flyway/bootstrap/demo password behaviour for production misuse.

## AI demo vs live

Demo adapter responses are **deterministic and labelled**. They do **not** prove live-model quality, multilingual answer quality, or production prompt-injection resistance. Do not configure external providers in this review unless Mohamed has approved hosting.

## Secrets

No passwords, reset tokens, or API keys in git. Demo password only via env / `%TEMP%\epi-e2e-pw.txt`.
