# Matrice rôles et permissions (catalogue implémenté)

Source : `Role`, `MeAssembler`, contrôles serveur. L’UI ne ré-invente pas les droits.

## Rôles exacts

| Code | Périmètre réel | Permissions UI typiques | Ne remplace pas |
|---|---|---|---|
| `REQUESTER` | Société (grant) | `REQUEST_CREATE` | Approbation, achats |
| `ENTER_ON_BEHALF` | Société | `REQUEST_CREATE`, `REQUEST_ENTER_ON_BEHALF` | Auto-approbation |
| `DEPARTMENT_MANAGER` | Société + département | `APPROVAL_ACT`, `REQUEST_VIEW_DEPARTMENT`, `DASHBOARD_VIEW` | File d’achats |
| `BUDGET_APPROVER` | Société | `APPROVAL_ACT` | — |
| `MANAGEMENT_APPROVER` | Société | `APPROVAL_ACT` | — |
| `BUYER` | Société (buying) | `PURCHASING_ACT`, `MESSAGING_BUYER`, `ASSISTANT_USE`, `SUPPLIER_READ` | Assignation globale |
| `PURCHASING_MANAGER` | Société | + `PURCHASING_MANAGE`, `SUPPLIER_MANAGE`, `IMPORT_RUN`, `ERP_EXPORT` | GLOBAL_ADMIN métier |
| `RECEIVER` | Société | `RECEIPT_ACT` | Clôture / paiement |
| `GROUP_MANAGEMENT` | Groupe | `REQUEST_VIEW_GROUP`, `DASHBOARD_VIEW`, `ERP_EXPORT`, `SUPPLIER_READ` | Rôles opérationnels |
| `ADMIN` | **Société** (`company_id` obligatoire ; une grant par société) | `USER_ADMIN`, `SYSTEM_ADMIN`, `APPROVAL_ADMIN`, `ORG_ADMIN`, `IMPORT_RUN`, `SUPPLIER_*`, **`REQUEST_DRAFT_ADMIN`** | Actions élevées demandes (`REQUEST_ADMIN`) ; création de sociétés |
| `GLOBAL_ADMIN` | Installation / groupe | ci-dessus admin + **`REQUEST_ADMIN`** + `REQUEST_DRAFT_ADMIN` (+ `USER_ADMIN_GLOBAL`) | Buyer / approver / messaging / assistant |
| `SUPPLIER_CONTACT` | Portail | `MESSAGING_SUPPLIER` | Compte interne |

`ADMIN` est désormais **company-scoped** partout (migration V17 + seeder). Les nouvelles sociétés ne reçoivent **pas** automatiquement un ADMIN ; seul `GLOBAL_ADMIN` attribue.

## Couverture ADMIN vs GLOBAL_ADMIN

| Module | Voir | Gérer | Corriger | Retirer |
|---|---|---|---|---|
| Organisation (sites/dépts/CC) | Sociétés autorisées | Sociétés autorisées | — | — |
| Création de société | — | **GLOBAL_ADMIN seulement** | — | — |
| Utilisateurs / grants | Sociétés autorisées | Sociétés autorisées ; pas `GLOBAL_ADMIN`/`ADMIN`/`GROUP_MANAGEMENT` | — | — |
| Règles / absences / import org | Sociétés autorisées | Sociétés autorisées | — | — |
| Demandes brouillon | Sociétés autorisées | Inspecter / corriger / supprimer éligible | Oui (`CORRECT_DRAFT`) | Hard-delete brouillon seulement |
| Demandes soumises / remove / archive / cancel / cleanup | Groupe (GLOBAL_ADMIN) | **GLOBAL_ADMIN** | `CORRECT_SUBMITTED` | `REMOVE` logique |

## `REQUEST_ADMIN` / `REQUEST_DRAFT_ADMIN`

- `REQUEST_ADMIN` : uniquement `GLOBAL_ADMIN` (correction soumise, remove, archive, cancel, return, cleanup).
- `REQUEST_DRAFT_ADMIN` : `GLOBAL_ADMIN` **ou** `ADMIN` avec ≥1 grant société (inspecter / corriger / supprimer brouillons dans le périmètre).
- Frontière : `groupId` + sociétés autorisées pour ADMIN.
- Pas d’`APPROVAL_ACT` / `PURCHASING_ACT` silencieux.

## Montants d’approbation Sfax

Bandes **`[min_amount, max_amount)`** en TND société. **Exactement 5 000 TND** → règle « Sfax 5 000 to 50 000 » (département **puis** budget), pas « up to 5 000 ».

## Import

`IMPORT_RUN` : `ADMIN` / `PURCHASING_MANAGER` (et équivalents). Import organisation : société cible dans le périmètre ADMIN. **Pas** automatique pour `GLOBAL_ADMIN` seul.

## Combinaisons

Usuelles : REQUESTER + approbateur ; BUYER + PURCHASING_MANAGER. Éviter un compte « tous les rôles ». `SUPPLIER_CONTACT` via messagerie, pas Users.
