# Handoff: Meridian refinement, approved and committed (2026-11-12)

**Approved by the owner on 2026-11-12** and committed locally as `feat(ui): complete Meridian interface refinements`, annotated tag `meridian-refinement-approved` on `stabilization-and-ledger-redesign`. Find the commit with `git rev-parse meridian-refinement-approved^{commit}`. Not pushed; a push needs the owner's separate instruction.

**The committed source is the tested source.** The last product file was edited at 08:07:12 on 2026-11-12; lint, unit tests and the build ran after that, then the Playwright suite (finished 08:33:48). No file under `FrontEnd/src`, `FrontEnd/public`, the specs in `FrontEnd/e2e` or `BackEnd/src` changed afterwards. Evidence: [docs/qa/meridian-refinement/](docs/qa/meridian-refinement/) (lint, unit, build and full-suite logs, the keyboard and dashboard-link check with its log, and the two-theme axe script).

**Checkpoint before the refinement:** commit `507247a266f4121827d6a70b8224d2b50a0bc7ec` on `stabilization-and-ledger-redesign`, tag `meridian-before-refinement`. Local only; not pushed.

**Contents:** both refinement passes (2026-11-11 and 2026-11-12). See them with `git diff meridian-before-refinement meridian-refinement-approved`. UI only: no backend, migration, route, permission, validation or API change.

**Known at approval, carried forward**

- **Two build warnings.** Initial bundle 518.70 kB, 18.70 kB over the 500 kB budget (it was 6.50 kB over before the redesign). `sign-in.scss` 9.24 kB against the 8 kB per-component warning (new). Neither fails the build.
- **Visual checks not done.** Listed under "Not verified" below.
- **Draft validation.** A draft cannot be saved while a line has no unit (finding 15 below). Behaviour left as it is; recorded as a functional issue for the next phase.

**Review instance:** http://localhost:4201 (this working tree), backend on :8081, database `procurement_qa_redesign`. Separate from the owner's :8080 and :4200. It was restarted on 2026-11-12 and stops when the session that started it ends; restart commands are in the 2026-11-10 section below. Sign-in: the accounts in [docs/guides/global-admin-review.md](docs/guides/global-admin-review.md) with the `DEMO_USER_PASSWORD` given to the backend.

## What changed in this pass

| Area | Change |
|---|---|
| Request form | Saving shows a turning ring on the button that was pressed (`[busy]` on `appButton`); the other actions are disabled; the result is the toast |
| Attachments | Tinted dashed "add a file" control, styled progress bar, file rows with an icon tile |
| Lists | Filter bar as one contained strip on the request list, approvals inbox and purchasing inbox; page range and page number as a pill |
| Record | Shipment milestones as a timeline (reached, late, to come); purchasing, sourcing, order, logistics and receipt forms keep a 46 rem measure with the action at the start of the row |
| Mobile navigation | Drawer 18 rem wide with a rounded trailing edge, 44 px links, dimmed and softened page behind it |
| Home on phones | Shortcuts in one column; labels no longer break inside a word |
| Notifications | Escape now closes the menu when focus is still on the bell (it stayed open when the menu had nothing focusable) |
| Buttons, skeletons | Busy state; skeleton radius follows containers |
| Sign-in | On tall tablets the panel is centred; the earlier layout work is unchanged |

The interaction rules came from the `design-skills:interaction-design` skill (feedback near the trigger, every state designed, no wait without feedback).

