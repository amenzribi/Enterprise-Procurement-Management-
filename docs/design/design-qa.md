# Design QA: Ledger rollout

Date: 2026-11-08. Self-check by the developer session, not independent QA. Direction A with a static sign-in.

## What changed

| Area | Depth | Result |
|---|---|---|
| Tokens, fonts, icons in place | Application-wide | Every module follows the new palette, type and radius through tokens. |
| Shell | Rebuilt | Light navigation with hairlines; product named Procurement 360. |
| My work | Rebuilt | Next-approval band, compact lists. |
| Approval inbox | Rebuilt | Queue beside the decision at 64 rem and wider, using the existing decision component and API. |
| Requests list | Restyled, one bug fixed | "Created" now shows the creation date; see below. |
| Request detail | Restyled | Quieter header; reference in mono. |
| Purchasing inbox | Layout changed | Search, status, priority and sort stay visible; 11 secondary filters fold under "More filters" and stay open while one is applied. |
| Receipt panel | Rebuilt form | Per-line quantities, accept with shortfall, mark as paid; demo pre-fills removed. |
| Sourcing, ordering, logistics, admin, messaging, assistant, forms, dialogs, notifications | **Tokens only** | Restyled through tokens. No layout work. |
| Sign-in | Restyled | Static. |

## Defect fixed

The requests table had 7 headers and 6 cells, so "Created" showed the last-modified date and "Last modified" was empty. The list API did not return a creation date. `createdAt` was added to the response: an additive change. Covered by a new unit test.

## Differences from the prototype

The prototype's approval pane showed a quote comparison. In the real workflow, quotes come after approval, so the live pane shows amount, requester, wait and due date. Quote comparison belongs to sourcing, whose screens were not rebuilt.

## Checks run

| Check | Result |
|---|---|
| Lint | Clean |
| Unit tests | 218/218 |
| Production build | OK |
| Backend `verify -Pdb-it` | 171/171 |
| Playwright, full suite (last full run) | 38 passed, 2 failed |
| The 2 failures | Both closure journeys relied on removed demo pre-fills and a waiver field that is now hidden. Tests updated; both rerun and passed. |
| New split-pane journey | Keyboard selection, deciding in the pane, next item loads: passed |
| Reduced motion | Row transition removed: passed |
| axe on the inbox in the split-pane layout, EN and AR | Passed |
| axe on 16 further screens, desktop, English | No violations |
| Phone overflow at 390 px on those 16 screens | None, after fixing 3 |

After the last full Playwright run, the only changes were the user-admin form style fix and those two journey tests. The two journeys and the 16-screen sweep were rerun after them; the full suite was not.

Screens reviewed by eye: the inbox at desktop, tablet and phone; My work, requests, record, sign-in, purchasing, dashboards, request administration, messages and assistant at desktop. Arabic right-to-left was reviewed on the inbox only.

## Before and after

| | Before | After |
|---|---|---|
| Approvals visible at 1440×900 | About 4 | 8, with the decision alongside |
| Initial bundle | 504.87 kB | 506.46 kB (6.46 kB over the 500 kB budget) |
| Initial transfer (estimated by the build) | 134.54 kB | 134.60 kB |

Screenshots: [`baseline/`](baseline/) and [`after/`](after/), with other modules in [`after/modules/`](after/modules/). The baseline has no Arabic capture or request-detail capture. Some screens were empty in the test data (purchasing inbox, messages).

## Known gaps

- Screens listed as tokens only are consistent but have had no layout redesign.
- Request administration still shows its allowed actions as raw codes (VIEW, CANCEL).
- New French and Arabic strings need native review. Arabic beyond the inbox was checked by automated specs only.
- At tablet width in development, the environment badge truncates the product name. The badge is development-only.
- Runtime performance (for example, Core Web Vitals) was not measured.
- The 20-step Playwright test overwrites `docs/qa/evidence/` on every run; restore with `git checkout -- docs/qa/evidence` unless new evidence is intended.
