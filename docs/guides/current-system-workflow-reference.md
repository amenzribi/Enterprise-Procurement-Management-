# Current system workflow reference

Read-only snapshot of **Enterprise Procurement Intelligence** as implemented in this repository, for authoring a French visual guide, organisational chart and manual test scenarios.

**Do not** treat this document as user-accepted UAT. Passwords, tokens and invitation/reset links are omitted on purpose.

---

## 1. Version and evidence

| Item | Value |
|---|---|
| Git tip (verified) | `947465d` (`947465d4c6dc1edca28bcebe7d975f6666edc6a0`) |
| Branch | `main` (local ahead of `origin/main`) |
| Notable ancestors | `fef8b1f` usability; `95b469f` assistant dossier-ref fix; `d9b209f` pre-acceptance verification fixes |
| Uncommitted (preserved, unrelated) | Working-tree changes on V7 / import / supplier / org / purchasing / sprint-3 / repository-review — treated as **CRLF/line-ending noise** relative to HEAD (`git diff -w` empty when previously checked). **Do not edit applied migration V7.** |
| App URLs (health checked this session) | Frontend **http://localhost:4300** → Backend **http://localhost:8081** |
| Backend health | `UP` via `/actuator/health` |
| Active profiles (intended local) | `dev,demo` with `ASSISTANT_MODE=DEMO` when started for review |
| Database | Microsoft SQL Server (`procurement_dev` for running app; tests use `procurement_test`) |

### Evidence classes

| Source | Used for |
|---|---|
| **Code** | Roles, permissions (`MeAssembler`), routes, guards, workflow writers, self-approval, closure policy, messaging audiences, assistant |
| **Configuration / Flyway demo seeds** (`db/demo/R__*.sql`, `DemoUserSeeder`) | Demo users, org, approval rules, suppliers — **seeded defaults**, re-applied with `demo` profile |
| **Browser / API developer checks (prior sessions)** | Assistant summarise on `DEMO-TN-PLT-PR-2026-000490`; responsive viewport samples; logistics inbox |
| **Automated** | Backend `mvnw verify -Pdb-it` (141) at tip lineage; FE lint/unit/build; Playwright (with noted flake) |

### Not verified in this documentation pass

- Live SQL query of *currently mutated* `approval_rule` rows in `procurement_dev` (document uses **seeded** `R__demo_workflow.sql`; if an admin edited rules in UI, DB may differ).
- Physical-device testing.
- Full end-to-end browser walk of UAT-02 (scenario below is **designed** from code+seeds, not freshly executed here).
- User-confirmed acceptance of S5–13 / usability packs.

---

## 2. Exact roles and permissions

### 2.1 Distinctions (do not conflate)

| Concept | Meaning in this product |
|---|---|
| **Account** | `app_user` row (email, password hash, active flag, home `company_id` / site / department) |
| **Role grant** | Row in `user_role`: role code ± company ± department |
| **Permission** | Derived string on `/api/v1/me` from roles (`MeAssembler`) — used by Angular guards/nav |
| **Company membership** | Home company + company-scoped grants; company isolation → out-of-scope **404** |
| **Approval-rule assignment** | At submit time, stages resolve holders of a **role type** (or named user) in the **requesting** company; not a permanent “approver of company X” UI flag alone |
| **Request ownership** | Requester / entered-by; current owner shown on dossier |
| **Buyer assignment** | Purchasing assignment record (accept / return) on buying company |

### 2.2 Role catalogue

Enum: `REQUESTER`, `ENTER_ON_BEHALF`, `DEPARTMENT_MANAGER`, `BUDGET_APPROVER`, `MANAGEMENT_APPROVER`, `BUYER`, `PURCHASING_MANAGER`, `RECEIVER`, `GROUP_MANAGEMENT`, `ADMIN`, `GLOBAL_ADMIN`, `SUPPLIER_CONTACT`.

Display names from `auth.roles.*` where present; help text from `usersAdmin.roleHelp.*`.

