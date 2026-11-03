# Utilisateurs réels (référence)

**Ne pas y mettre** : mots de passe, jetons, liens de réinitialisation.

## Comptes seed / démo (profil `demo`)

Les comptes ci-dessous sont des **valeurs de seed** documentées dans les scripts `db/demo`. L’état exact en base locale peut différer après UAT — vérifier via **Administration → Utilisateurs** ou SQL lecture seule.

| Compte (e-mail type démo) | Affichage typique | Rôles attendus | Société / service | Notes |
|---|---|---|---|---|
| Compte GLOBAL_ADMIN démo | Admin global | `GLOBAL_ADMIN` | Groupe démo | `REQUEST_ADMIN` via MeAssembler |
| Compte ADMIN société | Admin société | `ADMIN` | Groupe démo | Org / users groupe ; pas REQUEST_ADMIN |
| Karim (approbateur) | Karim … | Rôle d’approbation (ex. DEPARTMENT_MANAGER) | Sfax / règles ≤ 5000 TND | Scénarios absence / délégation |
| Hichem (délégué) | Hichem … | Rôle d’approbation compatible | Même société que Karim | Délégué éligible |
| Acheteur / PM démo | — | `BUYER` / `PURCHASING_MANAGER` | Buying company | File achats |
| Demandeur démo | — | `REQUESTER` | Société demandeuse | UAT-01 seals |

## Enregistrement UAT à préserver

| Référence | Titre | Action |
|---|---|---|
| `DEMO-TN-PLT-PR-2026-000490` | UAT-01 — Pump seals | **Ne jamais** supprimer définitivement (protégé côté serveur) |

## Distinction seed vs base vérifiée

- **Seed** : scripts Flyway demo / documentation produit.
- **Base vérifiée** : à remplir lors de vos tests manuels (colonne « constaté le … ») — non exécuté automatiquement ici.

| Compte | Constaté en base | Date | Notes |
|---|---|---|---|
| _(à compléter par l’utilisateur)_ | Not executed by user | — | — |
