# Sprint 1 Test Results

Plan: [sprint-1-test-plan.md](sprint-1-test-plan.md) · Defects: [sprint-1-defects.md](sprint-1-defects.md)

| Item | Value |
|---|---|
| Revision | git tree `be1f311` (staged index on commit `77d22b1`); `git diff -- BackEnd FrontEnd` empty at start |
| Date | 2026-10-25 |
| Machine | Windows 11, JDK 21.0.12 (Temurin), Node 24.20 / npm 11.19, SQL Server 2025 Developer 17.0.1000.7 (Windows auth) |
| Backend under test | Built JAR, `dev,demo`, `SERVER_PORT=8081` (8080 is held by an unrelated `Ticket System` app + Keycloak), DB `procurement_dev`, QA-chosen `DEMO_USER_PASSWORD` |
| Extra backends | 8082: same DB, idle 60 s / absolute 120 s / throttle window and block 60 s (T-1/T-2). 8083: scratch DB `procurement_qa_scratch_s1` (created and dropped) |
| Frontend | `ng serve` on 4200 with a scratchpad proxy file targeting 8081; Playwright with installed Microsoft Edge |
| API harness | Stdlib Python client (cookie session + `XSRF-TOKEN` → `X-XSRF-TOKEN`), kept in the session scratchpad |

Legend: **P** pass · **F** fail (see defect) · **NE** not executed (reason given) · **B** blocked.

## Totals

| Executed | Passed | Failed | Not executed | Blocked |
|---|---|---|---|---|
| 121 | 117 | 4 | 14 | 0 |

The 4 failures map to QA1-D-01 (Major: QA1-10.1), QA1-D-02 (Minor: QA1-07.1, QA1-07.7) and QA1-D-05 (Minor: QA1-01.13).
QA1-D-03, QA1-D-04 and QA1-D-06 were found during execution outside a planned scenario.

## Automated suites (run by QA)

| Suite | Result |
|---|---|
| `mvnw.cmd -o verify` | 74 tests, 0 failures, 0 errors, 43 skipped (SQL Server tests), BUILD SUCCESS |
| `mvnw.cmd -o verify -Pdb-it` (procurement_test) | 74 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS |
| First combined run | Tests 74/74 green, then the JAR step failed on `target/classes/META-INF/build-info.properties` (file rewritten concurrently by the VS Code Java language server). Re-run sequentially: green. Environmental, not a product defect. |
| `npm ci` | OK; `package-lock.json` unchanged (hash compared) |
| `npm run lint` | All files pass |
| `npm test` | 16 files, 136/136 passed |
| `npm run build` | OK, initial transfer 124.22 kB |
| `npm audit --omit=dev` | 0 vulnerabilities |
| `npm run e2e` (Edge) | 10 tests: **9 passed, 1 failed** (`accessibility EN desktop`, colour contrast, QA1-D-01). Re-run of the accessibility file: 3 passed, the same 1 failed. |

## S1-AC-01 — Sign-in, sign-out, session security

