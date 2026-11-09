# Independent QA report: Procurement 360 release candidate

Date: 2026-11-09. QA verification only; no product code was changed.

## Result

**Independent QA: FAIL for candidate `36c69d8`.** Two Major defects are open (QA-001, QA-002). Under the delivery rules, a sprint is not complete while a Major defect is open.

The previously reported receipt and invoice defects are fixed at the server and in the main paths: zero-unit lines, split deliveries, cumulative quantities, and recovery after an unchecked attestation all work. Authorization held in every case tested.

## Tested code

| Item | Value |
|---|---|
| Tested commit | `36c69d8dafdcd69b8cf326ab41b460903876c812`, branch `stabilization-and-ledger-redesign`, exported with `git archive` |
| Commit cited by `docs/design/verification-report.md` | `c3adb10` plus uncommitted changes (latest update); `c67c733` plus uncommitted changes (original section) |
| Match | **No exact match can be shown** (see below) |
| Working tree | `docs/tooling.md` modified and `.anchor/` untracked. Neither is product code. Tracked `BackEnd/` and `FrontEnd/` files are identical to `36c69d8`. |
| Remote | `enterprise/main` is at `36c69d8`, the head named for QA hand-over. `origin`'s branch is still at `c67c733`. |

**Discrepancy.** The report's latest fingerprint is `git diff HEAD -- BackEnd FrontEnd` SHA-256 `9212AE27BBE93D05…17A33E`, taken on `c3adb10` plus uncommitted changes. The diff `c3adb10..36c69d8` for the same paths hashes to `36746FE3D75CD47E…E20301`. The original output was not retained and the capture method may differ, so this shows only that equivalence cannot be proven, not that the code differs.

To remove the doubt, every automated check was re-run here on `36c69d8` instead of relying on the report's results. The report's own 177/177 backend run also predates its last frontend fix; the run below does not.

## Environment

| Item | Value |
|---|---|
| Machine | Local Windows 11 Pro |
| Code | Clean export in a session scratch folder, outside OneDrive; no git-ignored local configuration |
| Runtime | Java 21.0.12.1, Node 24.20.0, Spring profiles `dev,demo`, `npm start` proxy on :4200 to :8080 |
| Database | SQL Server 2025 (17.0.1000.7), new databases `procurement_qa_36c69d8` (application) and `procurement_qa36c_test` (integration tests). `procurement_dev` and `procurement_test` were not used. |
| Browser | Microsoft Edge 157.0.4341.42 through Playwright |
| Data | Synthetic demo accounts and requests only; demo password generated for this instance |

## Automated checks

| Check | Expected | Actual | Status |
|---|---|---|---|
| Backend `verify -Pdb-it` on SQL Server | All pass | 177/177, BUILD SUCCESS | Pass |
| Frontend lint | Clean | All files pass | Pass |
| Frontend unit tests | All pass | 223/223 | Pass |
| Production build | Succeeds | Succeeds; 506.30 kB initial, 6.30 kB over budget (warning, as reported) | Pass |
| Playwright full suite, including axe and overflow matrices | All pass | 58/58 in 18.5 min | Pass |
| Closure journeys repeated 8 times | 8/8 | Not repeated; the 20-step journey passed once in the suite and once with payment added | Not run |

## Scenarios

Check IDs refer to `checks.jsonl` in the evidence folder.

### Receipt and invoice (priority)

