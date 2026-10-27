# Sprint 1 Defects

Revision under test: git tree `be1f311` (staged index on top of commit `77d22b1`). Environment and evidence:
[sprint-1-test-results.md](sprint-1-test-results.md). Severity scale from [test-strategy.md](test-strategy.md).

| ID | Severity | Summary | Status |
|---|---|---|---|
| QA1-D-01 | Major | Request-number link fails WCAG AA contrast on the hovered table row (4.42:1); EN desktop axe e2e fails 2/2 runs | Open |
| QA1-D-02 | Minor | Visible-but-forbidden actions return 409 `invalid-state` instead of 403 `forbidden` | Open |
| QA1-D-03 | Minor | Circular dependency (NG0200) makes the fallback `en` translations fail to load on every page load | Open |
| QA1-D-04 | Minor | Spring Boot's default in-memory user with a generated password is created on every start | Open |
| QA1-D-05 | Minor | Demo seeder stores the same bcrypt hash for all 8 demo accounts | Open |
| QA1-D-06 | Trivial | Audit insert uses `OUTPUT` without `INTO`: any trigger added to `audit_event` breaks every audited action, including sign-in | Open |

---

## QA1-D-01 — Hover-state contrast failure on the requests table (Major)

- **Criterion:** S1-AC-10 (axe 0 violations), WCAG 2.2 SC 1.4.3. Blocks the S1-AC-10 automated gate.
- **Expected:** All text has a contrast ratio of at least 4.5:1 in every state; `npm run e2e` is green.
- **Actual:** The number link in the first column (`#0E7C7B`, 13 px, weight 500) sits on the row hover background
  `--color-surface-sunken` `#EEF1F2` at **4.42:1** (5.01:1 on white). After sign-in at 1280 px the pointer rests over
  the first row (where the sign-in button was), so axe evaluates the hovered state and fails.
  `accessibility EN desktop` failed in both QA runs (target `a[href="/requests/158"]`, then `a[href="/requests/165"]`);
  the other 9 e2e tests passed. FR desktop passed only because pointer position differs.
- **Repro:** Backend `dev,demo`, `npm start` (or the 8081 proxy), then
  `npx playwright test -c e2e/playwright.config.ts accessibility` → `accessibility EN desktop` fails with
  `color-contrast: a[href="/requests/…"]`. Manually: sign in as `requester.tn`, hover any row, measure the link contrast.
- **Evidence:** `docs/qa/evidence/s1-hover-contrast.png`; computed values from the live page:
  link `rgb(14,124,123)`, hover background `rgb(238,241,242)`, ratio 4.42.
- **Suggested fix:** Darken the link/accent text token (for example a value giving ≥ 4.5:1 on `#EEF1F2`) or use a lighter
  hover background; keep the e2e test as is (it found a real issue).

## QA1-D-02 — 409 instead of 403 for forbidden actions on visible requests (Minor)

- **Criterion:** S1-AC-07 matrix; CLAUDE.md / ADR-007 "out of scope → 404, forbidden action → 403".
- **Expected:** `manager.tn` and `ceo` can see C1 (TN, CREATED) but may not change it: `PUT`, `DELETE` and
  `POST …/submit` → 403 `forbidden`.
- **Actual:** 409 `invalid-state` for all five cells (`PUT C1` manager/ceo, `DELETE C1` manager/ceo, `SUBMIT C2` ceo).
  The state check runs before the permission check. No data leaks, but the response suggests the action would be
  allowed in another state; it will matter in S2 when these roles receive approval actions.
- **Repro:** As `manager.tn`: `PUT /api/v1/requests/{C1}` with a valid body and C1's version → 409 `invalid-state`.
- **Evidence:** `t_authz.py` output in the results file (QA1-07.1 / 07.7).

## QA1-D-03 — NG0200 circular dependency breaks fallback translation loading (Minor)

- **Criterion:** S1-AC-09 robustness; S0 CR-09 (translation-load fallback).
- **Expected:** No dependency-injection errors; the fallback language `en` loads so that any missing French key shows
  English text.
- **Actual:** On every page load the console shows
  `@ngx-translate/core: failed to load fallback "en". Cause: RuntimeError: NG0200: Circular dependency detected for _AuthService`
  thrown from `sessionInterceptor`. Chain: translation HTTP loader → `HttpClient` → `sessionInterceptor`
  (`inject(AuthService)`) → `AuthService` (`inject(LanguageService)`) → `LanguageService` (`inject(TranslateService)`).
  The primary language still loads, and key parity is 100 % today, so there's no visible impact yet.
