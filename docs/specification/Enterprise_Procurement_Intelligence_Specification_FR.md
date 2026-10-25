ENTERPRISE SOLUTIONS PORTFOLIO

Enterprise Procurement Intelligence

Plateforme de gestion de la demande, des achats, du commerce international et de la logistique

Cahier des charges fonctionnel et technique

Une passerelle numérique unique pour toutes les demandes d’achat, internes, de filiales ou de clients, de la demande à la clôture : validations, centrale d’achat, consultations, évaluation, commande, transport international, réception, tableaux de bord et IA, en lien avec l’ERP.

Pour les entreprises, les groupes multi-sociétés et multi-pays, les sociétés de négoce, les sociétés pétrolières, industrielles, financières, d’assurance et les administrations

Cahier des charges produit | Version 2.1 | Octobre 2026

Vue d’ensemble

## 1. Objet et vision

## 2. Modèles d’organisation couverts

## 3. Organisation, filiales et demandeurs

## 4. Vue d’ensemble des modules

## 5. Cycle de vie et statuts

## 6. Demande numérique (M1)

## 7. Workflow et validations de la société (M2)

## 8. Centrale d’achat (M3)

## 9. Sourcing et consultations (M4)

## 10. Évaluation technique et commerciale (M5)

## 11. Commande, contrat et paiements (M6)

## 12. Commerce international et logistique (M7)

## 13. Réception, acceptation et clôture (M8)

## 14. Fournisseurs (M9)

## 15. Budget et dépenses (M10)

## 16. Tableaux de bord (M11)

## 17. Intelligence artificielle (M12)

## 18. Intégrité et conformité (M13)

## 19. Documents (M14)

## 20. Rôles et visibilité

## 21. Intégration avec l’ERP (M16)

## 22. Architecture technique

## 23. Modèle de données principal

## 24. Cybersécurité et audit

## 25. Exigences non fonctionnelles

## 26. Livrables

## 27. Plan de réalisation et backlog du produit minimum

## 28. Positionnement et modèle commercial

Annexe A : Glossaire

## 1. Objet et vision

Enterprise Procurement Intelligence remplace les demandes d’achat envoyées par e-mail, WhatsApp, téléphone ou papier par un processus numérique unique, gouverné et mesuré, de l’expression du besoin jusqu’à la livraison et à la clôture financière.

Ce n’est pas seulement une plateforme d’achat : c’est aussi un outil de suivi commercial et logistique. Elle fonctionne seule dès le premier jour et devient, lorsque l’ERP existe ou arrive, la couche de saisie et de pilotage de la demande en amont de l’ERP (SAP, Oracle, Sage, Odoo, Dynamics). Elle est développée en Spring Boot et Angular, installée chez le client ou en cloud privé, sans licence tierce.

### Objectifs

- Centraliser toutes les demandes d’achat, quelle que soit la société, le pays ou l’origine de la demande.

- Supprimer les demandes informelles (e-mail, messagerie, oral) et les demandes perdues.

- Standardiser les circuits de validation tout en respectant les règles propres à chaque société.

- Renforcer la gouvernance, la transparence et la traçabilité par une piste d’audit complète.

- Numériser les consultations, la comparaison des offres et l’évaluation des fournisseurs.

- Suivre le commerce international et la logistique jusqu’à l’entrepôt ou au chantier.

- Donner à la direction générale une vue en temps réel et des réponses immédiates.

- Préparer l’intégration avec l’ERP dès la conception du modèle de données.

- Apporter une IA qui accélère la saisie, la comparaison et le pilotage, sans retirer la décision à l’humain.

### Les engagements du produit

| Engagement | Ce que cela signifie |
| --- | --- |
| Une seule porte d’entrée | Toutes les demandes, internes ou externes, entrent par le même portail et reçoivent un numéro unique. |
| Règles par société | Chaque société garde ses circuits, seuils et valideurs ; la plateforme reste commune. |
| Visibilité totale | À tout moment : où est la demande, qui est responsable, combien, en retard ou non, livraison prévue. |
| Prêt pour l’ERP | Référentiels et transactions structurés pour se synchroniser avec l’ERP, en sens unique ou dans les deux sens. |
| IA contrôlée | L’IA lit, propose, compare et alerte ; elle ne valide, ne commande et ne paie jamais seule. |
| Sans licence tierce | Composants open source ; IA exécutée localement ; installation sur site possible. |

## 2. Modèles d’organisation couverts

La plateforme est conçue pour plusieurs façons d’acheter, configurables sans développement.

| Modèle | Description | Exemple |
| --- | --- | --- |
| Entreprise unique | Demandes des départements, validation interne, service achats unique. | Banque, assurance, société de services. |
| Groupe avec centrale d’achat | Chaque filiale valide ses demandes selon ses règles, puis la centrale d’achat du groupe les exécute. | Groupe industriel avec usines, chantiers et filiales dans plusieurs pays. |
| Groupe décentralisé | Chaque filiale achète elle-même ; le groupe consolide la visibilité et les fournisseurs. | Holding multi-activités. |
| Négoce et distribution | Des clients, revendeurs ou entrepreneurs soumettent des demandes ; la société source et livre. | Société de négoce de pièces et d’équipements. |
| Société pétrolière ou coentreprise | Règlement achats de la compagnie, autorisations de dépenses, approbations des partenaires. | Opérateur pétrolier, société de services pétroliers. |
| Organisme public | Règles des marchés publics, commissions, préparation et suivi autour des plateformes publiques. | Entreprise publique, administration. |

## 3. Organisation, filiales et demandeurs

Pour chaque demande, la plateforme répond clairement à trois questions : pour quelle société, par qui, et pour le compte de qui.

### 3.1 Structure de l’organisation

| Niveau | Définition | Exemple |
| --- | --- | --- |
| Groupe | Ensemble des sociétés contrôlées par une même maison mère. | Un groupe industriel présent dans deux pays. |
| Société mère | Société qui détient les filiales et fixe les règles communes. | La holding du groupe. |
| Filiale | Société juridiquement distincte, avec ses propres comptes, son budget, sa devise, ses règles de validation et son code société dans l’ERP. | Une usine, une société de construction, une filiale à l’étranger. |
| Centrale d’achat | Société ou service du groupe chargé d’acheter pour les filiales. Ce peut être la société mère, une filiale dédiée ou un simple service. | La société de négoce et d’achat du groupe. |
| Site | Lieu physique rattaché à une filiale. | Usine, chantier, entrepôt, agence. |
| Département | Unité interne d’un site ou d’une filiale. | Maintenance, production, administration. |