| Role code | EN short / FR short (or help if no short) | Purpose | Scope | Typical menus | Permitted actions (summary) | Important prohibitions |
|---|---|---|---|---|---|---|
| `REQUESTER` | Requester / Demandeur | Create & submit needs | Company | Mon travail, Demandes d’achat, Validations (if also approver), Absences | Draft/submit/answer questions; may confirm acceptance / close as author | No purchasing/assistant/messaging by default |
| `ENTER_ON_BEHALF` | Enters on behalf… / Saisie pour le compte d’autrui | Enter for another requester | Company | Same as requester (+ on-behalf fields) | Create with chosen requester | Same as requester |
| `DEPARTMENT_MANAGER` | Department manager / Responsable de département | Approval stage + dept visibility | Company (+ department on grant) | Validations, Demandes (dept view), Tableaux de bord | Approve/reject/return/question; absences | Not automatic buyer |
| `BUDGET_APPROVER` | Budget approver / Valideur budget | Budget stage | Company | Validations | Same decision actions when tasked | — |
| `MANAGEMENT_APPROVER` | Management approver / Valideur direction | Management stage / escalation target | Company | Validations | Same when tasked | — |
| `BUYER` | Buyer / Acheteur | Execute buying on accepted assignment (or as allowed) | Buying company | Boîte achats, Messages, Assistant, Logistique, Consultations/Commandes (nav→requests), Fournisseurs | Accept assignment; RFQ; quote; tech/commercial validate; award; PO; ship; invoice; messaging | Cannot assign (needs PM); no GLOBAL_ADMIN powers |
| `PURCHASING_MANAGER` | Purchasing manager / Responsable achats | Queue ownership | Buying company | Same as buyer + assign + import + ERP export (with others) | Assign; may act without assignment; close; manage suppliers | — |
| `RECEIVER` | *(no `auth.roles.RECEIVER`)* — help: records goods receipt | Goods receipt | Requesting/charged company | Réceptions (nav→requests) | Record receipt; may confirm if not author | No approve/buy by default |
| `GROUP_MANAGEMENT` | Group management / Direction du groupe | Group dashboards | Unscoped grant | Tableaux de bord | View group management panels; ERP export | No auto operational buy |
| `ADMIN` | Administrator / Administrateur | Org/users (non-global) within matrix | Unscoped grant | Organisation, Utilisateurs, Règles, Taux, Import | Org admin; approval rules; users (cannot grant GLOBAL_ADMIN / group-wide forbidden set) | No auto all business data |
| `GLOBAL_ADMIN` | *(help only)* Installation admin | Companies, users, passwords | Unscoped | Organisation, Utilisateurs, Règles, Taux | Full user/org admin incl. GLOBAL_ADMIN grants | **No** automatic REQUEST/PURCHASING/ASSISTANT/MESSAGING |
| `SUPPLIER_CONTACT` | *(not in usersAdmin assignable)* | External portal | Linked via `supplier_contact` | Portail fournisseur only | EXTERNAL messages on authorised conversations | No internal workspace; no assistant |

### 2.3 Permissions → roles (derived)

| Permission | Roles |
|---|---|
| `MESSAGING_SUPPLIER` | `SUPPLIER_CONTACT` only (exclusive early return) |
| `USER_ADMIN` | `GLOBAL_ADMIN`, `ADMIN` |
| `USER_ADMIN_GLOBAL` | `GLOBAL_ADMIN` |
| `ORG_ADMIN` | `GLOBAL_ADMIN`, `ADMIN` |
| `SYSTEM_ADMIN` | `GLOBAL_ADMIN`, `ADMIN` |
| `REQUEST_CREATE` | `REQUESTER`, `ENTER_ON_BEHALF` |
| `REQUEST_ENTER_ON_BEHALF` | `ENTER_ON_BEHALF` |
| `REQUEST_VIEW_DEPARTMENT` | `DEPARTMENT_MANAGER` |
| `REQUEST_VIEW_GROUP` | `GROUP_MANAGEMENT` |
| `APPROVAL_ACT` | Approver roles (`DEPARTMENT_MANAGER`, `BUDGET_APPROVER`, `MANAGEMENT_APPROVER`) |
| `PURCHASING_ACT` | `BUYER`, `PURCHASING_MANAGER` |
| `MESSAGING_BUYER` | `BUYER`, `PURCHASING_MANAGER` |
| `ASSISTANT_USE` | `BUYER`, `PURCHASING_MANAGER` |
| `PURCHASING_MANAGE` | `PURCHASING_MANAGER` |
| `RECEIPT_ACT` | `RECEIVER` |
| `DASHBOARD_VIEW` | DM, BUYER, PM, GROUP_MANAGEMENT, ADMIN |
| `ERP_EXPORT` | PM, ADMIN, GROUP_MANAGEMENT |
| `APPROVAL_ADMIN` | `ADMIN` |
| `IMPORT_RUN` | `ADMIN`, `PURCHASING_MANAGER` |
| `SUPPLIER_READ` | BUYER, PM, ADMIN, GROUP_MANAGEMENT |
| `SUPPLIER_MANAGE` | PM, ADMIN |

