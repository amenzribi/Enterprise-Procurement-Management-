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

## B. Administration demande (5–6 min)

1. `global.admin@demo.local` → `/admin/requests` → chercher un **brouillon isolé de test** (pas UAT-01).  
   **Attendu** : matrice d’actions avec explications.  
   **Résultat réel** : Not executed by user

2. Ouvrir la fiche → motif → « Correct draft » (titre) si DRAFT.  
   **Attendu** : titre mis à jour ; audit.  
   **Résultat réel** : Not executed by user

3. Preview cleanup → cocher uniquement ce brouillon → confirmer APPLY.  
   **Attendu** : résultat DELETED ; jeton non réutilisable ; UAT-01 jamais proposé en delete.  
   **Résultat réel** : Not executed by user

4. Ouvrir `DEMO-TN-PLT-PR-2026-000490` → tenter Delete.  
   **Attendu** : bloqué (protected).  
   **Résultat réel** : Not executed by user

## C. Absence Karim → Hichem (5–6 min)

1. `manager.tn@demo.local` → `/absences` → lire l’encadré de limitation → délégué Hichem → dates **aujourd’hui**.  
   **Attendu** : absence enregistrée.  
   **Résultat réel** : Not executed by user

2. `requester.tn@demo.local` → créer/soumettre une demande &lt; 5 000 TND (étage département ANY).  
   **Attendu** : sur la tâche de Karim, assignee = Hichem, « pour le compte de » Karim (pas seulement « Hichem voit la ligne »).  
   **Prochain** : Hichem décide dans Approvals / Mon travail.  
   **Résultat réel** : Not executed by user

3. Fin d’absence (annuler la période) → nouvelle demande.  
   **Attendu** : nouvelles tâches de nouveau chez Karim (et/ou Hichem en ANY direct) ; tâches déjà déléguées restent chez Hichem.  
   **Résultat réel** : Not executed by user

## D. ADMIN société vs GLOBAL_ADMIN (2 min)

1. `admin@demo.local` → `/admin/requests` → ouvrir un **brouillon** DEMO-TN-PLT → **Corriger le brouillon** (titre + motif) → enregistrer.  
   **Attendu** : succès ; audit `REQUEST_ADMIN_DRAFT_CORRECTED`.  
   **Résultat réel** : Not executed by user

2. Sur une demande **IN_APPROVAL** : tenter Correct submitted / Remove.  
   **Attendu** : refusé (403) — réservé `GLOBAL_ADMIN`.  
   **Résultat réel** : Not executed by user

3. `global.admin@demo.local` → même demande → Correct submitted (confirmation nouveau tour) ou Remove.  
   **Attendu** : nouveau `round_no` / masquage listes selon l’action.  
   **Résultat réel** : Not executed by user