| ID | Status | Evidence |
|---|---|---|
| QA1-01.1 | P | 200 `Me`, roles `[REQUESTER]`, `idleTimeoutSeconds` 1800, `absoluteTimeoutSeconds` 28800; `Set-Cookie: EPI_SESSION=…; Path=/; HttpOnly; SameSite=Lax` (no `Secure` in dev, by design) |
| QA1-01.2 | P | Wrong password vs unknown e-mail: both 401, identical body `{detail, status, title, code: invalid-credentials}` |
| QA1-01.3 | P | Median 130 ms (existing) vs 128 ms (unknown), 9 samples each |
| QA1-01.4 | P | No session exists before sign-in; second sign-in within a session rotates the id; the old id → 401 |
| QA1-01.5 | P | No token / wrong token / other session's token → 403 `csrf-invalid` ×3 |
| QA1-01.6 | P | Login without CSRF token → 403 `csrf-invalid` |
| QA1-01.7 | P | Logout 204; replayed cookie → 401 `session-expired` |
| QA1-01.8 | P | (8082, idle 60 s) idle 70 s → 401 `session-expired`; a sliding session touched at 45 s still 200 at +25 s |
| QA1-01.9 | P | `application.yml`: `idle-timeout: 30m`, `absolute-timeout: 8h`; `Me` reports 1800 / 28800 |
| QA1-01.10 | P | (8082, absolute 120 s) activity every 20 s: 200 at 20–100 s, 401 `session-expired` at 120 s and after |
| QA1-01.11 | P | Adapted to S1DR-03 (throttling, no 423): after 9 failures the correct password → 401 `invalid-credentials`; (8082) released after 65 s → 200; after a success one failure doesn't block |
| QA1-01.12 | P | Unknown e-mail: identical 401, dummy hash compared (code review of `SignInService`), no throttle audit for unknown accounts |
| QA1-01.13 | **F** | `{bcrypt}$2a$…` hashes, no plaintext in logs or audit, but **1 distinct hash for 8 accounts** → QA1-D-05 |
| QA1-01.14 | P | Adapted to S1DR-04: global chain has `SIGNED_IN` 105, `SIGNED_OUT` 6, `SIGN_IN_THROTTLED` 1; failures not chained; 0 rows with password or hash in `payload_json` |
| QA1-01.15 | P | Two concurrent sessions return their own users |
| QA1-01.16 | P | Injection, 10 kB, Arabic, null and numeric e-mails → 401; malformed JSON → 400 `invalid-request`; no stack traces |

## S1-AC-02 — Demo users

| ID | Status | Evidence |
|---|---|---|
| QA1-02.1 | P | Scratch DB, `dev` only: V1–V4 applied, 0 users, demo login 401 |
| QA1-02.2 | P | All 8 demo users signed in (matrix run); roles and companies consistent with behaviour (create rights, visibility, form-options) |
| QA1-02.3 | P | `dev,demo` without `DEMO_USER_PASSWORD`: password printed once ("Password for all 8 demo accounts…: Demo-…"), sign-in 200 with it |
| QA1-02.4 | P | No literal demo password in tracked files (README uses a placeholder; e2e reads an env var). Test profile has a test-only literal (observation) |
| QA1-02.5 | P | Seeded scratch DB restarted with `dev` only: 8 demo rows remain, sign-in → 401 |
| QA1-02.6 | P | `@demo.local` addresses, display names end with "(Demo)" |

## S1-AC-03 — Draft content, lines, totals

