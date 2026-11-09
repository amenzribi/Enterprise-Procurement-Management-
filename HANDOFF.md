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
