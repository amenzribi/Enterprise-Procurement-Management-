# Sprint 1 Test Plan — Sign-in, company isolation, request drafting

> **Status: DESIGNED, NOT EXECUTED.** Derived independently from `docs/product/sprint-1.md` (S1-AC-01…16, demo users,
> visibility table, API contract), spec §3.2, §5, §6.1, §6.3, §20, §24, and `docs/qa/test-strategy.md`.
> No code existed when this plan was written; endpoint and field names follow the API contract and must be
> re-checked against the implementation before execution.

## 0. Conventions

| Item | Value |
|---|---|
| IDs | `QA1-<area>.<n>`; area numbers follow S1-AC numbers (QA1-01 = S1-AC-01, …), plus `QA1-R` (regression) and `QA1-X` (exploratory) |
| Types | **API** = automated HTTP calls (JUnit + RestClient or PowerShell `Invoke-WebRequest` with a cookie `WebSession`); **DB** = `sqlcmd -S localhost -E -C` against the database named; **E2E** = Playwright (`FrontEnd/e2e`, Edge/Chrome channel); **Manual** = browser by hand; **Unit** = review that the developer test exists and fails when the rule is broken |
| Users | Demo users from `sprint-1.md` (`requester.tn`, `assistant.tn`, `manager.tn`, `requester.fr`, `buyer`, `purchasing.manager`, `ceo`, `admin`, all `@demo.local`); password from `DEMO_USER_PASSWORD` set by QA before start-up |
| Backend under test | `dev,demo` profile against `procurement_dev` for E2E/manual; destructive tests against `procurement_qa_scratch_s1*` (created and dropped by QA); automated integration suite against `procurement_test` |
| API session helper | Every API scenario: `GET /api/v1/auth/csrf` → read `XSRF-TOKEN` cookie → send it as `X-XSRF-TOKEN` header on state-changing calls → `POST /auth/login` |
| Evidence | Command output excerpts or screenshots under `docs/qa/evidence/s1-*` |

### Assumptions the developer must satisfy for testability (flag as defects if missing)

| ID | Assumption |
|---|---|
| T-1 | Idle and absolute session timeouts are configurable properties (e.g. `server.servlet.session.timeout`, `app.security.session.absolute-timeout`), so QA can start the backend with `PT1M` / `PT2M` instead of waiting 30 min / 8 h. The **production defaults** must still be 30 min / 8 h (checked by QA1-01.9). |
| T-2 | Lockout duration and threshold are configurable (default 5 attempts / 15 min), so unlock can be tested with e.g. `PT1M`. |
| T-3 | The integration test suite can run ≥ 20 parallel submissions against `procurement_test`. |
| T-4 | Demo users are created only by a component that is active solely under the `demo` profile. |

---

