# Sprint 3–4 closure (developer self-labelled QA)

| | |
|---|---|
| Date | 2026-10-30 |
| Role | Developer self-check — **not** independent QA or reviewer sign-off |
| Tree | Local `main` through Sprint 4 delivery `f88345a` / null-check `98d7176` (handover note `c59f56d`), after Sprint 3 closure `e3c4747` |
| Manual UAT | Only the scenarios listed in [sprint-4-uat.md](sprint-4-uat.md). **Do not** treat this as exhaustive AC coverage. |
| Push | Not requested. Do not push. |

This document consolidates Sprint 3 and Sprint 4 for closure disposition. It does **not** claim Solution Guide §15 pilot passage, production readiness, or independent review.

---

## Explicit dispositions (required)

| Item | Disposition | Evidence |
|---|---|---|
| **S3-D-01** purchasing queue filters | **Fixed** (closed in `e3c4747`) | API + inbox UI expose company, country, category, priority, amount band, status, requester, required-date range, purchase mode, assigned buyer, completeness, text search; pagination/sort. IT `queueFiltersCountryAmountAndCompleteness…` in `PurchasingIntegrationTests`. Unit: `purchasing-inbox.spec.ts`. Defect log: [sprint-3-defects.md](sprint-3-defects.md). |
| **S3-D-02** workload-based buyer suggestions | **Fixed** (closed in `e3c4747`) | `buyer_category_affinity` (Flyway **V8**) + demo seed; suggestion prefers recorded category affinity then lowest open assigned-and-accepted workload. IT `buyerSuggestionPrefersCategoryAffinityAndConcurrentAssignConflicts`. Not AI. |
| **S2CR-08** proxy-aware rate limiting | **Deferred — Sprint 9** (unchanged) | `ApprovalLinkController.throttle()` still keys on `request.getRemoteAddr()`. Backlog **S2-C1**. Must not be labelled fixed. Required before reverse-proxy / production exposure of one-tap links. |
| **Native-speaker Arabic review** | **Outstanding** (non-code) | EN/FR/AR keys + RTL exist (NFR-11 / S2). No native-speaker review recorded. Backlog **S2-C2**. UAT scenario 13 only checked RTL/mobile layout manually — that is **not** a native wording review. |
| **Solution Guide §15 pilot gate** | **Unmet** (must remain unmet) | Paying pilot client not signed (roadmap Gate / G17). Restricts **rollout** (no production, no pilot tenant, no claim of client approval). Does **not** block local `dev,demo` development. Supplier quote links still need pen-test before production exposure (S4-AC-03 note). |

---

## Sprint 3 — criterion-by-criterion

Sources: [sprint-3.md](../product/sprint-3.md), [sprint-3-acceptance-matrix.md](sprint-3-acceptance-matrix.md), [sprint-3-test-results.md](sprint-3-test-results.md), [sprint-3-handover.md](sprint-3-handover.md).