| ID | Scenario | Expected | Actual | Status |
|---|---|---|---|---|
| R1c.1–2 | First split shipment A=6 of 10; receipt of 6; 1 more | Receipt accepted; over-receipt 409 | As expected | Pass |
| R1c.3–4 | Second shipment delivered | Status stays partial; receivable [4, 4] | As expected | Pass |
| R1c.5–7 | Two more receipts; over-receipt after completion | Nothing left; 409 | As expected | Pass |
| R1c.8–10 | Recorder confirms; others confirm out of order | 403; accepted only after all are confirmed | As expected | Pass |
| R1c.11 | Cumulative received | [10, 4] | [10, 4] | Pass |
| U1.1–2 | UI: line B entered as 0, line A received | Receipt holds A only; B stays open in full | As expected | Pass |
| R2.1–3 | API: explicit zero line | 422, nothing saved, no stack trace (the UI leaves such lines out) | As expected | Pass |
| U1.3–4 | UI: partial entry, changing only Received to 1 of 3 | Partial receipt recorded, or the form keeps Accepted in step | 422 with a generic message; nothing recorded | **Fail: QA-002** |
| U1.5–6 | UI: Accepted corrected, retried, then remainder | Recovers; 3 receipts; form shows 1 / 3; nothing left | As expected | Pass |
| U1.7–8 | UI: recorder has no confirm button; requester confirms | ACCEPTED | As expected | Pass |
| R1b.1–2 | Receipt on an unshipped line, then the remaining 3 shipped | 3 still receivable | 2 receivable | **Fail: QA-003** |
| R2.4–5 | Rejected unit confirmed, then a clean receipt confirmed | Stays NON_CONFORMING | PARTIALLY_RECEIVED | **Fail: QA-004** |
| R2.6, R2b.1–2 | Goods never shipped: requester records service acceptance, then invoice and close | Refused, or at least not closable | 200, ACCEPTED, then CLOSED with 0 receipts | **Fail: QA-001** |
| U2.1–2 | UI: blank invoice amount; attestation unchecked by default | Validation message; nothing recorded | As expected | Pass |
| U2.3 | UI: corrected and saved | Error disappears | Error stays beside the success toast | **Fail: QA-005** |
| U2.4 | UI: closure names the missing attestation | Named | As expected | Pass |
| U2.5–6 | UI: Close while attestation is missing | Not offered | Enabled; pressing it shows a translated refusal | **Fail: QA-007** |
| U2.7–9 | UI: attest, mark paid, close | PAID, then CLOSED | As expected | Pass |

Business rules used: S7-AC-01 to S7-AC-08 in `docs/product/sprint-7.md`; D-01, D-02, D-03 and the optional attestation decision in `HANDOFF.md`.

### Complete journey

| Scenario | Expected | Actual | Status |
|---|---|---|---|
| UI, EN: requester, manager, purchasing manager, buyer, receiver, requester, buyer, requester. Draft, submit, approve, assign, accept, quote, validate, award, PO, issue, confirm, shipment, delivery, receipt, acceptance, invoice 250 TND with attestation, **mark paid**, close, reload | Reaches CLOSED and persists | As expected, with a screenshot at each step (`run2/journey/`) | Pass |

### Authorization (API)

| ID | Scenario | Expected | Actual | Status |
|---|---|---|---|---|
| Z.1 | Anonymous read | 401 | 401 | Pass |
| Z.2–4, 8, 13, 21, 30 | Other-company users read, ship, receive, confirm, mark paid, approve | 404 | 404 | Pass |
| Z.5–7, 10–12, 15–16, 19–20, 23–24, 29, 33–34 | Wrong role in scope: shipment, receipt, confirm, invoice, attest, pay, close, approve own request, queue, admin | Refused | 403 (Z.6: 404) | Pass |
| Z.9, 14, 17 | Company admin, global admin and budget approver on a receipt or invoice | Refused | 404 | Pass |
| Z.22, 26 | Close while unattested; attest twice | 409 | 409 | Pass |
| Z.18, 25, 27, 28 | Permitted buyer and manager actions; payment after closure stays CLOSED (D-02) | 200 | 200 | Pass |
| Z.31 | Same-company peer reads a colleague's request | 404 | 404 | Pass |
| Z.32 | Department-scoped manager reads another department's request | 404 | 404 | Pass |
| Direct URLs (suite) | Anonymous, requester, buyer and other-company user on restricted routes | Sign-in, refused or not found | Passed in `ledger-verification.spec.ts` | Pass |

Z.9, Z.14 and Z.17 were first written as 403. A 404 is the correct refusal for a request outside the user's scope (ADR-007). The harness expectation was wrong, not the product.

### UI against the approved design

Compared `DESIGN.md` and the prototype (`docs/design/prototypes/index.html`, Ledger) with screenshots from this run.

