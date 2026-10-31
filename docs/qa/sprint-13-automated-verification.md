# Sprint 13 — Automated verification

| | |
|---|---|
| Date | 2026-10-31 |
| Label | Developer self-checks — **not** independent QA/reviewer |
| Manual UAT | Pending Sprints **5–13** |
| Live AI | **Not tested** (blocked ADR-012) |

## Commands

| Check | Command | Result |
|---|---|---|
| Backend | `.\mvnw.cmd verify -Pdb-it` (env **without** stray `DB_TEST_URL`) | **BUILD SUCCESS** after security fixes |
| Frontend | `npm run lint`; `npm test`; `npm run build` | Pass; **202** unit; build pass (bundle budget warning ~503.8 kB) |
| E2E | Full suite then targeted retry | Full run: **33 passed**, 1 flake (`accessibility-approvals` EN desktop — empty `.card`); retry a11y+assistant+messaging: **7 passed**. Messaging + assistant green. |
| Secrets scan | Sprint 13 paths | No credential literals |
| New-DB migrate | Attempted `procurement_s13_probe` via Flyway Maven | Blocked by JDBC auth DLL outside app classpath; **covered instead** by IT Flyway clean+migrate on `procurement_test` (V1–V15). Probe DB dropped. |

## Defects re-verified

| ID | Fix | Evidence |
|---|---|---|
| S13-F-01 | Nav/overview honesty | FE unit tests |
| S13-F-06/07 | Messaging invite + supplier visibility | Messaging IT (+ new invite denial case) |
| S13-F-08 | PO milestone draft guard | Ordering store/service + Ordering IT |

## Blocked

Live AI quality/security; pen-test; load; restore drill; MFA; §15; manual UAT.