## S1-AC-01 — Sign-in, sign-out, session security

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-01.1 | API | Backend up, demo | Login `requester.tn` with correct password | 200, body = `Me` (email, displayName, company DEMO-TN-PLT, roles [REQUESTER], preferredLanguage, `sessionExpiresAt`, `idleTimeoutSeconds` = 1800). `Set-Cookie` session cookie has `HttpOnly`, `SameSite=Lax`; no `Secure` in dev is acceptable. |
| QA1-01.2 | API | — | Login with wrong password for an existing user; login with a non-existent e-mail | Both 401, `code: invalid-credentials`, **byte-identical** body apart from `instance`/timestamps. |
| QA1-01.3 | API | — | Timing: 20 attempts each for existing-wrong-password vs non-existent e-mail; compare median latency | Medians within ~30 % of each other (bcrypt run for unknown users too). Large difference = user-enumeration defect (Major). |
| QA1-01.4 | API | Fresh session cookie obtained before login (anonymous `JSESSIONID`) | Note anonymous session id → login → compare | Session id after login **differs** (fixation protection). Re-using the pre-login cookie after login → 401 on `GET /me`. |
| QA1-01.5 | API | Logged in | `POST /requests` **without** `X-XSRF-TOKEN` header; then with a wrong token; then with a token from another session | All 403 (`code: forbidden` or a CSRF code), no request created (DB count unchanged). |
| QA1-01.6 | API | Anonymous | `POST /auth/login` without CSRF token | Rejected (403), per the sprint design "login also CSRF-protected". |
| QA1-01.7 | API | Logged in | `POST /auth/logout` → then `GET /me` with the **same** cookie (replayed manually) | Logout 204; replayed cookie → 401 `unauthenticated` or `session-expired` (server-side invalidation, not just cookie deletion). |
| QA1-01.8 | API | Backend started with idle timeout `PT1M` (T-1) | Login → wait 70 s idle → `GET /me` | 401 with `code: session-expired`. A request at 50 s keeps the session alive (sliding). |
| QA1-01.9 | API/config | Default profile settings | Inspect effective config (`application.yml` + `GET /me` `idleTimeoutSeconds`, `sessionExpiresAt` − login time) | Idle = 30 min, absolute = 8 h by default. |
| QA1-01.10 | API | Absolute timeout `PT2M`, idle `PT1M` | Login → call `GET /me` every 30 s (never idle) → at 2 min 10 s | 401 `session-expired` despite activity. |
| QA1-01.11 | API | Lockout `PT1M` (T-2) | 5 wrong passwords for `requester.fr` → 6th attempt with the **correct** password | 6th → 423 `account-locked`. After 61 s, correct password → 200. Failed-attempt counter resets after success (one more wrong password does not lock). |
| QA1-01.12 | API | — | 5 wrong passwords for a **non-existent** e-mail, then compare response with QA1-01.11 | No 423 for unknown e-mails *or* identical behaviour for both — whichever is chosen must not reveal account existence. Record behaviour; enumeration via 423 = Minor defect to discuss. |
| QA1-01.13 | DB | After QA1-01.1 | `SELECT password_hash FROM user_credential` for demo users | Values start with `{bcrypt}` (or bcrypt `$2`), unique per user even when the same demo password is used (salted). No plaintext anywhere (`grep` demo password in logs and DB). |
| QA1-01.14 | DB | After login success, failure, lockout, logout | Query `audit_event` global chain | One event each: sign-in success, sign-in failure, lockout, sign-out; no password or hash in `payload_json`. |
| QA1-01.15 | API | Logged in as A in session 1, as B in session 2 | Use both concurrently | No cross-talk: `GET /me` returns the right user per cookie. |
| QA1-01.16 | API | — | Login payload with SQL/JSON injection strings in e-mail (`' OR 1=1 --`, 10 kB e-mail, unicode) | 401/400 with ProblemDetail; no 500; no stack trace. |

## S1-AC-02 — Demo users only under the `demo` profile; no demo password in the repository

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-02.1 | DB/API | Scratch DB, start **without** `demo` | Count `user_account`, `user_credential`; try login `requester.tn@demo.local` | 0 demo users; login 401. |
| QA1-02.2 | DB/API | Same DB, start with `dev,demo` and `DEMO_USER_PASSWORD` set | Login each of the 8 users | All 8 succeed; company, department and roles exactly match the `sprint-1.md` table. |
| QA1-02.3 | Manual | `demo` without `DEMO_USER_PASSWORD` | Read console output | A random password is printed **once**; it works; it is not written to any file in the repo or to the DB in clear. Restarting prints a new one (or documents the reuse rule). |
| QA1-02.4 | Grep | Repo | `git grep -nIiE "DEMO_USER_PASSWORD\s*[:=]\s*\S+"` and grep for any literal demo password in `BackEnd/`, `FrontEnd/e2e`, docs | No literal password committed (E2E reads it from an env var). |
| QA1-02.5 | DB | After `dev,demo` then `dev` only | Login with demo users | Behaviour documented (demo users remain in DB, as with S0 demo data, or are disabled). Undocumented = Minor. |
| QA1-02.6 | DB | demo | Demo users labelled as demonstration data (e-mail domain `demo.local`, display name "(Demo)") | Labelled. |