## Verification (2026-11-12, final code)

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm test` | Passed, 245 of 245 |
| `npm run build` | Passed with two warnings: initial bundle **518.70 kB** (18.70 kB over the 500 kB budget; 136.58 kB estimated transfer); `sign-in.scss` 9.24 kB against the 8 kB per-component warning |
| Playwright, full suite, run alone | **63 of 63 passed** (24.0 min). No screenshot or other script touched the instance during the run. No source file changed after it |
| `ledger-verification` "deciding keeps focus" | Passed. Not reproduced on 2026-11-11 or 2026-11-12 (5 passes); the 2026-11-10 failure remains unexplained |
| axe WCAG A/AA, 17 screens in each theme, 1440 px | No violations (one buyer record view is skipped by the script: the buyer's "visible" list is empty) |
| Keyboard walk-through, scripted (`C:\qa\p360-redesign\verify.mjs`) | 16 checks, all passed: sign-in tab order and focus rings, sign-in by Enter, skip link, theme switch by Enter and Space, notification menu open and Escape, account menu, sidebar focus ring on ink, mobile drawer open, focus trap and Escape |
| Dashboard links | All 9 status bars clicked (the first by keyboard). Each lands on `/requests?scope=visible&status=…`, the filter shows that status, every row has it, and the list total equals the bar's count |
| Saving state, toast, dialog | Seen in both themes: one busy button and two disabled while the save is held; "Draft saved" toast; discard dialog takes focus and closes with Escape |
| Screens looked at this pass | Sign-in on phone, tablet and 200 % zoom; record with order, shipment and receipt panels (light and dark); logistics inbox (dark); form validation on a phone; mobile drawer (light and dark); notification menu; request list on desktop and phone |

**Investigated and closed**

- **Tracker looked collapsed in two full-page screenshots.** Measured in real viewports at 900 px and 3,200 px height, with and without reduced motion: 698 px wide, ten evenly spaced steps. It is a stitching artifact of full-page capture, not a layout defect; an element capture and a later full-page capture show it correctly.
- **Two accessibility specs failed on 2026-11-11.** Caused by a screenshot script signing in to the same accounts during the run. Captures now run only when the suite is not running.

**Not verified**

- Screen reader, real touch devices, native French and Arabic review, browser text-size settings other than zoom.
- Attachment upload progress and file rows with a real file: the styles changed, but no file was uploaded in this pass (the suite's attachment test passed).
- Dark theme by eye on: sourcing comparison table, supplier portal, assistant, request administration detail, approval rules editor, exchange rates, import.
- Error state and loading skeleton by eye (restyled, not captured).
- GLOBAL_ADMIN write actions and single-company ADMIN isolation (unchanged from 2026-11-11).
- The Playwright suite runs in light theme only.

## Functional findings for the next phase

Added to the fourteen below:

15. **A draft cannot be saved while a line has no unit.** Saving a new request with only a title returns "Line 1, unit: Invalid value". The form says "Save a draft at any time". Seen on the review instance; not compared with the commit before the redesign, and no styling change touches it. Decide whether drafts may have incomplete lines.
16. **Empty menus and Escape:** fixed for notifications in this pass; check other menus that can open empty.

Unchanged and still in their own documents: GLOBAL_ADMIN gaps ([review](docs/guides/global-admin-review.md)), dashboard aggregates (item 10 below), assistant provider, transport, retention and action scope ([plan](docs/product/assistant-next-phase.md)), all proposals pending the owner's decision.

## Next action

Wait for the owner's instruction to push. Then the next phase: the functional findings (1 to 16), the GLOBAL_ADMIN gaps, the dashboard aggregates and the assistant decisions, each in its document. `docs/tooling.md` and `.anchor/` stay uncommitted on purpose.

## Skills Review

- ✅ `design-skills:interaction-design`: invoked this pass; drove the busy state, the Escape fix and the states checked.
- ✅ `frontend-design:frontend-design`, `anthropic-skills:ui-ux-pro-max`, `design-skills:accessibility-audit`: loaded earlier in the session and applied; not invoked again this pass.
- ❌ Self-assessment: a `:host` rule could not reach the button's projected icon, so the ring and icon showed together; caught by capturing the saving state, not by reading the code.

---

# Handoff: Meridian refinement pass (2026-11-11)

**Checkpoint before this pass:** commit `507247a266f4121827d6a70b8224d2b50a0bc7ec` on `stabilization-and-ledger-redesign`, annotated tag `meridian-before-refinement`. Local only; not pushed. To return to it: `git checkout meridian-before-refinement`.

**This pass:** uncommitted working tree on top of that commit, left for the owner's review. UI only. No backend, migration, route, permission, validation or API change.

**Review instance:** http://localhost:4201 (this working tree) with its backend on :8081 and database `procurement_qa_redesign`. It is separate from the owner's :8080 and :4200, which were not touched. Sign-in: any demo account in [docs/guides/global-admin-review.md](docs/guides/global-admin-review.md) with the `DEMO_USER_PASSWORD` the backend was started with. Restart commands are in the section below this one.

## What changed in this pass

- **Sign-in layout.** A masthead row holds the brand, language and theme. Every vertical measure follows the viewport height, so the orbit, type and spacing shrink before the form. At 1366 × 640 the form, button and recovery link are in view with no scroll and 34 px between masthead and panel. The page grows and scrolls when content needs it. Glass panel: gradient surface, lit top edge. Stage: vignette and an outer ring.
- **Dashboard.** Lifecycle ring by phase with meaning, count and share; status bars that open the filtered list; "late on this page" count. Chart colour tokens for both themes.
- **Navigation.** Location trail in the top bar (journey group, page).
- **Dialogs.** One scrim with a soft blur, shared entrance, feature radius.
- **Home.** The request-list shortcut shows how many requests are the person's.
- **Documents.** [GLOBAL_ADMIN review](docs/guides/global-admin-review.md), [assistant next-phase plan](docs/product/assistant-next-phase.md), DESIGN.md, route checklist.
- **New translation keys (EN, FR, AR):** `shell.location`, `dashboards.pipeline.byPhase`, `dashboards.pipeline.byStatus`, `dashboards.phases.*`, `dashboards.management.lateOnPage`. Not reviewed by a native speaker.

## Verification (2026-11-11, on the refined tree)

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm test` | Passed, 245 of 245 |
| `npm run build` | Passed. Warning: initial bundle 517.41 kB, 17.41 kB over the 500 kB budget |
| Playwright, full suite | 61 of 63. The two failures (`accessibility-approvals` AR desktop, `accessibility` FR desktop) timed out while a screenshot script of mine was signing in to the same accounts and changing their saved language |
| Those two specs rerun alone | 10 of 10 passed |
| `ledger-verification` "deciding keeps focus" (intermittent on 2026-11-10) | Passed in the suite and 3 of 3 repeats. Not reproduced today; cause still not established |
| axe WCAG A/AA, 18 screens in each theme, 1440 px | No violations |
| Sign-in fit, measured (document size against viewport) | Fits without scrolling at 1366 × 640 in EN, FR and AR, both themes; 1366 × 768; 1920 × 1080; 1024 × 768; 820 × 1180; 390 × 844. Scrolls, with every control reachable, at 1280 × 600 (3 px), 360 × 640, 320 × 568 and at 200 % zoom |

