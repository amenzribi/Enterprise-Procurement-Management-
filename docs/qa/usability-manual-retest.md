# Scénarios de retest manuel (usabilité)

À exécuter sur http://localhost:4300 → :8081. Mot de passe démo : `%TEMP%\epi-e2e-pw.txt`. **Ne pas modifier** `DEMO-TN-PLT-PR-2026-000490`.

## A. Administration

1. Connexion `global.admin@demo.local` → **Mon travail** (pas la liste demandes seule).
2. **Organisation** : créer société fictive (code unique), site, département.
3. **Utilisateurs** : lire l’aide rôles ; créer un utilisateur ; envoyer reset ; définir mot de passe temporaire ; désactiver.
4. Ouvrir lien `/p#…` depuis le mail capturé localement.
5. **Mot de passe oublié** depuis Connexion (réponse neutre).

## B. Parcours métier (nouveau dossier)

1. Sami : créer + soumettre → dialogue référence → copier → ouvrir.
2. Karim : **Approbations** / Mon travail ; rechercher la référence ; approuver.
3. Mehdi : **Boîte d’achats** (plus récentes d’abord) ; assigner Ines.
4. Ines : accepter ; RFQ + devis ; validations ; adjudication ; PO ; émettre ; confirmer.
5. **Logistique** : trouver par référence ; créer envoi ; avancer jusqu’à Delivered.
6. Omar : retrouver par référence demande ; réception.
7. Acceptation / facture / attestation / clôture selon droits — **sans** marquer payé automatiquement.
8. Vérifier historique, références et montants cohérents.

## C. Tri et recherche

1. Créer plusieurs demandes ; confirmer la plus récente en tête (page 1).
2. Rechercher une référence d’une page suivante ; pagination remise à 0.
3. Changer le tri utilisateur ; paginer ; le tri actif reste.

## D. Responsive / i18n (émulation navigateur)

Largeurs ~360, 390, 768, 1024, 1440 ; FR et AR (RTL). Navigation tiroir, formulaires, focus clavier. **Pas** de test appareil physique sauf confirmation Mohamed.

## E. Négatifs

1. Utilisateur hors société : recherche référence → vide (pas d’oracle).
2. GLOBAL_ADMIN sans rôle acheteur : pas d’actions métier inventées.
3. Notes internes messagerie invisibles côté fournisseur.