Une entreprise unique est simplement un groupe d’une seule société : la même plateforme sert donc les petites organisations comme les grands groupes.

### 3.2 Qui a fait la demande ? Les acteurs d’une demande

Une demande peut faire intervenir plusieurs personnes et plusieurs sociétés. La plateforme enregistre chacune séparément, ce qui supprime toute ambiguïté sur la responsabilité et sur l’imputation des coûts.

| Rôle dans la demande | Question à laquelle il répond | Exemple |
| --- | --- | --- |
| Saisi par | Qui a tapé la demande dans le système ? | L’assistante du chef de maintenance. |
| Demandeur | Qui exprime le besoin et en est responsable ? | Le chef de maintenance de l’usine. |
| Bénéficiaire | Qui, ou quel site, recevra le bien ou le service ? | La ligne de production 2 de l’usine. |
| Société demandeuse | Quelle société a le besoin et valide la demande ? | La filiale usine. |
| Société d’imputation | Quelle société supporte le coût (budget, centre de coûts) ? | En général la société demandeuse. |
| Société acheteuse | Quelle société passe la commande au fournisseur ? | La centrale d’achat, ou la filiale elle-même. |
| Client externe | Pour une demande externe : quel tiers a demandé ? | Un entrepreneur qui demande des matériaux. |

Exemple complet : l’assistante (saisi par) crée la demande du chef de maintenance (demandeur) pour la ligne 2 (bénéficiaire) de la filiale usine (société demandeuse et société d’imputation). La centrale d’achat du groupe (société acheteuse) achète auprès du fournisseur, puis refacture la filiale.

### 3.3 Le demandeur interne

Un demandeur interne est un employé du groupe, qu’il travaille dans la société mère, dans une filiale ou dans la centrale. Il se connecte avec son compte d’entreprise et exprime un besoin pour sa propre société.

- Il appartient à une société, un site et un département ; la demande reprend automatiquement ces informations.

- Sa demande suit le circuit de validation de sa société : responsable, budget, direction, selon les montants et les catégories.

- Le coût est imputé à son budget ou à son centre de coûts ; aucune facture de vente n’est émise.

- Il voit ses propres demandes, ou celles de son département ou de sa société, selon ses droits.

- Il confirme lui-même la réception ou la réalisation du service avant la clôture.

Cas particulier : une filiale peut demander à une autre filiale, par exemple des pièces disponibles dans l’entrepôt central. C’est une demande interne entre sociétés du groupe, qui donne lieu à une cession ou à une refacturation interne plutôt qu’à un achat externe.

### 3.4 Le demandeur externe

Un demandeur externe est un tiers qui ne fait pas partie du groupe : client, revendeur, distributeur, entrepreneur, usine cliente, société de projet ou organisme public. Il demande au groupe de lui fournir des produits ou des services. Pour le groupe, ce n’est pas une dépense mais une opportunité de vente.

- Il accède au portail externe avec un compte vérifié, ou un commercial du groupe saisit la demande pour son compte.

- Sa demande ne passe pas par un circuit budgétaire interne mais par une qualification commerciale : identité et solvabilité du client, faisabilité, marge, conditions de paiement.

- La centrale source les produits, puis le groupe adresse au client une offre de prix de vente (coût d’achat, frais et marge).

- L’acceptation de l’offre par le client crée une commande client, qui déclenche la commande au fournisseur.

- Le suivi va jusqu’à la livraison au client, la facture de vente et l’encaissement.

- Le client voit uniquement ses demandes, ses offres, ses expéditions et ses factures ; jamais les prix d’achat ni les fournisseurs.

### 3.5 Demande interne et demande externe : comparaison

| Élément | Demande interne | Demande externe |
| --- | --- | --- |
| Qui demande | Employé de la société mère, d’une filiale ou de la centrale | Client, revendeur, entrepreneur, organisme |
| Accès | Portail interne, compte d’entreprise | Portail externe, ou saisie par un commercial |
| Nature pour le groupe | Dépense | Vente |
| Validation | Circuit interne de la société : responsable, budget, direction | Qualification commerciale : client, marge, conditions |
| Document remis au demandeur | Aucun ; suivi dans la plateforme | Offre de prix de vente, puis facture |
| Engagement | Commande fournisseur | Commande client, puis commande fournisseur |
| Finance | Imputation budgétaire ; refacturation interne si achat centralisé | Facture de vente et encaissement |
| Prix d’achat visibles | Selon les droits | Jamais |
| Clôture | Réception confirmée par le demandeur et facture fournisseur | Livraison confirmée par le client, facture de vente et paiement |

### 3.6 Les filiales dans la plateforme

Chaque filiale est configurée comme une organisation autonome à l’intérieur de la même installation.

| Paramètre de la filiale | Contenu |
| --- | --- |
| Identité | Raison sociale, pays, identifiant fiscal, code société dans l’ERP. |
| Devise et langue | Devise locale, langue par défaut, fuseau horaire, calendrier des jours ouvrés. |
| Organisation | Sites, départements, centres de coûts. |
| Circuits de validation | Valideurs, seuils, catégories et délégations propres à la filiale. |
| Budget | Lignes budgétaires de la filiale. |
| Mode d’achat | Achète elle-même, passe par la centrale, ou les deux selon la catégorie. |
| Fournisseurs | Fournisseurs propres à la filiale ou partagés au niveau du groupe. |
| Droits | Utilisateurs et rôles limités à la filiale ; la direction du groupe voit toutes les filiales. |

### 3.7 Trois modes d’achat pour une filiale

