# Sprint 2 Code Review

- Revision: git tree `767933d` (staged index; changes since the Sprint 1 commit `ad27dc8`)
- Reviewer: Reviewer agent, static review only (no build, test or server run)
- Scope: high-risk code first — approval engine, one-tap links, rules and rates, SLA calendar, outbox and reminders,
  attachments, visibility, test hooks, V5 migration, and the frontend one-tap page.
- Verdict: **not yet acceptable** — 3 Major findings (one security, one acceptance-criterion gap, one verification gap).
  No Blocker.

## Findings

| ID | Severity | Location | Finding | Recommended fix |
|---|---|---|---|---|
| S2CR-01 | Major | `notification/Notifications.java:68`, `approval/ApprovalNotices.java:53`, `V5…sql:264` | The one-tap URL (`/a#<token>`) is rendered into `notification_outbox.body_html/body_text` and kept there in clear with no purge. Anyone who can read that table (DBA, backup, support export) can approve pending tasks. This defeats storing only the token hash (S2-AC-07, S2DR-07). In dev the same applies to the `.eml` files (acceptable there, but should be documented). | Keep the token out of stored bodies: store a placeholder and inject the link at send time from a short-lived encrypted/separate column, **or** redact the link from the row as soon as it is sent (and on `FAILED`), and purge sent rows after a retention period. Add a DB-IT asserting no stored outbox row contains a usable token after sending. |
| S2CR-02 | Major | `approval/ApprovalRulesAdmin.java`, frontend rule editor | The rule editor cannot list the group's companies or users: an administrator cannot create the first rule for a company that has none, and named approvers are entered as a user number. S2-AC-02 ("an administrator configures, per company, rules … without code") is only partly met through the UI. | Add `GET /admin/companies` (the admin's group) and `GET /admin/users?companyId=&role=` (minimal fields), and use them for a company selector and a user picker. Cross-group tests. |
| S2CR-03 | Major (verification gap) | `db/demo`, `identity/DemoUserSeeder.java`, e2e `absence delegation` spec | The demo data has only one department manager at Sfax, so no eligible delegate exists and the absence-delegation journey is skipped. S2-AC-06 and S2-AC-14 are therefore not verified end to end. | Add a second Sfax `DEPARTMENT_MANAGER` demo user (e.g. Production department, display name from the v3.0 cast or "(Demo)"), and un-skip the e2e. |
| S2CR-04 | Minor | `approval/ApprovalReminders.java` `send()` | The due task list is read outside the transaction; `send()` claims the reminder but does not re-check that the task is still `PENDING` and has no open question. A task decided (or paused by a question) between the query and the send still gets a reminder/escalation. | Lock and re-read the task inside the transaction; skip unless pending and not paused. |
| S2CR-05 | Minor | `approval/ApprovalReminders.java` (escalation fallback) | When no escalation contact exists, group administrators are notified **and added as participants**, giving ADMIN users business-data visibility contrary to the Sprint 1 visibility table ("ADMIN: no business data"). | Notify administrators without granting participation (they act through configuration), or record a decision changing the visibility table. |
| S2CR-06 | Minor | `notification/OutboxSender.java` `sendBatch()` | SMTP sends for up to 20 mails run inside one DB transaction holding `UPDLOCK` rows and a pooled connection; slow SMTP holds both, and a rollback after sending re-sends the whole batch. | Claim rows in a short transaction (status `SENDING` + lease time), send outside the transaction, mark each mail in its own short update. |
| S2CR-07 | Minor | `approval/ApprovalRulesAdmin.java:117` | The overlap check at activation is not race-safe: two administrators can activate overlapping versions concurrently. Submission then fails with `ambiguous-approval-rule` instead of the configuration being refused. | Take `TransactionLocks.lockExclusively("approval-rules:" + companyId)` before the check. |
| S2CR-08 | Minor | `approval/ApprovalLinkController.java` `throttle()` | Rate limiting is keyed on `getRemoteAddr()`. Behind a reverse proxy every approver shares one bucket (30/min for the whole company). | Proxy-aware client address with a trusted-proxy list (S9), plus a per-token limit. |
| S2CR-09 | Minor | `approval/ApprovalLinks.java` `validate()` | `resolver.isEligible(approver.id(), task.approverType(), approver.id(), request)` passes the approver's own id as the stage's named user, so the `NAMED_USER` re-check at click time is always true. Low impact (the task is still bound to the assignee), but the "still eligible" check of S2DR-07 is vacuous for that type. | Store the stage's named user on the task (or look it up from the rule) and pass it. |
| S2CR-10 | Minor | `approval/ApprovalLinks.java` `summary()` | Link summary lines carry only the description, not quantity and unit — thin for a phone decision (S2-AC-07 lists "lines up to 5"). | Return `{description, quantity, unit}` (or "10 EA …"). |
| S2CR-11 | Minor | `approval/ApprovalEngine.java:288` `end()` | Reject/return do not record `QUESTION_SUPERSEDED` (nor notify) when the deciding task has an open question; approve and delegate do (QA2-30). The tracker history is inconsistent. | Call `supersedeQuestion(...)` at the start of `end()`. |
| S2CR-12 | Minor | `document/Attachments.java:119` | The per-request limits use `UPDLOCK` on an aggregate; with no existing rows nothing is locked, so concurrent first uploads can exceed the 30-file / 100 MB limits. | Lock the request row (or an application lock per request) before counting. |
| S2CR-13 | Suggestion | `approval/TestingController.java:24` | Testing endpoints are enabled by a property only (`app.testing.endpoints`); an environment variable in production would expose the clock shift and job triggers to administrators. | Also require the `dev`/`test` profile (`@Profile`), and fail startup if the property is set in another profile. |
| S2CR-14 | Suggestion | `approval/ApprovalRulesAdmin.java:130`, `platform/web/Idempotency.java` | `retire()` has no state check (a DRAFT can be "retired"); idempotency rows are never purged. | Allow retire only for ACTIVE; scheduled purge of idempotency rows older than 24 h. |