The suite includes the receipt rules (partial receipt, unshipped line, two-person confirmation, service acceptance), order and split delivery, and the 20-step journey through invoice, payment and closure. All of those passed.

**Not verified**

- A full suite run with nothing else touching the accounts. The 63-of-63 claim rests on 61 in the suite plus the two reruns.
- Keyboard: only what the suite covers (skip link, drawer, focus after a decision, form error focus). No manual walk-through of the new trail, theme switch or dashboard links.
- Dashboard links: built on the list's existing `status` filter; not clicked through in a test.
- Dark theme by eye on order, logistics and receipt panels, supplier portal, assistant, dialogs and toasts.
- GLOBAL_ADMIN write actions and single-company ADMIN isolation (see the review document).
- Screen reader, real touch devices, native French and Arabic review, browser text-size settings other than zoom.

## Functionality gaps recorded for the next phase

Added to the nine below (2026-11-10):

10. **Dashboard measures that need the backend:** late count and ageing over all requests (today only on the loaded page); approvals overdue per approver; purchasing workload per buyer; delivery progress (shipments by milestone); a time series of submissions. Each needs an aggregate endpoint with company scope.
11. **Per-role dashboards:** requesters and receivers have no dashboard permission; their overview is the home page. Decide whether they get one.
12. **GLOBAL_ADMIN:** the six gaps in the review document (single-company demo ADMIN, role holders view, empty business menus, link from record to administration, administration activity view, last-GLOBAL_ADMIN protection).
13. **Assistant:** provider, transport, retention and action scope decisions in the plan.
14. **Tests:** screenshots and suites must not share demo accounts; give captures their own accounts or run them apart.

## Next action

The owner reviews http://localhost:4201 and the diff (`git diff meridian-before-refinement`). If accepted: run the full Playwright suite once more with nothing else signed in, then commit the refinement. Leave `docs/tooling.md` and `.anchor/` out.

## Skills Review