| Mode | Fonctionnement | Quand l’utiliser |
| --- | --- | --- |
| Achat direct | La filiale valide, consulte et commande elle-même. | Achats locaux simples, faibles montants. |
| Achat centralisé | La filiale valide ; la centrale consulte, commande au nom du groupe et refacture la filiale. | Achats importants, internationaux ou regroupables entre filiales. |
| Achat pour le compte de la filiale | La centrale négocie et choisit le fournisseur, mais la commande est émise au nom de la filiale. | Lorsque la filiale doit rester l’acheteur légal (douane, fiscalité, contrat). |

Le mode est défini par catégorie, par montant ou par pays, et la centrale peut le modifier au moment de l’affectation.

### 3.8 Parcours types

Parcours A : demande interne d’une filiale traitée par la centrale

## 1. Le chef de maintenance de la filiale usine crée une demande de dix pièces de rechange.

## 2. Le responsable du département, puis le contrôle budgétaire de la filiale, valident.

## 3. La demande arrive dans la boîte de réception de la centrale d’achat du groupe.

4. La centrale l’affecte à un acheteur international, consulte trois fournisseurs et obtient la validation technique de l’usine.

## 5. La centrale commande, suit l’expédition et le dédouanement, puis livre l’usine.

6. Le chef de maintenance confirme la réception ; la centrale refacture la filiale ; la demande est clôturée.

Parcours B : demande externe d’un client

## 1. Un entrepreneur client dépose une demande de matériaux sur le portail externe.

## 2. Le service commercial qualifie la demande : client connu, solvabilité, faisabilité.

## 3. La centrale consulte les fournisseurs et calcule le coût rendu.

## 4. Le commercial envoie au client une offre de prix de vente avec la marge validée.

## 5. Le client accepte : la commande client est créée, puis la commande fournisseur.

6. Le groupe livre le client, qui confirme la réception ; la facture de vente est émise et encaissée ; la demande est clôturée.

Parcours C : demande entre deux filiales

## 1. Une usine demande des pièces disponibles dans l’entrepôt central d’une autre filiale.

## 2. Après validation interne, la plateforme propose une cession interne au lieu d’un achat externe.

## 3. L’entrepôt expédie, l’usine réceptionne, et la cession est refacturée entre les deux sociétés.

## 4. Vue d’ensemble des modules

| N° | Module | Phase |
| --- | --- | --- |
| M1 | Demande numérique (formulaire, multi-lignes, pièces jointes, saisie assistée par IA) | Phase 1 |
| M2 | Workflow et validations de la société demandeuse | Phase 1 |
| M3 | Centrale d’achat : boîte de réception, contrôle, affectation des acheteurs | Phase 1 |
| M4 | Sourcing et consultations (local et international) | Phase 1 |
| M5 | Évaluation technique et commerciale, commissions | Phase 1 |
| M6 | Commande, contrat, paiements et garanties | Phase 1 |
| M7 | Commerce international et logistique | Phase 1 |
| M8 | Réception, acceptation et clôture | Phase 1 |
| M9 | Fournisseurs (registre, dossier, risque, performance) | Phase 1 (registre) / Phase 2 (portail) |
| M10 | Budget et dépenses | Phase 2 |
| M11 | Tableaux de bord et tableau du directeur général | Phase 1 |
| M12 | Assistant IA et analyses | Phase 1 (saisie) / Phase 2 (assistant) |
| M13 | Intégrité et conformité | Phase 2 |
| M14 | Documents | Phase 1 |
| M15 | Administration, rôles, référentiels | Phase 1 |
| M16 | Intégration ERP | Phase 1 (fichiers) / Phase 2 (API) |
| M17 | Demandes externes, offres de vente et refacturation interne | Phase 1 (demande et suivi) / Phase 2 (offres et factures de vente) |

## 5. Cycle de vie et statuts

Chaque demande suit un cycle de vie visible par tous ses acteurs. Chaque ligne d’une demande multi-lignes peut avoir son propre statut (par exemple une pièce livrée, une autre encore en transit).

| Étape | Statut | Responsable |
| --- | --- | --- |
| Création | Brouillon, Demande créée | Demandeur |
| Validation interne | En validation, Validée / Rejetée / À réviser | Valideurs de la société |
| Centrale d’achat | En revue achats, Affectée à un acheteur | Responsable achats |
| Consultation | Consultation en cours, Offres reçues | Acheteur |
| Évaluation | Validation technique, Validation commerciale, Validation de la direction | Technique, finance, direction |
| Engagement | Commande émise, Contrat signé, Commande confirmée | Acheteur |
| Expédition | En fabrication, Prête, Expédiée, Au port, En douane, En entrepôt | Acheteur, logistique |
| Réception | Livrée, Réception partielle / non conforme / acceptée | Réceptionnaire, demandeur |
| Finance | Facturée, Paiement en attente, Payée | Finance |
| Clôture | Clôturée | Système, après conditions de clôture |

### Cycle d’une demande externe

Une demande de client suit un cycle propre, car elle aboutit à une vente et non à une dépense.

| Étape | Statut | Responsable |
| --- | --- | --- |
| Réception | Demande client reçue | Client ou commercial |
| Qualification | Qualifiée ou refusée | Service commercial |
| Sourcing | Consultation des fournisseurs, puis coût rendu calculé | Centrale d’achat |
| Offre au client | Offre de prix envoyée, puis acceptée ou refusée | Commercial, direction |
| Engagement | Commande client créée, puis commande fournisseur émise | Commercial, acheteur |
| Livraison | Expédiée, puis livrée au client | Logistique |
| Finance | Facture de vente émise, puis encaissée | Finance |
| Clôture | Clôturée | Système |

## 6. Demande numérique (M1)

### 6.1 Contenu de la demande

| Groupe | Champs |
| --- | --- |
| Identification | Numéro automatique unique, société, pays, département, demandeur, projet, centre de coûts. |
| Classement | Catégorie, priorité, type d’achat (local ou international), origine (interne ou externe). |
| Lignes | Description, référence article (catalogue ou ERP), quantité, unité, prix estimé, date requise, lieu de livraison, statut par ligne. |
| Budget | Montant estimé, devise, ligne budgétaire. |
| Pièces techniques | Spécifications, plans, fiches techniques, photos, certificats, documents justificatifs, par demande ou par ligne. |
| Justification | Motif, urgence motivée, lien avec un équipement ou un ordre de maintenance. |

### 6.2 Saisie assistée par l’IA

