# Règles d’administration des demandes (REQUEST_ADMIN)

Frontière : **groupe d’organisation** du `GLOBAL_ADMIN` (`group_id`). Hors groupe → 404. Pas d’accès silencieux buyer/approver.

UI : **Administration → Administration des demandes** (`/admin/requests`).

## Signification des verbes

| Action | Effet | Ne fait pas |
|---|---|---|
| **Supprimer (Delete)** | Retrait permanent d’un brouillon éligible | Réutiliser le numéro ; supprimer l’audit admin |
| **Annuler (Cancel)** | Passe en `CANCELLED`, annule tâches d’approbation en attente et liens one-tap inutilisés | Annuler un PO, inverser réception/paiement |
| **Archiver (Archive)** | Quitte les listes actives (`archived_at`) ; historique conservé | Changer le statut métier ; « disparaitre » une tâche ouverte sans explication |
| **Restaurer** | Retire l’archive ; revient dans les listes | Rouvrir un état annulé/clôturé |
| **Renvoyer pour révision** | `REVISION_REQUESTED` + annulation des tâches pending | Contourner les règles d’auto-approbation au resoumission |

## Matrice par état (résumé)

| État | Voir | Corriger | Retour | Annuler | Archiver | Restaurer | Supprimer |
|---|---|---|---|---|---|---|---|
| DRAFT | Oui | Titre/justification + motif | Non | Non (utiliser Delete) | Non | Non | Oui si aucun lien métier / pas de n° / pas protégé |
| CREATED / IN_APPROVAL | Oui | Non (pas de rewrite silencieux) | Oui | Oui | Non | Non | Non |
| REVISION_REQUESTED / PURCHASING_RETURNED | Oui | Non via admin général | Non | Oui (si &lt; ORDERED et sans engagement) | Non | Non | Non |
| APPROVED … AWARDED (pré-ORDERED, sans PO/award) | Oui | Non | Non | Oui si pas d’engagement lié | Non | Non | Non |
| ORDERED et après / avec PO, shipment, receipt, invoice | Oui | Non | Non | **Bloqué** (pas de workflow financier inventé) | Non | Non | Non |
| REJECTED / CANCELLED / CLOSED | Oui | Non | Non | Non si déjà terminal | Oui | Après archive | Non |

Référence protégée : `DEMO-TN-PLT-PR-2026-000490` — suppression définitive toujours refusée.

## Listes et métriques

- Listes métier quotidiennes (`/api/v1/requests`, file achats, vues visibles) : **excluent** `archived_at IS NOT NULL`.
- Administration : filtre archive = toutes / actives / archivées.
- Tableaux de bord : les agrégats basés sur les mêmes files actives **n’incluent pas** les archivées ; documenter toute métrique historique séparément si ajoutée plus tard.

## Audit

Chaque mutation admin écrit un événement (`REQUEST_ARCHIVED`, `REQUEST_RESTORED`, `REQUEST_ADMIN_CANCELLED`, `REQUEST_ADMIN_RETURNED`, `REQUEST_ADMIN_DRAFT_CORRECTED`, `REQUEST_ADMIN_DRAFT_DELETED`) avec acteur, motif, avant/après sans secrets.

## Nettoyage (preview)

1. Filtrer / sélectionner → `POST /cleanup/preview` (max 50).
2. Relire impact + actions recommandées.
3. Confirmer `confirmation=APPLY` + motif → `POST /cleanup/execute`.
4. Jeton one-shot (pas de double traitement).
5. **Aucun** nettoyage automatique au démarrage ni en développement.
