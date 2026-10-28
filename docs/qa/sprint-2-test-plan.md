# Sprint 2 Test Plan — Approvals, tracker, Arabic

**Status: designed, not executed.** Source: `docs/product/sprint-2.md` (S2-AC-01…14, demo rules, API), Solution Guide
v3.0 Steps 0–2 and §7–§9, `docs/qa/test-strategy.md`. Derived independently by QA.

Types: **API** (HTTP against the running backend), **DB** (SQL Server `procurement_test` or a scratch database
`procurement_qa_scratch_s2*`), **E2E** (Playwright, Edge), **MAN** (manual or exploratory), **UNIT-R** (review of the
developer's automated tests, with mutation where cheap).

Environment: backend on 8081 (`SERVER_PORT`), dev server on 4300 with a scratchpad proxy (ports 8080 and 4200 belong
to another project). Dev e-mails are read as `.eml` files from `BackEnd/storage/mail`. Destructive tests (triggers,
crash simulation) run on scratch databases only.

Demo cast: Sami = `requester.tn`, Karim = `manager.tn`, Leila = `budget.tn`, management = `ceo` (MANAGEMENT_APPROVER),
`assistant.tn`, `requester.fr`, `manager.fr`, `buyer`, `admin`. TND has 3 decimals, EUR 2.

Assumptions to confirm with the developer (marked **[A]** in the plan):
- A1: the test profile can shorten the SLA and the link lifetime, and inject a clock (properties like Sprint 1's `app.security.*`).
- A2: the scheduler can be triggered on demand in tests, or runs at a short interval.
- A3: the outbox sender can be made to fail on demand, e.g. an `app.mail.mode=fail` test hook or an unwritable mail directory.

## 1. Approval rules and exchange rates (S2-AC-02, S2-AC-03)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-01 | API | Sami | Submit TND request, total 4 999.999 | Rule band 1: one stage (Karim). `approval_amount` 4 999.999 TND, rate 1 |
| QA2-02 | API | Sami | Submit TND request, total exactly 5 000.000 | Band 2: Karim → Leila (boundary belongs to the upper band) |
| QA2-03 | API | Sami | Submit TND request, total 50 000.000 | Band 3: Karim → Leila → Management |
| QA2-04 | API | Sami | Submit a EUR request; demo rate EUR→TND | Amount converted with the latest rate ≤ submission date and rounded HALF_UP to **3** decimals. Amount, rate and rate date are stored and the band is chosen on the converted amount |
| QA2-05 | API | Sami | EUR request whose converted amount lands within 0.0005 of a band boundary | Rounding happens before band matching; verify no off-by-rounding band choice |
| QA2-06 | API | admin, Sami | Add a newer rate dated after the submission date, then submit | The newer future-dated rate is ignored; the latest rate on or before today is used |
| QA2-07 | API | Sami | Submit in a currency with no rate (e.g. JPY) | 422 `missing-exchange-rate`; request stays DRAFT, no number consumed, no audit or task rows |
| QA2-08 | API | admin, Sami | Deactivate all rules for TN, then submit | 422 `no-approval-rule`; nothing persisted (check counter, tasks, audit) |
| QA2-09 | API | admin | Create a TN rule for category X plus a generic TN rule for the same band; submit a category-X request | The category-specific rule wins (most specific). Also test purchase-type specificity |
| QA2-10 | API | admin | Two active rules equally specific with overlapping bands | Admin save/activate reports `ambiguous-approval-rule`; if it reaches submission, 422 `ambiguous-approval-rule`, nothing persisted |
| QA2-11 | API | admin, Sami | Submit (rule v1), then admin edits the rule (v2) and adds a stage | The in-flight request keeps v1's stages; a new request uses v2 |
| QA2-12 | API | non-admin users | Call `/admin/approval-rules` and `/admin/exchange-rates`, GET and POST | 403 (`forbidden`); anonymous → 401 |
| QA2-13 | API | admin | Rate ≤ 0, more than 8 decimals, same currency pair, unknown currency, duplicate pair+date | 422 with field codes; no row |
| QA2-14 | API | admin | Rule with negative or overlapping bands in one rule set, zero stages, SLA ≤ 0, a named user from another company | 422 with field codes |
| QA2-15 | API | Sami | Stage condition not applicable (stage skipped) | Skipped stage recorded in the tracker as skipped, not as approved by someone |

## 2. Approval flow (S2-AC-04, S2-AC-05)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-20 | E2E | Sami → Karim → Leila | Band-2 request: submit; Karim approves; Leila approves | DRAFT → IN_APPROVAL (shown "Submitted") → APPROVED ("Approved — sent to central purchasing"). Each step has user, time, comment and from/to statuses; audit chain verifies |
| QA2-21 | API | Sami → Karim → Leila → ceo | Band-3 request through 3 stages | Tasks appear only for the current stage; ceo sees no task before Leila decides |
| QA2-22 | API | admin + 2 managers | Stage policy ANY with 2 approvers; first approves | Stage closes; sibling task cancelled (not "approved"); the second approver's action → 409 `task-not-pending` |
| QA2-23 | API | as above | Stage policy ALL; first approves, second returns | Stage ends with RETURNED; the request is REVISION_REQUESTED; the remaining pending tasks are cancelled |
| QA2-24 | API | as above | ALL; both approve | Next stage starts only after the second approval |
| QA2-25 | API | Karim | Reject without a reason, or with only whitespace; return without a reason | 422 `reason-required` |
| QA2-26 | API | Karim, Sami | Karim rejects with a reason; Sami tries to edit, submit or resubmit | REJECTED is terminal: edit and submit → 409 `invalid-state`; no new tasks; tracker shows the reason |
| QA2-27 | E2E | Karim, Sami | Karim returns with a reason; Sami edits a line and resubmits | REVISION_REQUESTED → IN_APPROVAL, `round_no` 2, circuit restarts at stage 1 (Karim again even though he approved in round 1). Round 1 history kept |
| QA2-28 | API | Sami | Resubmit after the amount changes band (e.g. 4 000 → 6 000 TND) | Round 2 uses the band for the new amount (the rule is re-evaluated) — **[confirm: re-evaluate the rule or keep the version?]** |
| QA2-29 | API | Karim, Sami | Karim asks a question; Sami answers | Request stays IN_APPROVAL; the task shows "information requested"; Sami and then Karim are notified; question and answer recorded; the SLA clock is paused for the waiting period |
| QA2-30 | API | Karim, Sami | Karim asks; Karim approves before Sami answers | Define behaviour: the approve is allowed and closes the open question, or it's blocked (409). Record the actual behaviour — **[confirm]** |
| QA2-31 | API | assistant.tn | Request entered by the assistant for Sami: who may answer the question and resubmit after a return? | Requester and entered-by (consistent with S1DR-15) |
| QA2-32 | API | Karim → another dept. manager of the same company | Delegate one task | Task reassigned, marked "on behalf of Karim"; Karim can no longer act; the delegate gets notified; audit records the delegation |
| QA2-33 | API | Karim | Delegate to Sami (no manager role), to `manager.fr` (other company), to himself, to an inactive user | 422 `delegate-not-eligible` for each; `GET .../delegates` doesn't list them |
| QA2-34 | API | Karim | Delegate a task that's already decided or cancelled | 409 `task-not-pending` |
| QA2-35 | API | Sami | Requester tries to approve their own request, by holding the manager role in a test user setup or by direct API with Karim's task id | 404 for a task that isn't theirs. If a self-approval rule applies (the requester is also the stage approver), record the behaviour — **[confirm: self-approval allowed?]** |
| QA2-36 | API | Karim | Approve with a stale `version` | 409 `stale-version` |
| QA2-37 | DB | — | After each terminal outcome | No PENDING tasks remain for the request; request status consistent with the round outcome |

## 3. Absence delegation (S2-AC-06)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-40 | E2E | Karim, delegate, Sami | Karim records an absence today–tomorrow with an eligible delegate; Sami submits | Task goes to the delegate, marked "on behalf of Karim"; Karim gets no task |
| QA2-41 | API | Karim | Absence overlapping an existing one | 422 `absence-overlap` |
| QA2-42 | API | Karim | Self-delegation; delegate from another company; delegate without the role | 422 `delegate-not-eligible` (self-delegation may have its own code: record it) |
| QA2-43 | API | Karim | End date before start date; a period wholly in the past | 422 |
| QA2-44 | API | — | Task created before the absence starts | Stays with Karim (only tasks created during the period are redirected); record the behaviour |
| QA2-45 | API | delegate | The delegate is also absent, with their own delegate (chain) | No infinite loop; documented behaviour (one hop, or fall back to the original) — **[confirm]** |
| QA2-46 | API | Sami | Sami creates an absence for Karim | 403 / 404; only the user or an admin may |
| QA2-47 | API | admin | Admin records an absence for Karim | Allowed and audited |

## 4. One-tap e-mail approval (S2-AC-07)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-50 | MAN/API | Karim | Read the task `.eml`; check the content | Summary has number, title, requester, company, amount, required date, ≤5 lines; Approve and Review buttons; in Karim's language |
| QA2-51 | API | anon | `GET /api/v1/approval-links/{token}` (and the `/a/:token` page) three times | Summary returned; the task stays PENDING (no action on GET) |
| QA2-52 | API | anon | Simulate a link scanner: HEAD and GET on the e-mail URL, plus a prefetch | Never approves |
| QA2-53 | API | anon | POST approve without CSRF | 403 `csrf-invalid`; no decision |
| QA2-54 | E2E | Karim (phone, 360 px, not signed in) | Open the Approve link → confirm | Task approved, audit says "approved via e-mail link", next stage created |
| QA2-55 | API | anon | Reuse the same token after success | Neutral "link no longer valid" page/body |
| QA2-56 | API | anon | Token after 72 h (clock or property **[A1]**) | Neutral invalid |
| QA2-57 | API | anon | Token for a task decided in the inbox first | Neutral invalid |
| QA2-58 | API | anon | Token for a task delegated or reassigned (single delegation, or an absence) | The original approver's token is invalid; the delegate gets their own |
| QA2-59 | API | anon | Random token, truncated or altered by one character, wrong length, SQL/script characters | Neutral invalid. The response body, status and headers are **byte-identical** across QA2-55…59, and the timing is comparable |
| QA2-60 | API | anon | Try to reject, return, question or delegate through the token (guessing the endpoints, extra body fields `action=reject`) | No such capability; only approve is possible |
| QA2-61 | API | Leila (signed in) | Use Karim's token while signed in as Leila | The token is bound to Karim's task: either Karim's approval is recorded as "via link" (not as Leila), or it's rejected. It must never record Leila as the approver — **[confirm]** |
| QA2-62 | DB | — | Inspect `approval_link_token` | Only the hash is stored (no plaintext); expiry and `used_at` set; token ≥ 256 bits (decode length) |
| QA2-63 | MAN | — | grep backend logs, access logs and the audit payload for the token | The token never appears in logs (also check query strings and error logs) |
| QA2-64 | API | anon | 50+ rapid GET/POST with invalid tokens from one IP | Rate limit kicks in (429 or a neutral delay); valid users from other IPs are unaffected |
| QA2-65 | API | anon | Two parallel POSTs approve with the same token | Exactly one decision; the other is neutral invalid or replays the same result |
| QA2-66 | API | anon | Approve via the link with the same `Idempotency-Key` twice | Second returns the first result; no second decision |
| QA2-67 | MAN | — | The "Review" link | Goes to the signed-in request page; requires sign-in; returns there after sign-in |

## 5. Races and idempotency (S2-AC-05, S2-AC-13)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-70 | API | Karim | Fire the link-approve and the inbox-approve concurrently (×10 runs) | Exactly one decision; the other gets 409 `task-not-pending` or neutral invalid; one next stage, no duplicate tasks |
| QA2-71 | API | Karim, other approver | ANY stage: two approvers approve concurrently | One closes the stage; one gets 409; the next stage is created once |
| QA2-72 | API | Karim | Approve vs reject concurrently | Exactly one wins; state consistent |
| QA2-73 | API | Karim | Same `Idempotency-Key` + same body, twice | Identical response, one decision |
| QA2-74 | API | Karim | Same key, different body (e.g. approve then reject) | Rejected (422 or 409), no second action — record the code |
| QA2-75 | API | Leila | Reuse Karim's `Idempotency-Key` value as another user | Must not return Karim's stored result (keys scoped per user) |
| QA2-76 | API | Sami | Resubmit twice in parallel after a return | One round 2, not rounds 2 and 3 |
| QA2-77 | UNIT-R | — | Developer's concurrency tests | Exist for the above and fail when the conditional update is removed (mutation) |

## 6. Reminders and escalation (S2-AC-09)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-80 | API | Karim | Task created Monday 09:00 Tunis, SLA 16 working hours | Working window 08:00–17:00 (sprint-2.md): 8 h on Monday (09:00–17:00) + 8 h on Tuesday (08:00–16:00) → due Tuesday 16:00 Tunis |
| QA2-81 | API | Karim | Task created Friday 16:00 | The weekend is excluded; due on Tuesday |
| QA2-82 | API | manager.fr | Same scenario for the Lyon company | Computed in Europe/Paris; compare with Tunis across the DST change (late October) |
| QA2-83 | API | Karim | A question is open for 4 h during the SLA | Due date pushed by 4 working hours |
| QA2-84 | API | — | Run the scheduler repeatedly past the SLA **[A2]** | Exactly one reminder per task; exactly one escalation at 2× SLA to the stage's escalation role; none after a decision |
| QA2-85 | API | — | Scheduler running on two triggers at once (simulate a concurrent run) | Still exactly one reminder (idempotent) |
| QA2-86 | API | — | Task outside working hours (created Saturday) | The SLA starts Monday 08:00 |
| QA2-87 | MAN | Karim | Reminder `.eml` | In Karim's language, with a deep link and a fresh one-tap link (or a reused valid one), no stale data |

## 7. Notifications and outbox (S2-AC-10)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-90 | MAN | all | Trigger every event: task assigned, question asked/answered, returned, rejected, approved, reminder, escalation, delegation | One `.eml` per recipient per event in `BackEnd/storage/mail`; in-app notification created; deep links correct |
| QA2-91 | MAN | Karim (ar), manager.fr (fr), Sami (en) | Same event to users with different languages | Each e-mail is in the recipient's stored language (not the actor's) |
| QA2-92 | DB/API | — | Make the sender fail **[A3]** | Outbox row stays pending with attempts and back-off; the business transaction is unaffected; the e-mail is delivered once the failure ends |
| QA2-93 | DB | — | Kill the backend after the commit, before sending; restart | The e-mail is still sent exactly once |
| QA2-94 | DB | — | Roll back a business transaction (force failure after the outbox insert) | No outbox row, no e-mail (transactional) |
| QA2-95 | API | Sami | In-app list, unread count, mark one read, mark all read; another user marks Sami's notification read | Counts correct; the other user → 404 |
| QA2-96 | MAN | — | `.eml` headers and HTML | No internal prices beyond the approver summary; no stack traces; links use `app.public-base-url` |

## 8. Attachments (S2-AC-12)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-100 | API | Sami | Upload PDF, PNG, JPG, DOCX, XLSX, CSV, TXT on the request and on a line | Accepted; listed; downloadable |
| QA2-101 | API | Sami | 20 MB exactly; 20 MB + 1 byte | First accepted, second 413/422 `file-too-large`, nothing stored |
| QA2-102 | API | Sami | `.exe`, `.html`, `.svg` (script), `.js` | 422 `file-type-not-allowed` |
| QA2-103 | API | Sami | An EXE renamed `.pdf` with `Content-Type: application/pdf`; a PDF sent as `image/png` | Content sniffed: the EXE is rejected; the stored type is the sniffed type |
| QA2-104 | API | Sami | Filenames `../../evil.pdf`, `..\\..\\x.pdf`, `C:\\x.pdf`, `con.pdf`, NUL byte, 300 chars, Arabic name `مواصفات.pdf`, RTL override char `‮` | Stored under a generated name outside the web root; no file written outside the storage directory; display name sanitised (RTL override neutralised); Arabic name preserved |
| QA2-105 | API | requester.fr | Download, delete or upload on Sami's request or attachment id | 404 for each, with a body identical to a non-existent id |
| QA2-106 | API | Karim (approver) | Download Sami's attachment | Allowed (task-based visibility); delete → 403 |
| QA2-107 | API | Sami | Upload a new version of a file | Version 2 is current, version 1 is retained and downloadable per the API; audit records it |
| QA2-108 | API | Sami | Upload/delete after submit (IN_APPROVAL), after APPROVED | 409 `invalid-state`; allowed again in REVISION_REQUESTED |
| QA2-109 | API | Sami | Download response headers | `Content-Disposition: attachment` (or inline only for safe types), `X-Content-Type-Options: nosniff`, no HTML rendering of uploaded content |
| QA2-110 | MAN | — | Antivirus port log | A "scan" log line per upload in dev; no-op does not block |

## 9. Visibility and user-facing status (S2-AC-11, S2-AC-13)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-120 | API | Leila | Before Leila's stage starts, open Sami's band-2 request | 404 (no task yet) — **[confirm: "has or had a task" excludes future stages]** |
| QA2-121 | API | Karim | After Karim approves and the request moves on | Still visible (had a task), read-only |
| QA2-122 | API | requester.fr, manager.fr | Tasks, approval data, attachments, inbox of TN requests | 404 / absent |
| QA2-123 | API | Karim | `GET /approvals/tasks/{id}` actions on Leila's task id | 404 |
| QA2-124 | API | every role | Role × new endpoint matrix (inbox, task actions, absences, admin, notifications, attachments, approval links) | Matches the role table; anonymous → 401 except approval links |
| QA2-125 | API | Sami | Check `userStatus` for each internal status present (DRAFT, CREATED from S1 rows, IN_APPROVAL, REVISION_REQUESTED, REJECTED, APPROVED) | DRAFT→DRAFT, CREATED→SUBMITTED, IN_APPROVAL→SUBMITTED, REVISION_REQUESTED→RETURNED, REJECTED→REJECTED, APPROVED→APPROVED; lists and detail agree |
| QA2-126 | E2E | Sami | Tracker | Steps in order; current step highlighted; "Waiting for Karim since …", due date, next actor; return and reject branches shown; skipped stages labelled |
| QA2-127 | API | Sami | Mass-assignment: send `status`, `round_no`, `approval_amount`, `approval_rate` on update/submit | 422 `not-editable` |

## 10. Arabic and RTL (S2-AC-01)

| ID | Type | User | Steps | Expected |
|---|---|---|---|---|
| QA2-130 | UNIT-R | — | Key-parity test | Covers en, fr and ar: identical keys and placeholders, no empty values, no untranslated English copied into ar.json (spot check) |
| QA2-131 | E2E | Sami (ar) | Select Arabic on sign-in and in the shell | `<html lang="ar" dir="rtl">`; the layout mirrors (sidebar on the right, the drawer slides in from the right at 360 px, directional icons such as back/next chevrons mirrored, non-directional ones not) |
| QA2-132 | MAN | Sami (ar) | Amounts, dates, quantities | Latin digits (`ar-TN`); currency code placement correct; no Arabic-Indic digits |
| QA2-133 | MAN | Sami (ar) | Mixed text: request number `DEMO-TN-PLT-PR-2026-000123`, "SKF 6205", e-mail addresses, "P-204", amounts with minus signs | Not scrambled (isolated or `dir="ltr"` / `<bdi>` where needed); tables align correctly |
| QA2-134 | MAN | Sami (ar) | Form validation messages, error summary, toasts, session warning, confirm dialog | Arabic text, RTL alignment, focus order follows the visual order |
| QA2-135 | E2E | all three languages | axe at 1280 and 360 on inbox, approval page, tracker, delegation, rule admin, one-tap page | 0 violations in LTR and RTL |
| QA2-136 | MAN | Karim (ar) | Task and reminder `.eml` | `dir="rtl"`, Arabic text, buttons readable, links work |
| QA2-137 | API | Sami | `PUT /me/preferences {"preferredLanguage":"ar"}`, then sign in elsewhere | Arabic applied; invalid code `"xx"` → 422 |
| QA2-138 | MAN | Sami (ar) | Long Arabic titles at 360 px; truncation; no horizontal scroll | No overflow |
| QA2-139 | MAN | — | Arabic font self-hosted | No request to a public CDN |

## 11. E2E journeys (S2-AC-14)

| ID | Type | Language | Journey |
|---|---|---|---|
| QA2-150 | E2E | EN | Sami submits band 2 → Karim approves via the e-mail link (read from the `.eml`) on 360 px → Leila approves in the inbox → Approved; tracker checked at each step |
| QA2-151 | E2E | FR | Karim returns → Sami edits and resubmits → round 2 → approval |
| QA2-152 | E2E | AR | Karim asks a question → Sami answers → Karim approves → Leila rejects with a reason |
| QA2-153 | E2E | EN | Karim records an absence → the delegate approves on Karim's behalf; history shows "on behalf of" |
| QA2-154 | E2E | any | Cross-company: requester.fr cannot open the TN request, task or attachment (UI and API) |
| QA2-155 | E2E | any | Invalid or used one-tap link shows the neutral page in all three languages |

## 12. Sprint 1 regression subset

| ID | Check |
|---|---|
| QA2-R1 | Sign-in, sign-out, CSRF, session expiry and warning, throttling (generic 401) |
| QA2-R2 | Draft create/edit with line ids, submit numbering under 20 parallel submits — now with circuit creation in the same transaction (no duplicates, no gaps) |
| QA2-R3 | Atomicity: force an audit failure during submit — the request, number, counter, round, tasks and outbox are all rolled back |
| QA2-R4 | Cross-company 404 on list, detail, update, submit (Sprint 1 matrix) |
| QA2-R5 | Language preference persistence (now three languages) |
| QA2-R6 | Health and status exposure (anonymous status only) |
| QA2-R7 | Existing S1 `CREATED` rows still display and are not broken by the new circuit — **[confirm: do S1 CREATED rows enter a circuit?]** |
| QA2-R8 | `mvnw verify`, `verify -Pdb-it`, npm lint/test/build, full e2e |
| QA2-R9 | S1CR-09: a `TransactionLocks` call on an auto-commit connection fails fast; DB test shows no open transaction or lock after commit and after rollback |

## Exit criteria

Every scenario Executed / Blocked / Not executed with a reason; no open Blocker or Major defect; all **[confirm]**
items answered by the lead and reflected in the expected results; evidence stored in `docs/qa/evidence/`.