- ✅ `frontend-design:frontend-design`, `anthropic-skills:ui-ux-pro-max`: loaded earlier in this session and applied; not invoked again in this pass.
- ✅ `design-skills:design-systems`, `audit-suite:web-animation-design`, `design-skills:accessibility-audit`, `zizi-skills:i18n`: same; their rules drove the tokens, the reduced-motion fallback, the axe scan and the key-parity check.
- ❌ Refero, GetLayers, `design-skills:interaction-design`, `dataviz`: not used. Refero needs sign-in; GetLayers is not installed.
- ❌ Self-assessment: I ran screenshots against the same accounts as a running test suite, which broke two tests and mislabelled one capture. Caught by reading the screenshot, then confirmed by rerunning.

---

# Handoff: Meridian redesign, light and dark (2026-11-10)

**Candidate:** uncommitted working tree on `stabilization-and-ledger-redesign`, on top of `6869b2e`. UI only. No backend, migration, route, permission, validation or API change. Not committed, pushed or deployed.

**Running for review:** http://localhost:4201 (Angular dev server, serving this working tree) against a backend on :8081 with its own database `procurement_qa_redesign` (synthetic demo data only). Backend sources are an export of `HEAD` in `C:\qa\p360-redesign\src\BackEnd`. Demo accounts are in the README; the QA password is the `DEMO_USER_PASSWORD` the backend was started with. The owner's :8080 and :4200 were not touched. Both processes belong to the session that started them and stop with it; restart commands are under "Restart".

## What changed

- **Identity.** Ledger is replaced by Meridian ([DESIGN.md](DESIGN.md)): ink navigation, porcelain work surfaces, ultramarine for action, saffron for "waiting for you", and the ring (brand mark and lifecycle progress). Bricolage Grotesque and IBM Plex Sans replace Geist; both are self-hosted.
- **Themes.** Light and dark share every token. `ThemeService` follows the system until the person uses the switch (top bar, sign-in, guest pages); the choice is stored on the device.
- **Sign-in.** Full-screen stage with the lifecycle orbit and a glass panel. CSS and SVG with one pointer listener; still under reduced motion.
- **Navigation.** Ink sidebar, glass top bar and menus, saffron marker on the current item. One location now lights one item: sourcing, purchase orders and receipts share the request list's route and no longer all show as current.
- **Home.** Next-approval band with wait and due date, shortcuts taken from the navigation (same permissions), late approvals marked.
- **Dashboard, approvals, list, record, form.** Rebuilt or restyled as listed in [the route checklist](docs/design/redesign/route-checklist.md).
- **New shared components:** `app-brand-mark`, `app-stage-ring`, `app-theme-toggle`, `app-public-frame`.
- **New translation keys (EN, FR, AR):** `overview.shortcuts`, `dashboards.pipeline.total`, `theme.toDark`, `theme.toLight`. The Arabic and French are the developer's and have not had native review.

## Decisions

- No GSAP or Lenis. The cinematic skill's choreography and timings are implemented in CSS. Why: sign-in has no scroll scenes, and the initial bundle is already over its 500 kB warning.
- Glass only on the top bar, menus and the sign-in panel. Why: tables and forms must keep full contrast.
- A lifecycle ring next to every written status, never instead of it. Why: colour and shape must not carry meaning alone.
- The dashboard figure is the sum of the status counts the API returned. It makes no claim beyond 5,000 requests (D-05 still applies).