## S1-AC-03 — Draft content, lines, totals

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-03.1 | API | `requester.tn` | `POST /requests` minimal body (title only, no lines) | 201 draft (save-anytime), `number: null`, `status: DRAFT`, `Location` header. |
| QA1-03.2 | API | — | Quantity `0`, `-1`, `0.00001` (5 decimals), `1.2345` (4 decimals), `"abc"`, `1e3`, `99999999999999.9999` (19,4 max), `100000000000000` (overflow) | 0 / negative / 5 decimals / non-numeric / overflow → 422 `quantity-positive` / `max-decimals` / type error; 4 decimals and 19,4 max accepted and stored exactly (DB check, no float drift). |
| QA1-03.3 | API | — | Estimated unit price `-0.01`, `0`, 5 decimals | Negative → 422; 0 accepted; 5 decimals → 422 `max-decimals`. |
| QA1-03.4 | API | — | 100 lines → save; 101 lines → save | 100 OK; 101 → 422 with a clear code. |
| QA1-03.5 | API | — | Draft with a line having empty description / no unit → save; then submit | Save allowed (draft); submit 422 with per-line field errors (`lines[0].description: required`). |
| QA1-03.6 | API/DB | Currency TND (3 minor units) | Lines: qty 3 × 0.3335 TND; qty 1 × 1.0005 TND | Line totals and request total rounded HALF_UP to 3 decimals (1.0005 → 1.001; 1.0005×1 etc.). Rule (sum of rounded lines vs round of sum) documented and consistent between API, DB and UI. |
| QA1-03.7 | API | Currency EUR (2) and JPY (0) | 1 × 0.005 EUR; 1 × 0.5 JPY | EUR total 0.01; JPY total 1. |
| QA1-03.8 | API | — | Send a **line-level** currency field or two different currencies | Ignored or rejected; only one currency per request persisted. |
| QA1-03.9 | API | — | Client sends `lineTotal`/`totalAmount` = 1 for a 1000 total | Server recomputes; client totals ignored. |
| QA1-03.10 | API | — | Unknown category id, unknown currency code `XXX`, priority `SUPER`, purchase type `ORBITAL` | 422 field errors, no 500. |
| QA1-03.11 | API | — | Title 0, 1, max, max+1 chars; unicode (Arabic, emoji, accented French) | Limits enforced with 422; unicode round-trips unchanged (NVARCHAR). |
| QA1-03.12 | API | — | Required date in the past on submit | Behaviour specified? If no rule exists, record as open question (spec §6.1 silent). |

## S1-AC-04 — Request actors and relationship rules

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-04.1 | API/DB | `requester.tn` | Create draft without requester field | requester = entered-by = session user; DB columns separately populated. |
| QA1-04.2 | API | `requester.tn` (no ENTER_ON_BEHALF) | Create with `requesterId` = `manager.tn` | 422 (not permitted) and nothing saved. |
| QA1-04.3 | API/DB | `assistant.tn` | Create with `requesterId` = `requester.tn` | 201; `entered_by` = assistant, `requester` = requester.tn; both see it in "My requests". |
| QA1-04.4 | API | `assistant.tn` | `requesterId` = `requester.fr` (other company) | 422 `not-in-company` or 404 — never 201. |
| QA1-04.5 | API | `assistant.tn` | Body `enteredById` = `requester.tn` (mass assignment) | Ignored: entered-by is always the session user. |
| QA1-04.6 | API | `requester.tn` | `requestingCompanyId` = DEMO-FR-PLT | Rejected (422/404); request not created in FR company. |
| QA1-04.7 | API | `requester.tn` | `departmentId` / `siteId` / `costCenterId` belonging to DEMO-FR-PLT or DEMO-CPA | 422 `not-in-company` (or 404) per field. |
| QA1-04.8 | API | `requester.tn` | `chargedCompanyId` omitted; = DEMO-HOLD (same group); = a company of another group (create one in scratch DB) | Default = requesting company; same-group accepted; other group rejected. |
| QA1-04.9 | API | `requester.tn` | `buyingCompanyId` = DEMO-TN-PLT; = DEMO-CPA (purchasing centre); = DEMO-FR-PLT | First two accepted; FR plant rejected. |
| QA1-04.10 | API | `requester.tn` | Non-existent ids (999999) for each relation | Same error shape as a foreign id (no existence oracle). |
| QA1-04.11 | API | `GET /requests/form-options` as `requester.tn` and `assistant.tn` | Inspect lists | Only own-company departments/sites/cost centres; on-behalf people list empty for requester.tn and own-company only for assistant.tn; no other company's people leaked. |

