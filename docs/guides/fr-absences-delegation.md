# Guide absences et délégation (comportement réel)

## Qui configure

| Acteur | Page / API | Droits |
|---|---|---|
| Utilisateur lui-même | `/absences` → `POST /api/v1/me/absences` | Ses propres absences ; délégué doit partager un rôle d’approbation dans la même société |
| ADMIN ou GLOBAL_ADMIN | Admin absences (API `/api/v1/admin/...`) | Même groupe ; option `adminOverride` pour délégué hors rôle strict |

**Correction** : `Absences.requireAdmin` accepte désormais `GLOBAL_ADMIN` (auparavant `Role.ADMIN` seul → décalage UI `SYSTEM_ADMIN` / API).

## Champs

- `startsAt`, `endsAt` (UTC) — `endsAt` strictement après `startsAt`
- `delegateUserId` — pas soi-même ; même groupe
- `reason` (≤ 300)
- `adminOverride` (admin seulement)

Chevauchements refusés (`absence-overlap`).

## Routage (création de tâche uniquement)

Documenté dans `ApproverResolver.route` / commentaire `Absences` :

1. À la **création** d’une tâche d’approbation, si l’approbateur désigné a une absence **active** à l’instant `at`, la tâche est assignée au délégué (note `ABSENCE_DELEGATE`, `original_user_id` renseigné).
2. Chaîne : jusqu’à `MAX_DELEGATION_HOPS` (voir code), détection de cycle A→B→A.
3. Délégué inactif, inéligible, auteur de la demande, ou boucle → `ABSENCE_UNRESOLVED` : la tâche reste sur l’approbateur désigné.
4. **Tâches déjà créées avant le début d’absence** : **non réassignées** automatiquement (politique actuelle).
5. **Après la fin d’absence** : les nouvelles tâches suivent l’approbateur nominal ; les tâches déjà chez le délégué restent chez lui jusqu’à décision.
6. Auto-approbation : les auteurs (requester / entered-by) sont retirés des détenteurs ; un délégué auteur est inéligible.
7. Stages ANY / ALL : politique inchangée ; un même utilisateur ne doit pas compter deux fois (résolution des holders + délégation).
8. Questions : mettent en pause les SLA (`question_open`) ; rappels / escalade **n’approuvent pas**.
9. Audit : `actor_user_id` + `on_behalf_of_user_id` sur les événements de décision déléguée.

## Scénario type (Karim → Hichem)

1. Créer une absence datée pour Karim avec Hichem délégué (éligible même société / rôle).
2. Pendant la période : soumettre une demande qui crée une tâche pour le rôle de Karim → assignee = Hichem.
3. Avant / après les dates : assignee = Karim (sauf tâche déjà créée).
4. Tentative de délégué hors société / hors groupe → refus.
5. Boucle A→B→A → unresolved / pas de boucle infinie.

## Limites connues (pas inventées)

- Pas de « task delegation » séparée hors absence (hors action DELEGATE sur une tâche pending si exposée dans le moteur).
- Pas de redistribution automatique des tâches ouvertes à la révocation d’absence ou à la désactivation du délégué.
- Si aucun approbateur éligible au démarrage du circuit → `no-eligible-approver` (422), notification admin éventuelle.

## Décisions métier encore ouvertes

Aucune décision bloquante pour livrer REQUEST_ADMIN. Si le métier exige la **réassignation des tâches déjà ouvertes** au début d’une absence, cela doit être une ADR explicite (comportement actuel = création seule).
