# Core Journey — Acceptance & Regression Scenarios

> **Status: DESIGNED, NOT EXECUTED.** Forward-looking; refined in each sprint's test plan once the
> sprint scope, API and screens exist. Traced to the specification (§) and user stories (US-xx, §27).

Journey: **request → submission → approval / revision → purchasing assignment → sourcing & quotation
comparison → purchase order → receipt & exceptions** (Parcours A, §3.8).

## Reference test cast (multi-company by default)

| Actor | Company | Role (§20) |
|---|---|---|
| Amira (assistant) | Subsidiary "Usine Sfax" (TND) | Requester — enters on behalf of |
| Karim | Usine Sfax, Maintenance | Requester (demandeur) |
| Leila | Usine Sfax, Maintenance | Department manager |
| Sami | Usine Sfax | Budget controller |
| Nadia | Usine Sfax | Management approver (above threshold) |
| Youssef | Group central purchasing (EUR/TND) | Purchasing manager |
| Hela | Central purchasing | International buyer |
| Omar | Central purchasing | Local buyer |
| Rim | Usine Sfax | Technical validator |
| Fares | Usine Sfax | Warehouse / receiver |
| Mehdi | Subsidiary "BTP Sousse" | Requester (other company — isolation checks) |
| Admin | Group | Administrator |

## J1 — Request creation (M1, §6, US-01)

| ID | Scenario | Expected |
|---|---|---|
| J1.1 | Karim creates a draft with 3 lines (description, qty, unit, estimated price, required date, delivery place) and 1 attachment per request + 1 per line | Saved as **Brouillon**; no number consumed or provisional number clearly distinct (rule to be confirmed); attachments linked at correct level. |
| J1.2 | Amira creates a request **on behalf of** Karim for beneficiary "Ligne 2" | "Saisi par" = Amira, "Demandeur" = Karim, "Bénéficiaire" = Ligne 2, requesting & charged company = Usine Sfax (§3.2). |
| J1.3 | Submit valid draft | Status **Demande créée → En validation**; unique number generated (format agreed); audit event written. |
| J1.4 | 20 parallel submissions | 20 distinct numbers, no gaps rule as documented, no duplicates. |
| J1.5 | Submit with missing mandatory fields for the category | Rejected with localised field-level errors (EN/FR). |
| J1.6 | Duplicate / near-duplicate request already open (§6.3) | Warning shown, user can proceed with justification (not hard block unless configured). |
| J1.7 | Estimated price far from history (§6.3) | Warning flagged (when price history exists — later sprint). |
| J1.8 | Mehdi (other company) tries to open Karim's request by URL/ID | 404/403, no data leaked. |
| J1.9 | Edit after submission | Not allowed except through "return for revision". |
| J1.10 | Money: line 3 × 1 234.5675 TND | Stored and displayed with TND 3-decimal rule; total consistent. |

## J1-AI — Free-text assisted entry (§6.2, US-02) — bounded increment

| ID | Scenario | Expected |
|---|---|---|
| JAI.1 | "10 roulements SKF 6205 pour la ligne 2 de l'usine, urgent" | Proposal: 1 line, qty 10, item "SKF 6205 bearing", priority urgent, beneficiary Ligne 2 — shown as **proposal to verify**, not saved until user confirms. |
| JAI.2 | Each extracted value shows source span + confidence | Present (§17 guardrails). |
| JAI.3 | Labelled sample set (≥ 50 FR/EN sentences) | Field-level precision/recall reported; acceptance threshold agreed before sprint (e.g. qty/unit ≥ 90% exact). |
| JAI.4 | AI service down | Manual form fully usable; clear message. |

## J2 — Approval circuit (M2, §7, US-03, US-04)

| ID | Scenario | Expected |
|---|---|---|
| J2.1 | Amount below threshold | Circuit: department manager → budget control; management step skipped. |
| J2.2 | Amount above threshold (e.g. > 50 000 TND) | Management step added. Threshold evaluated in **request currency converted at recorded rate**. |
| J2.3 | Leila approves | Moves to Sami; audit: user, timestamp, comment, old/new status. |
| J2.4 | Leila **rejects** without reason | Blocked — reason mandatory. With reason → **Rejetée**, terminal, requester notified. |
| J2.5 | Leila **returns for revision** | Status **À réviser**, back to Karim; Karim edits and resubmits; circuit restarts at the rule-defined step; history keeps both rounds. |
| J2.6 | Leila **requests information** | Request stays with Leila; question visible to Karim; SLA suspended per rule; answer returns it to Leila. |
| J2.7 | Leila **delegates** to an authorised delegate within dated window | Delegate can act; audit shows "on behalf of Leila". Delegation to unauthorised user or outside window → refused. |
| J2.8 | Leila and Sami act at the same moment / Leila double-clicks Approve | Exactly one transition; second gets 409/no-op; no double step. |
| J2.9 | Non-approver (Karim, Mehdi, Hela) calls approve API | 403. |
| J2.10 | Approver of another company | Never sees the request in queue. |
| J2.11 | Admin changes the circuit while a request is in flight | In-flight request keeps the circuit **version** it started with (§7.1 versioned). |
| J2.12 | Approval from phone (360px) | All five actions usable; reason dialogs usable with on-screen keyboard. |
| J2.13 | Overdue step | Reminder then escalation per configured delay (when SLA implemented). |

