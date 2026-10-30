# Sprint 3 — Test Results (closure revision)

| | |
|---|---|
| Revision | **`e3c47470bc91374da17f3251b75cc8544b168347`** (`e3c4747`), on top of `a24500e` / hash note `30e35b9`. |
| Date | 2026-10-30 |
| Environment | Windows 11; SQL Server `procurement_test` (DB-IT) and `procurement_dev` (manual/e2e). Ports **8081** / **4300** for app; 8080/4200 untouched. Demo DB not wiped. |
| Tester | Developer. **Not** an independent QA or reviewer run. |
| Defects | [sprint-3-defects.md](sprint-3-defects.md). S3-D-01 and S3-D-02 closed in this revision. |
| Acceptance | [sprint-3-acceptance-matrix.md](sprint-3-acceptance-matrix.md). |

## Freshly executed on the closure tree

| Suite | Result | Notes |
|---|---|---|
| Backend `.\mvnw.cmd -o verify -Pdb-it` | **108 / 108 passed**, BUILD SUCCESS (~4:53) | Includes new purchasing filter/question/affinity/concurrency tests (5 in `PurchasingIntegrationTests`). ModularityTests pass with V8. |
| Frontend `npm run lint` | **Pass** | All files. |
| Frontend `npm test` | **201 / 201 passed** (27 files) | Inbox filter-options + extended `queueQueryFromParams` covered. |
| Frontend `npm run build` | **Pass** | Production build. |
| E2E full suite `npx playwright test` (4300→8081) | **First run: 27 passed / 1 failed** (10.8 min). Failed: `approval screens accessibility EN desktop` — 60s test timeout during axe/`page.evaluate` (no assertion failure on product behaviour). **Re-run of that spec alone: passed.** | Full suite on the closure tree (S1+S2+S3), not purchasing-only. Not treated as a product defect. |
| Secrets scan of staged closure files | Clean | No passwords or connection strings in committed paths. Demo password only outside repo (`%TEMP%\epi-e2e-pw.txt`). |

## Isolation / AC checks (DB-IT, this tree)

| Check | Result |
|---|---|
| Queue country / amount / completeness filters | Pass |
| Purchasing question keeps `IN_PURCHASING_REVIEW`, writes audit | Pass |
| Affinity suggests Ines for DEMO-SPARE; stale assign → 409 | Pass |
| Claire queue 403; FR requester TN request 404; admin TN 404 | Pass |
| Purchase mode DIRECT: charged unchanged | Pass |
| Import duplicate DEMO-SKF → 422 | Pass |

## Still outstanding (not Sprint 3 defects)

| Item | Status |
|---|---|
| Independent QA / reviewer | Not done (self-review only). |
| Native-speaker Arabic review | Outstanding. |
| S2CR-08 proxy-aware rate limit | Sprint 9. |
| Pilot gate §15 | Unmet — rollout / production only. |