## Verification (2026-11-10, on this working tree)

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm test` | Passed, 245 of 245 |
| `npm run build` | Passed. Warning: initial bundle 515.47 kB, 15.47 kB over the 500 kB budget (it was 6.50 kB over before the redesign) |
| Playwright, full suite | 63 of 63 passed, on the tree before the last two small changes (one active navigation item; definition-list margin reset) |
| Playwright after those two changes: `session`, `accessibility`, `accessibility-purchasing`, `purchasing-journey`, `ledger-verification` | 32 of 33 passed. One failure: `ledger-verification` "deciding keeps focus in the queue". Repeated four times afterwards, it passed four times |
| axe WCAG A/AA, 18 screens in light and 18 in dark, 1440 px | First run: light all passed; dark found one defect (notification count text). After the fix, repeated on the final tree: no violations in either theme. Script: `C:\qa\p360-redesign\axe.mjs` |
| Horizontal overflow at 390, 820 and 1440 px, EN, FR and AR samples | None found on the screens captured |

The full suite covers sign-in and session expiry, request submission, the three-language approval journey with the e-mail link, return and rejection, purchasing assignment, line classification, sourcing, order and shipment, split delivery, partial receipt, two-person receipt confirmation, service acceptance, closure, messaging, request administration and the 20-step journey.

**Not verified**

- The focus failure above is intermittent (one failure in six runs today); its cause is not established. The earlier verification report also recorded intermittent races in this area.
- Invoice attestation recovery and payment recording were exercised only as far as the existing 20-step and receipt specs cover them; no new scenario was written.
- Dark theme was not looked at on the order, logistics and receipt panels, the administration screens, the supplier portal, the assistant, dialogs or toasts. The checklist lists them.
- Screen reader, a full keyboard walk-through, real touch devices, native French and Arabic review, runtime performance.
- The Playwright suite runs in light theme only.
- Backend tests were not run: no backend file changed.

**Release gates are unchanged:** independent QA, penetration test, MFA and restore drill are still open. This redesign does not alter the earlier no-go for pilot and production.

## Functional ideas for the next phase (not done here)

1. The shell asks for the approval and purchasing counts for every user; roles without the permission get a 403 on each navigation. Ask only when the permission is held.
2. Sourcing, Purchase orders and Receipts in the sidebar all open the request list unfiltered. Give each a filtered view (for example by stage).
3. The record's back link always reads "My requests", including for buyers who arrived from the purchasing inbox.
4. The supplier quote link takes one unit price for the whole request; quote per line.
5. The requester's home shows no due dates; the list API has no SLA field for requests in approval.
6. Dashboard: late count and ageing by stage as figures. The data is only on the paged table today.
7. Guest pages (`/p`, `/q`) had no language switch before; they have one now through the shared frame. Confirm the link e-mails should carry the recipient's language.
8. A Playwright project that runs the accessibility specs in dark theme.
9. A lint rule against raw colour values in component styles (carried over).

## Restart

```powershell
# Backend (isolated database, port 8081)
cd C:\qa\p360-redesign\src\BackEnd
$env:SERVER_PORT="8081"; $env:DEMO_USER_PASSWORD="<choose>"; $env:APP_PUBLIC_BASE_URL="http://localhost:4201"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=procurement_qa_redesign;integratedSecurity=true;encrypt=true;trustServerCertificate=true"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
# Frontend
cd FrontEnd; npx ng serve --port 4201 --proxy-config proxy.conf.8081.json
# Browser tests against it
$env:E2E_BASE_URL="http://localhost:4201"; $env:MAIL_DIR="C:\qa\p360-redesign\src\BackEnd\storage\mail"; npm run e2e
```

Running the suite overwrites `docs/qa/evidence` and `docs/design/verification`; both were restored with `git checkout --` after each run.

## Next action

The owner reviews http://localhost:4201 in both themes. If the direction is accepted: rerun the full Playwright suite once on the final tree, look at the "still to look at by eye" list in the route checklist, then commit. When committing, leave out `docs/tooling.md` and `.anchor/`.

## Skills Review

- ✅ `frontend-design:frontend-design`: set the direction and the rule against generic defaults.
- ⚠️ `anthropic-skills:ui-ux-pro-max`: its generated system (navy, Plus Jakarta Sans) was generic and was not adopted; its checklist items were used.
- ✅ `design-skills:design-systems`: token tiers and the component table in DESIGN.md.
- ✅ `audit-suite:web-animation-design`: easing and duration choices, reduced-motion handling.
- ⚠️ `anthropic-skills:cinematic-gsap-lenis-motion-system`: choreography and timings used; its GSAP and Lenis implementation was not.
- ⚠️ `design-skills:accessibility-audit`: automated layer and a partial visual layer done; keyboard walk-through and screen reader not done in this pass.
- ⚠️ `zizi-skills:i18n`: key parity (unit test), logical properties and mirrored icons checked; no pseudo-locale sweep.
- ❌ `design-skills:interaction-design`, Refero, GetLayers: not used. Refero needs sign-in; GetLayers is not installed.
- ❌ Self-assessment: arrows were double-mirrored in Arabic and white text was placed on a light red in dark; screenshots and the axe scan caught both, not review.

---

# Handoff: Remediation of independent QA findings (2026-11-09)

**Candidate:** the local commit that introduces this text, on branch `stabilization-and-ledger-redesign`, based on `36c69d8`. Find its SHA with `git log -1 --format=%H -- HANDOFF.md`. Not pushed, merged or deployed.

## Service classification control (product decision taken 2026-11-09)

The earlier hold reproduced physical goods declared as a service reaching CLOSED with no shipment or receipt, unseen by approvers (`docs/qa/remediation-36c69d8/commit-hold/`). Options A and B were chosen together (D-10):

- **Approvers see it.** The queue row, decision pane, one-tap page and record state how many service lines a request has and that they skip shipment and receipt. The count covers all lines, including those beyond the first five shown.
- **Purchasing decides it.** Before award, a buyer or purchasing manager confirms each service line or marks it as physical goods, which sends it through shipment and receipt. The decision is audited (`LINE_CLASSIFICATION_DECIDED`), and the request's author cannot make it (403 `own-classification`). Award and purchase-order creation return 409 `classification-unconfirmed` until it is recorded.
- **It cannot be undone quietly.** A line approved as goods stays goods after a purchasing return (422 `line-kind-locked`). Changing a confirmed service line's description clears the confirmation.

**Remaining risk, accepted by this decision:** a requester and a purchasing user who both misdescribe goods as a service can still skip receipt. Service acceptance stays one person's action under S7-AC-05.

Findings and original evidence: [docs/qa/independent-qa-report.md](docs/qa/independent-qa-report.md), `docs/qa/independent-qa-36c69d8/` (unchanged). Remediation evidence: `docs/qa/remediation-36c69d8/`.

## Finding-by-finding disposition

All findings are fixed by the developer and **await independent retest**. Independent QA is pending.

| ID | Sev. | Disposition | Change | Regression evidence |
|---|---|---|---|---|
| QA-001 | Major | Fixed, with the classification control above | A line approved as goods can no longer be reclassified after a purchasing return, which skips re-approval (422 `line-kind-locked`). Migration V20 adds `line_kind` (GOODS, SERVICE; existing lines GOODS). Service acceptance needs a service line and covers all of them. Goods need a shipment, a confirmed receipt and full acceptance or a shortfall decision; closure checks each line, and neither a waiver nor an old service acceptance replaces a confirmed receipt. Service lines cannot be shipped or received. Mixed orders work per line. UI: line type on the request form, and service acceptance on the record. | Backend `goodsCannotBeAccepted…`, `anOldServiceAcceptance…`, `aServiceOrder…`, `aMixedOrder…`, `anApprovedGoodsLine…`, `approversAreTold…`, `anOrderCannotBeAwarded…`, `purchasingCanSend…`, `theRequesterCannotDecide…`, `changingAConfirmed…`; browser `goods cannot…`, `a service order…`, `approvers see service lines…` |
| QA-002 | Major | Fixed | Accepted follows Received until edited. A valid lower manual value is kept. An invalid value shows a specific inline error and blocks submission. Zero lines are still left out. | Unit `Received and Accepted`; browser `a partial receipt…` |
| QA-003 | Medium | Fixed | A receipt line is tied to one delivered shipment line (DELIVERED milestone has an actual date) and limited to what remains on it. Unshipped and in-transit lines are refused (409 `line-not-shipped`). The form has one row per delivered shipment line. | Backend `anUnshippedLine…`, `repeatedReceipts…`, `receiptsWithoutAShipmentLine…`; browser `an unshipped line…`; R1c, R1b |
| QA-004 | Medium | Fixed | Status stays NON_CONFORMING while any problem is open. Each affected receipt line shows Open issue or Resolved (fully accepted, or shortfall accepted). | Backend `anEarlierProblem…`; unit |
| QA-005 | Medium | Fixed | Errors clear when invoice, confirm or close starts. | Unit |
| QA-006 | Medium | Fixed | After recording, focus moves to the next line to receive, or to the panel heading. | Unit; browser `a partial receipt…` |
| QA-007 | Low | Fixed | Close is disabled and described by the list of what is missing, except when a manager can waive with a note. The backend still returns 409. | Unit; browser `a service order…`; Z.22 |
| QA-008 | Low | Fixed | `untilText` counts calendar days. Two days away reads "in 2 days". | `relative-time.spec.ts` |

Also corrected: the `quantity-exceeded` message, which wrongly mentioned shipping for receipts.

**Older data.** Nothing historical is changed. Queries and actions: [docs/decisions/receipt-history-reconciliation.md](docs/decisions/receipt-history-reconciliation.md). Decisions D-08 and D-09 are in the decision log.

**Points for review.**
- API compatibility (reviewed): the Angular UI always sends the chosen `shipmentLineId`; the only other callers are the repository's tests. No caller change is needed. Omission works when exactly one delivered shipment line is open. Several open lines give 422 with `errors[0] = { field: "lines", code: "shipment-line-required" }`. Any future integrator must send `shipmentLineId` for split deliveries.
- The requester proposes the line type; purchasing decides it before award. After the order it cannot be changed in the application.
- Service acceptance remains one person's action (S7-AC-05); D-03 applies to goods receipts.
- `docs/qa/independent-qa-36c69d8/qa-independent.spec.ts`: the U1–U3 and R2 scenarios must be updated for shipment-line field IDs and the new rules before reuse.

## Test evidence

Run from a clean export of `36c69d8` overlaid with this working tree, outside OneDrive, confirmed identical to the workspace. New databases `procurement_rem_app` and `procurement_rem_test`; synthetic data only. Edge 157 through Playwright; Java 21; SQL Server 2025.

| Check | Result |
|---|---|
| Backend `verify -Pdb-it` | Passed, 193/193 |
| Frontend lint | Passed |
| Frontend unit tests | Passed, 245/245 |
| Production build | Passed; initial bundle 6.50 kB over budget (warning) |
| Playwright full suite | 62 of 63 passed on the first run. The one failure was the new test looking at a row hidden in the split view. After correcting only that test, `receipt-rules.spec.ts` passed 5/5 on the same application code. |
| Full journey through payment and closure, split delivery, authorization matrix | Passed, 3 tests. Three recorded mismatches are expected: Z.9, Z.14, Z.17 (correct 404 refusals). |

Evidence: `docs/qa/remediation-36c69d8/final/`.

Not run: closure journeys repeated 8×, screen reader, real touch device, native FR/AR review.

## Release gates (tracked separately)

| Gate | Status |
|---|---|
| Independent QA | Failed for `36c69d8`; **pending** on this candidate |
| Penetration test | Not passed; no evidence of completion |
| MFA verification | Not passed; no evidence of completion |
| Restore drill | Not passed; no evidence of completion |

Pilot and production remain no-go until every required gate passes with evidence.

## Next action

Return this commit to independent QA, with `docs/qa/independent-qa-report.md` as the retest list. Pushing, merging and deployment are separate decisions.

**Scenario updates for the independent reviewer** (`docs/qa/independent-qa-36c69d8/qa-independent.spec.ts`):
- **U1:** form fields are keyed by shipment line. Read `purchaseOrder.lines[n].shipmentLines[0].id` from the dossier and use it for `#rcpt-received-*`, `#rcpt-accepted-*` and `#rcpt-error-*`. U1.3 now expects a recorded partial with Accepted following Received; U1.4 expects no error.
- **U2:** U2.5 now expects Close disabled, with `#closure-why` naming what is missing. Remove the U2.6 press-Close-early step (Close cannot be clicked). U2.3 expects no stale error.
- **U3:** use the shipment-line field IDs. U3.7 expects focus on `#receipt-title` after the last line is recorded.
- **R2:** R2.5 stays NON_CONFORMING. R2.6 uses goods lines and expects 409 `no-service-lines`. For a valid SERVICE-line case, call `POST /api/v1/purchasing/requests/{id}/line-classification` as the buyer before award.
- **R1b:** R1b.1 now expects 409 `line-not-shipped`.

