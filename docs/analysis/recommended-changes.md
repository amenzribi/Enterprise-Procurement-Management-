# Recommended Changes to the Specification

These are recommendations only. The original specification
(`docs/specification/Enterprise_Procurement_Intelligence_Specification_FR.md`) is preserved unchanged.
Status: **Accepted** = client-confirmed; **Proposed** = awaiting confirmation (implemented as an assumption).

| ID | Spec section | Change | Rationale | Status |
|---|---|---|---|---|
| RC-01 | §22, §1 | Database: Microsoft SQL Server instead of PostgreSQL. | Client decision. See ADR-001. | Accepted |
| RC-02 | §1 "Sans licence tierce" | Reword to "no licence beyond the client's database server". | SQL Server is licensed (RC-01). | Proposed |
| RC-03 | §22 Interface, §22 Languages | MVP languages English and French, user-selectable; Arabic/RTL kept as a later increment, with the UI built RTL-ready. | Client decision on EN/FR. | Accepted (EN/FR) / Proposed (Arabic later) |
| RC-04 | §27 Lot 0 | Replace "Reprise du socle existant" with a greenfield foundation: Sprint 0 (foundation) and Sprint 1 (identity, roles, company isolation). | No existing code base. | Proposed |
| RC-05 | §4, §27 | Redefine Phase 1 as the "Essentials" internal journey for a group with central purchasing (see `docs/product/mvp.md`). Move M10, M13, M17 (offers/invoices *and* external portal), supplier portal, NL assistant, document-reading AI to Phase 2. | Deliverability and quality of the first sale. | Proposed |
| RC-06 | §7.1 | Approval circuits configured through a rule table in MVP; the BPMN graphical designer (Flowable) becomes Phase 2. | Meets US-04 "without development" at a fraction of the cost. | Proposed |
| RC-07 | §22 | Infrastructure introduced on demand (ADR-003): no RabbitMQ, OpenSearch, S3, Keycloak, Camel in MVP. | Lower on-prem operating cost; fewer failure points. | Proposed |
| RC-08 | §17 | MVP AI limited to free-text request drafting (US-02) with an evaluation set and a published quality threshold; AI provider pluggable (local model or client-approved endpoint). | Hardware cost and quality risk of "all-local" AI. | Proposed |
| RC-09 | §5, §13 | Clarify closure: system proposes closure when conditions are met, an authorised user confirms. | Resolves contradiction C4. | Proposed |
| RC-10 | §24 "journaux chaînés et infalsifiables" | Say "tamper-evident" (hash-chained, verifiable) rather than "tamper-proof"; offer periodic external anchoring of the chain head as an option. | A database administrator can always rewrite rows; the chain makes it detectable, not impossible. | Proposed |
| RC-11 | §27 timeline | Replace "6 months for lots 0–5" with the sprint roadmap in `docs/product/roadmap.md` (Sprint 0 + 9 sprints ≈ 20 weeks for the Essentials MVP). | Realistic commitment. | Proposed |
| RC-12 | §3.4–§3.5, §28 | External (customer) requests: Phase 2, starting with sales-entered requests before any public portal. | Security exposure, pentest prerequisite. | Proposed |
| RC-13 | §10, §18 | Public-sector and oil & gas specifics (commissions, conflict-of-interest declarations, Arabic minutes, TUNEPS) as optional vertical packs. | Niche; heavy. | Proposed |
| RC-14 | §6.1 | Define the request numbering scheme (assumption A1). | Unspecified. | Proposed |
| RC-15 | §4 M17, US-13, US-14, US-16, §2 "Négoce" model | External customer requests (including "demande et suivi"), sales quotes, customer orders and internal recharging move to Phase 2. | Separate sales lifecycle; portal security prerequisites. | Proposed — needs client sign-off |
| RC-16 | §11 | Payments, letters of credit and bank guarantees with expiry alerts move to Phase 2; MVP tracks PO status and stores contract documents. | Finance integration depth. | Proposed — needs client sign-off |
| RC-17 | §4 M12, §17 | Reading of quotations, transport documents and invoices by AI moves to Phase 2. | See RC-08. | Proposed — needs client sign-off |
| RC-18 | §14 | Supplier administrative dossier with validity dates and expiry alerts, risk score and performance move to Phase 2; MVP keeps the registry. | Scope. | Proposed — needs client sign-off |
| RC-19 | §19 | Full-text/OCR search, document preview and connection to an external DMS move to Phase 2; MVP keeps versioned attachments. | Scope. | Proposed — needs client sign-off |
| RC-20 | §7.1 | Parallel stages are in the MVP (ADR-005); conditional branching beyond stage conditions and the BPMN designer are Phase 2. | Partial coverage of §7.1. | Proposed — needs client sign-off |
| RC-21 | §22 "Spring Boot 3" | Spring Boot 4.1 (ADR-008). | 3.x open-source support has ended. | Proposed |
