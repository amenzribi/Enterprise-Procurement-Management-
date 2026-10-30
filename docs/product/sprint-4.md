# Sprint 4 — Quotes and evaluation

**Status:** Locally implemented 2026-10-30 (developer). Self-labelled S3+S4 closure in `docs/qa/sprint-3-4-closure.md`. Pilot gate unmet. Not independently reviewed.  
**Edition:** Core.  
**References:** Solution Guide v3.0 UC-10…UC-14; French spec §9–§10; register FR-SRC-02…04; roadmap S4.  
**Depends on:** Sprint 3 purchasing (accepted buyer) and supplier register.  
**Pilot gate:** Unmet — restricts rollout / production exposure of supplier quote links; local development authorised.

## Business objective

An accepted buyer can invite eligible suppliers, collect quotations (manual entry and secure one-time supplier link), compare offers like-for-like with recorded exchange rates and a landed-cost estimate, then record technical and commercial validation. No automatic award or purchase order (Sprint 5).

## In scope

| ID | Criterion |
|---|---|
| S4-AC-01 | **Start sourcing.** Only an assigned **accepted** buyer (or PURCHASING_MANAGER of the buying company) may open an RFQ on a request in `ASSIGNED_TO_BUYER`. Status → `SOURCING`. Audit + notice. |
| S4-AC-02 | **RFQ kinds.** `QUICK` (local / urgent) and `STANDARD` (international-capable). Buyer selects one or more **visible, active** suppliers of the group. Lines copy from the purchase request. |
| S4-AC-03 | **Secure supplier link.** Each invited supplier gets a hashed, single-use-capable, time-bounded token URL (`/q/{token}`) with no account. Link shows RFQ summary and line quantities only. Submit creates/updates that supplier’s quotation. Token misuse → neutral invalid page. Rate-limit note: production pen-test before exposure (pilot gate). |
| S4-AC-04 | **Manual quotation.** Buyer may enter a quotation for an invited supplier: currency, validity date, lead time days, payment terms, Incoterm, optional freight/duty/other landed components, per-line unit price and quantity offered. Missing line prices are explicit in comparison. |
| S4-AC-05 | **Lifecycle.** First received quote → `QUOTES_RECEIVED`. Buyer may close RFQ for new quotes. Comparison uses buying-company currency via `ExchangeRates` (missing rate → 422). Landed cost = merchandise + freight + duty + other (converted). |
| S4-AC-06 | **Validation.** Buyer (or purchasing manager) records **technical** then **commercial** validation with a reason/comment; statuses `TECHNICAL_VALIDATION` → `COMMERCIAL_VALIDATION`. No award, no PO. Management validation committee (FR-EVL-01 full) stays thin: commercial step satisfies UC-14 for Core MVP; committee minutes remain P2. |
| S4-AC-07 | **Isolation.** Out-of-scope request → 404. Claire / Julien cannot open RFQs or quotes. Anonymous token cannot see other suppliers’ prices. |
| S4-AC-08 | **Quality.** EN/FR/AR + RTL; loading/empty/error; 360 px; axe on RFQ and comparison; e2e journey; SQL Server IT; audit on create RFQ, quote submit, validations. |

## Out of scope

- AI quote reading (FR-SRC-05 Deferred P2) — no provider port claimed; human entry only.
- Award / PO / supplier confirmation (S5).
- Supplier portal accounts, Teams/WhatsApp.
- Grouping multi-request RFQs (FR-PUR-05 Deferred).
- Live ERP, financial tolerances engines.

## Technical design

| Topic | Decision |
|---|---|
| Module | New `sourcing` (RFQ, quotes, comparison, public link). Uses `RequestWorkflow`, `SupplierService`/`supplier` tables, `ExchangeRates`, `AuditLog`, `Notifications`. |
| Schema | `rfq`, `rfq_supplier`, `rfq_line`, `quotation`, `quotation_line`, `quote_link_token` (V9). |
| Tokens | ≥256-bit random, stored as SHA-256 hash, bound to `rfq_supplier`, default 14-day expiry, audit on use. |
| AI | Not implemented. |

## Demo path

Ines (accepted buyer) opens RFQ QUICK for DEMO-SKF → copies link or enters quote manually → comparison shows converted totals → technical OK → commercial OK. Sami sees status QUOTING. Claire cannot open sourcing APIs.