### 2.4 Combinations and restrictions

- UI guidance (`usersAdmin.roleCombinations`): approver + REQUESTER OK; BUYER + PURCHASING_MANAGER OK; **do not** use GLOBAL_ADMIN as “all roles”; SUPPLIER_CONTACT invited from messaging, not Users admin.
- Company ADMIN cannot grant `GLOBAL_ADMIN`, `ADMIN`, `GROUP_MANAGEMENT`, `SUPPLIER_CONTACT` (`UserAdminService.COMPANY_ADMIN_FORBIDDEN`).
- Last active GLOBAL_ADMIN cannot be deactivated / stripped.
- Self cannot deactivate self.
- **Self-approval forbidden:** requester and entered-by excluded from approver resolution (`ApproverResolver`).
- Same demo person rarely stacks operational + admin: Hedi holds GROUP_MANAGEMENT + MANAGEMENT_APPROVER (TN+FR); Amel holds REQUESTER + ENTER_ON_BEHALF. Purchasing (Mehdi) and buying (Ines) are **separate accounts**.

---

## 3. Exact demo users and organisation

Source: `DemoUserSeeder.java` + `R__demo_organization.sql` + `R__demo_suppliers.sql` + `R__demo_supplier_contacts.sql` + `R__demo_buyer_affinity.sql`. Shared demo password is **out of band** (`DEMO_USER_PASSWORD` / `%TEMP%\epi-e2e-pw.txt`) — never documented here.

### 3.1 Accounts

| Persona | Display name | Login | Roles (company) | Home company / site / dept | Notes |
|---|---|---|---|---|---|
| Sami | Sami Ben Ali (Demo) | `requester.tn@demo.local` | REQUESTER @ DEMO-TN-PLT | DEMO-TN-PLT / DEMO-SFAX / DEMO-MAINT | Pref lang fr |
| Amel | Amel Trabelsi (Demo) | `assistant.tn@demo.local` | REQUESTER + ENTER_ON_BEHALF @ DEMO-TN-PLT | same | On-behalf |
| Karim | Karim Haddad (Demo) | `manager.tn@demo.local` | DEPARTMENT_MANAGER @ DEMO-TN-PLT / DEMO-MAINT | same | Pref en |
| Hichem | Hichem Gharbi (Demo) | `manager2.tn@demo.local` | DEPARTMENT_MANAGER @ DEMO-TN-PLT / DEMO-MAINT | same | Delegate / second DM |
| Leila | Leila Mejri (Demo) | `budget.tn@demo.local` | BUDGET_APPROVER @ DEMO-TN-PLT | DEMO-TN-PLT / DEMO-SFAX / *(no dept)* | Pref ar; used when amount ≥ 5 000 TND |
| Claire | Claire Martin (Demo) | `requester.fr@demo.local` | REQUESTER @ DEMO-FR-PLT | DEMO-FR-PLT / DEMO-LYON / DEMO-MAINT | Cross-company negative |
| Julien | Julien Moreau (Demo) | `manager.fr@demo.local` | DEPARTMENT_MANAGER @ DEMO-FR-PLT / DEMO-MAINT | same | FR approvals |
| Omar | Omar Mansouri (Demo) | `receiver.tn@demo.local` | RECEIVER @ DEMO-TN-PLT | DEMO-TN-PLT / DEMO-SFAX / DEMO-MAINT | Pref en |
| Ines | Ines Jaziri (Demo) | `buyer@demo.local` | BUYER @ DEMO-CPA | DEMO-CPA / DEMO-RADES / DEMO-PURCH | Affinity DEMO-SPARE @ CPA |
| Mehdi | Mehdi Khelifi (Demo) | `purchasing.manager@demo.local` | PURCHASING_MANAGER @ DEMO-CPA | same | Assigns buyers |
| Hedi | Hedi Ferchichi (Demo) | `ceo@demo.local` | GROUP_MANAGEMENT (unscoped); MANAGEMENT_APPROVER @ DEMO-TN-PLT and DEMO-FR-PLT | DEMO-HOLD / DEMO-HQ | High-band / escalation |
| Platform admin | Platform Administrator (Demo) | `admin@demo.local` | ADMIN (unscoped) | DEMO-HOLD / DEMO-HQ | Company admin matrix |
| Global admin | Global Administrator (Demo) | `global.admin@demo.local` | GLOBAL_ADMIN (unscoped) | DEMO-HOLD / DEMO-HQ | Installation |
| SKF contact | SKF Contact (Demo) | `contact.skf@demo.local` | SUPPLIER_CONTACT (unscoped) | DEMO-CPA home fields; contact → **DEMO-SKF** | Portal only |

