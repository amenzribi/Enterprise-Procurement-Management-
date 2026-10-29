# Sprint 3 — Defects

Tester: Developer (same tree as the implementation). This is **not** an independent QA run.

## Open

| ID | Severity | Summary | Status |
|---|---|---|---|
| S3-D-01 | Minor | Purchasing inbox UI exposes text, status, priority, purchase mode and sort. S3-AC-01 also lists company, country, category, amount band, requester, required date, assigned buyer and completeness. Several of those exist on `GET /purchasing/queue`; country, amount band and completeness-as-filter are not on the API either. | Open — [backlog S3-C1](../product/backlog.md) |
| S3-D-02 | Minor | Buyer suggestion is lowest open ASSIGNED/ACCEPTED load. Category affinity is not stored, so the “same category if recorded” half of S3-AC-03 has nothing to read. | Open — [backlog S3-C2](../product/backlog.md) |

Neither is a Blocker or Major. Workarounds: call the API with extra query params; assign a buyer by name.

## Closed in this revision

| ID | Severity | Summary | Resolution |
|---|---|---|---|
| S3-D-03 | Major (test) | `GET /suppliers` returned 500: `OFFSET`/`FETCH` bind order was reversed, so page 0 requested `FETCH NEXT 0`. | `SupplierService.list` now binds offset then fetch size. |
| S3-D-04 | Major (test) | Setting purchase mode DIRECT returned 404 because the actor (CPA purchasing manager) lost visibility after `buying_company_id` moved to Sfax, on the post-update `requireVisible`. | Purchasing mutations reload with `RequestWorkflow.currentLocked`. |
| S3-D-05 | Minor | Import page at 360 px overflowed ~41 px (native file input). | Field/`input[type=file]` max inline size + file wrap. |
| S3-D-06 | Minor (test) | Julien 404 page has both an `h1` and an empty-state `h2` with the same title; Playwright strict mode failed. Product behaviour was correct (404). | e2e uses `heading` level 1, matching `request-journey.spec.ts`. |
| S3-D-07 | Blocker (build) | Demo supplier SKF used country `SE`, which is not in `country`. Flyway demo failed. | Country `DE`, tax id `DEMO-DE-SKF`. |
| S3-D-08 | Blocker (build) | Modulith cycle `identity → organization → identity` from `OrganizationAdminController` importing `CurrentUser`. | Writes stay in `organization` (`Actor` record); HTTP adapter is `orgadmin`. |

## Explicitly not defects of this sprint

- **S2CR-08** (rate limit behind proxy) stays Sprint 9. Not re-labelled fixed.
- Native-speaker Arabic review remains outstanding (S2-C2).
- Pilot-client gate (Solution Guide v3.0 §15) is unmet. No production / pilot-tenant claim.
- Grouping of similar needs, internal transfer, buy again, RFQs, AI, supplier portals, refacturing, live ERP APIs: out of scope.
