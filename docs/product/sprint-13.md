# Sprint 13 — Full-system verification and consistency

**Status:** In progress (developer self-review). Manual UAT for Sprints **5–13** remains **pending**. Not independently reviewed.

## Objective

Reconcile approved Core requirements against the implemented product (S0–S12), review correctness/security/UI consistency, fix acceptance blockers and manageable defects, and produce an acceptance pack — **without** inventing a new feature sprint.

## Out of scope for this sprint

- Live AI provider selection or external model calls (ADR-012 pending).
- Trading / Enterprise add-ons, MFA, pen-test, restore drill, load tests, §15 pilot signing.
- Rewriting working modules or changing applied Flyway migrations.
- Committing unrelated CRLF-only dirty files.

## Acceptance criteria

| ID | Criterion |
|---|---|
| S13-AC-01 | Requirements implementation matrix published with status per FR/UC (implemented / partial / deferred / blocked / out of scope). |
| S13-AC-02 | System review findings recorded with severity; Critical/High blockers fixed or explicitly accepted with evidence. |
| S13-AC-03 | UI no longer presents delivered Core stages (S4–S7) as “planned sprint” placeholders. |
| S13-AC-04 | Assistant remains demo/disabled only; hosting decision brief prepared; no external transmission. |
| S13-AC-05 | Automated verification re-run on the Sprint 13 revision; results recorded honestly. |
| S13-AC-06 | Final manual acceptance plan + Codex/Claude review brief published; handover updated. |
| S13-AC-07 | Manual UAT not marked passed; production readiness not claimed. |

## Deliverables

- `docs/qa/requirements-implementation-matrix.md`
- `docs/qa/sprint-13-system-review.md`
- `docs/qa/sprint-13-automated-verification.md`
- `docs/qa/final-manual-acceptance-plan.md`
- `docs/qa/codex-claude-review-brief.md`
- Updated handover and manual index