## Design review items (S2DR) verified in code

| Item | Status | Evidence |
|---|---|---|
| S2DR-01 legacy circuits | Resolved | `LegacyCircuitJob` (lowest order, after `DemoUserSeeder` at highest order), admin awaiting-circuit list |
| S2DR-02/03 self-exclusion, empty stages | Resolved | `ApprovalEngine.createStageTasks` skips authors; `no-eligible-approver` + admin notice |
| S2DR-04 revision and rounds | Resolved | `RequestAccessPolicy.isEditableStatus`, round per submission with basis stored |
| S2DR-05 bands, specificity, overlap | Resolved (race: S2CR-07) | `Rule.inBand` `[min,max)`, `specificity()`, activation check, unique active index |
| S2DR-06 rates | Resolved | `ExchangeRates.convert`: equal currency → 1, direct, inverse with 8-decimal rate, HALF_UP to minor units; no update endpoint |
| S2DR-07 one-tap security | **Partly** (S2CR-01, S2CR-09) | 256-bit token, SHA-256 at rest, fragment, POST body, CSRF applies, `no-store` + `no-referrer`, single use via conditional update, re-validation, neutral 404, `TokenBody.toString()` masked; frontend removes the hash with `replaceState` |
| S2DR-08 one decision service, lock order | Resolved | `ApprovalEngine.decide` (request → round → tasks → audit), re-checks assignee/pending after locking; link path calls the same service |
| S2DR-09 questions per task | Resolved (S2CR-11) | `question()`, `answer()` shift due/escalation by paused working minutes |
| S2DR-10 SLA calendar, outbox | Resolved (S2CR-04, S2CR-06) | `WorkingCalendar` per company zone/days (DST-safe via `ZonedDateTime` per day); stable `Message-ID`; reminder claim unique per (task, kind) |
| S2DR-11 attachments | Resolved (S2CR-12) | content sniffing allow-list, macro/legacy Office refused, generated keys with path check, `attachment` disposition + `nosniff`, download only when `CLEAN`, upload/delete audited |
| S2DR-12 visibility | Resolved (S2CR-05) | participant table + `exists` subquery; drafts excluded |
| S2DR-13 Arabic | Resolved in code; native review open | V5 language checks include `ar` |
| S2DR-16 cuts | None taken | — |
| S2DR-17 API additions | Resolved | preview, inbox count, attachments list, admin absences, versions |

## Acceptance criteria (static verdict)

| AC | Verdict | Reason |
|---|---|---|
| S2-AC-01 Arabic | Met (pending native-speaker review) | language checks, `ar.json`, RTL e2e reported passing |
| S2-AC-02 Approval rules | **Partly** | S2CR-02 (no company list / user picker), S2CR-07 |
| S2-AC-03 Exchange rates | Met | |
| S2-AC-04 Circuit start | Met | |
| S2-AC-05 Approver actions | Met (S2CR-11 minor) | |
| S2-AC-06 Absence delegation | **Implemented, not verified end to end** | S2CR-03 |
| S2-AC-07 One-tap e-mail approval | **Partly** | S2CR-01 (token in clear in outbox), S2CR-09, S2CR-10 |
| S2-AC-08 Inbox | Met | |
| S2-AC-09 Reminders and escalation | Met (S2CR-04 minor) | |
| S2-AC-10 Notifications | Met (S2CR-06 minor) | |
| S2-AC-11 Tracker | Met | |
| S2-AC-12 Attachments | Met (S2CR-12 minor) | |
| S2-AC-13 Visibility, idempotency | Met (S2CR-05 minor) | |
| S2-AC-14 Quality | **Partly** | delegation e2e skipped (S2CR-03); native Arabic review open |