### 3.2 Organisation

**Group:** `DEMO-GRP` — Atlas Industrial Group (Demo) — consolidation `EUR`.

| Code | Name | Type | Country | Currency | Lang | TZ | Purchasing mode | Parent |
|---|---|---|---|---|---|---|---|---|
| DEMO-HOLD | Atlas Holding SA (Demo) | PARENT | TN | TND | fr | Africa/Tunis | DIRECT | — |
| DEMO-CPA | Atlas Sourcing & Trading (Demo) | PURCHASING_CENTER | TN | TND | fr | Africa/Tunis | DIRECT | DEMO-HOLD |
| DEMO-TN-PLT | Atlas Mechanical Sfax (Demo) | SUBSIDIARY | TN | TND | fr | Africa/Tunis | **MIXED** | DEMO-HOLD |
| DEMO-FR-PLT | Atlas Composites Lyon SAS (Demo) | SUBSIDIARY | FR | EUR | fr | Europe/Paris | **CENTRALIZED** | DEMO-HOLD |

**Derived buying company** (`RequestService.buyingCompany`): for `CENTRALIZED` or `MIXED`, proposed buyer = group’s purchasing centre (`DEMO-CPA`); charged = requesting company. Purchasing may later confirm another mode/company.

**Sites / departments / cost centres:** see seed SQL — Sfax plant + Maintenance/Production; Lyon plant + Maintenance; Radès warehouse + Group purchasing; HQ + Finance; cost centres DEMO-CC-2100 / 3100 / 1100.

**Categories:** DEMO-SPARE, DEMO-MECH, DEMO-ELEC, DEMO-CHEM, DEMO-SERV, DEMO-IT (`R__demo_reference.sql`).

**Suppliers:** DEMO-SKF (SHARED); DEMO-SFAX-SEALS (COMPANY, DEMO-TN-PLT). Contact email only for SKF.

---

## 4. Active approval configuration

**Source of truth for demo:** `R__demo_workflow.sql` (idempotent insert-if-missing). Rule `rule_key` = generated id. **Category and purchase type columns are NULL** → match any category / LOCAL|INTERNATIONAL. Amount band in **company currency** (TND for Sfax, EUR for Lyon). Stage policy **ANY**. SLA **16 hours**; escalation role **MANAGEMENT_APPROVER**. Approvers resolved by **role**, not named users. Working calendar: **no separate calendar entity** — company timezone used for local dates; SLA shift on question documented in approval engine (working-time assumptions in code, not a UI calendar master).

| Name | Company | Amount `[min, max)` | Stages (order) | Eligible demo holders (typical) |
|---|---|---|---|---|
| Sfax up to 5 000 TND (Demo) | DEMO-TN-PLT | 0 – 5000 | 1 DEPARTMENT_MANAGER | Karim, Hichem (dept DEMO-MAINT) |
| Sfax 5 000 to 50 000 TND (Demo) | DEMO-TN-PLT | 5000 – 50000 | 1 DM → 2 BUDGET_APPROVER | + Leila |
| Sfax from 50 000 TND (Demo) | DEMO-TN-PLT | 50000 – ∞ | 1 DM → 2 Budget → 3 MANAGEMENT_APPROVER | + Hedi (TN grant) |
| Lyon all amounts (Demo) | DEMO-FR-PLT | 0 – ∞ | 1 DEPARTMENT_MANAGER | Julien |

