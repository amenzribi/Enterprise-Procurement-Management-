# Project handover (current)

Updated after the **usability** implementation pass (admin clarity, My work, newest-first lists, references, logistics inbox, FR guides). Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `b967b3e` / `fabfe96` | Sprint 12 assistant + handover |
| `4616e83` … `838d9f9` | Sprint 13 verification / nav / workspace home |
| Usability commits (local) | See `git log` after this pass |

**Unrelated dirty tree** (purchasing/supplier/org/import/V7/frontend/docs): classified **CRLF-only** — leave uncommitted unless Mohamed asks. **Do not edit applied V7.**

## Running now

- Frontend: **http://localhost:4300** → Backend: **http://localhost:8081**
- Profiles: `dev,demo`; DB: `procurement_dev`; Flyway **V15**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit or print.
- Assistant: **demo** under demo profile; default elsewhere **disabled**. No live provider.
- Mail: file capture only (no real external mail).

## Usability docs (French)

- [fr-administrateur-societes-utilisateurs.md](../guides/fr-administrateur-societes-utilisateurs.md)
- [fr-matrice-roles-permissions.md](../guides/fr-matrice-roles-permissions.md)
- [fr-guide-utilisateur-trouver-son-travail.md](../guides/fr-guide-utilisateur-trouver-son-travail.md)
- [list-sort-defaults.md](../product/list-sort-defaults.md)
- [usability-findings-and-fixes.md](usability-findings-and-fixes.md)
- [usability-manual-retest.md](usability-manual-retest.md)

## Preserve

- Closed UAT dossier **DEMO-TN-PLT-PR-2026-000490** — UAT-01 — Pump seals.

## Manual acceptance

- Sprints **5–13** and this usability pass: **pending** Mohamed’s confirmation — do not mark passed without it.

## Residual / gates

- Independent Codex/Claude review
- Live AI hosting decision (A/B/C)
- MFA, pen-test, restore drill, load, §15 pilot, native AR review
- Deferred: messaging attachments, OCR, algorithmic 3-way, G12 grouping/transfer/buy-again, Trading/Enterprise
