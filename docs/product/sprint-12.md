# Sprint 12 — Internal procurement AI assistant

**Status:** Locally implemented (developer) with **demo/disabled** providers only. Production hosting **pending**. Manual UAT pending. Not independently reviewed.

## Objective

Authorized buyers and purchasing managers can ask scoped questions and receive grounded answers or reviewable supplier-message drafts via a provider-neutral assistant. Live AI requires a separate hosting decision (ADR-012).

## Acceptance criteria

| ID | Criterion |
|---|---|
| S12-AC-01 | Mode `disabled` (default) / `demo` / `live-reserved`; no silent external calls; secrets out of repo. |
| S12-AC-02 | Permission `ASSISTANT_USE` for BUYER / PURCHASING_MANAGER only; SUPPLIER_CONTACT and bare GLOBAL_ADMIN denied. |
| S12-AC-03 | Find authorized requests/POs with allowlisted filters; guessed IDs → 404; no existence leak. |
| S12-AC-04 | Explain status/owner/dates from server-verified records with source citations. |
| S12-AC-05 | Summarize dossier + authorized messaging; INTERNAL never enters EXTERNAL drafts. |
| S12-AC-06 | Drafts labelled; never auto-sent; optional handoff to messaging composer only. |
| S12-AC-07 | No write tools; cannot approve/award/issue/receive/pay/close/change users. |
| S12-AC-08 | Retrieved content treated as untrusted data; injection attempts cannot widen tools/audience. |
| S12-AC-09 | Rate limit, timeout, bounded context; clear errors; demo responses labelled. |
| S12-AC-10 | UI EN/FR/AR + RTL; mode visible; starter questions. |
| S12-AC-11 | Audit without full sensitive prompt/response by default. |
| S12-AC-12 | Document OCR, autonomous actions, forecasting, messaging attachments, live provider — **out of scope**. |

## Permissions

| Actor | Assistant | Data |
|---|---|---|
| BUYER (assigned / company) | Yes | Own buying-company scope via existing request/PO/messaging rules |
| PURCHASING_MANAGER | Yes | Buying-company scope |
| SUPPLIER_CONTACT | No | — |
| GLOBAL_ADMIN alone | No | — |
| Requester / approver / receiver | No (unless also buyer/PM) | — |

## Data sources (read-only tools)

- Request search/get (request module facade → ScopedRequests).
- PO by request (ordering facade).
- Conversation messages for threads the user can open (messaging facade); EXTERNAL-only for supplier drafts.

## Excluded

OCR/document ingestion; live model; autonomous send; messaging file attachments; forecasting.
