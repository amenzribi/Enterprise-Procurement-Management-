# Sprint 4 — User acceptance (manual)

| | |
|---|---|
| Date | 2026-10-30 |
| Tester | Product user (Mohamed), not automated QA |
| Application | http://localhost:4300 → backend :8081 (`dev,demo`) |
| Revision under test | Local main including `f88345a` / `98d7176` (Sprint 4 delivery + null-check fix), after Sprint 3 closure `e3c4747` |

## Confirmed scenarios (user)

The user confirms these manual scenarios **worked**:

1. Sami creates and submits a request.
2. Karim approves it.
3. Mehdi assigns Ines.
4. Ines accepts the assignment.
5. Ines opens a Quick quote RFQ using an eligible supplier.
6. Entering a quotation updates the dossier and comparison.
7. Technical validation followed by commercial validation works.
8. Supplier-link quotation submission works without signing in.
9. Invalid supplier tokens show an invalid-link page.
10. Claire cannot access purchasing.
11. Julien cannot access Sami’s Tunisia request.
12. Purchasing assignment and purchase-mode regression scenarios work.
13. Arabic RTL / mobile layout was manually checked.

## Explicitly not claimed by this UAT

- Exhaustive coverage of every Sprint 3/4 acceptance criterion.
- Automated suite green on this exact interactive session.
- Independent code review or penetration test of supplier links.
- Production readiness or pilot-gate passage (Solution Guide §15 remains unmet).
- API-only abuse cases beyond what the UI exercises (rate limits, concurrent award, etc.).

## Relation to automated evidence

Automated verify/e2e results remain separate evidence in [sprint-3-test-results.md](sprint-3-test-results.md) and Sprint 4 IT (`SourcingIntegrationTests`). UAT does not replace them.
