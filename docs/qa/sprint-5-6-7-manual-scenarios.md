# Sprint 5, 6 & 7 — Manual test scenarios

| | |
|---|---|
| Application | http://localhost:4300 (proxy → http://localhost:8081) |
| Profiles | `dev,demo`; database `procurement_dev` |
| Demo password | `%TEMP%\epi-e2e-pw.txt` via `DEMO_USER_PASSWORD` (never commit) |
| User acceptance | **Pending** for Sprints **5, 6 and 7** until Mohamed confirms |

**Supplier confirmation:** no external portal — **Ines** or **Mehdi** uses **Record supplier confirmation** on the dossier.

**Photo / invoice files:** receipt and invoice screens take **references** (metadata). Real binaries go under **Attachments** on the request.

---

## A. Sprint 5 — Award → PO → confirmation

1. http://localhost:4300 — **Sami** (`requester.tn@demo.local`) EN → New request `S5 seals`, line qty **10**, price 20 → Submit.
2. **Karim** → Approve.
3. **Mehdi** → Assign **Ines**.
4. **Ines** → Accept → Quick RFQ + quote unit **25** → Technical + commercial validation.
5. **Award supplier** (reason ≥ 3 chars) → **Create purchase order draft** → **Issue** → **Record supplier confirmation** (`Supplier OK`).
6. Expect PO **Confirmed**; buying CPA vs charged TN plant preserved.
7. Negatives: **Claire** → 404 on Sami’s URL. Second award → conflict.
8. AR + ~360px width on Award/PO panel.

---

## B. Sprint 6 — Shipment → delivery

Start from a **Confirmed** PO (finish A).

1. **Ines** → **Shipments** → Local, carrier `Demo Freight` → **Create shipment**.
2. **Record actual date** on each open milestone until delivered.
3. Add document ref `DN-MANUAL-1` → **Add transport document**.
4. Expect request **Delivered** / user **Received** — not Accepted.
5. Negatives: ship before confirm → error; Claire 404.
6. AR / mobile on Shipments.

---

## C. Sprint 7 — Receipt → invoice → closure

Start from **Delivered** (finish B). Sample: ordered/shipped **10**; receive all accepted OK (or try qty 10 received / 9 accepted / 1 damaged and note remaining).

1. Sign out → **Omar** (`receiver.tn@demo.local`, RECEIVER) → open dossier → **Receipt and closure**.
2. Optional photo ref `CAM-1` (metadata only) → **Record goods receipt**. Status → partially received / non-conforming as applicable. Outstanding = ordered − accepted after confirm.
3. Sign out → **Sami** → **Confirm acceptance**. Full OK accept → **Accepted**. Delivery alone must not have been Accepted.
4. Sign out → **Ines** → invoice `INV-100`, amount matching PO currency, doc ref `PDF-1`, check **match attested** → **Record final invoice** → **Invoiced**.
5. Sign out → **Mehdi** → rating `5` → **Confirm closure** → **Closed**. Optional waiver note if quantity shortfall remains.
6. Negatives: Omar before delivery → 404; Claire 404; close without invoice → `closure-not-ready`; over-receive → `quantity-exceeded`.
7. AR / mobile on Receipt panel.

---

## D. Combined end-to-end

New title `S5-S7 full journey`, qty **10**: A → B → C without skipping accounts. After close, Sami sees **Closed**; no further receipt create.

---

## Accounts

| Person | E-mail | Use |
|---|---|---|
| Sami | requester.tn@demo.local | Request; confirm receipt |
| Karim | manager.tn@demo.local | Approve |
| Mehdi | purchasing.manager@demo.local | Assign; close |
| Ines | buyer@demo.local | Source, award, PO, ship, invoice |
| Omar | receiver.tn@demo.local | Record goods receipt (new demo) |
| Claire / Julien | requester.fr / manager.fr | Cross-company negatives |