## J3 — Central purchasing inbox & assignment (M3, §8, US-05, US-15)

| ID | Scenario | Expected |
|---|---|---|
| J3.1 | Validated request appears in central inbox | Status **En revue achats**; visible to Youssef; not visible to buyers until assigned (per §20). |
| J3.2 | Filters (company, country, category, priority, amount, status, requester, required date, purchase type, buyer) | Each filter correct; combined filters; empty state localised; pagination stable. |
| J3.3 | Youssef returns for clarification | Back to requester with question; audit. |
| J3.4 | Assignment rule: international → Hela, local → Omar | Status **Affectée à un acheteur**; ownership + "since" shown. |
| J3.5 | Hela rejects assignment with reason | Back to Youssef's queue; reason recorded. |
| J3.6 | Purchasing mode chosen: direct / centralised / on behalf of subsidiary | Buying company set accordingly (§3.7); changeable at assignment only by authorised role. |
| J3.7 | Group similar requests from two subsidiaries into one RFQ | Both requests linked; each keeps own number/status; isolation preserved for requesters. |

## J4 — Sourcing & quotation comparison (M4, M5, §9, §10, US-06, US-07)

| ID | Scenario | Expected |
|---|---|---|
| J4.1 | Hela creates RFQ inviting 3 suppliers | Status **Consultation en cours**. |
| J4.2 | Enter 3 offers: EUR (CIF), USD (FOB), TND (DAP) with validity, lead time, payment terms | Saved; status **Offres reçues**. |
| J4.3 | Comparison table | Converted to comparison currency at **recorded** rate; landed cost (price + transport + estimated duties) per §9.2; best price / best lead time highlighted; non-compliant offer flagged. |
| J4.4 | Offer expired (validity date passed) | Flagged, cannot be selected without explicit override + reason. |
| J4.5 | Rim technical validation: rejects one offer with reason | Recorded; offer excluded; Rim sees only submitted offers (§20). |
| J4.6 | Commercial validation (finance) then management validation above threshold / LC / advance | Required steps enforced. |
| J4.7 | Requester Karim views request | Sees status; **supplier prices visible only per rights** (§20). |
| J4.8 | Decision recorded with justification; readable decision history | Ordered timeline, EN/FR. |
| J4.9 | Rounding: 3 lines × unit prices with 4 decimals, conversion USD→TND | Comparison totals reproducible from documented rule; no float drift. |

## J5 — Purchase order (M6, §11)

| ID | Scenario | Expected |
|---|---|---|
| J5.1 | Create PO from selected offer | Status **Commande émise**; lines, currency, Incoterm, payment terms copied; PO number unique. |
| J5.2 | Contract signed / supplier confirms | **Contrat signé**, **Commande confirmée**. |
| J5.3 | PO amount differs from validated amount beyond tolerance | Blocked or re-approval required (rule to be confirmed). |
| J5.4 | Create PO on request not fully validated | Refused. |
| J5.5 | Export validated request / PO for ERP (file mode, US-12) | File contains expected fields; re-export idempotent (no duplicate key). |

## J6 — Receipt & exceptions, closure (M8, §13, US-09)

| ID | Scenario | Expected |
|---|---|---|
| J6.1 | Fares receives 6 of 10 on line 1 | Line 1 **Réception partielle**; other lines unaffected; request not closable. |
| J6.2 | Remaining 4 received, 1 damaged with photos | Exception recorded with photos; line status reflects non-conformity. |
| J6.3 | Refused delivery | Recorded with reason; quantities not counted. |
| J6.4 | Over-receipt (11 of 10) | Blocked or tolerance rule. |
| J6.5 | Service acceptance by requester (service fait) | Recorded with user/date. |
| J6.6 | Attempt closure with a missing condition (§13: delivery, documents, final invoice amount, confirmation, final supplier/amount/date/owner) | Refused; UI lists missing conditions. **Never auto-closed.** |
| J6.7 | All 5 conditions met → close | **Clôturée**; audit chain verifies across full history. |
| J6.8 | Receiver from another site/company | Cannot receive. |
| J6.9 | Receipt from phone at 360px, QR of PO | Works, photos upload, offline/poor network shows recoverable error. |

## J7 — Cross-cutting regression for every journey step

- Every transition writes exactly one audit event; audit timeline readable by auditor (US-11).
- Both languages: run each journey once fully in FR and once in EN.
- "Where is my request?" (US-10 preview): status, owner, since when, amount, overdue flag are correct at each step.
- Loading / empty / error / recovery states on every list and detail screen.
- Backend restart mid-journey: no state lost, no duplicate submission on retry (idempotent submit).
