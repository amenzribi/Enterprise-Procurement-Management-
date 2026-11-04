# Request-to-closure 20-step verification

**Label:** developer-executed verification  
**Not** user UAT · **Not** independent QA acceptance

## Environment

| Item | Value |
|---|---|
| Tested commit | `a9a8740` (+ local uncommitted journey/e2e readiness fixes from this pass) |
| Execution date | 2026-11-04 (Africa/Tunis) |
| Frontend | http://localhost:4200 |
| Backend | http://localhost:8080 (`dev,demo`) |
| Database | `procurement_dev` (SQL Server) |
| Flyway tip | **V17** `admin company scope and request admin` (success=1) |
| Mail | Local file mode only (no real supplier contact) |
| UAT-01 preserved | `DEMO-TN-PLT-PR-2026-000490` remains **CLOSED** |

## Successful end-to-end dossier (clean rerun)

| Artefact | Reference |
|---|---|
| Title | `AUTO-UAT — Pump seals — 20261104115213` |
| Request | `DEMO-TN-PLT-PR-2026-000674` (id 1788) — **CLOSED** |
| Purchase order | `DEMO-CPA-PO-2026-000030` — **CONFIRMED** — **250.0000 TND** |
| Shipment | `DEMO-CPA-SH-2026-000030` — **DELIVERED** |
| Goods receipt | `DEMO-TN-PLT-GR-2026-000019` — **CONFIRMED** |
| Invoice | `INV-AUTO-UAT-20261104115213` — **250.0000 TND** — payment **PENDING** — match attested |
| Evidence folder | `docs/qa/evidence/01-sami-draft.png` … `20-sami-reopen-closed.png` |

Approval rule used (200 TND): **Sfax up to 5 000 TND (Demo)** — department manager only.  
Karim absences: all cancelled beforehand (no active absence); Karim approved in UI.

## 20-step results

| Step | Account | Page/menu | Action | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|---|---|
| 1 | Sami `requester.tn@demo.local` | Purchase requests → New | Save draft: spare parts, seal P-204, 10×20 TND=200, justification, Sfax fictional address | Draft saved, no company PR number | Draft on `/requests/{id}/edit`; total TND 200; no `DEMO-TN-PLT-PR-…` | **PASS** | `01-sami-draft.png` |
| 2 | Sami | Edit → Submit | Submit via preview | Unique PR, IN_APPROVAL / Submitted | `DEMO-TN-PLT-PR-2026-000674`, Submitted | **PASS** | `02-sami-submitted.png` |
| 3 | Karim `manager.tn@demo.local` | Approvals / dossier | Approve | Purchasing review next | Approved; purchasing queue reachable | **PASS** | `03-karim-approved.png` |
| 4 | Mehdi `purchasing.manager@demo.local` | Purchasing → dossier | Assign Ines | Assignment + company consistency | Assigned Ines Jaziri (Demo); requesting TN / buying CPA | **PASS** | `04-mehdi-*.png` |
| 5 | Ines `buyer@demo.local` | Dossier | Accept assignment | Ownership / next action | Accepted; sourcing available | **PASS** | `05-ines-accepted.png` |
| 6 | Ines | Sourcing | Quick RFQ + DEMO-SKF | RFQ linked | Quick RFQ created for DEMO-SKF | **PASS** | `06-ines-rfq.png` |
| 7 | Ines | Sourcing | Quote 25 TND/unit | 10×25=250 TND comparison | Quote + comparison shows 250 TND | **PASS** | `07-ines-quote-250.png` |
| 8 | Ines | Sourcing | Tech + commercial validation | Both decisions by Ines (demo) | Both validations recorded (demo allows buyer to do both) | **PASS** | `08-ines-validated.png` |
| 9 | Ines | Ordering | Award DEMO-SKF | Award snapshot 10×25 TND | Awarded with reason | **PASS** | `09-ines-award.png` |
| 10 | Ines | Ordering | Create PO Net 30 / DAP / fictional address | Unique PO, 250 TND | `DEMO-CPA-PO-2026-000030` draft 250 TND | **PASS** | `10-ines-po-draft.png` |
| 11 | Ines | Ordering | Issue PO | ISSUED; no auto supplier confirm | Issued; confirm form still required | **PASS** | `11-ines-po-issued.png` |
| 12 | Ines | Ordering | Record supplier confirmation | CONFIRMED (in-app, not portal) | Buyer-recorded confirmation | **PASS** | `12-ines-po-confirmed.png` |
| 13 | Ines | Logistics | Create shipment 10 units | Linked to PO/lines | `DEMO-CPA-SH-2026-000030` created | **PASS** | `13-ines-shipment.png` |
| 14 | Ines | Logistics | Milestones → DELIVERED + doc ref | Delivery ≠ auto receipt/close | Shipment DELIVERED; request user status Received; open for receipt | **PASS** | `14-ines-delivered.png` |
| 15 | Omar `receiver.tn@demo.local` | Receipts / dossier | Record 10/10/0 OK | Receipt persisted | GR `…000019` RECORDED then path continues | **PASS** | `15-omar-receipt.png` |
| 16 | Sami | Dossier | Confirm acceptance | Saved as Sami | Confirm acceptance succeeded as requester | **PASS** | `16-sami-acceptance.png` |
| 17 | Ines | Dossier | Invoice 250 TND + match attest | Persists | Invoice 250 TND attested | **PASS** | `17-18-ines-invoice-pending.png` |
| 18 | Ines | Invoice form | Payment PENDING | Default PENDING; no bank txn | `paymentStatus=PENDING` set on record (no separate payment button) | **PASS** | same |
| 19 | Sami | Dossier | Score 5, empty waiver, Close | CLOSED with PENDING OK | CLOSED; policy allows close with PENDING | **PASS** | `19-sami-closed.png` |
| 20 | Sami | Leave + reopen by id | Tracker/history/invoice/CLOSED | Remains closed after reload | CLOSED + invoice 250 TND visible | **PASS** | `20-sami-reopen-closed.png` |

