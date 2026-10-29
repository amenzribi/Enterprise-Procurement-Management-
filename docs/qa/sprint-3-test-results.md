# Sprint 3 — Test Results

| | |
|---|---|
| Revision | **`a24500ef35d3fb371a5f45cdf68044c6b3a16166`** (`a24500e`), on top of Sprint 2 close `42c03b9`. |
| Date | 2026-10-29 |
| Environment | Windows 11; backend `spring-boot:run` profiles `dev,demo` on **8081** (restarted with this tree); session `DEMO_USER_PASSWORD` in a temp file outside the repo; `ng serve --port 4300` with a scratchpad proxy to 8081; SQL Server `procurement_dev` (live) and `procurement_test` (DB-IT). Ports 8080/4200 were not in use and were not touched. Demo database was **not** wiped. |
| Tester | Developer. **Not** an independent QA or reviewer run. |
| Defects | [sprint-3-defects.md](sprint-3-defects.md). Open: S3-D-01, S3-D-02 (Minor). |

## Freshly executed on this tree

| Suite | Result | Notes |
|---|---|---|
| Backend `.\mvnw.cmd -o verify -Pdb-it` | **106 / 106 passed**, BUILD SUCCESS (3:08) | Includes `ModularityTests`, `PurchasingIntegrationTests` (3), `RequestApiIntegrationTests` (15, MIXED proposes DEMO-CPA), `ApprovalIntegrationTests` (15). SQL Server `procurement_test`. |
| Frontend `npm run lint` | **Pass** | After import overflow CSS. |
| Frontend `npm test` | **201 / 201 passed** (27 files) | Includes purchasing-inbox, suppliers, import-page, i18n key parity. |
| Frontend `ng build` | **Pass** (earlier this session, before the 360 px file-input CSS) | Production build. The later CSS/e2e-locator change is covered by lint + unit tests + the e2e re-run. |
| E2E run 1 `npx playwright test` (4300 → 8081, msedge) | **25 passed, 3 failed** | Failures: import page 360 px overflow (S3-D-05); Julien heading strict-mode (S3-D-06). S1/S2 journeys, session, axe EN/FR/AR desktop+phone, purchasing desktop+journey up to Julien: green. |
| E2E run 2 (failed specs only, after S3-D-05/D-06) | **5 / 5 passed** (1.6 min) | `accessibility-purchasing` EN/AR desktop+phone; purchasing journey Sami → Karim → Ines queue → Mehdi assigns Ines → Ines accepts; Claire `/purchasing` → page not found; Julien TN request → request not found. |
| Secrets | No passwords, tokens or connection strings in the working tree. Demo password only in `%TEMP%\epi-e2e-pw.txt`. | — |

S1/S2 e2e from run 1 that stayed green (not re-run after the CSS fix): accessibility EN/FR/AR desktop+phone, accessibility-approvals EN/AR desktop+phone, approval journey (EN/FR/AR + return/reject/question/absence), request journey EN/FR, session specs.

## Isolation checks (DB-IT, this tree)

| Check | Result |
|---|---|
| Ines (CPA BUYER) and Mehdi (CPA PM) see a Sfax MIXED approved request in `GET /purchasing/queue` | Pass |
| Claire (FR requester) `GET /purchasing/queue` | 403 |
| Claire `GET /requests/{tnId}` | 404 |
| Admin `GET /requests/{tnId}` | 404 |
| Purchase mode DIRECT leaves `charged_company_id` unchanged; buying company becomes DEMO-TN-PLT | Pass |
| Admin org companies 200; requester 403; sites of id 999999999 → 404 | Pass |
| Supplier list: PM 200, requester 403 | Pass |
| Supplier CSV import commit 200; duplicate DEMO-SKF → 422 `import-rejected`; still one DEMO-SKF row | Pass |

## Not executed this run

| Check | Status |
|---|---|
| Historical Sprint 0/1/2 API scripts | Not re-run. Do not treat those rows as new. |
| Independent QA / reviewer pass | Not done. |
| Native-speaker Arabic review | Outstanding (S2-C2). |
| S2CR-08 proxy-aware one-tap rate limit | Still Sprint 9. Not claimed fixed. |
| k6 / load | Not in this sprint. |
| Frontend production rebuild after the file-input CSS | Skipped; lint + 201 unit tests + e2e run 2 cover the change. |

## Pilot gate

Solution Guide v3.0 §15 is **unmet**. No production, no pilot tenant, no client approval recorded.
