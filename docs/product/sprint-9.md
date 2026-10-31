# Sprint 9 — Pilot readiness (security hardening)

**Status:** Locally delivered (developer). Manual UAT pending. Pilot gate unmet.  
**Edition:** Core.  
**References:** Roadmap S9; S2CR-08; S4 supplier-link findings S4SL-03/07/09/11/13; FR-IAM-04 (MFA — gate); NFR-01/10 (measure/runbook — gate).  
**Depends on:** Delivered purchasing lifecycle through Sprint 8.  
**Pilot gate §15:** Unmet — no production exposure, no real supplier messaging, no go-live.

## Roadmap reconciliation (snapshot)

| Sprint | Scope | Status |
|---|---|---|
| S0–S8 | Foundation → visibility/ERP files | Locally delivered (developer). Manual UAT **pending** for S5–S8. |
| **S9** | Proxy-aware rate limits; supplier-link security hardening; security regression tests; document unmet pilot gates | **This sprint** |
| Gate §15 | Pilot client signed | **Unmet** — blocks production exposure |
| After (add-ons) | Trading / Enterprise editions | By demand |

**Core/MVP journey (request → close + dashboards/ERP files)** is locally implemented. That is **not** full product vision (AI, live ERP, supplier portal, budget, MFA, go-live).

### S9 in scope (code)

| ID | Criterion |
|---|---|
| S9-AC-01 | **S2CR-08.** Client address for rate limits uses `X-Forwarded-For` / `Forwarded` **only** when the immediate peer is in `app.security.trusted-proxies`; otherwise `getRemoteAddr()`. Direct access unchanged. |
| S9-AC-02 | **One-tap + quote-link rate limits** share the proxy-aware resolver; per-address and per-token buckets on public endpoints. Spoofed forwarding headers from untrusted peers are ignored. |
| S9-AC-03 | **Supplier link revoke-on-reissue (S4SL-09).** Issuing a new quote link for an RFQ supplier expires/revokes prior active tokens for that supplier invitation. |
| S9-AC-04 | **Public DTO toString redaction (S4SL-07).** Token-bearing public request bodies never print the raw token. |
| S9-AC-05 | **Security IT (S4SL-11).** Invalid/expired/revoked/out-of-scope tokens → 404; closed RFQ public submit → 404; rate-limit returns 429; re-issue invalidates previous token. |
| S9-AC-06 | **Docs + regression.** Automated verification report; manual plan with Not executed by user; combined S5–9 scenarios. |

### S9 explicitly unmet / deferred (pilot gates — not falsely closed)

| Item | Disposition |
|---|---|
| Full independent pen-test of external links | Requires §15 + dedicated security engagement |
| Performance at agreed load (NFR-01) | Measure when load profile agreed |
| Restore test / DR runbook execution (NFR-10) | Ops runbook; not automated here |
| Two-step / MFA (FR-IAM-04) | Via IdP — blocked until IdP decision |
| Training + go-live | Blocked by §15 |
| S4SL-01 single-use after first submit | **Accepted Core** — updates allowed while RFQ OPEN |
| S4SL-14 quote optimistic lock | Remains operational control; award snapshots prices |
| S4SL-13 Idempotency-Key on public submit | Optional follow-up if pen-test requires |

## Out of scope this sprint

- **GLOBAL_ADMIN user lifecycle** — **proposed Sprint 10** (see roadmap). Fits identity/security but is a full vertical slice (migration, reset tokens, session epoch, UI, last-admin protection); not coherent to half-deliver inside S9 hardening.
- Buyer–supplier messaging — **proposed Sprint 11**.
- Internal AI assistant — **proposed Sprint 12** (after permissions + messaging foundations; hosting undecided).
- Live ERP, AI draft, algorithmic three-way match (remain deferred).

## Conflict notes

- Roadmap S9 lists MFA, restore, training, go-live: those remain **gates**, not silently claimed done.
- Requirements register FR-IAM-04 stays Planned/blocked on IdP.

## Demo / verification path

Direct: hammer `/api/v1/approval-links/summary` and `/api/v1/quote-links/summary` → 429 after limit.  
With trusted proxy config: only when peer matches `trusted-proxies`, `X-Forwarded-For` distinguishes clients.  
Re-issue supplier link → old token 404; new token works while RFQ OPEN.