**Journey completed through the UI:** yes (Playwright against live `:4200` → `:8080`, same business accounts and menus).

## Initial failures (before clean rerun)

Reported separately from the clean pass:

1. **Arabic UI after Sami sign-in** — preferred language AR; Save draft EN label missing. Fixed by forcing `lang=en` after navigation.
2. **Draft URL** — save stays on `/requests/{id}/edit`, not dossier. Assertion updated.
3. **Logistics milestones** — loop treated temporarily `busy()`-disabled buttons as complete; shipment left IN_PROGRESS. Fixed by waiting for enable + count decrease.
4. **Delivered readiness** — user-facing badge is **Received** (not “Delivered”). Wait condition updated (also root cause of full-suite ordering-logistics flake).
5. **Receipt visibility check** — asserting custom element `app-receipt-actions` was flaky; assert **Record goods receipt** button instead.

Partial AUTO-UAT drafts left in DB from failed attempts (`…000670`–`…000673`, plus draft `1783`) — not mass-cleaned (per instructions).

## Defects / fixes

| Issue | Root cause | Fix | Retest |
|---|---|---|---|
| Full-suite `ordering-logistics` receipt missing | Omar opened before user status Received; brittle host visibility | Wait for Received + record button; milestone enable/count waits; Sami closes (author) | Covered by 20-step PASS + updated e2e |
| Journey language / draft / logistics / receipt waits | As above | `request-to-closure-20-step.spec.ts` + `ordering-logistics-journey.spec.ts` | Clean 20-step **1/1 PASS** (~1.3 min) |

## Automated regression (this pass)

Run **sequentially** after UI journey (mutating suites not concurrent with each other):

| Suite | Result | Notes |
|---|---|---|
| Playwright `request-to-closure-20-step` (isolated clean) | **1 passed** (~1.3 min) | After readiness fixes |
| Backend `mvnw verify -Pdb-it` | **156 passed**, 0 fail/skip · **BUILD SUCCESS** | `procurement_test` |
| Frontend `npm run lint` | **PASS** | All files |
| Frontend `npm test` | **28 files / 212 tests passed** | |
| Frontend `npm run build` | **PASS** | Budget warning +4.87 kB (pre-existing) |
| Playwright **full** suite (post-verify) | **38 passed**, 0 failed (~10.7 min) | Includes ordering-logistics + 20-step |

Full-suite vs earlier isolated: previous full-suite ordering-logistics receipt flake **did not recur** after readiness fixes (38/38 clean). No separate isolated rerun required for that failure in this pass.

## Unresolved

- User UAT / independent QA acceptance: **pending Mohamed**.
- Historical USD approval-link IT flake under a prior full `verify` (isolated OK) — not reproduced in this `verify -Pdb-it` (156/156).
