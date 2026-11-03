# Rapport — administration des demandes, archive, délégation

## Actions implémentées

- Permission **`REQUEST_ADMIN`** pour `GLOBAL_ADMIN` uniquement (groupe / installation).
- API `/api/v1/admin/requests` : recherche, détail + liens, matrice d’actions, archive/restore, cancel, return-for-revision, correct-draft, delete draft, cleanup preview/execute.
- Migration **V16** : `archived_at` / `archived_by_user_id` / `archive_reason`.
- Listes métier actives excluent les archivées.
- UI `/admin/requests` (+ détail), i18n EN/FR/AR.
- `Absences.requireAdmin` : ADMIN **ou** GLOBAL_ADMIN (alignement règles / absences).
- Matrice explicite `RequestAdminActions` ; référence protégée `DEMO-TN-PLT-PR-2026-000490`.
- Aucun nettoyage automatique des données existantes.

## Permissions changées

| Permission | Qui | Pourquoi |
|---|---|---|
| `REQUEST_ADMIN` | GLOBAL_ADMIN | Admin contrôlé des demandes sans devenir buyer/approver |
| `APPROVAL_ADMIN` (déjà) | GLOBAL_ADMIN | Cohérent avec nav SYSTEM_ADMIN ; API absences/règles via `requireAdmin` |

`IMPORT_RUN` n’est **pas** ajouté automatiquement à GLOBAL_ADMIN.

## Délégation — constats / correctifs

- Politique actuelle : délégation d’absence appliquée à la **création** de tâche ; pas de réassignation rétroactive.
- Correctif : GLOBAL_ADMIN peut administrer absences/règles (`requireAdmin`).
- Boucles / hops / self-approval / cross-company : inchangés, documentés dans `fr-absences-delegation.md`.

## Guides FR

- `docs/guides/fr-matrice-roles-permissions.md` (A)
- `docs/guides/fr-utilisateurs-reels.md` (B)
- `docs/guides/fr-administration-demandes-regles.md` (C)
- `docs/guides/fr-absences-delegation.md` (D)
- `docs/guides/fr-scenarios-manuels-admin-delegation.md` (E)

## Limitations restantes

- Pas de workflow d’annulation PO / reversal financier.
- Pas de réassignation auto des tâches ouvertes au début d’absence (décision métier éventuelle).
- Cleanup preview en mémoire (nœud unique).

## Tests développeur (cette livraison)

| Suite | Résultat |
|---|---|
| `RequestAdminActionsTest`, `MeAssemblerRequestAdminTest`, Architecture, Modularity | OK |
| `mvnw verify -Pdb-it` (SQL Server) | OK |
| Frontend `lint` + `test` + `build` | OK |
| E2E Playwright complet | Non rejoué dans cette passe (régression antérieure déjà traitée) — **à rejouer** après redémarrage |
| Checks navigateur ciblés admin/délégation | À faire manuellement (scénarios guide E) |
| Scan secrets sur fichiers ajoutés | OK (pas de mots de passe / jetons) |

Révision testée : `ed6ed6b` — *Add GLOBAL_ADMIN request administration with archive and safe cleanup preview.*

## URLs locales

- Frontend : http://localhost:4300 (proxy → 8081)
- Backend : http://localhost:8081
- Admin demandes : http://localhost:4300/admin/requests
- Health : http://localhost:8081/actuator/health (si exposé)
