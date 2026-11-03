# Manual acceptance sessions (manageable packs)

**Status:** Not executed by user — do not mark passed until Mohamed confirms each scenario.

| | |
|---|---|
| App | http://localhost:4300 → http://localhost:8081 |
| Password | `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`) — never paste into tickets |
| Omar | `receiver.tn@demo.local` (RECEIVER) |
| Preserve | Closed UAT dossier `DEMO-TN-PLT-PR-2026-000490` — UAT-01 — Pump seals |
| Guides | [FR admin](../guides/fr-administrateur-societes-utilisateurs.md), [FR roles](../guides/fr-matrice-roles-permissions.md), [FR find work](../guides/fr-guide-utilisateur-trouver-son-travail.md) |
| Deep cases | [final-manual-acceptance-plan.md](final-manual-acceptance-plan.md), [usability-manual-retest.md](usability-manual-retest.md) |

Record **Actual** as *Not executed by user* / *Pass* / *Fail* only when you run the step.

---

## Session 1 — Smoke & shell (~20 min)

Accounts: Sami `requester.tn@demo.local`, Ines `buyer@demo.local`

| ID | Steps | Expected | Actual |
|---|---|---|---|
| S1-1 | Open 4300; sign in as Sami; land on **Mon travail** / **My work** | Overview with awaiting / my requests / notices | Not executed by user |
| S1-2 | Switch EN → FR → AR; confirm Arabic `dir=rtl` | Labels flip; drawer usable | Not executed by user |
| S1-3 | Open nav on ~360 px width (browser resize) | Drawer; no page-wide overflow | Not executed by user |
| S1-4 | Ines → **Assistant**; note DEMO label; summarise with ref `DEMO-TN-PLT-PR-2026-000490` | Non-empty DEMO summary with sources; live AI off | Not executed by user |
| S1-5 | Sign out; **Forgot password?** | Neutral response; link only in local mail capture | Not executed by user |

---

## Session 2 — Admin companies & users (~30 min)

Account: `global.admin@demo.local`

| ID | Steps | Expected | Actual |
|---|---|---|---|
| S2-1 | **Organisation** → create company (unique code) + site/dept | Success; company ≠ site | Not executed by user |
| S2-2 | Duplicate company code | Conflict / clear error | Not executed by user |
| S2-3 | **Utilisateurs** → read role help; create user; temporary password or setup link | Mail captured locally; forced change if temporary | Not executed by user |
| S2-4 | Assign company role(s); deactivate another test user | History preserved; no “all roles” account | Not executed by user |
| S2-5 | Confirm GLOBAL_ADMIN has no invented purchasing/assistant business actions | 403 / empty / no fake buttons | Not executed by user |

---

## Session 3 — Happy path by reference (~45–60 min)

**Use a fresh title** (e.g. `ACCEPT-YYYYMMDD-HHMM`). Do **not** edit `…-000490`.

| ID | Actor | Steps | Expected | Actual |
|---|---|---|---|---|
| S3-1 | Sami | Create + submit → success dialog | Reference visible; Copy; Open | Not executed by user |
| S3-2 | Karim | **Mon travail** / **Approbations**; search reference; approve | Found without guessing; moves to purchasing | Not executed by user |
| S3-3 | Mehdi | **Boîte d’achats**; newest first; assign Ines | Assignment visible | Not executed by user |
| S3-4 | Ines | Accept; RFQ; quote; validations; award; PO issue; confirm | Status coherent; refs labelled | Not executed by user |
| S3-5 | Ines | **Logistique**; search by request/PO; ship → Delivered | Delivered discoverable for receipt | Not executed by user |
| S3-6 | Omar | Find by request reference; receipt | Eligible after Delivered | Not executed by user |
| S3-7 | Auth. actors | Acceptance → invoice + manual match → closure | Closure ≠ payment auto-paid | Not executed by user |

---

## Session 4 — Search, sort, negatives (~25 min)

| ID | Steps | Expected | Actual |
|---|---|---|---|
| S4-1 | Several requests; list default newest-first across pages | Stable secondary order; user sort preserved when changed | Not executed by user |
| S4-2 | Search older reference not on page 1 | Server-side search; pagination resets | Not executed by user |
| S4-3 | Cross-company search of known foreign ref | Empty (no existence leak) | Not executed by user |
| S4-4 | Supplier contact vs buyer messaging | Internal notes hidden from supplier | Not executed by user |

---

## Session 5 — Responsive spot-check (~20 min)

Browser viewport only (not physical devices unless you confirm otherwise): **360, 390, 768, 1024, 1440**; FR + AR on:

Overview, Requests, Approvals, Purchasing, Logistics, Dossier, Assistant, Users, Organisation, Sign-in, Forgot password.

| Width | EN | FR | AR RTL | Notes / Actual |
|---|---|---|---|---|
| 360 | Not executed by user | | | |
| 768 | Not executed by user | | | |
| 1440 | Not executed by user | | | |

---

## Evidence classes

| Class | Meaning |
|---|---|
| Automated | Backend `-Pdb-it`, FE lint/unit/build, Playwright |
| Developer browser | Agent viewport checks |
| User-accepted | Only scenarios you explicitly confirm |
