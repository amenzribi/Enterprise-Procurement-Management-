# Matrice rôles et permissions (catalogue implémenté)

Source : rôles Java `Role` et permissions exposées par `/api/v1/me`. L’UI ne ré-invente pas les droits — le serveur décide.

## Rôles

| Rôle | Périmètre | Permet (résumé) | Ne remplace pas |
|---|---|---|---|
| REQUESTER | Société | Créer / soumettre / suivre ses demandes | Approbation, achats |
| ENTER_ON_BEHALF | Société | Saisir pour un autre demandeur | Approbation automatique |
| DEPARTMENT_MANAGER | Société | Approuver si la règle d’approbation assigne ce rôle | File d’achats |
| BUDGET_APPROVER | Société | Idem, étape budget | — |
| MANAGEMENT_APPROVER | Société | Idem, étape direction | — |
| BUYER | Société | Traiter dossiers assignés (RFQ → PO → logistique) | Assigner d’autres acheteurs (manager) |
| PURCHASING_MANAGER | Société | File d’achats, assignation, actions sans assignation | GLOBAL_ADMIN métier |
| RECEIVER | Société | Réception sur dossiers livrés | Clôture / paiement |
| GROUP_MANAGEMENT | Groupe | Vues de gestion / tableaux de bord | Rôles opérationnels |
| ADMIN | Groupe | Admin société/organisation dans le groupe | Accès données métier total |
| GLOBAL_ADMIN | Installation | Sociétés, utilisateurs, mots de passe, règles système | Accès automatique à toutes les demandes |
| SUPPLIER_CONTACT | Portail | Messagerie fournisseur uniquement | Compte interne |

## Combinaisons

- **Autorisées / usuelles** : REQUESTER + un rôle d’approbation ; BUYER + PURCHASING_MANAGER ; ADMIN + rôles société selon besoin.
- **À éviter** : un seul compte avec tous les rôles « pour faciliter les tests » en production — cela masque les vrais contrôles.
- **Interdit via Utilisateurs** : créer SUPPLIER_CONTACT (invitation messagerie).
- **GLOBAL_ADMIN** : administration technique ; pas un substitut à BUYER / RECEIVER / REQUESTER.

## Permissions UI (exemples)

| Permission | Usages typiques |
|---|---|
| REQUEST_CREATE | Nouvelle demande |
| APPROVAL_ACT | Boîte Approvals / Mon travail |
| PURCHASING_ACT | Boîte Achats, Logistique, actions dossier |
| RECEIPT_ACT | Réception sur dossier |
| USER_ADMIN / ORG_ADMIN | Utilisateurs / Organisation |
| MESSAGING_BUYER / MESSAGING_SUPPLIER | Messages |
| ASSISTANT_USE | Assistant (mode DEMO local) |
| DASHBOARD_VIEW | Tableaux de bord |
