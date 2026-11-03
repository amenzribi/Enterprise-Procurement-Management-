# Scénarios manuels — admin demandes, archive, nettoyage, délégation

Pour chaque cas : **Résultat attendu** puis **Résultat réel** (marquer « Not executed by user » jusqu’à votre test).

## 1. Créer une société et des utilisateurs

1. Se connecter en GLOBAL_ADMIN.
2. Organisation : créer / vérifier société, site, département, centre de coût.
3. Utilisateurs : créer compte, grant REQUESTER + éventuellement approbateur, mot de passe temporaire ou lien setup.
4. Vérifier dernière protection GLOBAL_ADMIN (désactivation du dernier admin global refusée).

**Attendu** : comptes actifs ; rôles visibles ; pas de secret dans l’UI après coup.  
**Résultat réel** : Not executed by user

## 2. Créer et approuver une demande

1. Demandeur : créer brouillon, lignes, soumettre → numéro unique.
2. Approbateur : Mon travail / Approvals → décider.
3. Suivre jusqu’à file achats si montant/règle OK.

**Attendu** : historique lisible ; pas de codes techniques bruts ; owner / next step clairs.  
**Résultat réel** : Not executed by user

## 3. Déléguer pendant une absence (Karim → Hichem)

1. Absence Karim datée, délégué Hichem éligible.
2. Pendant la période : nouvelle tâche d’approbation → Hichem.
3. Hors période : Karim.
4. Tenter délégation cross-company → refus.
5. Vérifier audit acteur / pour le compte de.

**Attendu** : conforme à `fr-absences-delegation.md`.  
**Résultat réel** : Not executed by user

## 4. Archiver une demande de test clôturée

1. GLOBAL_ADMIN → Administration des demandes → trouver `CLOSED` (ou CANCELLED/REJECTED).
2. Archiver avec motif.
3. Vérifier disparition des listes actives ; présence filtre « Archivées ».
4. Restaurer → statut métier inchangé.

**Attendu** : archive ≠ annulation ; pas d’effet PO/réception.  
**Résultat réel** : Not executed by user

## 5. Prévisualiser le nettoyage et supprimer un brouillon isolé

1. Créer un brouillon de test isolé (pas UAT-01).
2. Cleanup preview → sélectionner uniquement ce brouillon.
3. Confirmer APPLY + motif.
4. Vérifier résultats par enregistrement ; token invalidé.

**Attendu** : seul le brouillon éligible est DESTROYED ; `DEMO-TN-PLT-PR-2026-000490` jamais proposé en delete.  
**Résultat réel** : Not executed by user

## 6. Vérifier qu’une demande engagée ne peut pas être hard-deleted

1. Ouvrir une demande ORDERED / avec PO.
2. Matrice d’actions : DELETE et CANCEL bloqués avec explication.

**Attendu** : messages `commitment-exists` / `not-draft`.  
**Résultat réel** : Not executed by user

## 7. Inspecter l’historique administratif

1. Après archive/cancel/delete, consulter la piste d’audit (pack audit dossier ou table audit) : acteur, motif, timestamps, pas de secret.

**Attendu** : actions `REQUEST_ADMIN_*` / `REQUEST_ARCHIVED` présentes.  
**Résultat réel** : Not executed by user