## S1-AC-05 — Save, required fields at submission, numbering, DRAFT → CREATED, atomic audit

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-05.1 | API | Complete draft | `POST /requests/{id}/submit {version}` | 200; `number` = `DEMO-TN-PLT-PR-2026-000001` format (company **code**, 4-digit year, 6-digit zero-padded); `status: CREATED`; `submittedAt` set. |
| QA1-05.2 | API | Draft missing each required field in turn (title, category, priority, purchase type, currency, department, required date, zero lines, line description/quantity/unit) | Submit | 422 `validation-failed`, `errors[]` lists **all** missing fields at once with stable codes; status remains DRAFT; no number consumed (next valid submit gets the next number with no gap). |
| QA1-05.3 | DB | After QA1-05.1 | `document_counter` for (TN plant, 2026, PURCHASE_REQUEST); `audit_event` in chain `company:<id>` | Counter incremented once; one `submitted` event with from DRAFT / to CREATED, actor, entity id. |
| QA1-05.4 | DB | Scratch DB | Numbering per company: submit one TN and one FR request | Each starts at 000001 in its own company sequence. |
| QA1-05.5 | DB | Scratch DB | Numbering per year: set the TN counter row for 2025 to 57, submit in 2026 | 2026 sequence independent (000001 or next 2026 value); year boundary rule documented (server UTC vs company time zone — Africa/Tunis vs Europe/Paris at 31 Dec 23:30 UTC). Undefined rule = open question. |
| QA1-05.6 | DB | Scratch DB, first submission ever for a company/year | Two parallel first submissions (counter row absent) | Race-safe insert: both succeed with 000001 and 000002; no PK violation 500. |
| QA1-05.7 | API | — | `GET /requests/statuses` | Complete header (25) and line (14) enumerations per `sprint-1.md`; every code has an EN and FR label in the frontend files. |
| QA1-05.8 | API | Draft | Update of `number` or `status` in `PUT` body (mass assignment) | Ignored; still DRAFT, number null. |

### Atomicity (submission + audit in one transaction)

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-05.9 | DB/API | Scratch DB `procurement_qa_scratch_s1_atom`, backend pointed at it | Provoke an audit failure only at submission time: as `sa`/Windows admin, add a temporary trigger on `audit_event` `INSTEAD OF INSERT … WHERE action = 'REQUEST_SUBMITTED'` that `THROW`s (or `DISABLE` nothing — use an `AFTER INSERT` trigger with `THROW 50001`). Submit a complete draft. | API returns 5xx/409 ProblemDetail without stack trace; request **still DRAFT**, `number` NULL, `document_counter` **not** incremented, no partial audit row; chain verification still passes. Drop trigger → resubmit → succeeds with the number that would have been issued first (no gap). |
| QA1-05.10 | DB/API | Same scratch DB | Alternative: rename/lock the chain head by holding `UPDLOCK` on `audit_chain_head` in an open `sqlcmd` transaction for 60 s while submitting | Submit waits then either succeeds after release or times out cleanly; never commits the request without its audit event. |
| QA1-05.11 | Unit review | Code | Inspect that submit service, counter and audit share one `@Transactional` boundary | Present, and a developer test asserts rollback. |

