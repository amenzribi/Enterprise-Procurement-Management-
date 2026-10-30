# Sprint 5 & 6 — Manual test scenarios

| | |
|---|---|
| Application | http://localhost:4300 (proxy → http://localhost:8081) |
| Profiles | `dev,demo`; database `procurement_dev` |
| Demo password | Set `DEMO_USER_PASSWORD` from `%TEMP%\epi-e2e-pw.txt` (never commit) |
| User acceptance | **Pending** — do not mark Sprint 5 or Sprint 6 accepted until Mohamed confirms |

**Supplier confirmation in Core:** there is no external supplier portal for PO confirmation. **Ines** (accepted buyer) or **Mehdi** (purchasing manager) records confirmation in the dossier (**Record supplier confirmation**). Logistics actions use the same purchasing actors (no dedicated logistics demo role yet).

---

## A. Sprint 5 — Award → PO → issue → confirmation

1. Open http://localhost:4300. Sign in as **Sami** (`requester.tn@demo.local`), language **EN**.
2. **New request** → title `S5 manual seals`, category spare parts, department Maintenance, required date +21 days, one line (qty 1, unit PCE, price 20) → **Save** → **Submit** → confirm circuit.
3. Sign out. Sign in as **Karim** (`manager.tn@demo.local`) → open the request → **Approve**.
4. Sign out. Sign in as **Mehdi** (`purchasing.manager@demo.local`) → request dossier → **Assign** → choose **Ines Jaziri (Demo)** → confirm.
5. Sign out. Sign in as **Ines** (`buyer@demo.local`) → **Accept** assignment.
6. **Sourcing:** create Quick RFQ with demo supplier (e.g. SKF) → enter unit price `25` → **Submit quotation** → **Validate technical** (comment `Fits`) → **Validate commercial** (comment `Price OK`).
7. **Award and purchase order:** choose the quotation → reason `Best landed for seals` → **Award supplier**. Expect status **Awarded**; price memory row created (backend).
8. **Create purchase order draft** (payment terms Net 30) → expect PO number `DEMO-…-PO-YYYY-######`, status **Draft**.
9. **Issue purchase order** → request status **Ordered**; PO **Issued**. Commercial lines must stay frozen if you try to invent edits (draft-only fields are gone).
10. **Record supplier confirmation** with note `Supplier OK (demo)` → PO **Confirmed**. (This is the in-app confirmation — not a supplier login.)
11. Negatives: sign out → **Claire** (`requester.fr@demo.local`) → open Sami’s URL → **Request not found**. As Ines, a second award on the same request must fail (conflict).
12. Arabic/mobile: switch language to **AR**, width ~360px — Award/PO panel remains usable, RTL.

---

## B. Sprint 6 — Shipments (from a confirmed PO)

Prerequisite: a request whose PO is **Confirmed** (finish scenario A or use a prior demo request still `ORDERED` with confirmed PO).

1. As **Ines** on that dossier, open **Shipments**.
2. Kind **Local**, carrier `Demo Freight` → **Create shipment**. Expect shipment number `DEMO-…-SH-YYYY-######`, milestones seeded (Order confirmed / Dispatched / In transit / Delivered). A past planned date on Dispatched shows **Late**.
3. **Record actual date** on successive open milestones (start with Dispatched). Request status moves toward **Shipped** / **In transit** user status — never **Accepted**.
4. Add document reference `DN-MANUAL-1` → **Add transport document** (delivery note for local).
5. Continue until **Delivered** milestone actual is recorded → request status **Delivered**, user status **Received**. Receipt acceptance / closure must **not** appear.
6. Negatives: before confirmation, create shipment → error `po-not-confirmed`. Over-ship quantity via API/UI → `quantity-exceeded`. Claire cannot see the Tunisia shipment dossier.
7. Arabic/mobile: AR + narrow viewport on Shipments panel.

---

## C. Combined — fresh request through Sprint 5 and 6

1. Repeat A steps 1–10 on a new title `S5+S6 full journey`.
2. Without signing out of Ines, run B steps 2–5 on the same dossier.
3. Sign out → **Sami** → open the request: see shipment progress / **Received** when delivered; cannot create shipments (no purchasing act).
4. Optional: **Mehdi** can also create/update shipments on CPA buying company requests.

---

## Accounts cheat-sheet

| Person | E-mail | Role in these scenarios |
|---|---|---|
| Sami | requester.tn@demo.local | Requester (TN plant) |
| Karim | manager.tn@demo.local | Approver |
| Mehdi | purchasing.manager@demo.local | Assign / may act on logistics |
| Ines | buyer@demo.local | Buyer: accept, source, award, PO, confirm, ship |
| Claire | requester.fr@demo.local | Cross-company negative |
| Julien | manager.fr@demo.local | Cross-company negative |

No separate logistics demo account is seeded; buyers/PMs perform shipment actions in Core.
