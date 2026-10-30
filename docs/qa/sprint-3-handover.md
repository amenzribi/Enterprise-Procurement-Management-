# Sprint 3 handover (closure)

Local closure revision, 2026-10-30, on top of feature commit `a24500e` and hash note `30e35b9`. **Developer self-check only** — not independent QA or review. **Do not push** until the owner asks.

## Hashes

| Hash | Meaning |
|---|---|
| `a24500e` (`a24500ef35d3fb371a5f45cdf68044c6b3a16166`) | Sprint 3 feature delivery |
| `30e35b9` | Docs-only note that recorded `a24500e` in the handover |
| Closure commit | `e3c4747` (`e3c47470bc91374da17f3251b75cc8544b168347`) |

## What closed

S3-D-01 (full S3-AC-01 queue filters API+UI) and S3-D-02 (category affinity then workload suggestion). Purchasing question (S3-AC-02) added. Concurrent stale assign → 409 covered by IT. Fresh verify **108/108**, lint, 201 unit tests, production build. Full e2e on this tree: see test results.

Plan: [sprint-3.md](../product/sprint-3.md). Results: [sprint-3-test-results.md](sprint-3-test-results.md). Defects: [sprint-3-defects.md](sprint-3-defects.md). Matrix: [sprint-3-acceptance-matrix.md](sprint-3-acceptance-matrix.md).

## How to run (this machine)

Ports **8081** / **4300**. Backend `dev,demo` → SQL Server `procurement_dev` (Windows auth). Frontend: `npx ng serve --port 4300 --proxy-config <proxy-8081.json>`. Demo password: `%TEMP%\epi-e2e-pw.txt` (never commit). E2E: `E2E_BASE_URL=http://localhost:4300` and the same `DEMO_USER_PASSWORD`.

## Demo path

Sami → Karim → Ines queue → Mehdi assigns Ines → Ines accepts. Claire cannot open `/purchasing`. Julien cannot open the Tunisia request. MIXED Sfax proposes DEMO-CPA; DIRECT moves buying to Sfax without changing charged company.

## Outstanding (do not clear)

- S2CR-08 → Sprint 9.
- Native-speaker Arabic review.
- Pilot gate §15 unmet (rollout only).
- Independent QA / code review of Sprint 3.
- Sprint 4 (RFQs) is the next roadmap sprint.
