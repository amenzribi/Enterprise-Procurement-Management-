# Sprint 2 — Defects

Revision under test: git tree `767933d` (staged index). Tester: QA agent. Date: 2026-10-27/28.
Severity scale as in `test-strategy.md`. Reviewer findings are referenced, not duplicated.

| ID | Severity | Summary | Status |
|---|---|---|---|
| QA2-D-01 | Major | Notification bell fails WCAG 2.5.3 (axe `label-content-name-mismatch`) when a user has more than 99 unread notifications; 4 e2e accessibility tests fail | Open |
| QA2-D-02 | Major | Rule editor cannot create the first rule for a company that has no rule yet (no company list, no user picker) — same as Reviewer **S2CR-02** | Open |
| QA2-D-03 | Minor | ALL stage policy has no automated test and cannot be exercised with demo data (no stage with two approvers) | Open |
| QA2-D-04 | Minor | Demo data has no second Sfax department manager: single-task delegation shows no eligible delegate and the absence e2e skips — same as Reviewer **S2CR-03** | Open |
| QA2-D-05 | Minor | One-tap link summary lists line descriptions without quantity/unit (S2-AC-07 asks for lines up to 5; an approver on a phone cannot judge quantities) | Open |
| QA2-D-06 | Trivial | `POST /testing/clock` expires every existing session (idle/absolute checks use the shifted clock); test scripts must sign in again after shifting. Test hook only. | Open (document) |

Related Reviewer finding, not re-tested in depth: **S2CR-01** — approval token stored in clear in
`notification_outbox` bodies. QA confirmed tokens never appear in the backend or dev-server logs (333 tokens checked).

## QA2-D-01 — Bell accessible name does not contain its visible text above 99 unread

- **Expected:** axe clean on all approval screens (S2-AC-14); the accessible name contains the visible label text (WCAG 2.5.3, Level A).
- **Actual:** with 192 unread notifications (manager.tn after normal test activity) the bell shows `99+` (`aria-hidden` badge) while `aria-label` is "Notifications, 192 unread". axe reports `label-content-name-mismatch: .bell` on the inbox page.
- **Repro:** sign in as a user with > 99 unread notifications → open `/approvals` → run axe (or `npm run e2e`: `accessibility-approvals.spec.ts` EN/AR desktop/phone fail).
- **Evidence:** `FrontEnd/src/app/layout/notification-bell.ts:27–31`; Playwright failure output "label-content-name-mismatch: .bell" (4 tests); unread count API returned `{"unread":192}` for manager.tn.
- **Suggested fix:** keep the visible text inside the accessible name (e.g. label "Notifications, 99+ unread" when capped), or make the e2e independent of accumulated data. Fails a release-level criterion in a common long-run state, hence Major.

## QA2-D-02 — First rule for a new company impossible from the UI

- **Expected:** S2-AC-02 — an administrator configures rules per company without development.
- **Actual:** `features/admin/approval-rules.ts:88–129` builds the company list from existing rules plus the admin's request form options (empty for ADMIN), and named approvers are entered as a numeric user id. A company without a rule (e.g. DEMO-CPA, DEMO-HOLD) cannot be selected. See Reviewer S2CR-02.
- **Repro:** admin → Approval rules → New rule → company list contains only DEMO-TN-PLT and DEMO-FR-PLT.

## QA2-D-03 — ALL policy unverified

- **Expected:** S2-AC-05 — ALL requires every task to approve.
- **Actual:** implemented (`ApprovalEngine.java:255`) but no test in the DB-IT suite references ALL, and no demo stage has two approvers, so neither QA nor e2e can exercise it.
- **Suggested fix:** DB-IT test with two approvers in one ALL stage (approve + approve, approve + reject).

## QA2-D-04 — Demo data gap for delegation

Karim's eligible-delegate list is empty (`GET /approvals/tasks/{id}/delegates` → `[]`); absence delegation was verified
through `/admin/absences` with `adminOverride` (QA2-55…58 pass). See Reviewer S2CR-03.

## QA2-D-05 — Link summary without quantities

`ApprovalLinks.java:83` maps lines to descriptions only. Suggested: "10 EA Mechanical seal P-204".
