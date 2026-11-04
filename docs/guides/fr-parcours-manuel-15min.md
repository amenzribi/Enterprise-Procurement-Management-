# Parcours manuel court (≈ 15 min)

Format : Compte → menu/URL → champs → bouton → **attendu** → prochain responsable.  
**Résultat réel** : Not executed by user (jusqu’à votre confirmation).

## A. Mot de passe / utilisateur (2–3 min)

1. `global.admin@demo.local` → `/admin/users` → créer un utilisateur test ou ouvrir un existant → « Send password reset » / mot de passe temporaire.  
   **Attendu** : file mail locale démo ; pas de secret réaffiché.  
   **Prochain** : le nouvel utilisateur sur `/p` ou changement obligatoire.  
   **Résultat réel** : Not executed by user

2. Compte cible → `/account/password` → ancien / nouveau / confirmer → Save.  
   **Attendu** : succès ; autres sessions révoquées.  
   **Résultat réel** : Not executed by user

## B. Administration demande — brouillon (2–3 min)

1. `global.admin@demo.local` → `/admin/requests` → chercher un **brouillon isolé de test** (pas UAT-01).  
   **Attendu** : matrice d’actions avec explications.  
   **Résultat réel** : Not executed by user

2. Ouvrir la fiche → motif → formulaire « Corriger le brouillon » (titre + lignes si besoin) → **Correct draft**.  
   **Attendu** : champs/lignes mis à jour ; audit `REQUEST_ADMIN_DRAFT_CORRECTED` (acteur, motif, before/after).  
   **Résultat réel** : Not executed by user

3. `admin@demo.local` (ou ADMIN limité DEMO-TN-PLT) → même brouillon DEMO-TN-PLT → **Correct draft**.  
   **Attendu** : succès dans le périmètre société ; un brouillon FR/CPA est invisible (404).  
   **Résultat réel** : Not executed by user

## C. Correction d’une demande soumise (GLOBAL_ADMIN seulement) (3 min)

Scénario **distinct** de la suppression logique — ne pas les combiner.

1. Préparer une demande **IN_APPROVAL** de test (pas UAT-01) avec des tâches PENDING.  
   **Résultat réel** : Not executed by user

2. `admin@demo.local` → tenter **Correct submitted** / **Remove**.  
   **Attendu** : refusé (403) — réservé `REQUEST_ADMIN` / `GLOBAL_ADMIN`.  
   **Résultat réel** : Not executed by user

3. `global.admin@demo.local` → ouvrir la demande → motif clair → modifier titre/lignes → **Correct submitted** (confirmation nouveau tour).  
   **Attendu** :
   - statut reste / repasse `IN_APPROVAL` ;
   - `round_no` incrémenté ;
   - anciennes tâches PENDING invalidées ; nouveau round avec nouvelles tâches ;
   - liens d’approbation obsolètes inutilisables ;
   - audit `REQUEST_ADMIN_SUBMITTED_CORRECTED` (acteur, motif, before/after, priorRound/newRound).  
   **Résultat réel** : Not executed by user

## D. Suppression logique puis restauration (GLOBAL_ADMIN) (3 min)

Deux comportements **séparés** — tester remove, puis restore sur la même fiche.

### D1. Suppression logique (Remove)

1. `global.admin@demo.local` → demande active de test → motif → **Remove**.  
   **Attendu** :
   - `removed=true` ; hors listes actives du demandeur / travail courant ;
   - tâches PENDING annulées ;
   - numéro et historique conservés (≠ Archive, ≠ Delete permanent) ;
   - audit `REQUEST_ADMIN_REMOVED`.  
   **Résultat réel** : Not executed by user

### D2. Restauration (Restore removed)

2. Même fiche (filtre admin `removed=true` si besoin) → motif → **Restore removed**.  
   **Attendu** :
   - `removed=false` ;
   - **aucune** réactivation silencieuse des anciennes tâches / liens PENDING ;
   - pour reprendre le circuit : return/resubmit ou Correct submitted ;
   - audit `REQUEST_ADMIN_RESTORE_REMOVED`.  
   **Résultat réel** : Not executed by user

## E. Cleanup / protection UAT (1–2 min)

1. Preview cleanup → cocher uniquement un brouillon de test → confirmer APPLY.  
   **Attendu** : résultat DELETED ; jeton non réutilisable ; UAT-01 jamais proposé en delete.  
   **Résultat réel** : Not executed by user

2. Ouvrir `DEMO-TN-PLT-PR-2026-000490` → tenter Delete.  
   **Attendu** : bloqué (protected).  
   **Résultat réel** : Not executed by user

## F. Isolation ADMIN société (DEMO-TN-PLT seul) (2 min)

Compte dédié `admin.tn.only@demo.local` (ADMIN → DEMO-TN-PLT uniquement) — **pas** `admin@demo.local` (qui a toutes les sociétés après V17).

1. Organisation / utilisateurs / règles / absences : seulement DEMO-TN-PLT ; FR et CPA → 404 ou absents de la liste.  
   **Résultat réel** : Not executed by user

2. Tentative d’étendre son propre rôle ADMIN à DEMO-FR-PLT.  
   **Attendu** : 403.  
   **Résultat réel** : Not executed by user

## G. Absence Karim → Hichem (optionnel, 3–4 min)

1. `manager.tn@demo.local` → `/absences` → lire l’encadré de limitation → délégué Hichem → dates **aujourd’hui**.  
   **Attendu** : absence enregistrée.  
   **Résultat réel** : Not executed by user

2. `requester.tn@demo.local` → créer/soumettre une demande &lt; 5 000 TND (étage département ANY).  
   **Attendu** : sur la tâche de Karim, assignee = Hichem, « pour le compte de » Karim.  
   **Résultat réel** : Not executed by user
