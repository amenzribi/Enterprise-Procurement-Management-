# Sprint 1 — Sign-in, company isolation, request drafting

Status: **accepted** on revision tree af0bb38 (QA re-test 16/16, 0 open defects; review verdict: accept) · Sprint length: 2 weeks · Stories: US-01 (without attachments) · Spec: §3.2, §5, §6.1, §6.3, §20, §24

## Objective

A signed-in requester can create, edit, save and submit a multi-line purchase request, find it in "My requests",
and access only data authorised for their company and role.

## Acceptance criteria

The roadmap criteria S1-AC-01…14 apply, refined here. QA derives tests from this table.

| ID | Criterion |
|---|---|
| S1-AC-01 | Sign-in with e-mail + password and sign-out. Server-side session: cookie `HttpOnly`, `SameSite=Lax`, `Secure` outside dev; session id rotated at sign-in; CSRF protection on all state-changing requests. Session expires after 30 min idle and 8 h absolute. Generic failure message ("e-mail or password incorrect"); 5 consecutive failures lock the account for 15 min. Passwords hashed with Spring Security's delegating encoder (bcrypt). |
| S1-AC-02 | Eight demo users (table below) exist **only** when the `demo` profile is active. No demo password in the repository: the password comes from `DEMO_USER_PASSWORD`; if unset, a random password is generated at startup and printed once to the console. |
| S1-AC-03 | Draft request: header (title, category, priority, purchase type local/international, currency — one per request, requesting company, department, site, cost centre, project, justification, required date, delivery location) and 1..100 lines (description, optional item reference, quantity > 0 with max 4 decimals, unit, estimated unit price ≥ 0 with max 4 decimals, optional line required date, optional line delivery location). Line and request totals computed server-side, rounded HALF_UP to the currency's minor units. |
| S1-AC-04 | Actors recorded separately: entered by (always the session user), requester, beneficiary (free text and/or site), requesting, charged and buying company. Server rules: requester ≠ session user only with the `ENTER_ON_BEHALF` permission in the requesting company, and the requester must belong to it; department, site and cost centre belong to the requesting company; charged company is in the same group (default: requesting company); buying company is the requesting company or the group's purchasing centre. Client-supplied identifiers outside these rules → 422 with field errors (or 404 when the referenced object is outside the user's scope). |
| S1-AC-05 | Save draft (create/update) at any time; required fields enforced at submission (title, category, priority, purchase type, currency, department, required date, ≥1 line, all line required fields). Submit assigns `{COMPANY_CODE}-PR-{YYYY}-{seq6}` from `document_counter` locked in the transaction, moves `DRAFT → CREATED`, sets `submitted_at`, writes the audit event in the company chain in the **same transaction**. Drafts have no number. The full status enumeration (header and line, spec §5) with EN/FR labels is published by the API and the UI. |
| S1-AC-06 | "My requests" (requests where I am requester or entered-by): number (or "Draft"), title, status, total and currency, required date, last update; sort, filter by status and text, server-side pagination; loading, empty, error states. |
| S1-AC-07 | Company isolation through one central scope component. A request outside the user's visibility returns 404 on list (absent), detail, update and submit — never 403 and never a different error body. Tests cover every endpoint and identifier manipulation (other company id in body, other user id as requester, foreign department/site/cost centre). |
| S1-AC-08 | Optimistic locking: update and submit carry the version; a stale version → 409 with a localised message. A second submit of the same request → 409 (invalid transition). Concurrent submits of different requests never produce duplicate or skipped-by-error numbers (test with ≥ 20 parallel submissions). |
| S1-AC-09 | All new screens, validation messages and API error codes are complete in English and French; amounts and dates formatted per locale. |
| S1-AC-10 | Keyboard operable, labelled fields, errors announced and linked to fields, usable at 360 px; axe scan 0 violations on login, list, form, detail in EN and FR. |
| S1-AC-11 | Request detail shows current stage, current owner and time since assignment (draft/created: owner = requester); each transition records owner and timestamp. |
| S1-AC-12 | Language preference saved on the user account (`PUT /api/v1/me/preferences`) and applied at sign-in on any browser; before sign-in the browser choice applies. |
| S1-AC-13 | Anonymous callers: `/actuator/health` returns only `{"status":"UP"|"DOWN"}`; `/api/v1/system/status` requires sign-in; database version details only for `ADMIN`. API docs (dev profile) only for `ADMIN`. |
| S1-AC-14 | Architecture test fails if a web controller depends on a repository directly; request queries go through the scope component. |
| S1-AC-15 | UX: unsaved-changes guard (in-app navigation and browser close), success confirmation after save/submit, expired-session state that returns the user to sign-in with a message and back to the original page afterwards. |
| S1-AC-16 | Automated browser journey (Playwright) in EN and FR: sign in → create draft with 2 lines → save → edit → submit → find in "My requests" → detail shows number, status, owner; cross-company user cannot open it. |