DEMO-HOLD / DEMO-CPA: **no** seeded approval rules (CPA does not submit plant requests as requesting company in the happy path).

**Self-approval:** authors excluded. **Revision / question / delegation:** return → `REVISION_REQUESTED`; question pauses SLA; absences route via absence delegate (max hops); task delegate API exists.

### Rule for UAT-02 example

- Requesting company DEMO-TN-PLT, LOCAL, category DEMO-SPARE (or any), **10 × 20 TND = 200 TND** estimated.
- Matches **« Sfax up to 5 000 TND (Demo) »** only → **one stage: DEPARTMENT_MANAGER** (Karim/Hichem). Leila/Hedi **not** required for this amount.
- Quoted total 250 TND does **not** re-select the approval rule (approval already completed before quote).

If live DB rules were edited away from seeds, re-check **Administration → Règles de validation** before testing.

---

## 5. Pages, labels and references

### 5.1 Routes and menu labels (FR / EN)

| Route | Menu FR / EN | Gate |
|---|---|---|
| `/sign-in` | Connexion / Sign in | guest |
| `/forgot-password` | Mot de passe oublié ? / Forgot password? | guest |
| `/p#…` | Choisir un mot de passe / Password setup | token fragment |
| `/account/password` | Changer le mot de passe / Change password | auth; forced if temporary |
| `/overview` | **Mon travail** / My work | auth home |
| `/system-status` | État du système / System status | auth |
| `/requests` | Demandes d’achat / Purchase requests | auth |
| `/requests/new`, `/requests/:id/edit` | Formulaire | auth |
| `/requests/:id` | Dossier | auth |
| `/approvals` | Validations / Approvals | auth |
| `/purchasing` | **Boîte achats** / Purchasing inbox | `PURCHASING_ACT` |
| `/logistics` | **Logistique** / Logistics | `PURCHASING_ACT` |
| `/messages` | Messages fournisseurs / Supplier messages | `MESSAGING_BUYER` |
| `/assistant` | Assistant | `ASSISTANT_USE` |
| `/supplier/messages` | Portail fournisseur / Supplier portal | `MESSAGING_SUPPLIER` |
| `/dashboards` | Tableaux de bord / Dashboards (+ ERP CSV export if `ERP_EXPORT`) | `DASHBOARD_VIEW` |
| `/suppliers` | Fournisseurs / Suppliers | `SUPPLIER_READ` |
| `/admin/organization` | Organisation | `ORG_ADMIN` |
| `/admin/users` | Utilisateurs / Users | `USER_ADMIN` |
| `/admin/approval-rules` | Règles de validation / Approval rules | `SYSTEM_ADMIN` |
| `/admin/exchange-rates` | Taux de change / Exchange rates | `SYSTEM_ADMIN` |
| `/admin/import` | Import des données / Master-data import | `IMPORT_RUN` |
| `/absences` | Absences (approver) | auth |
| `/a#…` | One-tap approval | public token |
| `/q#…` | Public quote | public token |

Nav items **Sourcing / Commandes / Réceptions** currently deep-link to `/requests` (dossier-centred), except **Logistique** which has its own inbox.

### 5.2 Business references

| Object | When generated | Where visible / copy | Search |
|---|---|---|---|
| **Request reference** (`…-PR-YYYY-######`) | **First submit only** (drafts show draft label — no final number) | Submit success dialog (« Demande soumise » / Copy / Open); dossier header; lists; notifications; assistant field | Requests, Approvals, Purchasing, Logistics (authorised scope); spaces trimmed |
| RFQ / quote | On create / supplier flow | Dossier sourcing panel | Via dossier / messaging |
| PO number | On PO create/issue path | Ordering panel; logistics inbox | Logistics search |
| Shipment reference | On shipment create | Logistics dossier + inbox | Logistics search |
| Receipt | On record receipt | Receipt panel | Via dossier |

**Next actor discovery:** **Mon travail** (awaiting approvals + purchasing items + my requests + unread notices); role inboxes; paste reference in search; dossier **next action** from server-backed actions. Out-of-scope search → empty (no existence oracle).

---

## 6. Complete purchase scenario (UAT-02 — new request)