- Texte libre : « 10 roulements SKF 6205 pour la ligne 2 de l’usine, urgent » devient une demande structurée, à vérifier par le demandeur.

- Photo ou devis : l’IA lit la plaque signalétique d’une pièce ou un devis PDF et pré-remplit les lignes.

- Import Excel d’une liste de pièces de rechange en une seule demande multi-lignes.

- Suggestion d’articles existants dans le catalogue ou l’ERP pour éviter les doublons de référence.

### 6.3 Contrôles avant soumission

- Champs obligatoires selon la catégorie et le type d’achat.

- Demande en double ou très proche déjà en cours.

- Stock disponible (si l’ERP est relié) ou contrat-cadre existant pour l’article.

- Estimation de prix comparée à l’historique ; alerte en cas d’écart anormal.

## 7. Workflow et validations de la société (M2)

Une demande ne va jamais directement aux achats : elle passe d’abord par le circuit de validation de la société demandeuse.

### 7.1 Configuration des circuits

- Critères : société, département, catégorie, montant estimé, priorité, type d’achat, source de financement.

- Circuits séquentiels (par exemple responsable du département, puis contrôle budgétaire, puis direction), parallèles ou conditionnels.

- Matrice des délégations de pouvoir par société et par seuil ; délégation datée en cas d’absence.

- Délais par étape, rappels et escalade ; validation depuis le téléphone.

- Concepteur graphique des circuits (norme BPMN), versionné et testé avant activation.

### 7.2 Actions du valideur

| Action | Effet |
| --- | --- |
| Approuver | Passage à l’étape suivante. |
| Rejeter | Fin de la demande, motif obligatoire. |
| Renvoyer pour révision | Retour au demandeur pour correction. |
| Demander une information | Question au demandeur sans renvoyer la demande ; le délai est suspendu selon la règle. |
| Déléguer | Transfert à un délégué autorisé, tracé. |

Chaque action est enregistrée : utilisateur, date, heure, commentaire, statut précédent et nouveau statut.

## 8. Centrale d’achat (M3)

Fonction clé pour les groupes : toutes les demandes validées arrivent automatiquement dans une boîte de réception centrale, où l’équipe achats les contrôle, les priorise et les affecte.

- Filtres : société, pays, catégorie, priorité, montant, statut, demandeur, date requise, type d’achat, acheteur.

- Contrôle de complétude, de clarté technique et de faisabilité commerciale ; renvoi au demandeur avec demande de précision.

- Affectation selon des règles : achat local à un acheteur local, achat international à un acheteur spécialisé en commerce extérieur, par catégorie ou par pays.

- Suggestion d’acheteur par l’IA selon la catégorie, l’historique et la charge actuelle de chaque acheteur.

- Regroupement de demandes similaires de plusieurs filiales en une seule consultation pour obtenir de meilleurs prix.

- L’acheteur accepte l’affectation ou la renvoie avec motif.

## 9. Sourcing et consultations (M4)

### 9.1 Deux circuits : local et international

| Élément | Achat local | Achat international |
| --- | --- | --- |
| Fournisseurs | Registre des fournisseurs locaux, par pays | Registre des fournisseurs internationaux |
| Procédure | Consultation simple ; « devis rapide » pour l’urgent ou le faible montant | Consultation formelle et détaillée |
| Devise | Devise locale | Devises internationales (USD, EUR…), taux de change enregistré |
| Délai | Court (par exemple le jour même) | Production, transport et dédouanement |
| Paiement | Comptant, à la livraison, à terme | Crédit documentaire (LC), virement, avance |
| Incoterms | Simplifiés (livraison au site) | Incoterms standard (EXW, FOB, CIF, DAP…) |
| Critères de comparaison | Prix, délai, conformité technique, risque fournisseur local | Prix rendu, délai, conformité, conditions de paiement, risque fournisseur international |

### 9.2 Offres

Chaque offre comprend : fournisseur, date, devise, prix par ligne, délai, conditions de paiement, Incoterm, validité, conformité technique, remarques commerciales et pièces jointes.

- Phase 1 : saisie manuelle des offres par l’acheteur, ou lecture automatique d’une offre PDF ou numérisée par l’IA, vérifiée par l’acheteur.

- Phase 2 : portail fournisseurs pour le dépôt direct des offres, puis offres chiffrées jusqu’à l’ouverture lorsque la procédure l’exige.

- Conversion des offres dans une devise de comparaison et calcul du coût rendu (prix, transport, droits estimés).

- Tableau comparatif automatique : meilleur prix, meilleur délai, conditions de paiement, conformité, risque.

## 10. Évaluation technique et commerciale (M5)

| Étape | Contenu | Valideur |
| --- | --- | --- |
| Validation technique | Revue d’ingénierie, conformité du produit, acceptation technique (pièces de rechange, électricité, mécanique, pompes, vannes, automatismes, produits chimiques, matériaux). | Équipe technique de la société demandeuse |
| Validation commerciale | Vérification du budget dans la bonne devise, approbation du prix et des conditions de paiement. | Finance, achats |
| Validation de la direction | Obligatoire au-delà d’un seuil, pour les avances, les crédits documentaires et les matériaux critiques. | Direction |

- Matrice de conformité par l’IA : chaque offre est confrontée aux spécifications ; les écarts sont signalés à l’évaluateur.

- Pour les organismes publics et les compagnies pétrolières : commissions, déclarations d’absence de conflit d’intérêts, notation individuelle, procès-verbaux générés en arabe et en français.

- Décision motivée et tracée à chaque étape.

## 11. Commande, contrat et paiements (M6)

- Phase 1 : commande créée dans la plateforme ou en dehors, avec suivi complet du statut.

- Avec ERP : la commande est créée dans l’ERP à partir des données validées, et son numéro revient dans la plateforme.

- Commande locale (devise locale, adresse de livraison, conditions simples) ou internationale (devise, port ou aéroport, Incoterm complet).

- Contrat attaché à la demande ; suivi : commande émise, contrat signé, commande confirmée par le fournisseur.

- Suivi des paiements : avances, crédits documentaires (banque, montant, échéance), garanties bancaires et retenues, avec alertes avant expiration.

