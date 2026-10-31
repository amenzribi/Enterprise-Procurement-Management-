# Project handover (current)

Updated 2026-10-31 after **Sprint 13** (verification / reconciliation / consistency). Developer self-labelled evidence only — **not** independent QA/reviewer sign-off.

## Git (local only — not pushed)

| Commit | Role |
|---|---|
| `b967b3e` / `fabfe96` | Sprint 12 assistant + handover |
| `4616e83` | S13 security: contact invite + PO milestones |
| `2e54c53` | S13 UI: Core journey no longer “planned” |
| `69331a2` | S13 docs: matrix, review, acceptance, brief |
| Ahead of `origin/main` | Do not push until asked |

**Unrelated dirty tree** (purchasing/supplier/org/import/V7/frontend/docs): classified **CRLF-only** — leave uncommitted. **Do not edit applied V7.**

## Running now

- Frontend: **http://localhost:4300** → Backend: **http://localhost:8081**
- Profiles: `dev,demo`; DB: `procurement_dev`; Flyway **V15**
- Demo password: `%TEMP%\epi-e2e-pw.txt` (`DEMO_USER_PASSWORD`). Never commit or print.
- Assistant: **demo** under demo profile; default elsewhere **disabled**. No live provider.

## Sprint 13 docs

- [requirements-implementation-matrix.md](requirements-implementation-matrix.md)
- [sprint-13-system-review.md](sprint-13-system-review.md)
- [sprint-13-automated-verification.md](sprint-13-automated-verification.md)
- [final-manual-acceptance-plan.md](final-manual-acceptance-plan.md)
- [codex-claude-review-brief.md](codex-claude-review-brief.md)
- Manual index: [sprint-5-to-12-manual-index.md](sprint-5-to-12-manual-index.md) (covers 5–13)
- AI hosting brief: [ADR-012-hosting-decision-brief.md](../decisions/ADR-012-hosting-decision-brief.md)

## Manual acceptance

- Sprints **5–13**: **pending** Mohamed’s confirmation — do not mark passed without it

## Residual / gates

- Independent Codex/Claude review (use the brief)
- Live AI hosting decision (A/B/C)
- MFA, pen-test, restore drill, load, §15 pilot, native AR review
- Deferred: messaging attachments, OCR, algorithmic 3-way, G12 grouping/transfer/buy-again, Trading/Enterprise