**Preserve:** `DEMO-TN-PLT-PR-2026-000490` — UAT-01 — Pump seals (**do not modify**).

**Planned new dossier**

| Field | Value |
|---|---|
| Title | UAT-02 — Pump seals |
| Requester | Sami (`requester.tn@demo.local`) |
| Company | Atlas Mechanical Sfax (DEMO-TN-PLT) |
| Site / Dept | Sfax plant / Maintenance |
| Category | Spare parts (DEMO-SPARE) |
| Purchase type | Local |
| Priority | Normal |
| Line | Mechanical seal (or similar); qty **10**; unit PCE; est. unit **20 TND** → line **200 TND** |
| Quote later | Unit **25 TND** → **250 TND** goods |
| Approval rule | Sfax up to 5 000 TND → **Karim only** (stage 1) |
| Buying company (proposed) | DEMO-CPA (MIXED) → Mehdi / Ines |

### Clarifications (implemented behaviour)

| Topic | Behaviour |
|---|---|
| Acceptance confirmer | **Author (Sami) or RECEIVER (Omar)** — `requireConfirmer` prefers author path then receiver |
| Second confirmation after receiver? | **No second mandatory “requester confirm after Omar”** if Omar already confirmed; if Omar only **records** receipt, author/receiver must **confirm** to reach ACCEPTED |
| Invoice / payment | BUYER or PURCHASING_MANAGER on buying company; `matchAttested` required for closure; payment PENDING or PAID |
| Close with unpaid invoice? | **Yes** — closure checks acceptance + invoice + match attest (+ qty/waiver); **not** PAID |
| Documents | Submit attachments **optional**; shipment doc kinds optional reference entries; invoice `documentReference` optional |
| Tech/commercial validation | Same sourcing actor (**Ines** or Mehdi) — **not** separate TECHNICAL_EVALUATOR accounts in demo |
| Supplier confirmation of PO | Recorded by **buyer/PM** (`confirm` on PO) with note — not by supplier portal login |
| Closed dossiers searchable? | Yes within authorised visibility (author, participants, buying roles as scoped) |

### Step table (login → closure)

| # | Account / role | Preconditions | URL / menu | Fields / sample | Button / action | Backend / UI status | Next actor | How found | Audit / history | Why needed |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Sami / REQUESTER | Demo DB, password set | `/sign-in` → `/overview` | Email Sami | Sign in | Authenticated → Mon travail | Sami | — | Login session | Enter workspace |
| 2 | Sami | — | Demandes → Nouvelle / `/requests/new` | Title UAT-02…; category Spare parts; dept Maintenance; site Sfax; required date future; type Local; line qty 10 price 20 | Save draft (optional) | DRAFT / Brouillon; **no PR number** | Sami | Mes demandes | Draft saved | Capture need |
| 3 | Sami | Draft complete | Same form | — | Soumettre → confirm preview | CREATED/IN_APPROVAL; user **Soumise**; **number assigned**; success dialog | Karim | Dialog copy/open; Mon travail / Validations search | Submit + circuit planned | Start approval |
| 4 | Karim / DM | Pending task | `/approvals` or dossier | — | Approuver / confirm | Last stage → **IN_PURCHASING_REVIEW** / Approuvée | Mehdi | Boîte achats; Mon travail (Ines later) | Approval decision | Gate spend |
| 5 | Mehdi / PM | In purchasing review | `/purchasing` search ref | Buyer Ines | Assigner → confirm | ASSIGNED_TO_BUYER | Ines | Boîte / Mon travail / notice | Assignment | Route to buyer |
| 6 | Ines / BUYER | Assigned | Dossier | — | Accepter | Assignment ACCEPTED | Ines | Same dossier | Accept | Unlock sourcing |
| 7 | Ines | Accepted | Dossier sourcing | Supplier SKF (or seals) | Créer RFQ | SOURCING | Ines | Dossier | RFQ created | Solicit offer |
| 8 | Ines | RFQ open | Dossier | Unit price 25 | Enregistrer offre | QUOTES_RECEIVED | Ines | Dossier | Quote | Price evidence |
| 9 | Ines | Quote present | Dossier | Tech comment | Validation technique | TECHNICAL_VALIDATION | Ines | Dossier | Tech OK | Quality gate |
| 10 | Ines | Tech done | Dossier | Commercial comment | Validation commerciale | COMMERCIAL_VALIDATION | Ines | Dossier | Commercial OK | Price gate |
| 11 | Ines | Validated | Dossier ordering | Select quote; reason | Attribuer | AWARDED | Ines | Dossier | Award | Select supplier |
| 12 | Ines | Awarded | Dossier | — | Créer commande | PO draft | Ines | Dossier | PO draft | Formalise order |
| 13 | Ines | PO draft | Dossier | — | Émettre | ORDERED | Ines | Dossier | Issue | Commit order |
| 14 | Ines | Issued | Dossier | Confirm note e.g. Supplier OK | Confirmer | PO CONFIRMED | Ines | Dossier / Logistique | Confirm | Unlock shipment |
| 15 | Ines | Confirmed PO | Dossier logistics or `/logistics` | Kind local; lines qty | Créer envoi | Shipment PLANNED | Ines | Logistique / dossier | Shipment | Physical flow |
| 16 | Ines | Shipment | Dossier logistics | Record actual on each milestone | Enregistrer date réelle (× until Delivered) | … → **DELIVERED** | Omar | Search ref; dossier | Milestones | Delivery evidence |
| 17 | Omar / RECEIVER | DELIVERED | Dossier | Qty received | Enregistrer réception | PARTIALLY_RECEIVED or similar until full | Sami or Omar | Dossier | Receipt lines | Goods control |
| 18 | Sami or Omar | Receipt recorded | Dossier | — | Confirmer réception / acceptation | ACCEPTED | Ines/Mehdi | Dossier | Confirm | Business acceptance |
| 19 | Ines or Mehdi | ACCEPTED | Dossier | Invoice ref; **match attested**; payment PENDING | Enregistrer facture | INVOICED | Mehdi or Sami (closer) | Dossier | Invoice | Finance evidence |
| 20 | Mehdi or Sami | Invoice + match; qty OK | Dossier | Optional score/waiver | Clôturer | CLOSED | — | Search still works in scope | Close event | End dossier |

