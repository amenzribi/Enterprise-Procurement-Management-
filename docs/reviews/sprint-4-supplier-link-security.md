# Sprint 4 Supplier Quote Link — Security Review

**Scope:** Unauthenticated supplier quotation links — `quote-links` API, token hashing/storage, public `/q` route, submit path, attachment exposure.  
**Method:** Static reviews by [supplier-link security](4ec50fbf-f69b-4f8c-82b9-5025624b7b9a) and [quote-link security](a6a42bdf-aad5-4bf4-84af-698ad88a978a); no runtime pen-test (pilot gate unmet).  
**Verdict:** **Acceptable for local/test only.** Production exposure remains blocked by §15 / S9. Hardening landed in `f732bef` where noted below.

## Verified controls

| Control | Status |
|---|---|
| ≥256-bit tokens, SHA-256 at rest, bound to one `rfq_supplier` | Met |
| No cross-supplier / comparison / estimate / requester leakage on public API | Met |
| Attachments authenticated-only | Met |
| Token in URL fragment (`/q#…`); CSRF on POST | Met |
| Invalid/expired → 404 / neutral invalid page | Met |
| Lifecycle intentionally distinct from approval tokens (14d TTL) | Met |

## Findings and disposition

| ID | Severity | Disposition |
|---|---|---|
| **S4SL-01** `used_at` written but not blocking resubmit | Major (review) | **Accepted for Core** — S4-AC-03 explicitly allows create/**update** of that supplier’s quote via the link while RFQ is OPEN. `used_at` records first submit. Production may require fresh link per revision (pen-test / S9). |
| **S4SL-02** plaintext token in `idempotency_record` | Major | **Fixed** — link issuance no longer uses Idempotency storage (response would contain the raw token). |
| **S4SL-03** no rate limit on public endpoints | Major (one review) / Minor (other) | **Deferred** — blocks production exposure with §15; implement with S2CR-08 in Sprint 9. Local OK. |
| **S4SL-04** token left in address bar | Minor | **Fixed** — `history.replaceState` clears fragment after read. |
| **S4SL-05** missing Cache-Control / Referrer-Policy | Minor | **Fixed** — quote-link responses set `no-store` / `no-referrer`. |
| **S4SL-06** no token length bounds | Minor | **Fixed** — reject outside 20–100 chars with 404. |
| **S4SL-07** Public* DTOs lack redacted `toString` | Minor | **Deferred** — low risk with current logging; harden in S9 if needed. |
| **S4SL-08** submit error oracle (`rfq-closed`) | Minor | **Fixed** — closed RFQ on public submit → 404; UI maps 404 to invalid page. |
| **S4SL-09** re-issue does not revoke prior tokens | Minor | **Deferred** — document for S9; new tokens coexist until TTL. |
| **S4SL-10** summary after RFQ close | Minor | **Deferred** — lines only; submit blocked. |
| **S4SL-11** thin security IT | Minor | **Open** — add post-submit / closed-RFQ cases in S9. |
| **S4SL-12** spec `/q/{token}` vs `/q#token` | Info | **Doc drift** — fragment is preferred. |
| **S4SL-13** no Idempotency-Key on public submit | Medium | **Deferred** — S9; double-click last-write-wins on same supplier quote. |
| **S4SL-14** buyer vs supplier-link concurrent overwrite; no quote optimistic lock | Medium | **Deferred** — award (S5) snapshots commercial values; freeze/close RFQ before award remains operational control until quote versioning exists. |

## Pilot gate

No production supplier messaging. Local `dev,demo` and SQL Server IT only.
