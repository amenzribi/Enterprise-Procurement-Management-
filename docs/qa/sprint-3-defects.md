# Sprint 3 — Defects

Tester: Developer (same tree as the implementation). This is **not** an independent QA run.

## Open

| ID | Severity | Summary | Status |
|---|---|---|---|
| — | — | No open Sprint 3 defects after the closure revision. | — |

## Closed in the closure revision (2026-10-30)

| ID | Severity | Summary | Resolution |
|---|---|---|---|
| S3-D-01 | Minor | Inbox UI/API missing country, amount band, completeness and several other S3-AC-01 filters. | API + UI now expose company, country, category, requester, buyer, required dates, amount band, completeness (plus prior filters). IT covers country/amount/complete. |
| S3-D-02 | Minor | Buyer suggestion was workload-only; no category affinity store. | `buyer_category_affinity` (V8) + demo seed for Ines/DEMO-SPARE; suggestion prefers affinity then lowest open load. Not presented as AI. |

## Closed earlier in Sprint 3 delivery

| ID | Severity | Summary | Resolution |
|---|---|---|---|
| S3-D-03 | Major (test) | `GET /suppliers` OFFSET/FETCH bind order reversed. | Fixed in `SupplierService.list`. |
| S3-D-04 | Major (test) | Purchase mode DIRECT returned 404 after buying company change. | Mutations reload with `RequestWorkflow.currentLocked`. |
| S3-D-05 | Minor | Import page 360 px overflow (file input). | CSS max inline size + wrap. |
| S3-D-06 | Minor (test) | Julien 404 heading strict mode. | e2e uses `heading` level 1. |
| S3-D-07 | Blocker (build) | Demo SKF country `SE` invalid. | Country `DE`. |
| S3-D-08 | Blocker (build) | Modulith identity↔organization cycle. | `orgadmin` HTTP adapter. |

## Explicitly not defects of this sprint

- **S2CR-08** (rate limit behind proxy) stays Sprint 9. Not re-labelled fixed.
- Native-speaker Arabic review remains outstanding (S2-C2).
- Pilot-client gate (Solution Guide v3.0 §15) is unmet. Restricts rollout / production, not local development.
- Grouping of similar needs, internal transfer, buy again: G12 / deferred. RFQs are Sprint 4.