## 12. Commerce international et logistique (M7)

### 12.1 Étapes de suivi

| Achat local | Achat international |
| --- | --- |
| Commande confirmée par le fournisseur | En fabrication |
| Enlevée / expédiée | Prête à l’expédition |
| En transit local | Expédiée |
| Livrée à l’entrepôt ou au site | Au port ou à l’aéroport |
| Sans objet | En dédouanement |
| Sans objet | En entrepôt puis livrée |

### 12.2 Documents suivis

Facture commerciale, liste de colisage, certificat d’origine, connaissement (B/L), lettre de transport aérien (AWB), documents de douane, bons de livraison et lettres de voiture pour le local.

- Lecture automatique des documents de transport par l’IA : numéro de B/L ou d’AWB, navire, ports, dates, poids, nombre de colis, rapprochés de la commande.

- Date d’expédition et d’arrivée prévues ; alerte dès qu’une étape dépasse sa date prévue.

- Vue des expéditions par pays d’origine, mode de transport et statut douanier.

- Suivi jusqu’à la livraison au client final pour les demandes commerciales externes, puis jusqu’au paiement.

## 13. Réception, acceptation et clôture (M8)

- Réception des marchandises par le magasin ou le demandeur, depuis le téléphone (QR code de la commande, quantités, photos).

- Acceptation des services par le demandeur (attestation de service fait).

- Exceptions : livraison partielle, marchandise endommagée, documents manquants, problème de qualité, livraison refusée.

- Lecture automatique de la facture et rapprochement entre la commande, la réception et la facture ; écarts signalés.

### Conditions de clôture

Une demande n’est jamais clôturée automatiquement. Toutes les conditions suivantes sont exigées :

## 1. Livraison ou réalisation du service confirmée.

2. Documents obligatoires présents (facture locale ou facture commerciale définitive, documents de transport).

## 3. Montant final de la facture saisi et vérifié, dans sa devise.

## 4. Confirmation du demandeur ou du réceptionnaire autorisé.

## 5. Fournisseur final, montant final, date de livraison et responsable enregistrés.

## 14. Fournisseurs (M9)

| Fonction | Description |
| --- | --- |
| Registres | Fournisseurs locaux par pays et fournisseurs internationaux, catégories, coordonnées bancaires contrôlées. |
| Dossier administratif | Registre de commerce, attestations fiscales et sociales, assurances, certifications, avec dates de validité et alertes. |
| Risque | Score selon les documents expirés, les retards, les non-conformités, la dépendance et les signaux d’intégrité. |
| Performance | Respect des délais, qualité, réactivité, conformité documentaire, par période. |
| Portail (phase 2) | Inscription, mise à jour du dossier, réponse aux consultations, suivi des commandes et des paiements. |
| Synchronisation ERP | Création et mise à jour du fichier fournisseurs dans l’ERP après validation. |

## 15. Budget et dépenses (M10)

- Lignes budgétaires par société, centre de coûts et projet, importées de l’ERP ou saisies.

- Préengagement à la validation de la demande, engagement à la commande, consommation à la facture.

- Contrôle dans la devise de la demande, avec conversion au taux enregistré.

- Blocage ou circuit de dérogation lorsque le disponible est insuffisant.

- Plan annuel des achats et consolidation des besoins similaires des filiales.

## 16. Tableaux de bord (M11)

### 16.1 Indicateurs

- Nombre total de demandes, validations en attente, demandes en revue achats, en consultation, en validation technique, en cours d’achat, en retard, terminées.

- Valeur estimée et valeur engagée, séparées entre achats locaux et internationaux, par devise et en devise de consolidation.

- Délais par étape et goulets d’étranglement ; charge et performance des acheteurs.

- Économies réalisées, taux de mise en concurrence, part des urgences.

- Expéditions en cours, en douane, en retard.

Filtres : société, pays, acheteur, statut, catégorie, période, priorité, type d’achat, montant.

### 16.2 Tableau du directeur général

Pour chaque demande, une réponse immédiate aux questions de la direction :

| Question | Réponse affichée |
| --- | --- |
| Où en est la demande ? | Étape et statut actuels, avec l’historique. |
| Qui est responsable ? | Valideur ou acheteur en charge, depuis quand. |
| Quelle société l’a demandée ? | Société, département, demandeur. |
| Combien ? | Montant estimé et montant engagé, en devise d’origine et de consolidation. |
| Est-elle validée ? | Validations obtenues et en attente. |
| Est-elle en retard ? | Écart entre dates prévues et réelles, par étape. |
| Quand sera-t-elle livrée ? | Date de livraison prévue, recalculée selon la logistique. |

Question en langage naturel : le directeur peut demander « Où en est la demande des pompes de l’usine ? » ou « Quelles demandes de plus de 50 000 USD sont en retard ? » et obtenir la réponse ou la liste immédiatement.

## 17. Intelligence artificielle (M12)

L’IA est exécutée localement sur les serveurs du client. Elle accélère et alerte ; chaque proposition est vérifiée par un humain avant d’être enregistrée.

| Fonction IA | Bénéfice | Phase |
| --- | --- | --- |
| Demande en texte libre ou par photo | Demandes complètes en quelques secondes. | 1 |
| Lecture des devis et offres | Fin de la ressaisie des offres ; comparatif immédiat. | 1 |
| Lecture des documents de transport et des factures | Suivi logistique et rapprochement sans saisie. | 1 |
| Suggestion d’articles et détection de doublons | Référentiel propre, moins de doublons dans l’ERP. | 1 |
| Suggestion d’acheteur | Affectation plus rapide et charge équilibrée. | 2 |
| Matrice de conformité technique | Écarts entre offre et spécifications repérés tôt. | 2 |
| Estimation de prix et alerte d’écart | Prix anormaux détectés avant engagement. | 2 |
| Prévision des délais de livraison | Dates de livraison réalistes à partir de l’historique. | 2 |
| Questions et listes en langage naturel | Réponses immédiates pour la direction et les acheteurs. | 2 |
| Rédaction assistée | Projets de consultation, de relance fournisseur et de notification. | 2 |
| Signaux d’intégrité | Fractionnement, ententes, conflits d’intérêts orientés vers le contrôle. | 2 |

