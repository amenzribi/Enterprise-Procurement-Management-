# Sprint 5–8 — Manual test scenarios

| | |
|---|---|
| Application | http://localhost:4300 (proxy → http://localhost:8081) |
| Profiles | `dev,demo`; database `procurement_dev` |
| Demo password | `%TEMP%\epi-e2e-pw.txt` via `DEMO_USER_PASSWORD` (never commit) |
| User acceptance | **Pending** for Sprints **5, 6, 7 and 8** until Mohamed confirms |

**Supplier confirmation:** no external portal — **Ines** or **Mehdi** uses **Record supplier confirmation** on the dossier.

**Photo / invoice files:** receipt and invoice screens take **references** (metadata). Real binaries go under **Attachments** on the request.

**ERP exports:** CSV file download only — not a live ERP connector.

**Audit pack:** PDF text report of request header + audit events — not a full attachment archive.

---

## Start here

1. Confirm frontend http://localhost:4300 and health http://localhost:8081/actuator/health.
2. Open `%TEMP%\epi-e2e-pw.txt` for the shared demo password (do not paste it into tickets).
3. Prefer a fresh request title such as `S5-S8 full journey` for section E; reuse earlier demo dossiers only if you already know their numbers.
4. Sign out between account switches (top-right menu).

### Verified demo roles (seed)

| Person | E-mail | Roles (demo) | Typical use |
|---|---|---|---|
| Sami | requester.tn@demo.local | REQUESTER | Create request; confirm receipt acceptance |
| Karim | manager.tn@demo.local | DEPARTMENT_MANAGER | Approve; view dashboards |
| Mehdi | purchasing.manager@demo.local | PURCHASING_MANAGER | Assign; close; dashboards + ERP CSV |
| Ines | buyer@demo.local | BUYER | Source, award, PO, ship, invoice; dashboards (no ERP export) |
| Omar | receiver.tn@demo.local | RECEIVER | Record goods receipt |
| Hedi | ceo@demo.local | GROUP_MANAGEMENT (+ management approver) | Group company panel on dashboards |
| Claire / Julien | requester.fr / manager.fr | FR plant | Cross-company negatives |

---

## A. Sprint 5 — Award → PO → confirmation

1. http://localhost:4300 — **Sami** EN → **Purchase requests** → **New request** title `S5 seals`, line qty **10**, unit price **20** → **Submit**.
2. Sign out → **Karim** → **Approvals** → open the request → **Approve**.
3. Sign out → **Mehdi** → open dossier → **Assign** **Ines** → confirm.
4. Sign out → **Ines** → **Accept** → create Quick RFQ + quote unit **25** → **Validate technical** → **Validate commercial**.
5. **Award supplier** (reason ≥ 3 characters) → **Create purchase order draft** → **Issue** → **Record supplier confirmation** (note `Supplier OK`).
6. Expect PO **Confirmed**; buying CPA vs charged Tunisia plant preserved on the dossier.
7. Negatives: **Claire** → open Sami’s request URL → **not found**. Second award → conflict.
8. Switch language to **العربية** and shrink the window to ~360px on the Award/PO panel.

---

## B. Sprint 6 — Shipment → delivery

Start from a **Confirmed** PO (finish A).

1. **Ines** → **Shipments** → Local, carrier `Demo Freight` → **Create shipment**.
2. **Record actual date** on each open milestone until the shipment is delivered.
3. Add document reference `DN-MANUAL-1` → **Add transport document** (reference only).
4. Expect request **Delivered** / user status toward **Received** — **not** Accepted.
5. Negatives: ship before confirm → error; Claire → 404 on the same URL.
6. Arabic / ~360px on the Shipments panel.

---

## C. Sprint 7 — Receipt → invoice → closure

Start from **Delivered** (finish B). Sample quantities: ordered/shipped **10**.

**Partial / damaged example (if you try it):** record received **10**, accepted **9**, rejected **1** (damaged). After requester confirmation, outstanding remaining = ordered − accepted (**1**). Do not count a replacement line twice unless you create a new request/line. Full OK path: receive **10** accepted OK.

1. Sign out → **Omar** (`receiver.tn@demo.local`) → open dossier → **Receipt and closure**.
2. Optional photo reference `CAM-1` (metadata only) → fill quantities/condition → **Record goods receipt**. Status moves to partially received / non-conforming as applicable.
3. Sign out → **Sami** → **Confirm acceptance**. Full OK accept → **Accepted**. Delivery alone must never have set Accepted.
4. Sign out → **Ines** → invoice number `INV-100`, amount matching PO currency, document reference `PDF-1`, check **I attest that the order, receipt and invoice agree (manual check)** → **Record final invoice** → **Invoiced**. (Ines records the **manual match attestation**; this is not automated three-way matching.)
5. Sign out → **Mehdi** → optional supplier score `5` → **Confirm closure** → **Closed**. Optional waiver note if quantity shortfall remains (decision A7: user-confirmed closure).
6. Negatives: Omar before delivery → 404; Claire → 404; close without invoice → closure not ready; over-receive → quantity exceeded.
7. Arabic / mobile on the Receipt panel.

---

## D. Sprint 8 — Dashboards, audit pack, ERP CSV

1. Sign out → **Mehdi** → sidebar **Dashboards** → http://localhost:4300/dashboards.
2. Expect **Pipeline** counts by user status; open amounts listed **per currency** (never one grand total across currencies). **Where / who / how much / late** table with links to dossiers. **By requesting company** panel must **not** appear for Mehdi.
3. Click **Export requests (CSV)** and **Export purchase orders (CSV)** — files download; CSV headers include request/PO fields. Buyer **Ines** must not see these export buttons (no `ERP_EXPORT`).
4. Open any visible dossier → **Audit pack** → PDF downloads; content mentions the request number and audit actions.
5. Sign out → **Hedi** (`ceo@demo.local`) → **Dashboards** → expect **By requesting company** panel with demo company names/codes.
6. Sign out → **Karim** → **Dashboards** loads (department manager). Sign out → **Sami** → open `/dashboards` → access denied / not found (no `DASHBOARD_VIEW`).
7. Sign out → **Claire** → audit pack URL for a Tunisia-only request → not found; she must not see Tunisia-only rows on any dashboard she can open.
8. Arabic + ~360px on **Dashboards**: headings readable, table scrolls sideways if needed, `dir=rtl`.

---

## E. Combined end-to-end (S5–S8)

1. New title `S5-S8 full journey`, qty **10**: complete **A → B → C** without skipping accounts.
2. After **Closed**, **Sami** still sees the dossier as Closed; no further receipt create.
3. **Mehdi** → **Dashboards**: the closed request appears in pipeline/management (or counts include it); download **Audit pack** from the dossier; run both **ERP CSV** exports and confirm the request number appears in the request export when status is from APPROVED onwards (closed included).
4. **Hedi** → confirm group company breakdown still loads.

---

## What is intentionally out of these scenarios

- Live ERP connectors, AI request draft, automated three-way match, AI invoice reading, real payment execution, real supplier e-mail/portal links.
- Treating developer automated checks as user acceptance.