---

# Earlier handoff: Ledger verification (2026-11-09)

## Status

- DONE: stabilization and Ledger redesign, commits `96da606` and `c67c733`.
- DONE: verification of both and a fix batch. Results in [docs/design/verification-report.md](docs/design/verification-report.md).
- DONE: the fix batch, committed on `stabilization-and-ledger-redesign` as the commit after `c67c733` and pushed to `enterprise` `main`. Re-verify: `git log --oneline -3` and `git ls-remote enterprise main`.
- DONE: D-03 and D-07 rules, committed after `c3adb10` and pushed to `enterprise` `main`; verified as below. Re-verify: `git log --oneline -3` and `git ls-remote enterprise main`.
- NOT STARTED: independent QA and review; layout redesign of sourcing, ordering, logistics, admin and messaging.

**Readiness:** internal demo, yes. Hand-over to independent QA, yes. Pilot or production, no.

## Selected direction

Ledger (Direction A), static sign-in. Defined in [DESIGN.md](DESIGN.md). Tokens: `FrontEnd/src/styles/_tokens.scss`. Approved preview: `docs/design/prototypes/index.html`.

## Decisions

- D-01: a purchasing manager may accept a shortfall, with a reason, once every receipt is confirmed. Why: a rejected unit otherwise stranded the request.
- D-02: purchasing marks an invoice paid; closure does not require payment. Why: matches sprint-7 closure rules.
- An invoice may be recorded without attestation and attested later. Why: S7-AC-06 makes it optional, while closure requires it.
- D-03: the person who recorded a receipt cannot confirm it. Why: one person should not both record and accept goods. Cost: a site needs a second eligible person.
- D-07: expired quotes cannot be awarded, and a quantity different from the request needs a recorded reason. Why: an award should not commit beyond what was approved without an explanation.
- Quotes stay out of the approval pane. Why: they come after approval in the real workflow.