## S1-AC-06 — "My requests" list

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-06.1 | API | `requester.tn` with 3 drafts + 2 created; `assistant.tn` entered 1 for requester.tn | `GET /requests?mine=true` as each | requester.tn sees 6; assistant.tn sees the one they entered (and own); columns per AC; drafts show number null. |
| QA1-06.2 | API | 25 requests | `size=10&page=0..2`, `size=0`, `size=1000`, `page=-1` | Correct totals and pages; size capped (document max); invalid → 400, no 500. |
| QA1-06.3 | API | — | Sort by each allowed field asc/desc; `sort=password_hash`, `sort=1;DROP TABLE` | Allowed sorts work; unknown sort → 400 or ignored, never SQL error/500 (sort whitelist). |
| QA1-06.4 | API | — | `status=DRAFT`, `status=CREATED`, `status=BOGUS`; `q=` title fragment, number fragment, `%`, `_`, `'` | Filters correct; LIKE wildcards escaped (`%` searches a literal %). |
| QA1-06.5 | API | `requester.fr` | `GET /requests?mine=false` (or without `mine`) | Never returns other users' drafts or other companies' requests; `totalItems` excludes them (no count leak). |
| QA1-06.6 | E2E | — | List loading skeleton, empty state (new user `requester.fr`), error state (stop backend) with retry | Each state rendered and localised. |

## S1-AC-07 — Company isolation and IDOR (central scope component)

**Authorisation matrix** — QA executes every cell as API calls with the given user against requests owned by
`requester.tn` (TN draft **D1**, TN created **C1**), `requester.fr` (FR created **C2**), and a non-existent id **X**.
Expected: `OK` = 2xx; `404` = not visible (identical body to X); `403` = visible but action forbidden; `—` = not applicable.

| User → / Endpoint ↓ | requester.tn | assistant.tn | manager.tn | requester.fr | buyer | purchasing.manager | ceo | admin | anonymous |
|---|---|---|---|---|---|---|---|---|---|
| GET /requests (list) | own D1,C1 | entered/own only | C1 (non-draft, own dept) | own only | empty | empty | C1, C2 (non-draft) | empty | 401 |
| GET /requests/D1 | OK | 404 (unless entered) | 404 | 404 | 404 | 404 | 404 | 404 | 401 |
| GET /requests/C1 | OK | 404 (unless entered) | OK | 404 | 404 | 404 | OK | 404 | 401 |
| GET /requests/C2 | 404 | 404 | 404 | OK | 404 | 404 | OK | 404 | 401 |
| PUT /requests/D1 | OK | 404 | 404 | 404 | 404 | 404 | 404 | 404 | 401 |
| PUT /requests/C1 | 409 invalid-state | 404 | 403 | 404 | 404 | 404 | 403 | 404 | 401 |
| POST /requests/D1/submit | OK | 404 | 404 | 404 | 404 | 404 | 404 | 404 | 401 |
| POST /requests (create) | OK | OK | 403? (no REQUESTER role) | OK | 403 | 403 | 403 | 403 | 401 |
| GET /requests/form-options | OK | OK | per role | OK | 403 or minimal | 403 or minimal | 403 or minimal | 403 or minimal | 401 |
| GET /me, PUT /me/preferences | OK | OK | OK | OK | OK | OK | OK | OK | 401 |

Cells marked "?" or "per role" are not defined by `sprint-1.md`; QA records the implemented behaviour and raises an
open question if it contradicts §20.

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-07.1 | API | Matrix above | Execute all cells | All as expected. |
| QA1-07.2 | API | — | For every 404 cell: compare body/headers with `GET /requests/X` | Identical except `instance`; same status, same `code: not-found`, same content length; no `ETag`/version leak. |
| QA1-07.3 | API | — | Response time of 404 for C2 vs X (20 samples) | No meaningful timing difference. |
| QA1-07.4 | API | `requester.tn` | `PUT /requests/D1` changing `requestingCompanyId` to FR, `requesterId` to `requester.fr`, `departmentId` to Lyon maintenance | Rejected; DB row unchanged. |
| QA1-07.5 | API | `requester.fr` | `PUT /requests/D1` with D1's correct version (obtained by QA from DB) | 404 (not 409/403) and no change. |
| QA1-07.6 | API | — | Id formats: `/requests/abc`, `/requests/-1`, `/requests/1.5`, `/requests/9223372036854775808` | 400 or 404 ProblemDetail; never 500. |
| QA1-07.7 | API | `ceo` | `PUT` or `submit` of C1/D1 | Not permitted (403 for visible C1, 404 for draft D1). |
| QA1-07.8 | Grep/Unit | Code | Every request-repository query reachable from the API passes through the scope component (see S1-AC-14) | No direct `findById` on request repository from services serving the API without the visibility predicate. |
| QA1-07.9 | API | `manager.tn` | Requests of TN **production** department (create one as a second TN requester if available, else via DB in scratch) | Not visible to the maintenance manager (department scope). |

