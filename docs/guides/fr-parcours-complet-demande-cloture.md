# Parcours complet — de la demande à la clôture (observé)

Guide simple basé sur l’exécution développeur du **04/11/2026** sur  
`http://localhost:4200` → `http://localhost:8080` (profils `dev,demo`, Flyway V17).

**Ce n’est pas une UAT utilisateur.** Compte de démonstration uniquement. Mots de passe / jetons non reproduits.

Dossier de référence réussi : **`DEMO-TN-PLT-PR-2026-000674`**  
(`AUTO-UAT — Pump seals — 20261104115213`).

---

## Qui fait quoi

| Étape | Compte | Menu / page | Bouton ou action principale | Prochain responsable |
|---|---|---|---|---|
| Brouillon | Sami (`requester.tn@demo.local`) | Demandes d’achat → Nouvelle demande | **Enregistrer le brouillon** | Sami (soumission) |
| Soumission | Sami | Même formulaire (édition) | **Soumettre la demande** (prévisualisation des validations) | Karim |
| Validation | Karim (`manager.tn@demo.local`) | Validations / fiche demande | **Approuver** | Mehdi (file achats) |
| Attribution | Mehdi (`purchasing.manager@demo.local`) | Boîte achats → fiche | **Assigner** → confirmer Ines | Ines |
| Acceptation | Ines (`buyer@demo.local`) | Fiche demande | **Accepter** l’affectation | Ines (sourcing) |
| RFQ rapide | Ines | Panneau Sourcing | Type **Quick**, fournisseur **DEMO-SKF**, créer | Ines |
| Offre | Ines | Sourcing | Prix unitaire **25 TND**, envoyer l’offre | Ines |
| Validations | Ines | Sourcing | Validation **technique** puis **commerciale** (la démo autorise l’acheteur pour les deux) | Ines |
| Attribution fournisseur | Ines | Commandes | **Attribuer** (award) avec motif | Ines |
| Brouillon BC | Ines | Commandes | Net 30, DAP, adresse fictive → **Créer le BC** | Ines |
| Émission | Ines | Commandes | **Émettre** | Ines (pas de confirmation fournisseur automatique) |
| Confirmation fournisseur | Ines | Commandes | **Confirmer** (enregistrement interne — pas le portail fournisseur) | Ines |
| Expédition | Ines | Logistique | **Créer l’expédition**, puis **Enregistrer la date réelle** pour chaque jalon jusqu’à **Livré** | Omar |
| Réception | Omar (`receiver.tn@demo.local`) | Réceptions / fiche | **Enregistrer la réception** (10 reçus / 10 acceptés) | Sami |
| Acceptation | Sami | Fiche | **Confirmer l’acceptation** | Ines |
| Facture | Ines | Fiche | N° facture, **250 TND**, attestation de rapprochement ; paiement **PENDING** par défaut (pas de bouton bancaire) | Sami |
| Clôture | Sami | Fiche | Note fournisseur **5/5**, dispense quantité vide, **Clôturer** | — (CLOSED) |

---

## Points observés à retenir

1. **Montant 200 TND** → règle démo « Sfax jusqu’à 5 000 TND » : un seul étage (responsable de département = Karim).
2. **Statut affiché après livraison** : badge utilisateur **« Reçu / Received »** (statut interne `DELIVERED`).
3. **Confirmation fournisseur** : action acheteur dans l’application, pas une validation sur le portail.
4. **Paiement PENDING** : valeur par défaut à l’enregistrement de la facture ; aucune transaction bancaire réelle.
5. **Clôture avec paiement PENDING** : autorisée par la politique actuelle (Sami auteur ou responsable achats).
6. **Sociétés** : demandeur / imputé DEMO-TN-PLT ; achat / BC / expédition côté DEMO-CPA (mode d’achat démo).

---

## Captures

Dossier `docs/qa/evidence/` : `01-sami-draft.png` … `20-sami-reopen-closed.png`.

Rapport détaillé (EN) : `docs/qa/request-to-closure-20-step-verification.md`.
