# Rapport de vérification — politique admin révisée (V17)

Date : 2026-11-04. Périmètre : company-scoped ADMIN + administration demandes (brouillon / soumis / retrait logique). Pas de push ; pas de nettoyage de masse ; UAT `DEMO-TN-PLT-PR-2026-000490` préservée.

## Implémenté

| Élément | Statut |
|---|---|
| Flyway `V17__admin_company_scope_and_request_admin.sql` | Implémenté (expand ADMIN NULL → sociétés du groupe ; `ADMIN` exige `company_id` ; colonnes `removed_*`) |
| `Role.isGroupWide()` sans ADMIN ; seeder grants par société | Implémenté |
| `REQUEST_DRAFT_ADMIN` + `REQUEST_ADMIN` | Implémenté (`MeAssembler`) |
| Org / users / rules / absences / import company-scope | Implémenté |
| Correct draft (payload complet) + UI formulaire | Implémenté |
| Correct submitted + nouveau tour + audit | Implémenté |
| Remove / restore-removed (distinct archive / hard-delete) | Implémenté |
| i18n EN/FR/AR labels actions | Implémenté |

## Non supporté (écarts documentés)

- Amendement versionné PO / shipment / receipt / invoice après engagement.
- Recréation automatique des tâches après `RESTORE_REMOVED`.
- Auto-grant ADMIN sur nouvelles sociétés.

## Vérification (session 2026-11-04)

| Contrôle | Résultat | Notes |
|---|---|---|
| `mvnw verify -Pdb-it` | RequestAdmin / Actions / MeAssembler / Auth restore / UserAdmin / Purchasing / Logistics **OK** | Suite complète : 1 flake hors périmètre `ApprovalIntegrationTests.usdRequest…` (`link-invalid` après ~135 s) — **re-run isolé OK** ; Logistics 3/3 OK |
| `npm run lint` | **OK** | |
| `npm test` | **OK** 212 tests | |
| `npm run build` | **OK** | Warning budget initial +4.87 kB (préexistant / mineur) |
| Playwright `request-admin.spec.ts` | **Bloqué** sans BE V17 redémarré | Spec mis à jour ; run local a échoué (heading admin absent — BE courant probablement antérieur à `REQUEST_DRAFT_ADMIN`). Relancer après `spring-boot:run` profils `dev,demo` |
| Acceptation manuelle Mohamed | **En attente** | Parcours § D `fr-parcours-manuel-15min.md` |

## SQL migration report (seed)

Voir tableau ADMIN dans `fr-utilisateurs-reels.md`. Sur `procurement_dev`, après Flyway V17, relancer la requête SQL documentée pour confirmer l’absence de `ADMIN` à `company_id` NULL.