**Payment tracking:** optional update to PAID later; **not** required for step 20. **Do not** auto-mark paid on invoice.

---

## 7. Alternative and exception scenarios

| Case | Account / action | Expected |
|---|---|---|
| Rejection | Karim → Rejeter + reason | REJECTED; final; audit |
| Question + response | Karim → Question; Sami answers on dossier | SLA pause/resume; status stays in approval |
| Return + resubmit | Karim → Retourner; Sami edit; submit again | REVISION_REQUESTED → new round; **same request number** |
| Absence delegation | Approver Absences + delegate; or task delegate | Tasks route to delegate; hops limited |
| No eligible approver | Empty stage after exclude authors / missing role | Escalation role tried; else unresolved / blocked path (engine) — **test carefully** |
| Partial / damaged receipt | Omar records partial / non-conforming | PARTIALLY_RECEIVED / NON_CONFORMING; close may need waiver |
| Outstanding qty at close | Closer without full accept | Blocked unless waiver note |
| Invoice discrepancy | Record invoice without match attest | Closure blocked until `matchAttested` |
| Premature closure | Close before ACCEPTED/invoice/match | Error `closure-not-ready` (or equivalent ProblemDetail) |
| Wrong company | Claire opens Sami’s id / searches TN ref | 404 / empty search |
| Wrong role | GLOBAL_ADMIN alone opens purchasing/assistant | Guard / 403 / empty business actions |
| Deactivated user with open work | Admin deactivate | Warning possible; **no auto-reassignment** — history kept |

**Unsupported / enum-only (do not invent happy path):** dedicated MANAGEMENT_VALIDATION writer; CONTRACT_SIGNED writer; PAYMENT_PENDING status from invoice API; separate live AI; physical multi-shipment planner UI.

---

## 8. Administration scenario (GLOBAL_ADMIN)

Account: `global.admin@demo.local` (password out of band).