### Demo users (demonstration data, `demo` profile only)

| E-mail | Company | Department | Roles |
|---|---|---|---|
| requester.tn@demo.local | DEMO-TN-PLT | DEMO-MAINT (Sfax) | REQUESTER |
| assistant.tn@demo.local | DEMO-TN-PLT | DEMO-MAINT (Sfax) | REQUESTER, ENTER_ON_BEHALF |
| manager.tn@demo.local | DEMO-TN-PLT | DEMO-MAINT (Sfax) | DEPARTMENT_MANAGER |
| requester.fr@demo.local | DEMO-FR-PLT | DEMO-MAINT (Lyon) | REQUESTER |
| buyer@demo.local | DEMO-CPA | DEMO-PURCH | BUYER |
| purchasing.manager@demo.local | DEMO-CPA | DEMO-PURCH | PURCHASING_MANAGER |
| ceo@demo.local | DEMO-HOLD | — | GROUP_MANAGEMENT |
| admin@demo.local | DEMO-HOLD | — | ADMIN |

### Visibility in Sprint 1 (subset of the model in `docs/analysis/design-review-response.md`)

| Role | Sees requests | Can edit/submit |
|---|---|---|
| REQUESTER | Own (requester or entered-by) | Own drafts |
| ENTER_ON_BEHALF | Own + those they entered | Drafts they entered or own |
| DEPARTMENT_MANAGER | Non-draft requests of their department in their company | No (approval arrives in S2) |
| GROUP_MANAGEMENT | Non-draft requests of all group companies | No |
| BUYER, PURCHASING_MANAGER | None yet (central inbox in S3) | No |
| ADMIN | None (configuration role; no business data access) | No |

Drafts are visible only to their requester and entered-by user.

## Technical design