## Changes

Branch `stabilization-and-ledger-redesign`. Commits `96da606`, `c67c733`, on `1b59c4e`.
Remotes: `enterprise` (private; its `main` is at `c67c733`) and `origin` (same branch at `c67c733`; its `main` lacks this work).

**Changed routes (no route was added or removed):**

| Route | Change |
|---|---|
| `/overview` | Next-approval band, compact lists |
| `/approvals` | Queue beside the decision from 64 rem |
| `/requests`, `/requests/:id` | Restyled; "Created" column fixed; rebuilt receipt panel |
| `/purchasing` | Secondary filters folded |
| `/sign-in` | Static restyle |

**API additions under `/api/v1/receipts/requests/{id}`:** `shortfall-acceptance`, `invoice/paid`, `invoice/match-attested`. Request list adds `createdAt`. Dossier adds `shortfallAccepted` and `purchaseOrder` with `receivable` per line.

**Migrations:** V18 and V19, applied only to `procurement_test`. `procurement_dev` migrates at next start.

## Verification

On `c3adb10` plus the working tree (2026-11-09), detail in the report:

- Passed: lint; unit 223/223; build; Playwright 58/58; closure journeys repeated 8/8; backend `verify -Pdb-it` 177/177 (run before the last change, which was one frontend line).
- UNVERIFIED: screen reader; real touch devices; runtime performance against the baseline; native FR and AR review; independent QA.
- Two fixes have no automated test: the last-page redirect after a decision, and the quote total overflow check.

