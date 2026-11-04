# Inventaire utilisateurs — base locale `procurement_dev`

**Source** : inspection SQL en lecture seule le 2026-11-03 (profil `demo`).  
**Ne contient pas** : mots de passe, jetons, liens de réinitialisation.

Tous les comptes listés sont **actifs** (`is_active = 1`). Mot de passe démo : variable d’environnement locale / fichier e2e — **non documenté ici**.

## Groupe DEMO-GRP

| Affichage | E-mail (login) | Société domicile | Département domicile | Grants (rôle → société / département) | Menus / actions typiques |
|---|---|---|---|---|---|
| Global Administrator (Demo) | `global.admin@demo.local` | DEMO-HOLD | — | `GLOBAL_ADMIN` (sans société) | Org, Users, Approval rules, Exchange rates, **Request administration** (`REQUEST_ADMIN` + `REQUEST_DRAFT_ADMIN`), absences admin. **Pas** file achats / approvals métier / assistant |
| Platform Administrator (Demo) | `admin@demo.local` | DEMO-HOLD | — | `ADMIN` → DEMO-HOLD, DEMO-TN-PLT, DEMO-FR-PLT, DEMO-CPA (**company-scoped**) | Org/users/règles/taux/import **dans les sociétés accordées** ; brouillons demandes (`REQUEST_DRAFT_ADMIN`). **Pas** `REQUEST_ADMIN` (correction soumise / remove / archive / cleanup) |
| Hedi Ferchichi (Demo) | `ceo@demo.local` | DEMO-HOLD | — | `GROUP_MANAGEMENT` (groupe) ; `MANAGEMENT_APPROVER` → DEMO-TN-PLT ; `MANAGEMENT_APPROVER` → DEMO-FR-PLT | Tableaux de bord ; étapes management |
| Sami Ben Ali (Demo) | `requester.tn@demo.local` | DEMO-TN-PLT | DEMO-MAINT | `REQUESTER` → DEMO-TN-PLT | Demandes (créer/soumettre) |
| Amel Trabelsi (Demo) | `assistant.tn@demo.local` | DEMO-TN-PLT | DEMO-MAINT | `REQUESTER` + `ENTER_ON_BEHALF` → DEMO-TN-PLT | Saisie pour compte d’autrui |
| Karim Haddad (Demo) | `manager.tn@demo.local` | DEMO-TN-PLT | DEMO-MAINT | `DEPARTMENT_MANAGER` → DEMO-TN-PLT / DEMO-MAINT | Approvals / Mon travail (étage département) |
| Hichem Gharbi (Demo) | `manager2.tn@demo.local` | DEMO-TN-PLT | DEMO-MAINT | `DEPARTMENT_MANAGER` → DEMO-TN-PLT / DEMO-MAINT | Même étage ANY que Karim — **ne pas** prouver la délégation par la seule présence dans l’inbox |
| Leila Mejri (Demo) | `budget.tn@demo.local` | DEMO-TN-PLT | — | `BUDGET_APPROVER` → DEMO-TN-PLT | Étape budget (≥ 5 000 TND, borne `[5000, 50000)`) |
| Omar Mansouri (Demo) | `receiver.tn@demo.local` | DEMO-TN-PLT | DEMO-MAINT | `RECEIVER` → DEMO-TN-PLT | Réception |
| Claire Martin (Demo) | `requester.fr@demo.local` | DEMO-FR-PLT | DEMO-MAINT | `REQUESTER` → DEMO-FR-PLT | Demandes FR |
| Julien Moreau (Demo) | `manager.fr@demo.local` | DEMO-FR-PLT | DEMO-MAINT | `DEPARTMENT_MANAGER` → DEMO-FR-PLT / DEMO-MAINT | Approvals Lyon |
| Ines Jaziri (Demo) | `buyer@demo.local` | DEMO-CPA | DEMO-PURCH | `BUYER` → DEMO-CPA | File achats, sourcing, messagerie, assistant |
| Mehdi Khelifi (Demo) | `purchasing.manager@demo.local` | DEMO-CPA | DEMO-PURCH | `PURCHASING_MANAGER` → DEMO-CPA | File + assignation + import |
| SKF Contact (Demo) | `contact.skf@demo.local` | DEMO-CPA | DEMO-PURCH | `SUPPLIER_CONTACT` | Portail messagerie fournisseur uniquement |

## UAT à préserver

| Référence | Titre | Statut constaté | Archivé |
|---|---|---|---|
| `DEMO-TN-PLT-PR-2026-000490` | UAT-01 — Pump seals | `CLOSED` | non |

## Clarification ADMIN (V17)

`ADMIN` **exige** `company_id`. Migration V17 : chaque ancienne grant `ADMIN` à `company_id` NULL a été **étendue** en une grant explicite par société du groupe du compte, puis la ligne NULL a été supprimée. Les **nouvelles** sociétés ne reçoivent pas automatiquement un ADMIN — attribution par `GLOBAL_ADMIN` seulement.

### Rapport comptes ADMIN (seed DEMO-GRP)

| E-mail | Grants ADMIN résultantes |
|---|---|
| `admin@demo.local` | DEMO-HOLD, DEMO-TN-PLT, DEMO-FR-PLT, DEMO-CPA |

Vérifier en SQL : `SELECT ua.email, c.code FROM user_role ur JOIN user_account ua ON ua.id = ur.user_id JOIN company c ON c.id = ur.company_id WHERE ur.role = N'ADMIN' ORDER BY 1, 2;`