| Topic | Decision |
|---|---|
| Session | Spring Security with servlet `HttpSession` (in-memory, single node). Idle timeout `server.servlet.session.timeout=30m`; absolute 8 h enforced by a filter comparing the session creation time. Multi-node session storage (Spring Session JDBC on SQL Server) deferred until a multi-node deployment (ADR-003). |
| CSRF | `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`), which Angular's `HttpClient` supports natively. Login also CSRF-protected. |
| API auth errors | 401 JSON `ProblemDetail` with `code: session-expired` or `unauthenticated` (no redirects); 403 `forbidden`; 404 `not-found`. |
| Credentials | `user_credential(user_id PK/FK, password_hash, failed_attempts, locked_until, password_changed_at)` kept separate from `user_account` (OIDC-ready: production users may have no local credential). |
| Roles | `user_role(user_id, role, company_id NULL)` — company-scoped role assignment; `company_id NULL` = all group companies. Roles: REQUESTER, ENTER_ON_BEHALF, DEPARTMENT_MANAGER, BUYER, PURCHASING_MANAGER, GROUP_MANAGEMENT, ADMIN. |
| Scope component | `identity` module exposes `CurrentUser` (id, company, department, group, roles) resolved from the session; `request` module's `RequestAccessPolicy` builds the visibility predicate used by every query, and a single `findVisible(id)` used by detail/update/submit. |
| Request model | `purchase_request` (number NULL until submitted, unique filtered index on number; status; stage; current_owner_user_id; owner_since; actor columns; amounts; `version` for optimistic locking) and `purchase_request_line`; `request_category` reference table (group-scoped, EN/FR names, seeded in V-migration with neutral categories; demo categories in demo data). |
| Status model | Header: DRAFT, CREATED, IN_APPROVAL, APPROVED, REJECTED, REVISION_REQUESTED, IN_PURCHASING_REVIEW, ASSIGNED_TO_BUYER, SOURCING, QUOTES_RECEIVED, TECHNICAL_VALIDATION, COMMERCIAL_VALIDATION, MANAGEMENT_VALIDATION, ORDERED, CONTRACT_SIGNED, ORDER_CONFIRMED, IN_TRANSIT, DELIVERED, PARTIALLY_RECEIVED, RECEIVED, INVOICED, PAYMENT_PENDING, PAID, CLOSED, CANCELLED. Line: DRAFT, OPEN, ORDERED, IN_PRODUCTION, READY, SHIPPED, AT_PORT, IN_CUSTOMS, IN_WAREHOUSE, DELIVERED, PARTIALLY_RECEIVED, NON_CONFORMING, ACCEPTED, CANCELLED. Only DRAFT → CREATED is implemented in S1. |
| Numbering | `document_counter` row for (company, year, `PURCHASE_REQUEST`) selected `WITH (UPDLOCK, HOLDLOCK)` (created on first use with a race-safe insert), incremented in the submit transaction. |
| Audit | Request created / updated (draft) / submitted events in the requesting company chain, same transaction. Sign-in success/failure/lockout and sign-out events in the global chain. |
| Frontend | `features/auth` (sign-in page, guard, session-expired interceptor, user menu), `features/requests` (list, form create/edit, detail). Unsaved-changes `CanDeactivate` guard + `beforeunload`. Reactive typed forms with `FormArray` lines. E2E with `@playwright/test` + `@axe-core/playwright` in `FrontEnd/e2e`, run against locally started servers using the installed Edge/Chrome channel. |

## API contract (all under `/api/v1`, JSON)

| Method & path | Purpose | Notes |
|---|---|---|
| `GET /auth/csrf` | Initialise the CSRF cookie | 204 |
| `POST /auth/login` `{email, password}` | Sign in | 200 → `Me`; 401 `invalid-credentials`; 423 `account-locked` |
| `POST /auth/logout` | Sign out | 204 |
| `GET /me` | Current user | `Me` = `{id, email, displayName, company:{id,code,name}, department:{id,name}?, roles[], preferredLanguage, sessionExpiresAt, idleTimeoutSeconds}` |
| `PUT /me/preferences` `{preferredLanguage:"en"|"fr"}` | Save language | 204 |
| `GET /requests/form-options` | Data for the form | Categories, currencies, priorities, purchase types, the user's company (+ department/site/cost centre lists), companies allowed as charged/buying, people the user may enter on behalf of |
| `GET /requests/statuses` | Status enumeration | `[{code, kind:"HEADER"|"LINE"}]` — labels are in the frontend i18n files |
| `GET /requests?mine=true&status=&q=&sort=&page=&size=` | List | Spring `Page`-like `{items[], page, size, totalItems}`; only visible requests |
| `GET /requests/{id}` | Detail | 404 when not visible |
| `POST /requests` | Create draft | 201 + body + `Location` |
| `PUT /requests/{id}` | Update draft | body includes `version`; 409 `stale-version`; 409 `invalid-state` if not DRAFT |
| `POST /requests/{id}/submit` `{version}` | Submit | 200 with number; 422 `validation-failed` with `errors[{field, code}]`; 409 as above |

