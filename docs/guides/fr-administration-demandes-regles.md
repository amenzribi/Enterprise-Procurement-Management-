# Règles d’administration des demandes

Frontière serveur : `group_id` +, pour `ADMIN`, sociétés des grants. Hors périmètre → 404. Ne confère pas buyer / approver / messagerie / assistant.

UI : **Administration → Administration des demandes** (`/admin/requests`) — visible avec `REQUEST_DRAFT_ADMIN` ou `REQUEST_ADMIN`.

## Verbes

| Action | Qui | Effet | Ne fait pas |
|---|---|---|---|
| **Corriger brouillon** | GLOBAL_ADMIN ou ADMIN (société) | Même champs que le formulaire métier + motif audité | Contourner la validation |
| **Corriger soumis** | GLOBAL_ADMIN | Applique les champs, annule pending + liens, **nouveau tour** d’approbation | Réécrire PO / réception / facture |
| **Retirer (logique)** | GLOBAL_ADMIN | `removed_*` ; masque listes actives ; annule pending | Orpheliner un engagement ; = archive |
| **Rétablir retrait** | GLOBAL_ADMIN | Efface `removed_*` | Recréer les tâches pending obsolètes |
| **Supprimer** | GLOBAL_ADMIN ou ADMIN (société) | Retrait permanent d’un brouillon éligible + audit | Réutiliser un numéro |
| **Annuler** | GLOBAL_ADMIN | `CANCELLED` ; annule pending / liens | Annuler un PO |
| **Archiver** | GLOBAL_ADMIN | Quitte listes actives (`archived_at`) — **distinct** du retrait | Changer le statut métier |
| **Restaurer archive** | GLOBAL_ADMIN | Revient dans les listes | Rouvrir un état terminal |
| **Renvoyer pour révision** | GLOBAL_ADMIN | `REVISION_REQUESTED` + annulation pending | Contourner l’auto-approbation |

Référence protégée : `DEMO-TN-PLT-PR-2026-000490`.

## Matrice (résumé)

| État | Corriger brouillon | Corriger soumis | Retirer | Retour | Annuler | Archiver | Supprimer |
|---|---|---|---|---|---|---|---|
| DRAFT | Oui | Non | Non | Non | Non | Non | Oui si éligible |
| CREATED / IN_APPROVAL / REVISION_REQUESTED (pré-engagement) | Non | Oui (GA) | Oui (GA) | Oui | Oui | Non | Non |
| ORDERED+ / award / PO / shipment / receipt / invoice | Non | Bloqué | Bloqué | Non | Bloqué | Non | Non |
| REJECTED / CANCELLED / CLOSED | Non | Non | Selon engagement | Non | Non | Oui | Non |
| Retiré (`removed_at`) | Non | Non | Non | Non | Non | Non | Non — `RESTORE_REMOVED` |
| Archivé | Non | Non | Non | Non | Non | Non — `RESTORE` | Non |

## Conséquences d’une correction soumise

1. Snapshot before/after + motif + tours (audit `REQUEST_ADMIN_SUBMITTED_CORRECTED`).
2. Annulation des tâches pending et invalidation des liens one-tap inutilisés.
3. `nextRound()` + `RequestSubmittedEvent` → nouveau circuit ; décisions des tours précédents restent historiques.
4. Post-engagement : **pas** de workflow d’amendement versionné des engagements (écart documenté).

## Retrait vs archive vs suppression

| | Retrait logique | Archive | Suppression dure |
|---|---|---|---|
| Cible | Demandes soumises sans engagement | Terminal (CLOSED/CANCELLED/REJECTED) | Brouillons éligibles seulement |
| Listes actives | Masquée | Masquée | Disparue |
| Historique / numéro | Conservés | Conservés | Brouillon détruit ; audit admin conservé |
| Pending | Annulés | Déjà terminaux en principe | N/A |

## Listes

| Surface | Archivées / retirées |
|---|---|
| `/api/v1/requests`, file achats, dashboards | **Exclues** (`archived_at` et `removed_at` nuls) |
| Admin `archived` / `removed` | Filtres explicites |

## Nettoyage (GLOBAL_ADMIN)

1. Preview (max 50) — aucune mutation.
2. Execute one-shot + motif.
3. Pas de nettoyage de masse des données métier existantes (UAT `…000490` préservée).
