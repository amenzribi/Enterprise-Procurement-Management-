# Handoff: Ledger verification (2026-11-09)

## Status

- DONE: stabilization and Ledger redesign, commits `96da606` and `c67c733`.
- DONE: verification of both and a fix batch. Results in [docs/design/verification-report.md](docs/design/verification-report.md).
- DONE: the fix batch, committed on `stabilization-and-ledger-redesign` as the commit after `c67c733` and pushed to `enterprise` `main`. Re-verify: `git log --oneline -3` and `git ls-remote enterprise main`.
- DONE: D-03 and D-07 rules, committed after `c3adb10` and pushed to `enterprise` `main`; verified as below. Re-verify: `git log --oneline -3` and `git ls-remote enterprise main`.
- NOT STARTED: independent QA and review; layout redesign of sourcing, ordering, logistics, admin and messaging.

**Readiness:** internal demo, yes. Hand-over to independent QA, yes. Pilot or production, no.

## Selected direction

Ledger (Direction A), static sign-in. Defined in [DESIGN.md](DESIGN.md). Tokens: `FrontEnd/src/styles/_tokens.scss`. Approved preview: `docs/design/prototypes/index.html`.

## Decisions

- D-01: a purchasing manager may accept a shortfall, with a reason, once every receipt is confirmed. Why: a rejected unit otherwise stranded the request.
- D-02: purchasing marks an invoice paid; closure does not require payment. Why: matches sprint-7 closure rules.
- An invoice may be recorded without attestation and attested later. Why: S7-AC-06 makes it optional, while closure requires it.
- D-03: the person who recorded a receipt cannot confirm it. Why: one person should not both record and accept goods. Cost: a site needs a second eligible person.
- D-07: expired quotes cannot be awarded, and a quantity different from the request needs a recorded reason. Why: an award should not commit beyond what was approved without an explanation.
- Quotes stay out of the approval pane. Why: they come after approval in the real workflow.

## Changes

Branch `stabilization-and-ledger-redesign`. Commits `96da606`, `c67c733`, on `1b59c4e`.
Remotes: `enterprise` (private; its `main` is at `c67c733`) and `origin` (same branch at `c67c733`; its `main` lacks this work).

**Changed routes (no route was added or removed):**

| Route | Change |
|---|---|
| `/overview` | Next-approval band, compact lists |
| `/approvals` | Queue beside the decision from 64 rem |
| `/requests`, `/requests/:id` | Restyled; "Created" column fixed; rebuilt receipt panel |
| `/purchasing` | Secondary filters folded |
| `/sign-in` | Static restyle |

**API additions under `/api/v1/receipts/requests/{id}`:** `shortfall-acceptance`, `invoice/paid`, `invoice/match-attested`. Request list adds `createdAt`. Dossier adds `shortfallAccepted` and `purchaseOrder` with `receivable` per line.

**Migrations:** V18 and V19, applied only to `procurement_test`. `procurement_dev` migrates at next start.

## Verification

On `c3adb10` plus the working tree (2026-11-09), detail in the report:

- Passed: lint; unit 223/223; build; Playwright 58/58; closure journeys repeated 8/8; backend `verify -Pdb-it` 177/177 (run before the last change, which was one frontend line).
- UNVERIFIED: screen reader; real touch devices; runtime performance against the baseline; native FR and AR review; independent QA.
- Two fixes have no automated test: the last-page redirect after a decision, and the quote total overflow check.

Reports and screenshots: `docs/design/verification-report.md`, `docs/design/verification/`, `docs/design/design-qa.md`, `docs/design/after/`, `docs/design/baseline/`, `docs/reviews/stabilization-review.md`.

## Blockers

- Pilot needs independent QA, penetration test, MFA and restore drill.

## Open

D-05, D-06, PERF-04, and low items R-1 to R-6, all in the verification report.

## Next action

Hand the head of `enterprise` `main` to independent QA, using `docs/design/verification-report.md` as the starting point.

When committing, do not add `docs/tooling.md` (the owner's preflight) or `.anchor/` (a plugin's session file).

## Skills Review

- ✅ `supermatt:code-review`: three axes found four P1s in the receipt form that 171 passing backend tests had missed. Host-only; no external reviewer configured.
- ✅ `design-superpowers:design-review`: run at L1 by reading tokens and screenshots. Confirmed RTL, density and focus token conformance.
- ⚠️ `design-skills:accessibility-audit`: automated and keyboard layers done; screen reader layer not run.
- ✅ `superpowers:verification-before-completion`: repeated runs exposed two intermittent races one pass would have hidden.
- ❌ Earlier self-assessment: the receipt form was reported complete after happy-path tests only. Partial and split deliveries were broken.

**Environment findings**

- Automated checks: no CI gate runs Playwright. Propose a workflow that runs it on pull requests.
- Automated checks: the 20-step journey overwrites tracked evidence on every run. Propose writing to an ignored folder unless a flag is set.
- Standards: raw hex and `rgb()` in component styles are caught only in review. Propose a stylelint rule.