## S1-AC-08 — Optimistic locking, double submit, concurrent numbering

| ID | Type | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA1-08.1 | API | Draft v0 | Two sessions of `requester.tn` both GET v0; A saves (→ v1); B saves with v0 | B → 409 `stale-version`, localised message in UI; A's change preserved. |
| QA1-08.2 | API | Draft | Submit with stale version | 409 `stale-version`, still DRAFT. |
| QA1-08.3 | API | Draft | Submit twice sequentially with the same version | 1st 200; 2nd 409 (`invalid-state` or `stale-version`); one number; one audit event. |
| QA1-08.4 | API | Draft | Fire 10 parallel submits of the **same** request | Exactly 1 success; 9 × 409; counter +1; one audit event. |
| QA1-08.5 | API/DB | 30 complete drafts in TN plant (by `requester.tn` and `assistant.tn`) | Submit all 30 in parallel (PowerShell `ForEach-Object -Parallel` or test harness) | 30 × 200; numbers distinct, contiguous 000001…000030 (no duplicates, no gaps); counter = 31; 30 audit events; chain verifies (QA1-X.3). |
| QA1-08.6 | Unit review | Developer test | Confirm the developer's ≥ 20-parallel integration test exists and fails if the lock hint is removed (QA mutation in an exported copy, not in the repo) | Test catches removal. |
| QA1-08.7 | API | Draft | Update after submit (`PUT` CREATED request with current version) | 409 `invalid-state`. |

## S1-AC-09 — English and French completeness

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-09.1 | Unit | Key-parity test (from S0) covers new keys | Passes; no empty values. |
| QA1-09.2 | Grep | Every API error/validation `code` in the backend source has an EN and FR translation key | No missing codes (list extracted with grep vs JSON). |
| QA1-09.3 | E2E | In FR: login, list, form, detail, all validation messages, 409/404/401 messages | No English text visible (allow-list: proper nouns, codes like EUR). |
| QA1-09.4 | E2E | Amounts and dates | FR: `1 234,567 TND`, `25/10/2026`; EN: `TND 1,234.567` (or locale standard), `10/25/2026` or ISO per decision; TND shows 3 decimals, EUR 2. |
| QA1-09.5 | E2E | All 25 header + 14 line status labels | Present in both languages. |

## S1-AC-10 — Accessibility and small screens

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-10.1 | E2E | axe on login, list (populated + empty), form (with validation errors shown), detail — EN and FR | 0 violations. |
| QA1-10.2 | Manual | Keyboard only: sign in, create draft, add/remove lines, save, submit, open from list, sign out | All operable; visible focus; logical order; no traps; add-line moves focus to the new line; remove-line returns focus sensibly. |
| QA1-10.3 | Manual/E2E | Submit with errors | Error summary announced (`role="alert"`/live region), each field has `aria-invalid` + `aria-describedby` to its message; focus moves to summary or first invalid field. |
| QA1-10.4 | E2E | 360 × 740 viewport: full journey | No horizontal page scroll; lines usable (stacked layout or horizontally scrollable table region with labelled controls); buttons reachable. |
| QA1-10.5 | Manual | 200 % zoom on 1280 px | Content reflows, nothing clipped. |
| QA1-10.6 | Manual | Screen reader spot check (Narrator + Edge) on form and list | Field labels, line numbers ("Line 2 quantity"), status badges read meaningfully. |

## S1-AC-11 — Stage, owner, since when

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-11.1 | API/E2E | Detail of draft | Stage "Creation"/DRAFT, owner = requester, "since" = creation time, relative and absolute time in user's language. |
| QA1-11.2 | API/DB | After submit | Owner = requester, `owner_since` = submission time; transition recorded (owner + timestamp) in DB/audit. |
| QA1-11.3 | API | `assistant.tn` entered on behalf | Owner = requester (requester.tn), not assistant. |

