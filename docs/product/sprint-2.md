# Sprint 2 — Approvals, request tracker, Arabic

Status: in progress · Two weeks · Reference: Solution Guide v3.0 Steps 0–2, §7 (returned, approver on leave), §8,
§9 (one-tap approval, tracker), UC-01 (Arabic), UC-04, UC-05, UC-06 (e-mail), UC-28 (approval rules);
French spec v2.1 §7. Decisions of 2026-10-27: SQL Server only; Arabic in this sprint; e-mail link + mobile web
(Teams/WhatsApp later).

## Objective

A submitted request follows its company's approval rules. Approvers decide from the inbox or in one tap from an
e-mail on their phone; they can ask the requester a question, return, reject or delegate. The requester follows the
request in a parcel-style tracker. The whole application is available in Arabic (right to left), French and English.

## Out of scope

Teams and WhatsApp channels, organisation admin and master-data import (S3), budget availability checks (Enterprise),
anything after approval (S3+).

## Acceptance criteria

| ID | Criterion |
|---|---|
| S2-AC-01 | **Arabic.** Arabic is selectable everywhere a language is chosen; the layout is right to left (`dir="rtl"`), with an Arabic font and Latin digits for amounts and numbers (locale `ar-TN`). Every screen and e-mail is complete in AR, FR and EN (key-parity test covers three files). `preferred_language` accepts `ar`. axe and the e2e journey pass in Arabic. |
| S2-AC-02 | **Approval rules.** An administrator configures, per company, rules matched by category (optional), purchase type (optional) and amount band in **company currency**, each with ordered stages. A stage has: approver type (`DEPARTMENT_MANAGER` of the request's department, `BUDGET_APPROVER` or `MANAGEMENT_APPROVER` of the requesting company, or a named user), policy `ANY` or `ALL`, SLA in working hours, optional escalation role. Rules are versioned: editing creates a new version; requests keep the version they started with. The most specific active rule wins; ties are a configuration error shown to the administrator. |
| S2-AC-03 | **Exchange rates.** An administrator records rates (quote currency per 1 base currency, by date). At submission the request amount is converted to company currency with the latest rate on or before the submission date; amount, rate and rate date are stored on the request. Missing rate → 422 `missing-exchange-rate`; no matching rule → 422 `no-approval-rule`. |
| S2-AC-04 | **Submission starts the circuit.** Submit moves `DRAFT → IN_APPROVAL` (shown as "Submitted"), creates tasks for the first stage (and skips stages whose condition does not apply), notifies approvers. A request approved by its last stage moves to `APPROVED` ("Approved — sent to central purchasing"). |
| S2-AC-05 | **Approver actions** (inbox and request page): Approve; **Ask a question** (request stays in approval, task SLA paused, requester notified, answer recorded, approver notified); **Return for revision** (reason required → `REVISION_REQUESTED`; requester edits and resubmits; the circuit restarts from stage 1 with a new round number); **Reject** (reason required → `REJECTED`, terminal); **Delegate** (to an eligible user, reason optional, marked as delegated). `ANY`: first decision closes the stage and cancels sibling tasks; `ALL`: every task must approve; any rejection or return ends the stage. Every action records user, time, comment, previous and new status, on behalf of whom; audit event in the same transaction. |
| S2-AC-06 | **Absence delegation.** A user (or administrator) records an absence period with a delegate. Tasks created during the period go to the delegate, marked "on behalf of". The delegate must hold the same approver role in the same company (or be explicitly allowed by the administrator). Overlapping periods and self-delegation are rejected. |
| S2-AC-07 | **One-tap e-mail approval.** Each new task e-mail contains a summary (number, title, requester, company, amount, required date, lines up to 5) and two buttons: **Approve** and **Review**. Approve opens a mobile page with the summary and a single confirm button; it never acts on GET (link scanners). The token is random (≥ 256 bits), stored hashed, single use, bound to one task and one approver, valid 72 h, invalid once the task is decided or reassigned. Reject, return, question and delegate require sign-in (Review). Token misuse returns the same neutral "link no longer valid" page. |
| S2-AC-08 | **Inbox.** "To approve" lists pending tasks (request, requester, company, amount in request and company currency, stage, waiting since, due, overdue flag, info requested), filterable and sortable, with bulk-free one-by-one actions; usable at 360 px. Count badge in the navigation. |
| S2-AC-09 | **Reminders and escalation.** A scheduler sends a reminder when a task reaches its SLA and an escalation to the stage's escalation role at twice the SLA; SLA time excludes weekends and periods while a question is open. Each reminder is sent once (idempotent). |
| S2-AC-10 | **Notifications.** E-mails go through a transactional outbox (retry with back-off, no loss on crash, no duplicates), in the recipient's language, with deep links. In development, e-mails are written as `.eml` files to `BackEnd/storage/mail` (no external service). In-app notifications with an unread count and a list; marking read. Events: task assigned, question asked/answered, returned, rejected, approved (final), reminder, escalation, delegation. |
| S2-AC-11 | **Request tracker.** The request page shows the user-facing status (v3.0 §8), a step tracker (Draft → Submitted → Approved → Assigned → Quoting → Awarded → Ordered → In transit → Received → Closed, with Returned/Rejected branches), each approval stage with approvers, decisions, comments, "waiting for Karim since 2 h", due date, and who acts next. Lists show the user-facing status. |
| S2-AC-12 | **Attachments.** Files on the request or a line (datasheets, photos, quotes): max 20 MB each, allowed types PDF, images, Office, CSV/TXT; content type sniffed, not trusted from the client; stored outside the web root with a generated name; versions kept; download only for users who can see the request; antivirus port (no-op in dev, logged). Editable in draft and revision; read-only after. |
| S2-AC-13 | **Visibility.** An approver sees a request while they have or had a task on it (plus existing S1 rules). Requests and tasks of other companies → 404. Each new endpoint has cross-company and role tests; actions are idempotent (`Idempotency-Key` header; replays return the first result) and use optimistic locking. |
| S2-AC-14 | **Quality.** EN/FR/AR complete; axe clean (LTR and RTL) on inbox, approval page, tracker, delegation and rule admin; e2e covers Sami → Karim (e-mail link) → Leila (budget stage) → Approved, a return-and-resubmit, a rejection, a question/answer, and an absence delegation. |

### Demo data (demo profile only)

Display names follow the v3.0 story. New demo users: `budget.tn@demo.local` (Leila, BUDGET_APPROVER, Sfax),
`manager.fr@demo.local` (DEPARTMENT_MANAGER, Lyon), and `ceo@demo.local` also gets MANAGEMENT_APPROVER.
Renamed display names: requester.tn = Sami, manager.tn = Karim, buyer = Ines, purchasing.manager = Procurement manager.

| Company | Rule (amount in company currency) | Stages |
|---|---|---|
| DEMO-TN-PLT (TND) | 0 – 4 999.999 | Department manager (ANY, SLA 16 h) |
| DEMO-TN-PLT (TND) | 5 000 – 49 999.999 | Department manager → Budget approver (SLA 16 h each) |
| DEMO-TN-PLT (TND) | ≥ 50 000 | Department manager → Budget approver → Management approver (escalation: MANAGEMENT_APPROVER) |
| DEMO-FR-PLT (EUR) | any amount | Department manager |

Demo exchange rates: EUR→TND, USD→TND, USD→EUR at fixed demo values dated 2026-01-01, labelled "(Demo)".

## Technical design

| Topic | Decision |
|---|---|
| Module | New `approval` module (rules, stages, rounds, tasks, delegations, inbox, one-tap links). `request` exposes events (`RequestSubmitted`, `RequestResubmitted`) and a narrow transition API; `approval` calls back to move the request to APPROVED / REJECTED / REVISION_REQUESTED. New `notification` module (outbox, e-mail templates, in-app). `reference` data for exchange rates in `organization`. Attachments in a `document` module with a storage port. |
| Schema | `exchange_rate`, `approval_rule`, `approval_rule_stage`, `approval_round`, `approval_task`, `approval_task_event`, `approval_link_token` (hash, task, user, expires, used_at), `user_absence`, `notification_outbox`, `in_app_notification`, `idempotency_record`, `attachment` + `attachment_version`; request columns `approval_amount`, `approval_currency`, `approval_rate`, `approval_rate_date`, `approval_rule_version_id`, `round_no`. `preferred_language` check extended to `ar`. Roles added: BUDGET_APPROVER, MANAGEMENT_APPROVER. |
| Statuses | Internal codes unchanged; `IN_APPROVAL` used from S2 (`CREATED` stays valid for S1 rows and is displayed as "Submitted"). A `userStatus` (DRAFT, SUBMITTED, RETURNED, REJECTED, APPROVED, ASSIGNED, QUOTING, AWARDED, ORDERED, IN_TRANSIT, RECEIVED, CLOSED) is computed server-side and returned with every request. `AWARDED` internal code added to the enumeration. |
| Concurrency | Task decision = conditional update on `status = PENDING` + version; the stage completion check and the next-stage creation happen in the same transaction with the request row locked first (lock order: request → round → tasks → audit). |
| SLA time | Working hours Mon–Fri 08:00–17:00 in the company time zone (config per company later); question time excluded. |
| E-mail | Spring Mail. `app.mail.mode=file` in dev/test writes `.eml`; `smtp` in production via `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` env vars. Templates in AR/FR/EN with RTL markup for Arabic. Links use `app.public-base-url`. |
| One-tap page | Angular route `/a/:token` outside the signed-in shell; `GET /api/v1/approval-links/{token}` returns the summary only if valid; `POST /api/v1/approval-links/{token}/approve` with CSRF + `Idempotency-Key`. Rate-limited per IP. Audit event records "approved via e-mail link". |
| Frontend | Inbox, approval panel on the request page, tracker component, notification bell, delegation (my absence), admin pages for rules and exchange rates (ADMIN only), one-tap page, attachments uploader. RTL via `dir` on `<html>`, logical properties, mirrored icons where directional, Arabic font self-hosted (IBM Plex Sans Arabic or Noto Sans Arabic via fontsource). |

## API additions (under `/api/v1`)

| Method & path | Purpose |
|---|---|
| `GET /approvals/inbox?status=&q=&sort=&page=&size=` | My pending (and recently decided) tasks |
| `POST /approvals/tasks/{id}/approve` `{comment?, version}` | Approve |
| `POST /approvals/tasks/{id}/reject` `{reason, version}` | Reject |
| `POST /approvals/tasks/{id}/return` `{reason, version}` | Return for revision |
| `POST /approvals/tasks/{id}/question` `{question, version}` | Ask the requester |
| `POST /requests/{id}/answers` `{taskId, answer}` | Requester answers |
| `POST /approvals/tasks/{id}/delegate` `{toUserId, reason?, version}` | Delegate one task |
| `GET /approvals/tasks/{id}/delegates` | Eligible delegates |
| `GET /requests/{id}/approval` | Rounds, stages, tasks, decisions (tracker data) |
| `GET/POST/DELETE /me/absences` | Absence delegation |
| `GET /approval-links/{token}`, `POST /approval-links/{token}/approve` | One-tap |
| `GET/POST/PUT /admin/approval-rules`, `POST /admin/approval-rules/{id}/activate` | Rules (ADMIN) |
| `GET/POST /admin/exchange-rates` | Rates (ADMIN) |
| `GET /notifications?unread=`, `POST /notifications/{id}/read`, `POST /notifications/read-all` | In-app |
| `POST /requests/{id}/attachments` (multipart), `GET /attachments/{id}/content`, `DELETE /attachments/{id}` | Attachments |

Error codes added: `missing-exchange-rate`, `no-approval-rule`, `ambiguous-approval-rule`, `task-not-pending`,
`reason-required`, `delegate-not-eligible`, `absence-overlap`, `link-invalid`, `file-too-large`, `file-type-not-allowed`.

## Sprint 1 carry-over in this sprint

S1CR-09 (`TransactionLocks` fail-fast on auto-commit + DB test), S1CR-10, S1CR-11.

## Decisions on QA questions (lead, 2026-10-27)

| # | Question | Decision |
|---|---|---|
| QA2-28 | Resubmission after a return with a changed amount | A resubmission starts a new round and **re-evaluates** the rule (current active version, new amount, rate on the resubmission date). Earlier rounds keep their own rule version in the history. |
| QA2-30 | Approve while the approver's own question is open | Allowed. The open question is closed as "superseded by decision" and the requester is notified; the SLA pause ends. |
| QA2-35 | Requester is also an approver of a stage | No self-approval. The requester is excluded when tasks are created. If a stage then has no eligible approver, the task goes to the stage's escalation role; if none exists, submission fails with 422 `no-eligible-approver` (configuration problem shown to the administrator). |
| QA2-45 | Delegate is also absent | Follow the delegation chain up to 3 hops with cycle detection; if unresolved, the task stays with the original approver and is flagged. |
| QA2-61 | A signed-in user opens another approver's link | Nothing happens: the same neutral "link no longer valid" page; a security log entry (not an audit-chain event) records the mismatch. |
| QA2-120 | Approver of a later stage before it starts | Not visible until their task exists (visibility is task-based). |
| QA2-R7 | Sprint 1 requests still `CREATED` | A one-time, idempotent startup job enters them into the approval circuit using their submission date; if no rule matches they stay `CREATED` and are listed in the log for the administrator. |
| A1–A3 | Test hooks | Provide an injectable clock, configurable SLA/link lifetimes, an on-demand scheduler trigger (test profile only), and a failing mail sender for tests. |

## Design review outcomes (supersede conflicting text above)

Review: `docs/reviews/sprint-2-design-review.md`. All Major findings accepted; Minor/Suggestion findings applied where
they fit, otherwise listed as deferred in the sprint report.

| Finding | Decision |
|---|---|
| S2DR-01 | Startup job for Sprint 1 `CREATED` requests uses the rule version and rate valid at their submission date; unmatched requests appear in an admin "awaiting circuit" list (and the log). |
| S2DR-02/03 | Requester **and** entered-by user are excluded from every task. Empty stage → escalation role; still empty → 422 `no-eligible-approver` and company administrators are notified. |
| S2DR-04 | `REVISION_REQUESTED` requests are editable by requester/entered-by (number kept). Resubmission creates a new round recording rule version, rate, rate date and converted amount; pending tasks and links of earlier rounds are cancelled. |
| S2DR-05 | Bands are `[min, max)`; activation rejects overlapping rules for the same company/category/purchase type (`ambiguous-approval-rule` at activation, not at submission). Specificity order: category+type > category > type > company default. Rule versions are immutable once used. |
| S2DR-06 | Rate stored as "quote units per 1 base unit"; conversion to company currency uses the direct pair, else the inverse pair; no lookup when currencies are equal (rate 1). Recorded rates are immutable (corrections are new rows). |
| S2DR-07 | Token travels in the URL **fragment** (`/a#<token>`), removed from history after reading; the page calls `POST /approval-links/summary` and `POST /approval-links/approve` with the token in the body (CSRF + `Idempotency-Key`). Responses `Cache-Control: no-store`, `Referrer-Policy: no-referrer`. At approval: approver active, still eligible, task pending and assigned to them. Both calls rate-limited. Summary limited to the fields of S2-AC-07. |
| S2DR-08 | One decision service used by inbox and link; lock order request → round → tasks → audit; concurrency test (link approve vs inbox reject: exactly one wins). |
| S2DR-09 | Questions belong to one task; other approvers may decide, which closes the open question (consistent with QA2-30). |
| S2DR-10 | SLA in the requesting company's time zone and working week (company setting, default Mon–Fri 08:00–17:00). Outbox is at-least-once with a stable `Message-ID` per notification; reminders/escalations are de-duplicated by a unique (task, kind) record. |
| S2DR-11 | Attachments: allow-list by sniffed content; SVG, HTML and macro-enabled Office refused; generated storage names; `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`; per-request limits (30 files, 100 MB); download only after the scan port reports clean (dev no-op = clean); upload/delete audited. |
| S2DR-12 | Named-user stage approvers must belong to the same group; delegate eligibility checked server-side; architecture test extended to approval tables. |
| S2DR-13 | Migration extends the `preferred_language` and `default_language` checks to `ar`. Bidi isolation (`<bdi>` / `dir="auto"`) for numbers, codes and amounts in Arabic text. Arabic e-mails with `dir="rtl"` and safe system fonts. **Native-speaker review of Arabic texts is an open item**, not a passed check. |
| S2DR-16 | If capacity runs short, cut in this order and record as carry-over: rule edit screen (keep seeded rules + activation), escalation at 2×SLA, in-app notification list (keep badge), attachment versions, exchange-rate admin screen. |
| S2DR-17 | API additions: `actions` for the current user on `GET /requests/{id}/approval`; `GET /approvals/inbox/count`; `GET /requests/{id}/approval-preview` (who will approve, before submission); `GET /requests/{id}/attachments`; admin absences for other users (`/admin/absences`); `GET /admin/approval-rules/{id}/versions`. |
