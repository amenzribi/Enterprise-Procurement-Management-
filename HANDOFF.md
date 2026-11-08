# Handoff

Last update: 2026-11-08, after the stabilization pass and the Ledger redesign.

## State

- `main` at `1b59c4e`, with **all work uncommitted**. Nothing is pushed.
- New migrations: V18 (list index) and V19 (shortfall acceptance). They have been applied only to `procurement_test`; `procurement_dev` will migrate on next start.
- `docs/tooling.md` holds the user's uncommitted Desktop preflight. Leave it as it is.
- New dependencies: `@fontsource-variable/geist` and `geist-mono` (OFL-1.1).

## Documents

- Findings and verdict: [docs/reviews/stabilization-review.md](docs/reviews/stabilization-review.md)
- Design system: [DESIGN.md](DESIGN.md)
- Design results and gaps: [docs/design/design-qa.md](docs/design/design-qa.md)
- Prototype comparison: [docs/design/prototype-comparison.md](docs/design/prototype-comparison.md)

## Verified on the final code

Frontend lint, 218/218 unit tests and build. Backend `verify -Pdb-it` 171/171.
Playwright: last full run 38/40; the 2 failures were outdated tests, since fixed and rerun individually. A full run after those fixes was not repeated.

**Not done:** independent QA and review, penetration test, MFA, restore drill, production-like load.

## Verdict

No open Major finding. Pilot or production remains a no-go until the items above are done.

## Open

| ID | Question |
|---|---|
| D-03 | May a receiver confirm their own receipt? |
| D-05 | Dashboards and exports stop at 5,000 requests without saying so. |
| D-06 | Bursts of submissions in one company can get a retryable 503. |
| D-07 | May expired quotes be awarded, and may quote quantity differ from the request? |

Design: sourcing, ordering, logistics, admin, messaging, forms, dialogs and notifications have the new tokens but no layout redesign.

## Working notes

- Do not run Maven in the VS Code workspace while the Java extension builds: it overwrites `target/classes` mid-run. Use a copy outside the workspace.
- Validation errors in this API are 422; invalid query parameters are 400.
- Demo company admin scope covers all demo users, so it cannot exercise company-scoped pagination.
- The 20-step Playwright test overwrites `docs/qa/evidence/`. Restore it unless new evidence is intended.
- The initial bundle is 6.46 kB over its 500 kB budget.