## S1-AC-12 — Language preference on the account

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-12.1 | E2E | Browser A: login `requester.tn`, switch to FR | `PUT /me/preferences` 204; DB `preferred_language = 'fr'`. |
| QA1-12.2 | E2E | Browser B (new context, `navigator.language = en-US`, empty storage): login same user | UI switches to FR after sign-in. |
| QA1-12.3 | E2E | Before sign-in in browser B | Login page follows browser/local choice (EN). |
| QA1-12.4 | API | `PUT /me/preferences {"preferredLanguage":"de"}`, `"FR"`, `""`, missing body, no CSRF | 422/400/403 as appropriate; DB unchanged. |
| QA1-12.5 | E2E | Preference save fails (backend stopped after login) | UI keeps working language, shows non-blocking error; no silent mismatch. |
| QA1-12.6 | E2E | Sign out from a FR session, sign in as `requester.fr` whose preference is EN | UI EN after sign-in (no leakage of previous user's preference). |

## S1-AC-13 — Health, status and API docs exposure

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA1-13.1 | API | anonymous | `GET /actuator/health` | Body exactly `{"status":"UP"}` (no `components`). DB down → `{"status":"DOWN"}`, 503. |
| QA1-13.2 | API | anonymous | `GET /api/v1/system/status` | 401. |
| QA1-13.3 | API | `requester.tn` | status | 200 without DB product version/server details. |
| QA1-13.4 | API | `admin` | status; `/actuator/health` | Version details present for admin; health components visible only when authorised (if implemented). |
| QA1-13.5 | API | anonymous / `requester.tn` / `admin` in `dev` | `/api/v1/docs`, `/api/v1/openapi` | 401 / 403 / 200. In non-dev profile: 404 for all. |
| QA1-13.6 | API | anonymous | `/actuator/env`, `/actuator/beans`, `/actuator/info` | 404 (not exposed) or minimal info. |
| QA1-13.7 | E2E | `requester.tn` | System status page | Works signed in; anonymous redirected to sign-in. |

## S1-AC-14 — Architecture rule: no repository in controllers

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-14.1 | Unit | Run architecture test | Passes. |
| QA1-14.2 | Unit (mutation, exported copy) | Inject a repository into a controller in a copy of the repo; run test | Test fails. |
| QA1-14.3 | Review | Check request queries use the scope component (`findVisible`, visibility predicate) | Confirmed. |

## S1-AC-15 — UX: unsaved changes, success, expired session

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-15.1 | E2E | Edit form, click sidebar link | Confirmation dialog (localised); Cancel keeps edits; Leave discards. |
| QA1-15.2 | E2E | Edit form, reload / close tab | Browser `beforeunload` prompt. |
| QA1-15.3 | E2E | Save without changes, then navigate | No prompt (dirty state resets after save). |
| QA1-15.4 | E2E | Save, submit | Success confirmation announced (live region) and visible; focus managed. |
| QA1-15.5 | E2E | Idle timeout `PT1M`: open form, type, wait 70 s, click Save | Expired-session message; redirected to sign-in with return URL; after sign-in returns to the original page. Document whether unsaved form data is lost (data loss without warning = Major). |
| QA1-15.6 | E2E | Expired session while on list | Next request → sign-in page with "session expired" message, not a generic error. |
| QA1-15.7 | E2E | Deep link to `/requests/123` while signed out | Sign-in → lands on `/requests/123` (or 404 page if not visible). Return URL rejects external URLs (`?returnUrl=https://evil.example`) — open-redirect check. |
| QA1-15.8 | E2E | Double-click Submit quickly | One submission (button disabled while pending), no duplicate error shown to the user. |

## S1-AC-16 — End-to-end browser journey (EN and FR)

| ID | Type | Steps | Expected |
|---|---|---|---|
| QA1-16.1 | E2E | EN: `requester.tn` signs in → New request → header + 2 lines → Save → edit line 2 quantity → Save → Submit → My requests shows number and "Created" → open detail: number, status, owner, since | All steps pass; number format correct; totals correct in TND (3 decimals). |
| QA1-16.2 | E2E | Same in FR with `assistant.tn` entering on behalf of `requester.tn` | Passes; all text French; requester shown as requester.tn, entered-by assistant.tn. |
| QA1-16.3 | E2E | `requester.fr` opens the detail URL of QA1-16.1's request | Not-found page (localised), no data shown. |
| QA1-16.4 | E2E | Run the Playwright suite twice in a row on the same DB | Idempotent (tests create their own data, no reliance on order). |

---

## QA1-R — Sprint 0 regression subset (re-run on the S1 revision)

| ID | Scenario | Source |
|---|---|---|
| QA1-R.1 | `mvnw.cmd verify` and `verify -Pdb-it` green; Modulith test passes | QA-01.x, QA-07.1 |
| QA1-R.2 | Flyway migrates an empty scratch DB V1…Vn; re-run no-op; `dev,demo` then `dev` starts | QA-03.x, QA-D-02 |
| QA1-R.3 | Audit append-only: UPDATE/DELETE/TRUNCATE rejected; chain-head protections | QA-04.x, QA-D-01 |
| QA1-R.4 | No secrets in tracked files (grep) | QA-02.1 |
| QA1-R.5 | `npm ci` (lockfile unchanged), lint, tests, build, `npm audit` | QA-08.x |
| QA1-R.6 | EN/FR switching without reload, persistence, blocked storage fallback | QA-10.x |
| QA1-R.7 | Shell at 360/768/1280, skip link, drawer dialog, reduced motion | QA-09.x, QA-13.x, QA-D-03, QA-D-11 |
| QA1-R.8 | Status page states incl. DB down ≤ 2 s (now for a signed-in user) | QA-11.x, QA-D-06 |
| QA1-R.9 | Missing-translation test (QA-10.9 replacement) passes | QA-10.9 |
| **QA1-R.10** | **QA-01.4 git-history secrets scan** (now possible): `git log -p --all \| Select-String -Pattern '(password\|pwd\|secret\|token\|api[_-]?key)\s*[:=]\s*[^\s$\{<]'` and search history for `*.env`, `application.yml` under `BackEnd/config`, `.pfx`, `.key` | Previously blocked |

## QA1-X — Exploratory and cross-cutting (time-boxed 60 min)

| ID | Charter |
|---|---|
| QA1-X.1 | Try to see another company's data through any channel: form-options, error messages, counts, autocomplete, audit payloads, browser network tab. |
| QA1-X.2 | Back/forward browser buttons around save/submit; two tabs editing the same draft. |
| QA1-X.3 | After all scenarios: run the audit chain verification for every chain (global + per company) — no breaks; events present for created/updated/submitted and auth events. |
| QA1-X.4 | Long French text and 100-line request: performance of list/form/detail < 2 s locally (indicative, spec §25). |
| QA1-X.5 | Backend restart mid-session: user gets a clear session-expired path (in-memory sessions are lost — expected, but UX must be clean). |

## Open questions raised by test design (for the lead)

1. Required date in the past at submission — allowed? (QA1-03.12)
2. Year of the request number at a year boundary: UTC or company time zone? (QA1-05.5)
3. Lockout for non-existent e-mails — must behave identically to existing ones? (QA1-01.12)
4. `DEPARTMENT_MANAGER`, `GROUP_MANAGEMENT`, `ADMIN` without REQUESTER role: may they create requests? (`POST /requests` row in the matrix)
5. Demo users after switching off the `demo` profile — remain active or disabled? (QA1-02.5)
6. Unsaved form data on session expiry — must it be preserved (e.g. local draft) or is a warning sufficient? (QA1-15.5)

## Exit criteria (QA view)

- Every scenario Executed / Blocked / Not executed with reason; no Blocker or Major open.
- Authorisation matrix 100 % executed by API.
- Parallel numbering and atomicity scenarios executed against SQL Server (not mocked).
- E2E journey green in EN and FR; axe 0 violations on new screens.
- QA-01.4 git-history scan executed.
