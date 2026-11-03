# Rapport de vérification développeur — Request Admin / délégation

**Révision** : `f516547` (livraison) + correctifs locaux de cette passe (commit de vérification ci-dessous).  
**Nature** : vérification **développeur** (API, IT SQL Server, lint/build, Playwright, navigateur/API ciblés). **Ce n’est pas** une QA indépendante ni une acceptation utilisateur.

## Tableau implémenté / vérifié / différé

| Sujet | Statut |
|---|---|
| `REQUEST_ADMIN` GLOBAL_ADMIN + API/UI admin demandes | Implémenté + vérifié (IT + API + e2e ciblé) |
| Matrice delete/cancel/archive/restore/return/correct | Implémenté + vérifié IT + API sur fiches isolées |
| Cleanup preview → execute, jeton one-shot | Implémenté + vérifié IT + API (après redémarrage BE) |
| Exclusion archivées listes/dashboards actifs | Implémenté + vérifié IT liste |
| Protection `DEMO-TN-PLT-PR-2026-000490` | Implémenté + vérifié IT / API (`DELETE` non autorisé) |
| Absence création-seulement + UI limitation | Implémenté + IT timing + i18n `policyNote` |
| Délégation Karim→Hichem (assignee/original/note) | Vérifié API : `manager2` / `manager.tn` / `ABSENCE_DELEGATE` |
| `adminOverride` documenté (ne casse pas isolation / inactif / self) | Vérifié code + guide |
| Bornes 5 000 TND `[min,max)` | Vérifié code + seed + guide |
| Inventaire utilisateurs exact (SQL `procurement_dev`) | Vérifié lecture seule |
| Nettoyage de masse des demandes existantes | **Différé / non exécuté** (volontaire) |
| Réassignation rétroactive tâches ouvertes | **Différé** (hors politique ; pas d’action inventée) |
| Métriques dashboard historiques incluant archive | **Différé** (non implémenté) |
| Annulation PO / reversal financier | **Différé** (bloqué `commitment-exists`) |
| Acceptation utilisateur des scénarios manuels | **Not executed by user** |

## Compteurs de suites (développeur)

| Suite | Résultat | Détail |
|---|---|---|
| `mvnw verify -Pdb-it` | **154 passed / 0 failed / 0 skipped** | BUILD SUCCESS (passe solo après harden Outbox + IT RequestAdmin) |
| `npm run lint` | OK | All files pass linting |
| `npm test` | **28 files / 212 tests passed** | |
| `npm run build` | OK | Budget warning initial 504.70 kB (préexistant) |
| Playwright `npm run e2e` (sous charge concurrente verify) | **24 passed / 12 failed** | Échecs surtout a11y/submit/timeout pendant `mvnw verify` parallèle + classpath BE stale |
| Re-run ciblé après redémarrage BE | **14 passed / 1 failed** (+ question AR OK à part) | a11y approvals/spec + dashboards + `request-admin` OK ; `ordering-logistics-journey` flake (Issue PO / receipt) — hors cœur REQUEST_ADMIN |
| API ciblée `/admin/requests` + absences | OK | Deny 403/404 ; correct/return/cancel/archive/restore ; cleanup execute+reuse ; `commitment-exists` ; délégation assignee/original/`ABSENCE_DELEGATE` ; UAT préservé |
| Navigateur `/admin/requests` + `/absences` | OK via e2e `request-admin.spec.ts` | Menu GLOBAL_ADMIN ; ADMIN refusé ; `policyNote` visible |

### Correctifs de cette passe

- IT `RequestAdminIntegrationTests` : motif delete ≥ 3 caractères.
- Outbox IT : isolation des lignes dues (évite famine de claim batch).
- E2E a11y approvals : purge absences Karim + recherche inbox par numéro (271+ items).
- Redémarrage BE après `mvnw` concurrent : `NoClassDefFoundError CleanupItemResult` sur cleanup execute (classpath écrasé à chaud) — **pas un défaut métier**.

## Limitations restantes

- Une seule installation/groupe démo : frontière cross-groupe via id hors groupe → 404.
- Hichem est aussi approbateur ANY : prouver la délégation par assignee / `original_user_id` / `ABSENCE_DELEGATE` / audit, pas par la seule inbox.
- Cleanup : capacité disponible ; **aucune** purge des demandes utilisateur existantes.
- Ne pas lancer `mvnw verify` et le BE `spring-boot:run` sur le même `target/classes` sans redémarrer ensuite.

## Chemins docs

- Rôles : `docs/guides/fr-matrice-roles-permissions.md`
- Users : `docs/guides/fr-utilisateurs-reels.md`
- Admin demandes : `docs/guides/fr-administration-demandes-regles.md`
- Absences : `docs/guides/fr-absences-delegation.md`
- Parcours 15 min : `docs/guides/fr-parcours-manuel-15min.md`
- Scénarios longs : `docs/guides/fr-scenarios-manuels-admin-delegation.md`
- Rapport livraison : `docs/guides/fr-rapport-admin-demandes-delegation.md`

## Rôles et utilisateurs (exact)

Voir `fr-utilisateurs-reels.md`. Points clés :

- `global.admin@demo.local` → `GLOBAL_ADMIN` → `REQUEST_ADMIN`
- `admin@demo.local` → `ADMIN` **groupe** (pas « company admin ») → **pas** `REQUEST_ADMIN`
- `manager.tn@demo.local` (Karim) / `manager2.tn@demo.local` (Hichem) → `DEPARTMENT_MANAGER` DEMO-MAINT (ANY)

## URLs courantes

- Frontend : http://localhost:4300  
- Backend : http://localhost:8081  
- Admin demandes : http://localhost:4300/admin/requests  
- Absences : http://localhost:4300/absences  

## Premier test recommandé (≈ 15 min)

Suivre `fr-parcours-manuel-15min.md` sections B puis C.  
**Résultat réel** : Not executed by user jusqu’à votre confirmation.