| Check | Expected | Actual | Status |
|---|---|---|---|
| Pending approvals, desktop | Queue beside decision; dense rows; one primary action | Matches. The prototype's quote table is intentionally absent (`HANDOFF.md`). R-3 clipped search placeholder confirmed. | Pass |
| Pending approvals, 375 px | One column; each row opens the record | Matches, EN and AR | Pass |
| Procurement tables, desktop and mobile | Hairlines, status dot and word, no sideways scroll | Matches; EN and FR reviewed | Pass |
| Record details | Prototype: next action in a side column | Same tokens, but the decision band sits in the main column and the page carries more sections | Pass, with deviation noted for design owner |
| Keyboard: tab order, ring, Enter on receipt form (U3.4–6) | Logical; ring visible; submits | As expected | Pass |
| Keyboard: approvals, drawer, dialog, skip link, filters (suite) | Focus held and restored | Passed | Pass |
| Focus after a receipt is recorded (U3.7) | Stays in context | Falls to `<body>` | **Fail: QA-006** |
| Arabic RTL at 375 px (U3.1–3) | `dir=rtl`, no overflow, no raw keys | As expected | Pass |
| French (U3.8, suite) | No raw keys | As expected | Pass |
| axe WCAG A/AA, 2.2 AA; overflow at 375–1920 px (suite) | No violations | Passed | Pass |
| Screen reader | | Not available in this environment | Blocked |
| Real touch device | | Emulated touch only | Blocked |
| Native French and Arabic language review | | Needs a native reviewer | Blocked |

## Defects

Severity is QA's assessment; the owner may challenge it with a reason.

### QA-001: Major. One person can accept goods that were never shipped, and the request can close

Service acceptance has no status check and does not require a second person. It therefore bypasses receipt, delivery and the D-03 rule. It is available through the API only. S7-AC-05 allows it after PO confirmation because there is no `SERVICE` purchase type yet, so the rule needs a decision as well as a code change.

**Steps**
1. Take a goods request to a confirmed PO. Do not create a shipment.
2. As requester: `POST /api/v1/receipts/requests/{id}/service-acceptance` with `requestVersion` and `comment`.
3. As buyer: record the invoice with attestation.
4. As requester: `POST /api/v1/receipts/requests/{id}/close`.

**Expected:** refused for goods, or a second person required.
**Actual:** 200 at each step; status CLOSED; 0 receipts.
**Evidence:** `run2/checks.jsonl` R2b.1–2; `run1/checks.jsonl` R2.6; `ReceiptService.java:231-256`.
**Next action:** product owner decides how service purchases are identified and whether D-03 applies. Developer enforces it. QA retests R2b.

### QA-002: Major. A normal partial receipt fails with a message that does not say why

Received and Accepted are prefilled with the full quantity. Lowering only Received makes Accepted larger, and the server returns 422. This is the most common partial-delivery action. Nothing is lost and the receiver can recover, but only by working out that a second field must change.

**Steps**
1. Sign in as the receiver and open a delivered request with a line of quantity 3.
2. Change Received to 1 and leave Accepted at 3.
3. Press Record goods receipt.

**Expected:** a partial receipt is recorded, or Accepted follows Received, or the message names the field.
**Actual:** "Some information is missing or incorrect." at the top of the panel, nothing recorded.
**Evidence:** `run1/U1-03-partial-only-received-edited.png`, U1.3–4; `receipt-actions.ts:274-276, 455-460`.
**Next action:** developer keeps Accepted in step until it is edited by hand, and shows a field-level message. QA retests U1.3.

### QA-003: Medium. A unit can become impossible to receive after an early receipt

A line with no shipment can be received up to its PO quantity (S7-AC-02). Once any shipment exists for that line, the limit becomes the shipped quantity.

**Steps**
1. PO: A=2, B=4. Ship and deliver A only.
2. Receive B=1.
3. Ship and deliver the remaining B=3.

**Expected:** 3 receivable. **Actual:** 2 receivable, leaving one physical unit that cannot be received.
**Workaround:** record a shipment for the full 4, or use shortfall acceptance.
**Evidence:** `run2/checks.jsonl` R1b.2; `ReceiptService.java:144-149`.
**Next action:** product owner decides whether unshipped lines may be received while the request has shipments. Developer aligns the limit. QA retests R1b.

### QA-004: Medium. A later clean confirmation hides an earlier non-conformity

**Steps**
1. Record receipt 1 with a rejected unit and receipt 2 clean.
2. Confirm receipt 1: status is NON_CONFORMING.
3. Confirm receipt 2.

