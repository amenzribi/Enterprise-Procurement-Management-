# Guide administrateur — sociétés, utilisateurs et mots de passe

Ce guide décrit **les écrans actuels** de l’application Enterprise Procurement Intelligence (installation locale). Les exemples sont fictifs. **Aucun mot de passe réel n’est documenté ici.**

Compte d’administration d’installation : utilisateur démo `global.admin@demo.local` (ou l’équivalent créé pour votre installation). Le mot de passe démo local est fourni hors dépôt (fichier temporaire `epi-e2e-pw.txt` / variable d’environnement `DEMO_USER_PASSWORD`). Après connexion, changez-le via **Administration → Changer le mot de passe**.

Les e-mails de configuration / réinitialisation sont **capturés localement** (profil `dev` / mail fichier) — aucun envoi externe.

---

## 1. Créer une société

1. Connectez-vous en **GLOBAL_ADMIN**.
2. Ouvrez **Administration → Organisation** (`/admin/organization`).
3. Lisez l’aide : une **société** n’est pas un site ni un département.
4. Dans **Nouvelle société**, renseignez :
   - **Code** (unique)
   - **Raison sociale**
   - **Type** (SUBSIDIARY / PARENT / PURCHASING_CENTER)
   - **Pays**, **Devise**, **Langue**, **Fuseau horaire**
   - **Mode d’achat** (DIRECT, CENTRALIZED, ON_BEHALF, MIXED)
5. Cliquez sur **Créer la société**.
6. Un code en double renvoie une erreur de conflit (`duplicate-code`).

Le calendrier de travail n’est pas un référentiel séparé dans cette version ; le **fuseau de la société** sert pour les dates locales.

---

## 2. Configurer l’organisation et les achats

1. Dans **Organisation**, sélectionnez la société (lien sur le code).
2. Créez des **sites**, **départements** et **centres de coûts** via les onglets (code + nom).
3. Vérifiez le **mode d’achat** de la société (relations d’achat supportées : modes listés ci-dessus).
4. La **désactivation** d’une société conserve l’historique ; elle ne supprime pas les dossiers.

---

## 3. Créer un utilisateur pour chaque rôle implémenté

1. Ouvrez **Administration → Utilisateurs** (`/admin/users`).
2. Lisez **Ce que permet chaque rôle** (descriptions en langage clair + périmètre société / groupe).
3. Dans **Créer un utilisateur**, saisissez e-mail, nom affiché, société de rattachement, langue.
4. Cliquez sur **Créer et envoyer le lien de configuration**.
5. Un lien de **configuration initiale** est mis en file (dossier mail local en démo).

### Exemples fictifs (qui a besoin de quel rôle)

| Persona fictif | Rôle | Pourquoi |
|---|---|---|
| Sami Ben Ali | REQUESTER | Crée et soumet les besoins |
| Leila OnBehalf | ENTER_ON_BEHALF | Saisit pour le compte d’un autre |
| Karim Mansour | DEPARTMENT_MANAGER | Première étape d’approbation |
| Nadia Budget | BUDGET_APPROVER | Étape budget |
| Slim Management | MANAGEMENT_APPROVER | Étape direction |
| Ines Buyer | BUYER | RFQ, adjudication, commande, logistique |
| Mehdi Purchasing | PURCHASING_MANAGER | File d’achats, assignation |
| Omar Receiver | RECEIVER | Réception |
| Amira Group | GROUP_MANAGEMENT | Tableaux de bord groupe |
| Company Admin | ADMIN | Admin société (pas tout le métier) |
| Global Admin | GLOBAL_ADMIN | Installation (sociétés / users / mots de passe) |

**Ne créez pas** un compte « tous les rôles ». **GLOBAL_ADMIN** n’obtient pas automatiquement l’accès à toutes les données métier. Les **contacts fournisseurs** (SUPPLIER_CONTACT) s’invitent depuis la **messagerie**, pas depuis Utilisateurs.

---

## 4. Assigner sociétés et permissions

1. Sélectionnez l’utilisateur dans la liste.
2. Sous **Attributions de rôles**, choisissez le rôle et la **société** (si le rôle est au périmètre société).
3. Cliquez sur **Ajouter le rôle**.
4. Les rôles groupe (ADMIN, GLOBAL_ADMIN, GROUP_MANAGEMENT) n’exigent pas de société sur la grant.
5. Combinaisons usuelles : REQUESTER + rôle d’approbation ; BUYER + PURCHASING_MANAGER. Lisez le paragraphe **combinaisons** sur l’écran.

---

## 5. Configuration initiale du mot de passe

1. Après création, ouvrez le message capturé localement (lien `/p#…`).
2. Écran **Choisir un mot de passe** : nouveau mot de passe + confirmation.
3. Connectez-vous ensuite sur **Connexion**.

Alternative administrateur : **Définir un mot de passe temporaire** (≥ 12 caractères). L’utilisateur **doit le changer à la première connexion** (écran **Changer le mot de passe**). La valeur n’est jamais réaffichée ni journalisée en clair.

---

## 6. Changer et réinitialiser un mot de passe

### Utilisateur

- **Administration → Changer le mot de passe** (`/account/password`), ou
- **Mot de passe oublié ?** sur l’écran de connexion (`/forgot-password`) — réponse neutre, lien capturé localement.

### Administrateur

- Sur la fiche utilisateur : **Envoyer la réinitialisation** (lien mail capturé), ou mot de passe temporaire (ci-dessus).
- **Révoquer les sessions** force la reconnexion.

---

## 7. Désactiver un compte

1. Fiche utilisateur → **Désactiver**.
2. Si des approbations / achats ouverts existent, un avertissement s’affiche (pas de réaffectation automatique).
3. L’historique métier est conservé.

---

## 8. Vérifier la piste d’audit

1. Sur un dossier demande, téléchargez le **paquet d’audit** (bouton sur le dossier), ou
2. Consultez les **Tableaux de bord** / exports selon vos droits.
3. Les actions d’administration (création utilisateur, rôles, mots de passe, société) sont enregistrées côté serveur dans la même transaction que le changement d’état.