Validation error codes are stable strings (e.g. `required`, `quantity-positive`, `max-decimals`, `not-in-company`) that the
frontend translates; the server never returns user-facing English text for field errors.

## Sprint 0 carry-over in this sprint

CR-06 (S1-AC-13), CR-19, CR-22 (S1-AC-14), CR-25, CR-26, CR-27, CR-28, QA-01.4 (git-history secrets scan).

## Open business decisions (not blocking this sprint)

Phase 2 moves RC-15…RC-20 (unapproved, requirements kept), CI platform, AI hosting, PO and receipt tolerances.

## Decisions on QA questions (lead, 2026-10-25)

| # | Question | Decision |
|---|---|---|
| Q1 | Required date in the past? | Allowed while drafting; at submission the header and line required dates must be ≥ today in the requesting company's time zone (`required-date-past`). |
| Q2 | Year in the request number at New Year | The year of the submission instant in the requesting company's time zone. |
| Q3 | Lockout for unknown e-mails | Same response body and status as a wrong password, with comparable timing (a dummy hash comparison is performed). No lockout record is kept for unknown e-mails. |
| Q4 | Can roles without REQUESTER create requests? | No. Creating requires REQUESTER or ENTER_ON_BEHALF in the requesting company; otherwise 403 `forbidden`. |
| Q5 | Demo users after the `demo` profile is switched off | Their rows remain, but sign-in for `@demo.local` accounts is refused unless the `demo` profile is active. |
| Q6 | Unsaved form data on session expiry | The form content is kept in `sessionStorage` (same tab, per user, cleared on submit or discard) and offered for restore after signing in again. |
| T-1…T-4 | Testability of timeouts and lockout | Idle timeout, absolute timeout, lockout threshold and lockout duration are configuration properties (`app.security.*`), so tests can shorten them. |

## Design review outcomes (supersede conflicting text above)

Review: `docs/reviews/sprint-1-design-review.md`. All Major findings accepted; Minor/Suggestion findings to be applied
by the Developer where they fit this sprint, otherwise listed as deferred in the sprint report.

