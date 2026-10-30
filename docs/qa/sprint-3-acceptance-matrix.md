# Sprint 3 — Acceptance matrix (closure revision)

Revision under test: local main after Sprint 3 closure corrections (see [sprint-3-test-results.md](sprint-3-test-results.md)).  
Evaluator: Developer self-check. **Not** independent QA.

| AC | Verdict | Evidence |
|---|---|---|
| S3-AC-01 Buying queue + filters | **Implemented** | API filters: company, country, category, priority, amount band, status, requester, required date, purchase mode, assigned buyer, completeness, text. UI exposes the same. IT `queueFiltersCountryAmountAndCompleteness…`. |
| S3-AC-02 Completeness / question / return | **Implemented** | Completeness return → `PURCHASING_RETURNED`; resubmit skips approval. Purchasing question (`POST …/question`) SLA-free, status unchanged, audit `PURCHASING_QUESTION`. |
| S3-AC-03 Buyer assignment + suggestion | **Implemented** | Assign/accept/return; optimistic version; affinity table `buyer_category_affinity` when recorded else workload. IT affinity + stale assign 409. |
| S3-AC-04 Purchase mode | **Implemented** | DIRECT/CENTRALIZED/ON_BEHALF; charged unchanged. IT. |
| S3-AC-05 Supplier register | **Implemented** | SHARED/COMPANY; deactivate. IT + UI. |
| S3-AC-06 Organisation admin | **Implemented** | Companies/sites/departments/cost centres; cross-group 404. |
| S3-AC-07 Master-data import | **Implemented** | CSV all-or-nothing; duplicate reject. |
| S3-AC-08 Visibility | **Implemented** | Claire 403 queue / 404 request; Julien 404. |
| S3-AC-09 Audit and notices | **Implemented** | Assign/accept/return/mode/completeness/question audited; notices for assignment path. |
| S3-AC-10 Quality | **Implemented** (self) | EN/FR/AR; e2e journey; SQL Server verify. Independent axe/e2e of this closure revision: see test results. |

Grouping / internal transfer / buy again: **deferred** (G12 / backlog), not silently weakened.
