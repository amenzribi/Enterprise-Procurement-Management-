# Absences et délégation — comportement réel

## Qui configure

| Acteur | Où | Droit |
|---|---|---|
| Utilisateur | `/absences` → API `/api/v1/me/absences` | Ses absences ; délégué partageant un rôle d’approbation dans la même société |
| `ADMIN` (groupe) ou `GLOBAL_ADMIN` | API `/api/v1/admin/absences` | Même groupe ; option `adminOverride` |

L’écran absences rappelle explicitement la limitation « création de tâche uniquement ».

## `adminOverride` — ce qu’il contourne / ne contourne pas

| Contourne | Ne contourne **pas** |
|---|---|
| L’exigence que le délégué tienne le même rôle d’approbation que l’absent | Isolation de **groupe** (délégué hors groupe → refus / introuvable) |
| | Compte **inactif** (routage → `ABSENCE_UNRESOLVED`, tâche reste sur l’approbateur désigné) |
| | **Auto-approbation** : un délégué auteur (requester / entered-by) est inéligible |
| | Auto-délégation à soi-même |
| | Chevauchement de périodes (`absence-overlap`) |

## Politique de routage (création de tâche uniquement)

1. À la **création** d’une tâche, si l’approbateur désigné a une absence active → assignee = délégué, `original_user_id` = absent, note `ABSENCE_DELEGATE`.
2. Chaîne max **3** hops (`MAX_DELEGATION_HOPS`) ; cycle → `ABSENCE_UNRESOLVED`.
3. **Tâches déjà créées avant** le début d’absence : **non réassignées**.
4. **Après fin d’absence** : nouvelles tâches → approbateur nominal ; tâches déjà chez le délégué **restent** chez lui.
5. **ANY** : plusieurs absents dont le délégué est déjà détenteur → une seule tâche, « on behalf of » cumule.
6. **ALL** : si le délégué a déjà une tâche requise de l’étage, l’absent **garde** la sienne (`ABSENCE_UNRESOLVED`) — une personne ne satisfait pas deux approvals ALL.
7. Questions : pause SLA ; rappels / escalade **n’approuvent pas**.
8. Audit : `actor_user_id` + `on_behalf_of_user_id`.

## Ce qu’un administrateur peut faire pour une tâche déjà ouverte

- Enregistrer / annuler une **période d’absence** (futur ou en cours).
- Utiliser, si exposée, l’action métier **DELEGATE** sur une tâche pending (décision du titulaire), distincte de l’absence.
- **Ne peut pas** : forcer depuis l’admin d’absence une réassignation rétroactive des tâches déjà pending. Aucune action inventée.

## Karim → Hichem (preuve correcte)

Les deux sont `DEPARTMENT_MANAGER` DEMO-MAINT : sur un étage **ANY**, Hichem peut déjà voir la tâche **sans** délégation. Preuve de délégation = `assignee = Hichem`, `original_user_id = Karim`, `routing_note = ABSENCE_DELEGATE`, événement `ABSENCE_ROUTED` / audit, affichage « pour le compte de ».

## Montants Sfax (borne 5 000 TND)

Bandes `[min, max)` en devise société :

| Règle | Intervalle | Effet à **exactement 5 000 TND** |
|---|---|---|
| Sfax up to 5 000 | `[0, 5000)` | **N’applique pas** 5000 |
| Sfax 5 000 to 50 000 | `[5000, 50000)` | **Oui** — département puis budget (Leila) |
| Sfax from 50 000 | `[50000, ∞)` | — |