| AC | Status | Code / tests / docs evidence | Manual UAT |
|---|---|---|---|
| S3-AC-01 Buying queue + filters | **Met (self)** | Queue API + UI; S3-D-01 closed; DB-IT filters; frontend unit filters | Partial — Claire cannot access purchasing (UAT #10); assignment path (#3–4). **Not** every filter exercised in UAT. |
| S3-AC-02 Completeness / question / return | **Met (self)** | Completeness return → `PURCHASING_RETURNED`; resubmit without new approval; purchasing question keeps status + audit | Not in UAT list as a dedicated return/question scenario |
| S3-AC-03 Buyer assignment + suggestion | **Met (self)** | Assign/accept/return; affinity then workload (S3-D-02); stale assign → 409 IT | UAT #3–4 (Mehdi assigns Ines; Ines accepts). Affinity suggestion not claimed in UAT. |
| S3-AC-04 Purchase mode | **Met (self)** | DIRECT/CENTRALIZED/ON_BEHALF; charged company unchanged; IT | UAT #12 (purchase-mode regression) |
| S3-AC-05 Supplier register | **Met (self)** | SHARED/COMPANY; deactivate; IT + UI | Eligible supplier used in UAT #5; full register CRUD not in UAT |
| S3-AC-06 Organisation admin | **Met (self)** | Companies/sites/departments/cost centres; cross-group 404 | Not in UAT list |
| S3-AC-07 Master-data import | **Met (self)** | CSV all-or-nothing; duplicate → 422 IT | Not in UAT list |
| S3-AC-08 Visibility / isolation | **Met (self)** | Claire queue 403 / request isolation; Julien TN 404; cross-company tests | UAT #10–11 |
| S3-AC-09 Audit and notices | **Met (self)** | Assign/accept/return/mode/completeness/question audited in same TX | Not exhaustively checked in UAT |
| S3-AC-10 Quality | **Met (self)** with caveats | EN/FR/AR; verify 108/108 + FE lint/unit/build on S3 closure tree; e2e full suite one flaky axe timeout then pass alone | UAT #13 Arabic RTL/mobile only |

**Deferred (not defects of the S3 slice):** G12 grouping / internal transfer / buy again → backlog **S3-C3**. RFQs were Sprint 4 (delivered).

---

## Sprint 4 — criterion-by-criterion

Sources: [sprint-4.md](../product/sprint-4.md), [sprint-4-handover.md](sprint-4-handover.md), [sprint-4-uat.md](sprint-4-uat.md). Module `sourcing`, Flyway **V9**.

| AC | Status | Code / tests / docs evidence | Manual UAT |
|---|---|---|---|
| S4-AC-01 Start sourcing | **Met (self)** | Accepted buyer / PM opens RFQ → `SOURCING`; IT `acceptedBuyerOpensRfq…` | UAT #5 |
| S4-AC-02 RFQ kinds | **Met (self)** | QUICK / STANDARD; active visible suppliers; lines from PR | UAT #5 Quick; IT also STANDARD on public-link test |
| S4-AC-03 Secure supplier link | **Met (self) for local** | Hashed token `/q#…`; anon summary/submit; invalid → 404/neutral page; IT `publicQuoteLinkSubmitsWithoutAccount` | UAT #8–9. **Pen-test / production exposure blocked by pilot gate.** |
| S4-AC-04 Manual quotation | **Met (self)** | Buyer quote with currency, landed components, lines | UAT #6 |
| S4-AC-05 Lifecycle / comparison | **Met (self)** | First quote → `QUOTES_RECEIVED`; comparison + FX; landed cost | UAT #6 |
| S4-AC-06 Validation | **Met (self) Core** | Technical then commercial → `COMMERCIAL_VALIDATION`; no award/PO | UAT #7. Management committee / full FR-EVL-01 remains thin / later. |
| S4-AC-07 Isolation | **Met (self)** | FR requester TN RFQ 404; Claire/Julien cannot open sourcing | UAT #10–11; IT cross-company 404 on RFQ |
| S4-AC-08 Quality | **Partly evidenced** | IT journey; EN/FR/AR strings expected; UAT RTL/mobile. **No separate Sprint 4 verify/e2e report file** equivalent to S3 test-results on this exact post-`98d7176` tree in this pass. | UAT #13; axe/e2e of S4 screens not claimed as re-run here |

**Out of scope / deferred (correctly):** FR-SRC-05 AI quote reading; award/PO (Sprint 5); supplier portal accounts.

---

## Automated evidence (already recorded)

| Suite | When / where | Result (as recorded) |
|---|---|---|
| Backend `verify -Pdb-it` | S3 closure tree `e3c4747` | **108/108** ([sprint-3-test-results.md](sprint-3-test-results.md)) |
| Frontend lint / unit / build | S3 closure | Pass / **201/201** / Pass |
| Playwright e2e | S3 closure | 27 pass + 1 axe timeout; re-run of that spec alone pass |
| Sourcing IT | Present on S4 tree | `SourcingIntegrationTests` (RFQ + public link) — **not** re-executed in this documentation pass |
| Fresh full verify on HEAD after S4 | This closure pass | **Not re-run** — do not invent green |

---

## User UAT boundary (honest)

Confirmed **only** the 13 scenarios in [sprint-4-uat.md](sprint-4-uat.md) (submit → approve → assign → accept → RFQ → quote → validations → supplier link → invalid token → Claire/Julien isolation → purchasing/mode regression → RTL/mobile).

Explicitly **not** claimed by UAT or this closure:

- Exhaustive Sprint 3/4 AC manual coverage
- Automated suite green on the interactive UAT session
- Independent code review or pen-test of supplier links
- Pilot-gate passage or production readiness
- API abuse / rate-limit / concurrency beyond UI exercise

---

## Findings summary

### Fixed (this programme slice)

| ID | Notes |
|---|---|
| S3-D-01 … S3-D-08 | See [sprint-3-defects.md](sprint-3-defects.md). Queue filters and affinity closed on closure revision. |
| S4 delivery | RFQ, manual + link quotes, comparison, tech/commercial validation (`f88345a`); template null-check (`98d7176`). |

### Deferred (must stay deferred)

| ID | Target | Notes |
|---|---|---|
| S2CR-08 / S2-C1 | Sprint 9 | Proxy-aware client IP for rate limits |
| S2-C2 | When available | Native-speaker Arabic review |
| S3-C3 / G12 | Confirm with pilot | Grouping, internal transfer, buy again |
| FR-SRC-05 | P2 | AI quote reading — no provider port |
| Award / PO | Sprint 5 | Intentionally out of S4 |
| §15 pilot gate | Business | **Unmet** — rollout only |

### Blocked / open for “true” closure

| Item | Severity for rollout | Notes |
|---|---|---|
| Independent QA + reviewer on same integrated revision | Process | Self-labelled only. Delivery rules: sprint complete only when QA and reviewer check the same revision. |
| Native-speaker Arabic review | Residual product risk | Open since S2 |
| S2CR-08 | Production behind proxy | Sprint 9 |
| Pilot gate §15 | Production / pilot tenant | Unmet by design until a paying pilot is signed |
| Supplier-link pen-test | Production exposure of `/q` | Required by Guide / S4 note before opening externally |
| Fresh automated verify on post-S4 HEAD | See Sprint 5 verify report after separate S5 commit — Sourcing IT re-run green with Ordering IT; full suite recorded there |

### Tree note

Sprint 5 award/PO is a **separate** local commit after this closure record. Do not treat S5 code as part of Sprint 4 acceptance.

---

## Verdict

| Question | Answer |
|---|---|
| S3 local slice closable for demo/dev? | **Yes** (developer self-check): ACs met in code/tests; S3-D-01/D-02 fixed. |
| S4 local slice closable for demo/dev? | **Yes** (developer self-check + user UAT path): Core quote/evaluation path works; AI/award out of scope. |
| Independent sign-off? | **No** — self-labelled QA/review only; security review of supplier links launched separately |
| Pilot / production? | **No** — §15 unmet; S2CR-08 deferred; pen-test outstanding for supplier links |
| Sprint 5? | Delivered in a **later separate commit** on the same machine — see [project-handover.md](project-handover.md) |

Related: [sprint-3-handover.md](sprint-3-handover.md), [sprint-4-handover.md](sprint-4-handover.md), [project-handover.md](project-handover.md), [backlog.md](../product/backlog.md), [roadmap.md](../product/roadmap.md).
