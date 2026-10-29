# Sprint 3 handover (developer)

Local delivery, 2026-10-29, commit `a24500e` (`a24500ef35d3fb371a5f45cdf68044c6b3a16166`), on top of Sprint 2 close [`42c03b9`](../qa/sprint-2-test-results.md). **Not** an independent QA or code review. **Do not push** until the owner asks.

## What shipped

Approved requests appear in the buying company's purchasing inbox. A purchasing manager assigns a buyer (suggestion = lowest open load); the buyer accepts or returns. Completeness gaps are listed; a completeness return uses `PURCHASING_RETURNED` and resubmit does not restart approval. Purchase mode `DIRECT` / `CENTRALIZED` / `ON_BEHALF` may be set within the requesting company's mode (`MIXED` allows all three); `charged_company_id` never changes. Supplier register (SHARED / COMPANY), organisation admin, CSV import (all-or-nothing, duplicate detection, same-hash retry of a committed file). EN / FR / AR including RTL.

Plan: [sprint-3.md](../product/sprint-3.md). Results: [sprint-3-test-results.md](sprint-3-test-results.md). Defects: [sprint-3-defects.md](sprint-3-defects.md).

## How to run (this machine)

Ports **8081** and **4300** were used so 8080/4200 stay free. Backend `dev,demo` against Windows-auth SQL Server `procurement_dev`. Frontend: `npx ng serve --port 4300 --proxy-config <scratchpad proxy-8081.json>`. E2E: `E2E_BASE_URL=http://localhost:4300` and `DEMO_USER_PASSWORD` from the same value the backend process has (never committed).

## Demo path (S3-AC-10)

Sami (`requester.tn@demo.local`) submits → Karim (`manager.tn@demo.local`) approves → Ines (`buyer@demo.local`) sees it under `/purchasing` → Mehdi (`purchasing.manager@demo.local`) assigns Ines → Ines accepts. Claire cannot open `/purchasing`. Julien cannot open the Tunisia request.

Sfax is MIXED: the proposed buying company is the purchasing centre (DEMO-CPA), so the CPA queue sees Sfax approved requests. Setting DIRECT moves buying to Sfax and does not change the charged company.

## Out of scope (unchanged)

RFQs, quotation comparison, AI, supplier portals, internal refacturing, live ERP APIs. Grouping / internal transfer / buy again (G12). Placeholder screens and fake successful imports were not added.

## Open / do not regress

- S3-D-01, S3-D-02 (Minor) — inbox filter subset; no category-affinity table.
- S2CR-08 — Sprint 9.
- Native Arabic review — outstanding.
- Pilot-client gate — unmet; restricts rollout only.
- Company scope stays on the query (ADR-007). Do not obtain group visibility by dropping scoped queries.
- Do not delete demo data to make tests pass.

## Checks actually run

Backend `verify -Pdb-it` 106/106. Frontend lint pass, 201/201 unit tests. E2E: 25 + 5 after two phone/locator fixes; S1/S2 specs green on run 1. No independent review.
