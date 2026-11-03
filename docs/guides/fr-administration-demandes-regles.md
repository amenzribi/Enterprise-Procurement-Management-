# Règles d’administration des demandes (`REQUEST_ADMIN`)

Frontière serveur : `group_id` du `GLOBAL_ADMIN`. Hors groupe → 404. Ne confère pas buyer / approver / messagerie / assistant.

UI : **Administration → Administration des demandes** (`/admin/requests`).

## Verbes

| Action | Effet | Ne fait pas |
|---|---|---|
| **Supprimer** | Retrait permanent d’un brouillon éligible + audit | Réutiliser un numéro ; casser la chaîne d’audit |
| **Annuler** | `CANCELLED` ; annule tâches pending et invalide liens one-tap inutilisés | Annuler un PO / inverser réception-paiement |
| **Archiver** | Quitte listes actives (`archived_at`) | Changer le statut métier |
| **Restaurer** | Revient dans les listes actives | Rouvrir un état annulé/clôturé |
| **Renvoyer pour révision** | `REVISION_REQUESTED` + annulation pending | Contourner l’auto-approbation au resoumission |

Référence protégée : `DEMO-TN-PLT-PR-2026-000490`.

## Matrice (résumé)

Voir aussi tests `RequestAdminActionsTest` / `RequestAdminIntegrationTests`.

| État | Corriger | Retour | Annuler | Archiver | Supprimer |
|---|---|---|---|---|---|
| DRAFT | Oui (titre/justification + motif) | Non | Non | Non | Oui si éligible |
| CREATED / IN_APPROVAL | Non | Oui | Oui | Non | Non |
| Pré-ORDERED sans engagement | Non | Selon statut | Oui si pas d’engagement | Non | Non |
| ORDERED+ / PO / shipment / receipt / invoice | Non | Non | Bloqué | Non | Non |
| REJECTED / CANCELLED / CLOSED | Non | Non | Non | Oui | Non |

## Listes, badges, tableaux de bord

| Surface | Archivées |
|---|---|
| `/api/v1/requests`, file achats, vues visibles | **Exclues** |
| Badges inbox (approvals / purchasing) | Basés sur tâches / files actives — une demande archivée ne doit plus générer de travail ouvert |
| Tableaux de bord (`RequestWorkflow.searchVisible`) | **Métriques actives** : archivées exclues |
| Métriques historiques incluant l’archive | **Non implémentées** à part — l’admin archive sert à les consulter via filtre `archived=true` |

## Nettoyage

1. `POST .../cleanup/preview` (max 50) — **aucune** mutation.
2. Relecture + sélection.
3. `POST .../cleanup/execute` avec `confirmation=APPLY` + motif ; jeton **one-shot**.
4. Résultats par enregistrement (DELETED / CANCELLED / ARCHIVED / SKIPPED / BLOCKED).

**État livraison** : le flux preview→exécution est implémenté et testé sur des **fiches isolées**. **Aucun** nettoyage de masse des demandes existantes de l’utilisateur n’a été exécuté.