| Finding | Decision |
|---|---|
| S1DR-01 CSRF with the SPA | Use Spring Security 7's SPA CSRF configuration (plain token in `XSRF-TOKEN` cookie, accepted from the `X-XSRF-TOKEN` header). `GET /auth/csrf` reads the token so the cookie is written; the client re-fetches it after sign-in and sign-out. |
| S1DR-02 Custom login endpoint | Explicitly save the `SecurityContext` to the session, apply the session-authentication strategy (session id rotation) and renew the CSRF token. Tests prove the id changes and the old id is rejected. |
| S1DR-03 Lockout / enumeration | **Replaces the hard lock in S1-AC-01.** Every failure, including unknown e-mail and throttled attempts, returns the same 401 `invalid-credentials`. Unknown e-mails run a dummy hash comparison. Throttling instead of locking: 5 failures per (e-mail, client IP) within 15 min block that pair for 15 min; 50 failures per IP within 15 min block that IP. Thresholds are `app.security.*` properties. |
| S1DR-04 Audit of failed sign-ins | Only successful sign-in, sign-out and throttle activation go to the audit chain (global). Failures go to the application log with a hashed e-mail, not the raw value. |
| S1DR-05 Demo accounts | `user_account.is_demo` flag. Sign-in for demo accounts is refused unless the `demo` profile is active (replaces the `@demo.local` rule in Q5). The demo seeder refuses to run on a database containing non-demo companies. |
| S1DR-06 Lost updates | The client's `version` is compared on update and submit. Every save replaces the whole request (header + lines) and increments the header version, including line-only edits. |
| S1DR-07 Charged and buying company | **Replaces part of S1-AC-04.** Both are derived on the server and not accepted from the client: charged = requesting company; buying = requesting company for `DIRECT` and `MIXED` purchasing mode, the group's purchasing centre for `CENTRALIZED`. Recorded as a proposal that purchasing can change in S3 (US-15). |
| S1DR-08 Status model | One ordered status enumeration shared by header and lines, with a rank. Header uses all values; lines use DRAFT, CREATED and the post-order values. `IN_TRANSIT` removed. "Request information" (§7.2) will be a flag on the approval step in S2, not a status. Order: DRAFT, CREATED, IN_APPROVAL, REVISION_REQUESTED, REJECTED, APPROVED, IN_PURCHASING_REVIEW, ASSIGNED_TO_BUYER, SOURCING, QUOTES_RECEIVED, TECHNICAL_VALIDATION, COMMERCIAL_VALIDATION, MANAGEMENT_VALIDATION, ORDERED, CONTRACT_SIGNED, ORDER_CONFIRMED, IN_PRODUCTION, READY, SHIPPED, AT_PORT, IN_CUSTOMS, IN_WAREHOUSE, DELIVERED, PARTIALLY_RECEIVED, NON_CONFORMING, ACCEPTED, INVOICED, PAYMENT_PENDING, PAID, CLOSED, CANCELLED. |
| S1DR-09 Stage history | `purchase_request_transition` (request, from/to status, stage, owner, actor, at, comment) written on every transition, including creation. |
| S1DR-10 Lock order | Submit acquires locks in this order: request row → document counter → audit chain head. Counter rows are created race-safely (insert-if-absent with retry on duplicate key). One retry on deadlock (SQL Server error 1205). The parallel-submit test includes the first submission of a year for a company. |
| S1DR-11 Session expiry UX | Client-side idle timer (reset on API activity, no background polling) warns 2 minutes before expiry with a "Stay signed in" action. Unsaved form content is kept in `sessionStorage` and offered for restore after signing in again. |
| S1DR-15 Submit by entered-by user | The user who entered a request on behalf of someone may edit and submit it. |
| S1DR-16 Missing §6.1 fields | Add: origin (`INTERNAL` only for now), budget line reference (free text), urgency justification (required when priority is `URGENT`), equipment / maintenance-order reference (free text). |

## Closure record (2026-10-27)

| Check (revision af0bb38) | Result |
|---|---|
| Backend `mvnw verify` | 86 tests, 0 failed, 51 skipped (SQL Server tests) |
| Backend `mvnw verify -Pdb-it` (SQL Server `procurement_test`) | 86 / 86 passed |
| Frontend lint · unit tests · build | pass · 148 / 148 · pass |
| Playwright e2e (Edge; EN + FR journeys, cross-company 404, session expiry, axe at 1280/360) | 10 / 10 passed |
| QA first pass (be1f311) | 121 executed, 117 passed, 4 failed → 6 defects (1 Major), all fixed |
| QA re-test (af0bb38) | 16 / 16 passed, 0 open defects |
| Review | No Blocker/Major open; S1CR-01…08 resolved |

Fixed during closure: latent production bug in `TransactionLocks` — `sp_getapplock` as the first statement of a
transaction ran outside any transaction (implicit transactions start only on table access) and failed as `busy`;
now opens the transaction explicitly (fix by the lead, verified by Reviewer and QA).

### Carried forward (non-blocking)

| ID | Severity | Item | Planned |
|---|---|---|---|
| S1CR-09 | Minor | `TransactionLocks` must fail fast on an auto-commit connection; DB test for no lingering transaction/lock | S2 |
| S1CR-10, S1CR-11 | Suggestion | Line-number ceiling; one user lookup per request | S2 |
| — | Limitation | Sessions in memory (single node): a backend restart signs everyone out (ADR-003) | When multi-node |
| — | Limitation | Throttling uses the direct client address; proxy-aware addressing with deployment | S9 |
| DR-04 | Accepted limit | Separate migration/runtime DB principals not yet configured | S9 |
| — | Not done | CI pipeline (platform undecided — open client decision) | When decided |