Reports and screenshots: `docs/design/verification-report.md`, `docs/design/verification/`, `docs/design/design-qa.md`, `docs/design/after/`, `docs/design/baseline/`, `docs/reviews/stabilization-review.md`.

## Blockers

- Pilot needs independent QA, penetration test, MFA and restore drill.

## Open

D-05, D-06, PERF-04, and low items R-1 to R-6, all in the verification report.

## Next action

Hand the head of `enterprise` `main` to independent QA, using `docs/design/verification-report.md` as the starting point.

When committing, do not add `docs/tooling.md` (the owner's preflight) or `.anchor/` (a plugin's session file).

## Skills Review

- ✅ `supermatt:code-review`: three axes found four P1s in the receipt form that 171 passing backend tests had missed. Host-only; no external reviewer configured.
- ✅ `design-superpowers:design-review`: run at L1 by reading tokens and screenshots. Confirmed RTL, density and focus token conformance.
- ⚠️ `design-skills:accessibility-audit`: automated and keyboard layers done; screen reader layer not run.
- ✅ `superpowers:verification-before-completion`: repeated runs exposed two intermittent races one pass would have hidden.
- ❌ Earlier self-assessment: the receipt form was reported complete after happy-path tests only. Partial and split deliveries were broken.

**Environment findings**

- Automated checks: no CI gate runs Playwright. Propose a workflow that runs it on pull requests.
- Automated checks: the 20-step journey overwrites tracked evidence on every run. Propose writing to an ignored folder unless a flag is set.
- Standards: raw hex and `rgb()` in component styles are caught only in review. Propose a stylelint rule.