| ID | Status | Evidence |
|---|---|---|
| QA1-03.1 | P | Title-only draft → 201, `number: null`, `DRAFT`, `Location: /api/v1/requests/102` |
| QA1-03.2 | P | 0/−1 → `quantity-positive`; 5 decimals → `max-decimals`; "abc" → 400; 4 decimals and 1e3 accepted; 99999999999999.9999 stored exactly (DB check); 1e15 → `too-large`. (A 1e14 value fits DECIMAL(19,4); the plan's overflow example was wrong.) |
| QA1-03.3 | P | −0.01 → 422; 0 → 201; 5 decimals → 422 |
| QA1-03.4 | P | 100 lines 201; 101 → 422 `too-many-lines` |
| QA1-03.5 | P | Incomplete line saves; submit lists `lines[0].description/unit/estimatedUnitPrice: required` plus header errors |
| QA1-03.6 | P | TND: 3 × 0.3335 → 1.001, 1 × 1.0005 → 1.001, total 2.002 (lines rounded HALF_UP, then summed) |
| QA1-03.7 | P | EUR 0.005 → 0.01; JPY 0.5 → 1 |
| QA1-03.8 | P | `totalAmount` in body → 422 `not-editable`; line-level `currency`/`lineTotal` ignored, total recomputed 1000 |
| QA1-03.9 | P | See 03.8 |
| QA1-03.10 | P | Unknown category, currency, priority, purchase type → 422 `invalid-value` per field |
| QA1-03.11 | P | Arabic/emoji/accented title round-trips; 200 chars OK, 201 → `too-long` |
| QA1-03.12 | P | Past required date: draft saves, submit → `required-date-past` (decision Q1) |

## S1-AC-04 — Actors and relationship rules

| ID | Status | Evidence |
|---|---|---|
| QA1-04.1 | P | requester = entered-by = session user |
| QA1-04.2 | P | `requester.tn` with `requesterId` = manager → 422 `requester-not-allowed` |
| QA1-04.3 | P | `assistant.tn` on behalf of `requester.tn`: requester id 1, entered-by id 51; listed for both |
| QA1-04.4 | P | On behalf of `requester.fr` → 422 `requester-not-allowed` |
| QA1-04.5 | P | `enteredById` → 422 `not-editable` |
| QA1-04.6 | P | `requestingCompanyId` = FR → 422 `company-not-allowed` |
| QA1-04.7 | P | FR department/site/cost centre → 422 `not-in-company` each |
| QA1-04.8 | P | Revised by S1DR-07: client `chargedCompanyId` → 422; server derives charged = DEMO-TN-PLT |
| QA1-04.9 | P | Client `buyingCompanyId` → 422; server proposes buying = DEMO-TN-PLT (MIXED mode), `buyingCompanyProposed: true` |
| QA1-04.10 | P | Non-existent ids give the same codes as foreign ids (`not-in-company`, `invalid-value`, `requester-not-allowed`) |
| QA1-04.11 | P | `requester.tn`: only DEMO-TN-PLT, no on-behalf; `assistant.tn`: three TN colleagues only |

## S1-AC-05 — Submission, numbering, atomicity

| ID | Status | Evidence |
|---|---|---|
| QA1-05.1 | P | `DEMO-TN-PLT-PR-2026-000011`, `CREATED`, `submittedAt` set |
| QA1-05.2 | P | Empty draft → 6 field errors at once; next valid submit got 000012 (no gap) |
| QA1-05.3 | P | Audit `DRAFT_CREATED` and `SUBMITTED` DRAFT→CREATED in chain `company:101`; counter consistent |
| QA1-05.4 | P | Scratch: FR first → `DEMO-FR-PLT-PR-2026-000001` |
| QA1-05.5 | P | Scratch: TN 2025 counter row at 57 doesn't affect 2026 (→ 000003) |
| QA1-05.6 | P | Scratch: two parallel first submissions (no counter row) → 000001 and 000002, no 500 |
| QA1-05.7 | P | Revised by S1DR-08: 31 shared codes, no `IN_TRANSIT`; labels covered by the parity test |
| QA1-05.8 | P | `number`/`status` in PUT → 422 `not-editable`; still DRAFT |
| QA1-05.9 | P | Scratch: temporary `AFTER UPDATE` trigger on `audit_chain_head` throwing for the TN chain → submit 500 `internal-error`, no leak; request DRAFT, number null, counter 5 → 5, 0 audit rows, 0 transitions. Trigger dropped → resubmit got 000005 (no gap); chains intact. (A first attempt with an `audit_event` trigger exposed QA1-D-06.) |
| QA1-05.10 | NE | Lock-holding variant not run; 05.9 already proves atomicity |
| QA1-05.11 | NE | Code review not repeated; 05.9 gives independent evidence |

## S1-AC-06 — My requests

| ID | Status | Evidence |
|---|---|---|
| QA1-06.1 | P | `requester.tn` sees own and on-behalf requests; `assistant.tn` sees those entered (04.3) |
| QA1-06.2 | P | `size=10` paging correct; `size=0`, `size=1000`, `page=-1` → 400 |
| QA1-06.3 | P | All 8 sort fields 200; `sort=password_hash`, `sort=1;DROP TABLE` → 400 |
| QA1-06.4 | P | `q=%` and `q=_` match literals only; `q='` 200; `status=DRAFT` filter correct; `status=BOGUS` → 400 |
| QA1-06.5 | P | `requester.fr` scope=visible: only FR company requests |
| QA1-06.6 | NE | Browser loading/empty/error states not exercised by QA (developer unit tests only) |

## S1-AC-07 — Company isolation and authorisation matrix

| ID | Status | Evidence |
|---|---|---|
| QA1-07.1 | **F** | All 404 cells, list visibility (8 users × D1/C1/C2), create rights (`manager`, `buyer`, `purchasing.manager`, `ceo`, `admin` → 403), anonymous 401 as expected. **Deviation:** `PUT C1` (manager, ceo), `DELETE C1` (manager, ceo), `SUBMIT C2` (ceo) → 409 `invalid-state` instead of 403 → QA1-D-02 |
| QA1-07.2 | P | 404 bodies identical apart from `instance` for GET, PUT, submit (foreign vs non-existent); no ETag |
| QA1-07.3 | P | Median 9.7 ms (foreign) vs 14.3 ms (non-existent) — no oracle |
| QA1-07.4 | P | PUT switching company, requester or department → 422; version unchanged |
| QA1-07.5 | P | `requester.fr` PUT on D1 with the correct version → 404 |
| QA1-07.6 | P | `abc` 400, `-1` 404, `1.5` 400, overflow id 400 |
| QA1-07.7 | **F** | `ceo` on visible C1/C2 → 409 instead of 403 (same as QA1-D-02); on D1 → 404 ✓ |
| QA1-07.8 | P | Architecture rules pass; `ScopedRequests` is the only repository user (QA1-14.x) |
| QA1-07.9 | NE | No second TN department requester in demo data; not created |

## S1-AC-08 — Locking and concurrency

| ID | Status | Evidence |
|---|---|---|
| QA1-08.1 | P | A 200, B (stale) 409 `stale-version`, A's change kept; line-only edit bumps version 1 → 2 (S1DR-06) |
| QA1-08.2 | P | Stale submit → 409 `stale-version` |
| QA1-08.3 | P | Second submit → 409 `invalid-state` |
| QA1-08.4 | P | 10 parallel submits (separate sessions) → 1 × 200, 9 × 409 |
| QA1-08.5 | P | 30 parallel submits (two users) → all 200, numbers 000016…000045 contiguous, no duplicates; counter = 46 |
| QA1-08.6 | NE | Lock-hint mutation not run (time); 05.6 and 08.5 give independent evidence |
| QA1-08.7 | P | PUT on CREATED → 409 `invalid-state` |

## S1-AC-09 — English and French

| ID | Status | Evidence |
|---|---|---|
| QA1-09.1 | P | Parity test green; 394 keys each, identical sets |
| QA1-09.2 | P | Every API error/field code has EN and FR keys (unmatched strings are internal log reasons only) |
| QA1-09.3 | P | e2e FR journey green (on behalf, French UI) |
| QA1-09.4 | NE | Number/date formats not independently verified by QA |
| QA1-09.5 | P | 31 statuses covered by the parity test |

## S1-AC-10 — Accessibility and small screens

| ID | Status | Evidence |
|---|---|---|
| QA1-10.1 | **F** | axe: EN phone, FR desktop, FR phone 0 violations; **EN desktop color-contrast** (hovered row) → QA1-D-01 |
| QA1-10.2 | P | Keyboard on list: skip link (visible focus) → main → New request → search → status → table region → sortable headers, all with focus rings. Form keyboard covered by e2e only |
| QA1-10.3 | P | e2e: error summary receives focus; axe on the form with errors passes |
| QA1-10.4 | P | e2e 360 px EN and FR: no horizontal scroll on sign-in, list, form, detail |
| QA1-10.5 | NE | 200 % zoom not checked |
| QA1-10.6 | NE | Screen reader (Narrator) not available in this automated session |

## S1-AC-11 — Stage, owner, since

| ID | Status | Evidence |
|---|---|---|
| QA1-11.1 | P | Detail: stage `REQUEST`, owner = requester (API and e2e journey) |
| QA1-11.2 | P | Transitions `NULL→DRAFT` and `DRAFT→CREATED`, owner 1, timestamps; `owner_since` stays at assignment (owner unchanged) |
| QA1-11.3 | P | On-behalf request: owner = requester (1), entered-by 51 |

## S1-AC-12 — Language preference

| ID | Status | Evidence |
|---|---|---|
| QA1-12.1 | P | PUT 204; server preference changed (read back via `/me`) |
| QA1-12.2 | P | Fresh browser (en-GB, empty storage): English sign-in page, French after signing in as a user whose preference is `fr` |
| QA1-12.3 | P | See 12.2 |
| QA1-12.4 | P | `de`/`FR`/`""` → 422; empty body → 400; no CSRF → 403; `en` → 204 |
| QA1-12.5 | NE | Preference save failure UX not tested |
| QA1-12.6 | P | Browser kept `epi.language=fr` from the previous user; `requester.fr` (preference `en`) signed in without touching the selector → UI English, server still `en` |

## S1-AC-13 — Diagnostics exposure

| ID | Status | Evidence |
|---|---|---|
| QA1-13.1 | P | Anonymous health `{"status":"UP"}`; DB offline → 503 `{"status":"DOWN"}` in 5.0 s |
| QA1-13.2 | P | Anonymous status → 401 `unauthenticated` |
| QA1-13.3 | P | Requester status: `productVersion: null` |
| QA1-13.4 | P | Admin status shows `17.0.1000.7`; admin health includes components |
| QA1-13.5 | P | OpenAPI anon/requester/admin → 401/403/200; docs anon/requester → 401/403 |
| QA1-13.6 | P | `/actuator/env`, `/beans`, `/info` → 401 for anonymous |
| QA1-13.7 | P | Signed-out navigation to `/requests` → sign-in page |

## S1-AC-14 — Architecture rule

| ID | Status | Evidence |
|---|---|---|
| QA1-14.1 | P | `ArchitectureTests` green in both suites |
| QA1-14.2 | P | Exported copy with a `PurchaseRequestRepository` field in `RequestController` → both rules fail (2/2) |
| QA1-14.3 | P | Second rule enforces that only `ScopedRequests` uses the repository |

## S1-AC-15 — UX

| ID | Status | Evidence |
|---|---|---|
| QA1-15.1 | P | e2e unsaved-changes prompt test green |
| QA1-15.2 | NE | `beforeunload` prompt not tested |
| QA1-15.3 | NE | Not tested |
| QA1-15.4 | NE | Success announcement not verified by QA |
| QA1-15.5 | P | e2e expired session with restore green |
| QA1-15.6 | P | e2e: expired session → sign-in with message |
| QA1-15.7 | P | `returnUrl=https://evil.example/steal` → landed on `/requests` |
| QA1-15.8 | P | Buttons disabled while `busy() !== 'idle'` (code); double submit 409 (08.3/08.4) |

## S1-AC-16 — End-to-end journey

| ID | Status | Evidence |
|---|---|---|
| QA1-16.1 | P | e2e EN journey green |
| QA1-16.2 | P | e2e FR journey (on behalf) green |
| QA1-16.3 | P | Cross-company 404 in UI and API (e2e) |
| QA1-16.4 | P | Suite run twice on the same DB (plus the developer's earlier runs): journeys idempotent |

## Sprint 0 regression subset

| ID | Status | Evidence |
|---|---|---|
| QA1-R.1 | P | Both Maven suites green, Modulith test included |
| QA1-R.2 | P | Empty scratch DB migrated V1–V4; restart "up to date"; `dev,demo` then `dev` starts |
| QA1-R.3 | P | Scratch: UPDATE/DELETE on events → 51000; TRUNCATE events/head → 4712; delete head → 51001; rewind head → 51003 |
| QA1-R.4 | P | No secrets in tracked files |
| QA1-R.5 | P | `npm ci` (lockfile unchanged), lint, tests, build, audit 0 |
| QA1-R.6 | P | Switching and persistence (12.x, e2e); blocked-storage fallback not re-run |
| QA1-R.7 | P | Skip link focus and 360 px layout verified; drawer and reduced motion not re-run |
| QA1-R.8 | P | DB offline: status (signed in) 200 with DB DOWN in 1.51 s; DB back online afterwards |
| QA1-R.9 | P | Missing-translation tests in the 136/136 unit run |
| QA1-R.10 | P | **QA-01.4 history scan:** `git log -p --all` over `77d22b1` and the agent checkpoint refs: no secret assignments (only placeholders, env-var reads, masked `toString`); no `.env`, `BackEnd/config/application.yml`, `.pfx`, `.key` in history |

## Exploratory

| ID | Status | Evidence |
|---|---|---|
| QA1-X.1 | P | form-options, error bodies, counts and list totals leak no other-company data |
| QA1-X.2 | NE | Back/forward and two-tab editing not explored (two-session edit covered by 08.1) |
| QA1-X.3 | P | All chains: head = max(seq), 0 broken `previous_hash` links, 0 gaps (structural; cryptographic verification by the DB-IT suite) |
| QA1-X.4 | P | Detail (100 lines) median 21 ms, list (100) 18 ms, form-options 11 ms |
| QA1-X.5 | NE | Backend restart mid-session not tried |

## Cleanup

Servers on 8081, 8082, 8083 and 4200 stopped; `procurement_qa_scratch_s1` dropped (`procurement_dev` and
`procurement_test` remain and are online); temporary triggers dropped; mutation copy and the Playwright MCP folder
deleted. QA created additional demo requests in `procurement_dev` (titles prefixed "QA", "Axe check", etc.).

## Re-test on af0bb38

Date: 2026-10-27. Revision: git tree af0bb38 (staged index). Environment: Windows 11, SQL Server 2025 Developer,
Edge (Playwright channel `msedge`). Backend on port 8081 (`dev,demo`, idle timeout shortened to 150 s) against a
scratch database `procurement_qa_scratch_s1` (created, then dropped); Angular dev server on 4300 with a scratchpad
proxy. Ports 8080/4200 belong to another project and were not touched.

Automated suites were run by the lead on this revision and were not repeated: `mvnw verify` 86 tests / 0 failed /
51 skipped; `verify -Pdb-it` 86/86; frontend lint, build, unit tests 148/148; e2e 10/10.

| Check | Result | Evidence |
|---|---|---|
| QA1-D-01 axe (WCAG 2.2 AA tags) on "My requests" with hovered first row, EN 1280 px | Pass, 0 violations | `evidence/s1-retest-hover-en.png` |
| QA1-D-01 same, FR | Pass, 0 violations | `evidence/s1-retest-hover-fr.png` |
| QA1-D-03 console after sign-in (EN, FR): no NG0200, no translation-load error | Pass. Only console lines: "Failed to load resource: 401", the expected unauthenticated `GET /me` probe before sign-in. | script output |
| QA1-D-02 `manager.tn` edit / submit / discard of a visible submitted request | Pass: 403 `forbidden` ×3 | API script |
| QA1-D-02 `ceo` same | Pass: 403 `forbidden` ×3 | API script |
| QA1-D-04 startup log contains no "Using generated security password" | Pass (0 matches) | backend log |
| QA1-D-05 demo password hashes distinct | Pass: 8 distinct / 8 demo credentials | SQL |
| QA1-D-06 extra AFTER INSERT trigger on `audit_event`: sign-in (audited) and draft create still succeed | Pass: 200 / 201 | API script + SQL |
| S1CR-04 assistant updates an on-behalf draft without `requesterId` | Pass: requester unchanged | API script |
| S1CR-07 delete middle line (A,B,C → A,C) | Pass: remaining lines keep ids 1 and 3 and their content | API script |
| S1CR-02 admin reads `/api/v1/openapi` (200), ADMIN role deleted in DB, same session reads again | Pass: 403 immediately | API script |
| S1CR-05 tab A active (reload every 15 s, 75 s), tab B idle, idle timeout 150 s | Pass: no warning in tab B | UI script |
| S1CR-05 control: with both tabs idle, warning appears in tab B | Pass | UI script |
| Regression: sign-in 200; sign-out 204 then `/me` 401 | Pass | API script |
| Regression: submit → `CREATED`, number `DEMO-TN-PLT-PR-2026-000001`; second submit 409 | Pass | API script |
| Regression: FR user gets 404 on detail / update / submit, request absent from list, 404 body identical to a non-existent id (ignoring `instance`) | Pass | API script |

**Re-test totals:** 16 executed, 16 passed, 0 failed, 0 blocked. No new defects.
Not repeated here (covered by the lead's runs on the same revision): full backend, frontend and e2e suites.