**Expected:** remains NON_CONFORMING (S7-AC-04). **Actual:** PARTIALLY_RECEIVED. The receipt lines still show the rejection.
**Evidence:** `run1/checks.jsonl` R2.5; `ReceiptService.java:215-222`, which evaluates only the receipt being confirmed.
**Next action:** developer evaluates all confirmed receipts. QA retests R2.4–5.

### QA-005: Medium. An old error stays on screen after a successful action

**Steps**
1. As buyer, on an accepted request, clear the invoice amount and press Record invoice.
2. Enter an amount and press Record invoice again.

**Expected:** the error disappears. **Actual:** "Some information is missing or incorrect." remains beside "Invoice recorded".
**Evidence:** `run1/U2-01-saved-unattested.png`, U2.3. `saveInvoice`, `confirm` and `close` do not clear the error (`receipt-actions.ts:337, 352, 429`); only the invoice path was reproduced.
**Next action:** developer clears the error at the start of each action. QA retests U2.3, plus the confirm and close paths.

### QA-006: Medium. Keyboard focus is lost after a receipt is recorded

**Steps**
1. Sign in as the receiver (375 px, Arabic used) and open a delivered request with one line.
2. Tab to Record goods receipt and press Enter.

**Expected:** focus moves to a sensible place, such as the new receipt or panel heading. **Actual:** focus falls to `<body>` when the form is removed.
**Evidence:** U3.7, `run1/U3-02-ar-375-recorded.png`.
**Next action:** developer moves focus after the form closes. QA retests U3.7.

### QA-007: Low. Close is offered when closure cannot succeed

**Steps:** as purchasing manager, open an invoiced request with no attestation. The missing item is listed, but Close is enabled; pressing it shows "Closure conditions are not met yet."
**Expected:** `DESIGN.md` principle 5, never imply a path that does not work.
**Evidence:** `run1/U2-02-close-before-attest.png`, `run1/U2-03-close-too-early.png`, U2.5–6.
**Next action:** developer disables Close until ready, except where a manager's waiver applies. QA retests U2.5.

### QA-008: Low. A due date two days away is shown as "tomorrow"

**Steps:** on 2026-11-09, open a request whose approval is due 11 Nov 2026 at 11:12.
**Expected:** "in 2 days" or the date alone. **Actual:** "Due tomorrow (11 Nov 2026, 11:12:00)".
**Evidence:** `design/en-record-1280.png`.
**Next action:** developer compares calendar days. QA retests with a due date two calendar days out.

### Observations (not raised as defects)

- Approval stage names such as "Department manager" appear in English on Arabic and French screens. They are configured data (`approval_task.stage_name`), not missing translation keys. Product owner to decide whether stage names need translations.
- The receipt `quantity-exceeded` message reads "Shipped quantity would exceed the purchase-order line" (`en.json`). It would be inaccurate for a receipt; the receipt form was not driven into this state.
- The report's open items D-05, D-06, PERF-04 and R-1 to R-6 were not re-tested, except R-3, which was seen.
- Concurrency beyond the backend suite, and load, were not tested.

## Retest

Retest against the next commit, with a clean export and new databases. Copy `qa-independent.spec.ts` and `qa-journey.spec.ts` into `FrontEnd/e2e/`, then set `QA_EVIDENCE` and `DEMO_USER_PASSWORD`. The two QA databases were left in place for comparison and can be dropped when no longer needed.

## Evidence

In `docs/qa/independent-qa-36c69d8/`:

| Path | Content |
|---|---|
| `run1/` | First pass: R2, U1–U3 checks and screenshots. Its R1 and partial Z rows are superseded by run 2. |
| `run2/` | R1c, R1b, R2b, full Z matrix, paid journey screenshots |
| `design/` | Prototype and application screenshots |
| `*.spec.ts` | Scenario source |
| `*.tail.log` | Ends of the check logs |

## Release gates

These are tracked separately. The QA result above does not decide any other gate.

| Gate | Status | Evidence |
|---|---|---|
| Independent QA | **Failed** for `36c69d8` | This report |
| Penetration test | **Not passed; no evidence of completion** | None provided |
| MFA verification | **Not passed; no evidence of completion** | None provided |
| Restore drill | **Not passed; no evidence of completion** | None provided |

## Release decision

Separate from the QA result: **pilot and production remain no-go** until every required gate has passed with evidence.