### Garde-fous

- Les résultats de l’IA respectent les droits de l’utilisateur ; aucune donnée d’une autre société n’est exposée.

- Chaque valeur extraite montre sa source dans le document, avec un score de confiance.

- L’IA ne valide, ne commande, ne paie et ne clôture rien.

- L’assistant produit uniquement des critères de recherche autorisés, jamais de requête libre sur la base.

- Questions, sources et réponses journalisées selon la politique du client.

## 18. Intégrité et conformité (M13)

| Signal | Détection |
| --- | --- |
| Fractionnement | Demandes proches dont le cumul dépasse un seuil de procédure ou de validation. |
| Conflit d’intérêts | Liens déclarés employé-fournisseur ; mêmes coordonnées ou comptes bancaires. |
| Signaux d’entente | Prix identiques, écarts constants, mêmes auteurs de fichiers, rotation des gagnants. |
| Offre anormalement basse | Prix très inférieur à l’estimation et à la moyenne. |
| Fournisseur unique récurrent | Attributions répétées sans mise en concurrence. |
| Dépense hors processus | Facture sans commande, ou commande postérieure à la facture. |

Règles et seuils configurés avec le contrôle interne ; chaque alerte est traitée et tracée. Les règles des marchés publics (en Tunisie, décret n° 2014-1039 et plateforme TUNEPS) sont respectées : la plateforme prépare et suit, sans se substituer aux plateformes publiques obligatoires.

## 19. Documents (M14)

- Dépôt central et sécurisé : spécifications, plans, fiches techniques, offres, commandes, contrats, certificats, documents de transport et de douane, factures.

- Versions et historique de chaque document ; prévisualisation dans le navigateur.

- Documents rattachés à la demande, à la ligne, à l’offre, à la commande ou à l’expédition.

- Indexation et recherche plein texte, y compris dans les documents numérisés (OCR).

- Connexion possible avec la GED Enterprise Document Intelligence ou avec la GED du client.

## 20. Rôles et visibilité

| Rôle | Voit | Peut |
| --- | --- | --- |
| Demandeur | Ses demandes, ou celles de son département selon la configuration | Créer, compléter, suivre, confirmer la réception |
| Responsable de département | Demandes de son département | Valider, renvoyer, demander une information |
| Valideur budget / direction | Demandes à valider de sa société | Valider selon ses seuils |
| Valideur technique | Demandes et offres qui lui sont soumises | Accepter ou refuser techniquement |
| Acheteur | Demandes qui lui sont affectées | Consulter, saisir les offres, commander, suivre la logistique |
| Responsable achats / centrale | Toutes les demandes validées transmises aux achats | Affecter, renvoyer, regrouper, superviser |
| Logistique et magasin | Expéditions et réceptions de son périmètre | Mettre à jour les étapes, réceptionner |
| Finance | Engagements, factures, paiements | Valider commercialement, rapprocher |
| Direction générale | Toutes les sociétés et tous les statuts | Consulter, interroger, exporter |
| Commercial | Demandes externes de ses clients | Qualifier, saisir pour le compte d’un client, établir l’offre de vente |
| Client externe | Ses demandes, offres, expéditions et factures ; jamais les prix d’achat | Créer, suivre, accepter une offre, confirmer la livraison |
| Administrateur | Configuration | Gérer utilisateurs, sociétés, catégories, circuits, référentiels |

Les droits sont configurables par société, département, unité, pays et catégorie d’achat. Les prix des fournisseurs, les budgets et les informations stratégiques ne sont visibles que des rôles autorisés.

## 21. Intégration avec l’ERP (M16)

La plateforme fonctionne seule aujourd’hui et devient la couche de demande en amont de l’ERP demain, sans refonte : le modèle de données est conçu dès le départ pour correspondre aux objets de l’ERP.

### 21.1 Modes

| Mode | Fonctionnement |
| --- | --- |
| Autonome | Commandes, réceptions et suivi dans la plateforme ; exports comptables (CSV, Excel). |
| Échange de fichiers | Export des demandes validées et import des référentiels et statuts, planifiés (SFTP). |
| API en sens unique | Création automatique des demandes d’achat ou des commandes dans l’ERP. |
| API dans les deux sens | Référentiels, budgets, commandes, réceptions, factures et paiements synchronisés en continu. |

### 21.2 Points d’intégration

| Objet | Sens | Usage |
| --- | --- | --- |
| Demande validée transmise comme demande d’achat ERP (PR) | Vers l’ERP | Création de la demande d’achat officielle. |
| Commande (PO) | Dans les deux sens | Création dans l’ERP, numéro et statut renvoyés. |
| Fournisseurs | Dans les deux sens | Fichier fournisseurs commun. |
| Articles | Depuis l’ERP | Catalogue et références communes. |
| Stocks | Depuis l’ERP | Contrôle du stock avant achat. |
| Réceptions | Dans les deux sens | Entrée en stock et statut de réception. |
| Factures et paiements | Depuis l’ERP | Clôture financière et tableau de bord. |
| Budgets et centres de coûts | Depuis l’ERP | Contrôle budgétaire. |
| Ordres de maintenance | Depuis l’ERP | Demande de pièces créée depuis un ordre de maintenance. |
| Commandes clients | Vers l’ERP | Pour les demandes commerciales externes (négoce). |

### 21.3 Correspondance avec SAP

| Module SAP | Objets concernés | Mécanismes envisagés |
| --- | --- | --- |
| MM (achats) | Demande d’achat, commande, fiche fournisseur, fiche article, réception | BAPI (par exemple BAPI_PR_CREATE, BAPI_PO_CREATE1) ou API OData de S/4HANA |
| FI (finance) | Factures, paiements, centres de coûts | Lecture des statuts de facture et de paiement |
| WM / EWM (entrepôt) | Stocks, entrées de marchandises | Lecture des stocks, statuts de réception |
| PM (maintenance) | Ordres de maintenance, besoins en pièces | Création de demandes à partir des ordres |
| SD (ventes) | Commandes clients | Pour les demandes externes de négoce |

### 21.4 Autres ERP