- **Repro:** Open `http://localhost:4200/sign-in` in a fresh browser and inspect the console (1 warning).
- **Evidence:** Browser console output captured with Playwright (stack ends in `sessionInterceptor (main.js:834)`).

## QA1-D-04 — Default Spring Boot user created on every start (Minor)

- **Criterion:** S1-AC-01 secure credential handling; hygiene.
- **Expected:** No auto-configured in-memory user exists; logs contain no generated credentials.
- **Actual:** Every start logs `Using generated security password: <uuid>` and "This generated password is for
  development use only…" (seen in `dev,demo` and `dev` runs). `UserDetailsServiceAutoConfiguration` is not backed off.
  The account is inert in the tested surface: HTTP Basic with `user:<password>` → 401 on `/api/v1/me`,
  `/api/v1/system/status` and `/api/v1/requests`; `/actuator/health` returns status only; `POST /login` → 403.
  But a credential is printed to logs and would become usable if HTTP Basic or form login were ever enabled.
- **Repro:** Start the backend; search the console for "generated security password".
- **Suggested fix:** Exclude `UserDetailsServiceAutoConfiguration` or declare an empty `UserDetailsService` bean.

## QA1-D-05 — Identical password hash for all demo accounts (Minor)

- **Criterion:** QA1-01.13 (salted per user).
- **Expected:** Each `user_credential.password_hash` is unique, even for the same password.
- **Actual:** `SELECT COUNT(DISTINCT password_hash) FROM user_credential` → 1 for 8 rows.
  `DemoUserSeeder` calls `passwordEncoder.encode(password)` once and reuses the result
  (`DemoUserSeeder.java:96-97`). The hashes are bcrypt (`{bcrypt}$2a$`), and only demo accounts are affected. Risk: the
  pattern being copied into real user provisioning, and one cracked hash revealing every demo account.
- **Suggested fix:** Encode inside the per-user loop.

## QA1-D-06 — Audit insert incompatible with any trigger on `audit_event` (Trivial)

- **Observation from QA1-05.9 setup.** Adding an `AFTER INSERT` trigger to `audit_event` (as a DBA might for
  monitoring) makes every audited action fail with SQL Server error 334 ("cannot have any enabled triggers if the
  statement contains an OUTPUT clause without INTO clause"). Sign-in returned 500 `internal-error` (no stack trace,
  error reference present). The app's own append-only protection uses INSTEAD OF UPDATE/DELETE triggers and is
  unaffected.
- **Suggested action:** Use `OUTPUT … INTO @table` (or `SCOPE_IDENTITY()`) in `AuditLog`, or document the constraint in
  the operations runbook.

## Observations (not defects)

- With the DB offline, `GET /api/v1/requests` returns 500 `internal-error` after about 5 s (no stack trace). 503 would be
  more precise; acceptable for now.
- `BackEnd/src/test/resources/application-test.yml` contains a test-only demo password literal
  (`Test-Only-Demo-Password-1`), used only against `procurement_test`. Acceptable; not a production secret.
- The git repository holds extra refs `refs/agents/6c85d229-…/checkpoints/turn/{0,1}` created by another agent host
  session (not by this team). The history scan covered them too (clean). For the lead to decide whether to keep them.
- Port 8080 was occupied by an unrelated application (`Ticket System` project, plus Keycloak); QA used 8081 and an
  alternate proxy file in the scratchpad. The committed `proxy.conf.json` targets 8080.


## Re-test status on af0bb38 (2026-10-27)

| ID | Severity | Status | Verification |
|---|---|---|---|
| QA1-D-01 | Major | Verified fixed | axe 0 violations with hovered row, EN and FR; e2e accessibility 10/10 |
| QA1-D-02 | Minor | Verified fixed | manager.tn and ceo get 403 `forbidden` on edit, submit, discard |
| QA1-D-03 | Minor | Verified fixed | no NG0200 or translation-load error in the console (EN, FR) |
| QA1-D-04 | Minor | Verified fixed | no generated default-user password in the startup log |
| QA1-D-05 | Minor | Verified fixed | 8 distinct hashes for 8 demo accounts |
| QA1-D-06 | Trivial | Verified fixed | audited actions succeed with an extra trigger on `audit_event` |

Open defects: none.
