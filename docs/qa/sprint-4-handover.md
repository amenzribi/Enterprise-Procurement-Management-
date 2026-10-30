# Sprint 4 handover (developer)

Local delivery, 2026-10-30. **Not** independent QA or review. **Do not push** until asked.

## What shipped

Accepted buyer (or purchasing manager) opens a QUICK/STANDARD RFQ with eligible suppliers → request `SOURCING`. Manual quotation and/or secure `/q#token` supplier link (no account). Comparison with recorded FX + landed cost (merchandise+freight+duty+other); missing line prices listed. Technical then commercial validation → `COMMERCIAL_VALIDATION`. No award / PO. AI quote reading not implemented (FR-SRC-05 deferred).

Plan: [sprint-4.md](../product/sprint-4.md). Builds on Sprint 3 closure `e3c4747`.

## How to run

Ports **8081** / **4300**. Backend `dev,demo`, SQL Server `procurement_dev`. Demo password: `%TEMP%\epi-e2e-pw.txt`. Flyway through **V9**.

## Demo path

Sami → Karim → Mehdi assigns Ines → Ines accepts → Ines opens RFQ (DEMO-SKF) → enters quote → comparison → technical → commercial. Claire/Julien cannot open sourcing for the Tunisia request. Supplier link: copy from dossier, open `/q#…` signed out.

## Outstanding

- AI quote reading: deferred (no provider port).
- Award / PO: Sprint 5.
- Pilot gate §15 unmet — production exposure of supplier links requires pen-test / pilot approval.
- S2CR-08 Sprint 9; native Arabic review outstanding.
- Independent QA/review of S3 and S4.