| ERP | Mécanismes envisagés |
| --- | --- |
| Oracle E-Business Suite | Tables d’interface standard (import des demandes d’achat), API PL/SQL, vues en lecture. |
| Oracle Fusion Cloud | API REST des achats. |
| Microsoft Dynamics 365 | Entités de données en OData. |
| Sage | Services web ou fichiers, selon la version. |
| Odoo | API externe JSON-RPC / XML-RPC. |

### 21.5 Fiabilité

- Couche d’intégration dédiée (Spring Integration ou Apache Camel) ; enregistrement de chaque message avant envoi.

- Clés d’idempotence : aucun doublon dans l’ERP en cas de nouvel envoi.

- Reprises automatiques, file des erreurs, alertes, tableau de rapprochement quotidien.

- Tables de correspondance des codes (sociétés, fournisseurs, articles, comptes, devises).

- Mécanismes exacts validés selon la version de l’ERP ; les éventuels frais d’accès indirect facturés par l’éditeur de l’ERP sont vérifiés avec le client.

## 22. Architecture technique

| Couche | Technologie | Rôle |
| --- | --- | --- |
| Interface | Angular, application web responsive et PWA, droite à gauche | Espaces demandeur, valideur, achats, logistique, direction, client externe. |
| API et métier | Spring Boot 3, Java 21, Spring Security | Modules M1 à M16, API REST documentées (OpenAPI). |
| Base de données | PostgreSQL, migrations Flyway | Données multi-sociétés, audit. |
| Workflows | Moteur BPMN open source (Flowable) | Circuits par société, étapes et délais. |
| Intégration | Spring Integration ou Apache Camel | ERP, fichiers, messagerie. |
| Recherche | OpenSearch | Recherche dans demandes, offres, documents. |
| IA | Python (FastAPI), OCR et modèles exécutés localement | Saisie assistée, lecture de documents, comparaisons, assistant. |
| Fichiers | Stockage objet compatible S3 | Pièces jointes et versions. |
| Files de messages | RabbitMQ | Traitements asynchrones, notifications, intégrations. |
| Identité | Keycloak ou annuaire du client | Authentification unique, double authentification. |
| Rapports | Tableaux intégrés ; vues pour Power BI ou Enterprise Lake | Pilotage. |
| Supervision | Prometheus, Grafana, journaux centralisés | Santé, performance, alertes. |

### Exigences d’architecture

- Multi-sociétés : organisation, circuits, devises, langues et calendriers propres à chaque société.

- Multi-pays : fuseaux horaires, devises et taux de change, langues (arabe, français, anglais ; autres langues ajoutables, par exemple le turc).

- Monolithe modulaire pour le métier ; services séparés pour l’IA et les intégrations.

- Portail externe (clients, fournisseurs) isolé dans une zone exposée distincte.

- Déploiement sur site ou en cloud privé, en conteneurs Docker ou Kubernetes ; accès stable depuis plusieurs pays.

## 23. Modèle de données principal

| Entité | Informations clés |
| --- | --- |
| Société et organisation | Groupe, société (mère, filiale, centrale), pays, devise, langue, sites, départements, centres de coûts, calendrier, mode d’achat. |
| Acteurs de la demande | Saisi par, demandeur, bénéficiaire, société demandeuse, société d’imputation, société acheteuse, client externe. |
| Tiers externe | Client, revendeur ou entrepreneur : identité, contacts, conditions de paiement, solvabilité. |
| Offre et commande client | Prix de vente, marge, validité, acceptation, commande client liée à la demande. |
| Refacturation interne | Sociétés émettrice et destinataire, montant, frais de service, lien avec la demande. |
| Demande | Numéro, société, source, demandeur, catégorie, priorité, type d’achat, statut, dates. |
| Ligne de demande | Article, description, quantité, unité, prix estimé, date requise, statut par ligne, référence ERP. |
| Circuit et validation | Définition, version, étape, valideur, décision, commentaire, horodatage. |
| Consultation et offre | Fournisseurs invités, offres, devise, prix, délai, paiement, Incoterm, validité, conformité. |
| Évaluation | Validation technique, commerciale, direction ; notes ; procès-verbaux. |
| Commande et contrat | Numéros interne et ERP, montants, lignes, devise, conditions, paiements, garanties. |
| Expédition | Mode, ports, documents (B/L, AWB…), étapes, dates prévues et réelles, statut douanier. |
| Réception et facture | Quantités, exceptions, facture, montant final, rapprochement. |
| Fournisseur et article | Fiches synchronisables avec l’ERP ; dossier, risque, performance. |
| Audit | Utilisateur, date, heure, action, commentaire, ancien et nouveau statut, empreinte chaînée. |

## 24. Cybersécurité et audit

- Authentification sécurisée, politique de mots de passe forts, double authentification pour la direction, les achats et la finance.

- Chiffrement en transit (TLS) et au repos (base de données et fichiers).

- Contrôle d’accès par rôle et par périmètre (société, pays, catégorie).

- Piste d’audit complète : connexions, validations, changements de circuits, dépôts de documents, activités d’achat, changements de statut, actions des utilisateurs ; journaux chaînés et infalsifiables.

- Sauvegardes automatisées, restauration testée et plan de reprise après sinistre.

- Durcissement des serveurs, protection contre les accès non autorisés, analyse antivirus des fichiers.

- Tests d’intrusion avant l’ouverture des portails externes.

- Données personnelles traitées selon le droit applicable dans chaque pays.

## 25. Exigences non fonctionnelles

| Domaine | Exigence |
| --- | --- |
| Performance | Écrans courants en moins de 2 secondes et tableaux de bord en temps réel pour la charge convenue (objectif validé en recette). |
| Disponibilité | Objectif fixé par contrat ; haute disponibilité en édition Entreprise. |
| Montée en charge | Ajout de sociétés, utilisateurs, fournisseurs et transactions sans refonte. |
| Mobilité | Interface responsive ; validation et réception depuis le téléphone. |
| Langues et devises | Multilingue, droite à gauche, multi-devises. |
| Ouverture | API documentées, exports en formats ouverts. |
| Maintenabilité | Architecture propre, tests automatisés, intégration continue, documentation. |

## 26. Livrables

