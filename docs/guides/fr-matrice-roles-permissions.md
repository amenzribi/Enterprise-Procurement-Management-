# Matrice rôles et permissions (catalogue implémenté)

Source : rôles Java `Role`, permissions exposées par `GET /api/v1/me` (`MeAssembler`), contrôles serveur. L’UI n’invente pas les droits.

## Rôles exacts

| Code rôle | Périmètre | Permissions UI typiques | Ne remplace pas |
|---|---|---|---|
| `REQUESTER` | Société (grant) | `REQUEST_CREATE` | Approbation, achats |
| `ENTER_ON_BEHALF` | Société | `REQUEST_CREATE`, `REQUEST_ENTER_ON_BEHALF` | Auto-approbation |
| `DEPARTMENT_MANAGER` | Société / département | `APPROVAL_ACT`, `REQUEST_VIEW_DEPARTMENT`, `DASHBOARD_VIEW` | File d’achats |
| `BUDGET_APPROVER` | Société | `APPROVAL_ACT` | — |
| `MANAGEMENT_APPROVER` | Société | `APPROVAL_ACT` | — |
| `BUYER` | Société (buying) | `PURCHASING_ACT`, `MESSAGING_BUYER`, `ASSISTANT_USE`, `SUPPLIER_READ`, `DASHBOARD_VIEW` | Assignation globale |
| `PURCHASING_MANAGER` | Société | ci-dessus + `PURCHASING_MANAGE`, `SUPPLIER_MANAGE`, `IMPORT_RUN`, `ERP_EXPORT` | GLOBAL_ADMIN métier |
| `RECEIVER` | Société | `RECEIPT_ACT` | Clôture / paiement |
| `GROUP_MANAGEMENT` | Groupe | `REQUEST_VIEW_GROUP`, `DASHBOARD_VIEW`, `ERP_EXPORT`, `SUPPLIER_READ` | Rôles opérationnels |
| `ADMIN` | Groupe | `USER_ADMIN`, `SYSTEM_ADMIN`, `APPROVAL_ADMIN`, `ORG_ADMIN`, `IMPORT_RUN`, `SUPPLIER_*` | Accès total aux demandes métier |
| `GLOBAL_ADMIN` | Installation / groupe | `USER_ADMIN`, `USER_ADMIN_GLOBAL`, `ORG_ADMIN`, `SYSTEM_ADMIN`, `APPROVAL_ADMIN`, **`REQUEST_ADMIN`** | Buyer / approver / messaging / assistant |
| `SUPPLIER_CONTACT` | Portail | `MESSAGING_SUPPLIER` | Compte interne |

## Permission `REQUEST_ADMIN` (nouvelle)

- Accordée **uniquement** à `GLOBAL_ADMIN` via `MeAssembler`.
- Frontière serveur : `CurrentUser.groupId()` (groupe d’organisation / installation). Hors groupe → 404.
- Permet : recherche admin, inspection des liens, archivage / restauration, annulation pré-engagement, retour pour révision, correction limitée de brouillon, suppression de brouillon éligible, prévisualisation / exécution de nettoyage.
- **Ne confère pas** : `APPROVAL_ACT`, `PURCHASING_ACT`, messagerie fournisseur, assistant. L’admin n’entre pas silencieusement dans un circuit d’approbation ni une file acheteur.

## `APPROVAL_ADMIN` / règles / absences

- UI : règles d’approbation et taux de change sous `SYSTEM_ADMIN` (nav).
- API : `Absences.requireAdmin` accepte `Role.ADMIN` **ou** `GLOBAL_ADMIN` (alignement UI/API).
- Import master-data : `IMPORT_RUN` pour `ADMIN` / `PURCHASING_MANAGER` — **pas** automatiquement pour `GLOBAL_ADMIN` (sauf s’il détient aussi un rôle qui l’accorde). Documenter clairement : GLOBAL_ADMIN gère org/users/règles/REQUEST_ADMIN ; l’import reste un droit métier distinct.

## Combinaisons

- **Usuelles** : REQUESTER + rôle d’approbation ; BUYER + PURCHASING_MANAGER.
- **À éviter** : un seul compte « tous les rôles » en production.
- **Interdit via Utilisateurs** : créer `SUPPLIER_CONTACT` (invitation messagerie).
- **GLOBAL_ADMIN** : administration technique + REQUEST_ADMIN contrôlé ; pas un substitut acheteur/approbateur.

## Société vs groupe

Ne pas déduire le périmètre du seul nom de rôle : les grants portent `companyId` / `departmentId`. Les tests cross-company restent obligatoires (ADR-007).