| Goal | Path | Notes |
|---|---|---|
| Create company | Administration → **Organisation** `/admin/organization` | Code, name, type, country, currency, language, timezone, purchasing mode; duplicate code → conflict |
| Org structure | Select company → sites / departments / cost centres | Company ≠ site ≠ department |
| Users | **Utilisateurs** `/admin/users` | Create + setup mail (local capture) or temporary password (≥12; must change) |
| Roles | User detail → add role ± company | No SUPPLIER_CONTACT here; read role help |
| Approval circuit | **Règles de validation** `/admin/approval-rules` | `SYSTEM_ADMIN` (GLOBAL_ADMIN or ADMIN). Company must exist; stages by role |
| FX | **Taux de change** | Needed for multi-currency displays |
| Categories | Seeded / import — new company needs reference data | Import `/admin/import` if used |
| Password reset | User → send reset / temp password; user `/forgot-password` or `/account/password` | Mail file capture in `dev` |
| Deactivate | User → Désactiver | Keep history |
| Audit | Dossier audit package / server audit events; admin actions audited in-transaction | No plaintext passwords in logs |

**Prerequisites before a new company can submit & approve:** company + site/dept + at least one REQUESTER + matching DEPARTMENT_MANAGER (and further roles per rule bands) + **active approval rule** for that company + currency/FX as needed + (if MIXED/CENTRALIZED) purchasing centre and BUYER/PM on buying company.

---

## 9. Supplier messaging and assistant

### Messaging

1. Ines/Mehdi open conversation from RFQ/PO context (`/messages`).
2. Send **EXTERNAL** message (visible to supplier) or **INTERNAL** note (buyers only).
3. `contact.skf@demo.local` signs in → **Portail fournisseur** `/supplier/messages` — EXTERNAL only; unread badges.
4. Reply EXTERNAL; buyer sees reply; internal notes never appear on portal.
5. Closed/cancelled request → conversation read-only.

### Assistant

| Item | Current |
|---|---|
| Mode | **DEMO** under demo profile; default elsewhere **disabled**; LIVE returns not configured (ADR-012) |
| Who | BUYER / PURCHASING_MANAGER only |
| UI | `/assistant` — starters + **Request reference** field required for summarise/draft |
| Empty-summary defect | **Fixed** (reference binding + PR-number resolve). Working test: Ines + `DEMO-TN-PLT-PR-2026-000490` → labelled DEMO reply with sources |
| Live AI | Off — responses labelled demo |

---

## 10. Limits and test status

### Implemented and verified (developer / automated)

- Role/permission matrix and nav guards as coded.
- Demo org, users, seeded approval bands.
- Submit numbering; My work; reference dialog; logistics inbox; forgot/temp password flows.
- Assistant DEMO summarise with business reference.
- Backend IT suite green on tip lineage; FE lint/unit/build; most Playwright journeys (ordering-logistics alone green after suite flake).

### Implemented but not fully re-verified in this doc pass

- Live DB approval rows vs seeds after admin edits.
- Every exception path in §7.
- Full UAT-02 browser execution (table is design-from-code).
- ERP CSV exports under load.
- Arabic RTL on every page (sampled earlier).

### Known defects / residual risks

- Playwright **ordering-logistics** occasionally failed under concurrent suite load (Omar saw not-found) then passed alone — flake risk.
- Bundle size budget warning (~504 kB).
- Sourcing/PO/receipts nav still dossier-centred.
- Partial multi-shipment planner UX limited.
- Working calendar not a master data entity.

### Deferred / unimplemented (examples)

- Live AI hosting; MFA; messaging attachments; OCR; algorithmic 3-way match; grouping/transfer/buy-again; Trading/Enterprise tiers; CONTRACT_SIGNED / MANAGEMENT_VALIDATION writers.

### Production / live-AI gates

- Non-demo profiles; clear bootstrap secrets after first GLOBAL_ADMIN; assistant stays disabled/demo until ADR-012 live decision.

### Manual tests still requiring **your** confirmation

- Packs in `docs/qa/manual-acceptance-sessions.md`, `docs/qa/usability-manual-retest.md`, `docs/qa/final-manual-acceptance-plan.md` (S5–13).
- **Do not** mark passed unless you execute them. Prior confirmed scenarios (if any) remain limited to what you already accepted — not extended here.

---

## Related French guides (existing)

- `docs/guides/fr-administrateur-societes-utilisateurs.md`
- `docs/guides/fr-matrice-roles-permissions.md`
- `docs/guides/fr-guide-utilisateur-trouver-son-travail.md`
- `docs/product/list-sort-defaults.md`
- `docs/qa/project-handover.md`