| N° | Livrable |
| --- | --- |
| 1 | Analyse des besoins et paramétrage cible (sociétés, circuits, catégories, rôles). |
| 2 | Dossiers de conception fonctionnelle et technique. |
| 3 | Maquettes et parcours utilisateurs. |
| 4 | Modules M1 à M16 selon le périmètre retenu. |
| 5 | API et connecteurs ERP. |
| 6 | Tests unitaires, d’intégration et de recette utilisateur. |
| 7 | Formation des utilisateurs et des administrateurs. |
| 8 | Mise en production et accompagnement au démarrage. |
| 9 | Documentation technique, manuels utilisateurs et d’exploitation. |
| 10 | Garantie et support après mise en production. |

## 27. Plan de réalisation et backlog du produit minimum

| Lot | Contenu | Durée indicative |
| --- | --- | --- |
| Lot 0 : Socle | Reprise du socle existant (authentification, demandes, audit), multi-sociétés, rôles, intégration continue. | 3 semaines |
| Lot 1 : Demande et validations | M1, M2, M15 ; saisie assistée en texte libre. | 5 semaines |
| Lot 2 : Centrale d’achat et consultations | M3, M4, lecture des offres par l’IA, comparatif. | 5 semaines |
| Lot 3 : Évaluation et commande | M5, M6, échange de fichiers avec l’ERP. | 4 semaines |
| Lot 4 : Logistique et clôture | M7, M8, M14, lecture des documents de transport. | 5 semaines |
| Lot 5 : Pilotage | M11 et tableau du directeur général. | 3 semaines |
| Phase 2 | Budget, portail fournisseurs, intégrité, assistant en langage naturel, connecteurs API ERP. | Par incréments |

### Récits utilisateurs prioritaires

| Réf. | En tant que… | Je veux… |
| --- | --- | --- |
| US-01 | Demandeur | créer une demande multi-lignes avec pièces jointes et recevoir un numéro unique. |
| US-02 | Demandeur | décrire mon besoin en texte libre et obtenir une demande pré-remplie à vérifier. |
| US-03 | Responsable de département | approuver, rejeter, renvoyer ou demander une information depuis mon téléphone. |
| US-04 | Administrateur | configurer un circuit par société, catégorie et montant sans développement. |
| US-05 | Responsable achats | voir toutes les demandes validées dans une boîte centrale filtrable et les affecter. |
| US-06 | Acheteur | enregistrer ou importer des offres et obtenir un comparatif automatique. |
| US-07 | Valideur technique | accepter ou refuser une offre avec motif. |
| US-08 | Acheteur | suivre une expédition internationale étape par étape avec ses documents. |
| US-09 | Réceptionnaire | enregistrer une réception partielle ou non conforme avec photos. |
| US-10 | Directeur général | savoir en un clic où en est une demande, qui est responsable, combien, et si elle est en retard. |
| US-11 | Auditeur | consulter l’historique complet de chaque décision. |
| US-12 | Administrateur ERP | exporter les demandes validées et importer fournisseurs, articles et statuts. |
| US-13 | Client externe | déposer une demande sur le portail et suivre l’offre, l’expédition et la facture. |
| US-14 | Commercial | qualifier une demande externe et envoyer une offre de prix de vente avec la marge validée. |
| US-15 | Responsable achats | choisir pour chaque demande le mode d’achat : direct, centralisé ou pour le compte de la filiale. |
| US-16 | Finance | générer la refacturation interne d’un achat centralisé à la filiale demandeuse. |

Un produit vendable (lots 0 à 5) est prêt en 6 mois environ ; sprints de deux semaines avec démonstration.

## 28. Positionnement et modèle commercial

| Alternative | Limite | Réponse |
| --- | --- | --- |
| Suites internationales (SAP Ariba, Coupa, Oracle Procurement Cloud) | Coût élevé, cloud étranger, peu adaptées aux groupes régionaux multi-pays et au commerce international local. | Installation sur site, centrale d’achat de groupe, logistique internationale native, prix maîtrisé. |
| Modules achats des ERP | Faibles sur la demande, la collaboration et la logistique ; licences par utilisateur. | Couche de demande en amont de l’ERP, demandeurs sans licence ERP. |
| Développements spécifiques | Coûteux, longs, difficiles à maintenir. | Produit prêt, configurable, maintenu. |

| Édition | Contenu | Base de prix |
| --- | --- | --- |
| Essentielle | M1 à M8, M11, M14, M15, échange de fichiers ERP. | Licence par palier d’utilisateurs actifs ; demandeurs illimités |
| Groupe | Essentielle + multi-sociétés et multi-pays, centrale d’achat, demandes externes. | Licence par palier et par société |
| Entreprise | Groupe + budget, portail fournisseurs, intégrité, assistant IA, connecteurs API, haute disponibilité. | Licence par palier ou licence site |
| Services | Paramétrage, reprise des fournisseurs et articles, formation, connecteurs ERP, maintenance. | Forfait, régie et pourcentage annuel |

Licence perpétuelle avec maintenance ou abonnement annuel. Les montants figurent dans l’offre financière.

## Annexe A : Glossaire

| Terme | Définition |
| --- | --- |
| Demande d’achat (PR) | Expression formelle d’un besoin, avant sa transformation en commande. |
| Commande (PO) | Engagement contractuel envoyé au fournisseur. |
| Consultation (RFQ) | Demande de prix adressée à plusieurs fournisseurs. |
| Incoterms | Règles internationales qui fixent les obligations du vendeur et de l’acheteur (EXW, FOB, CIF, DAP…). |
| Crédit documentaire (LC) | Engagement bancaire de payer le fournisseur contre remise de documents conformes. |
| Connaissement (B/L) | Document de transport maritime qui vaut titre de propriété de la marchandise. |
| Lettre de transport aérien (AWB) | Document de transport pour le fret aérien. |
| Réception (GR) | Constat de l’arrivée et de la conformité des marchandises. |
| Rapprochement à trois voies | Contrôle de cohérence entre commande, réception et facture. |
| Matrice des délégations de pouvoir | Tableau qui fixe qui peut approuver quoi, jusqu’à quel montant. |

Principe : une seule demande, un seul numéro, un responsable à chaque étape, et une réponse immédiate à la question « où en est mon achat ? ».
