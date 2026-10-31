# Sprint 12 — Automated verification report

| | |
|---|---|
| Date | 2026-10-31 |
| Label | Developer self-checks — **not** independent QA/reviewer sign-off |
| Manual UAT | Pending for Sprints 5–12 |
| Provider under test | **DEMO only** — no live model |

## Sprint 11 baseline (before S12)

| Check | Result |
|---|---|
| Full e2e after messaging fix | **32 passed** — [sprint-11-regression-before-s12.md](sprint-11-regression-before-s12.md) |

## Commands executed (Sprint 12)

| Check | Command | Result |
|---|---|---|
| Full backend | `.\mvnw.cmd verify -Pdb-it` | **BUILD SUCCESS** — Tests run: **139**, Failures: 0, Errors: 0 (after removing assistant `@TestPropertySource` that forked a second context and collided Hibernate sequences; one prior run had a flake on `ApprovalIntegrationTests.linkApprovalAndInboxRejectionRaceHasExactlyOneWinner`, which passed in isolation and on the successful full verify) |
| Assistant IT | `AssistantIntegrationTests` | **3 passed** (demo answers, guessed-ID non-leak, role denies) |
| Frontend lint / unit / build | `npm run lint`; `npm test`; `npm run build` | Lint pass; **202** unit; build pass (existing budget warning ~503.6 kB) |
| E2E | Full suite + `e2e/assistant.spec.ts` | **34 passed** (includes assistant demo + supplier deny; S11 messaging still green) |
| Secrets scan | Grep Sprint 12 paths for password/token/key patterns | No credential literals |

## Provider matrix

| Scenario | Fake/demo | Live provider |
|---|---|---|
| Ask / draft / deny / injection | Covered by `AssistantIntegrationTests` + e2e | **Blocked** (ADR-012) |
| Answer quality EN/FR/AR | N/A (deterministic text) | **Blocked** |
| External data residency | N/A | **Blocked** |

## Explicitly not passed

- Live AI; OCR; autonomous actions; messaging attachments; manual UAT; pen-test; §15
