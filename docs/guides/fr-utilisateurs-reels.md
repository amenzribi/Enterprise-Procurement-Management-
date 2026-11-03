# Inventaire utilisateurs — base locale `procurement_dev`

**Source** : inspection SQL en lecture seule le 2026-11-03 (profil `demo`).  
**Ne contient pas** : mots de passe, jetons, liens de réinitialisation.

Tous les comptes listés sont **actifs** (`is_active = 1`). Mot de passe démo : variable d’environnement locale / fichier e2e — **non documenté ici**.

## Groupe DEMO-GRP

| Affichage | E-mail (login) | Société domicile | Département domicile | Grants (rôle → société / département) | Menus / actions typiques |
|---|---|---|---|---|---|
| Global Administrator (Demo) | `global.admin@demo.local` | DEMO-HOLD | — | `GLOBAL_ADMIN` (sans société) | Org, Users, Approval rules, Exchange rates, **Request administration** (`REQUEST_ADMIN`), absences admin. **Pas** file achats / approvals métier / assistant |
| Platform Administrator (Demo) | `admin@demo.local` | DEMO-HOLD | — | `ADMIN` (sans société = **groupe**) | Org/users/règles/taux/import dans le groupe. **Pas** `REQUEST_ADMIN` |
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

## Clarification ADMIN

`ADMIN` n’est **pas** « admin d’une seule société » : le grant démo est **sans `company_id`**, donc **périmètre groupe**. L’UI Users peut encore parler d’administration d’organisation ; le serveur applique le groupe du compte.
