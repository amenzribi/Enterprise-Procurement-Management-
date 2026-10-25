# Specification Review

Source: `docs/specification/Enterprise_Procurement_Intelligence_Specification_FR.md` (v2.1, October 2026, French), preserved unchanged.
Reviewed from three viewpoints: senior engineer, entrepreneur building a sellable product, enterprise buyer.
Proposed changes are listed separately in [recommended-changes.md](recommended-changes.md); the original spec is not edited.

## 1. Summary judgement

The specification is commercially well-aimed: it targets regional multi-company groups that Ariba/Coupa price out, and
correctly positions the product as a demand layer *upstream* of the ERP. The weaknesses are scope and sequencing:
"Phase 1" contains roughly the functional surface of a mature suite (17 modules, OCR/AI, international logistics, ERP
connectors, external portal) and a 12-component infrastructure stack, against a 6-month plan whose first lot assumes
an existing code base that does not exist. A sellable product requires narrowing Phase 1 to one complete, excellent
internal journey for a group with central purchasing.

## 2. Contradictions

| # | Where | Contradiction | Resolution adopted |
|---|---|---|---|
| C1 | §1 "sans licence tierce" vs confirmed SQL Server | SQL Server Standard/Enterprise is commercially licensed (Express is free but capped at 10 GB/DB and limited compute). The "no third-party licence" promise no longer holds. | SQL Server confirmed by the client (ADR-001). Sales material must say "no licence beyond the client's database", and sizing must state the edition. Keep data access portable (JPA + Flyway, minimal T-SQL specifics). |
| C2 | §22 PostgreSQL | Overridden by client decision. | ADR-001. |
| C3 | §27 Lot 0 "Reprise du socle existant" | No existing code base; the workspace is greenfield. Lot 0 at 3 weeks underestimates a from-scratch foundation (auth, multi-company, audit, CI). | Sprint 0 (foundation) + Sprint 1 (identity, roles, company isolation). |
| C4 | §13 "jamais clôturée automatiquement" vs §5 "Clôturée — Responsable : Système" | Unclear whether the system closes once conditions are met or a person closes. | Assumption A7: the system *proposes* closure when all conditions are met; an authorised user confirms. |
| C5 | §8 AI buyer suggestion (described as part of M3, Phase 1) vs §17 table (Phase 2) | Phase mismatch. | Phase 2. M3 MVP uses rule-based assignment. |
| C6 | §4 M12 "Phase 1 (saisie)" vs §17 listing offer reading, transport-document reading, invoice reading and article suggestion as Phase 1 | Phase 1 AI scope is far larger than "saisie". | MVP AI = free-text request drafting only (US-02), with a measured quality gate. Document reading is Phase 2. |
| C7 | §10 commercial validation "vérification du budget" and §6.1 budget line vs §4 M10 Budget = Phase 2 | Budget checks are referenced in Phase 1 flows but the budget module is Phase 2. | MVP stores the budget line as a reference and lets the commercial validator attest; no automatic availability check. |
| C8 | §22 languages Arabic/French/English + RTL; §10 minutes in Arabic and French | Client confirmed English and French only. | EN/FR in MVP; UI built with logical CSS properties and externalised strings so Arabic/RTL can be added without refactoring. |
| C9 | §4 M17 Phase 1 vs §28 external requests only in "Groupe" edition and §24 penetration test before opening external portals | External portal is Phase 1 functionally but requires an isolated DMZ deployment and a pentest. | External portal deferred to Phase 2. |

## 3. Ambiguities requiring assumptions

| # | Topic | Assumption (to be confirmed with a pilot client) |
|---|---|---|
| A1 | Request number format | `{COMPANY_CODE}-PR-{YYYY}-{seq6}`, sequence per company per year, gap-free not guaranteed. |
| A2 | Header status vs per-line statuses (§5) | Header status is the least advanced *active* line status, plus explicit header-only states (draft, in approval, rejected). Cancelled lines are ignored. |
| A3 | "Request information" suspends the SLA "selon la règle" (§7.2) | SLA clock pauses while awaiting requester response; configurable per circuit later. |
| A4 | Visibility of requester (§20) | Default: own requests; department visibility is a per-company setting. |
| A5 | Exchange rates (§9.1, §15) | Rates entered or imported daily per group; the rate used is stored on each quotation/PO (no retroactive revaluation). |
| A6 | Consolidation currency (§16.1) | One per group, configured by the administrator. |
| A7 | Closure (§13) | See C4. |
| A8 | Delegation (§7.1, §7.2) | Two kinds: dated absence delegation (configured in advance) and ad-hoc delegation of one task; delegate must hold an equivalent or higher authority limit. |
| A9 | "Sans développement" circuit configuration (US-04) | Satisfied by a rule table (company × category × amount × purchase type → ordered steps) rather than a BPMN graphical designer. |
| A10 | Money precision | `DECIMAL(19,4)` amounts, ISO-4217 currency codes, banker's rounding only at display/aggregation boundaries. |
| A11 | Time | Stored as UTC (`DATETIMEOFFSET`/`DATETIME2` UTC); displayed in the company or user time zone. |
| A12 | Authentication | Local accounts for development/demo; production via OIDC (Keycloak or client directory, e.g. Entra ID). MFA delegated to the IdP. |

## 4. Commercial risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| Scope versus 6-month promise | 17 modules + AI + connectors cannot be delivered with quality in 25 weeks; late, buggy pilots kill references. | Sell the Essentials journey first; publish the rest as a roadmap. |
| "Local AI" hardware cost | Running OCR + LLMs on-prem needs GPU servers the buyer may not budget; quality varies by language (French/Arabic technical text). | AI is optional, behind a provider interface (local or client-approved endpoint), measured against a labelled evaluation set, and never on the critical path. |
| ERP connector promises | SAP BAPI/OData, Oracle, Dynamics, Sage, Odoo each require client-specific access and possibly indirect-access licence fees (spec §21.5 acknowledges). | MVP = file exchange (CSV/Excel). API connectors sold as services per client. |
| SQL Server licensing | Contradicts the "no licence" pitch (C1). | Be explicit in proposals; support Express for small installs within its limits. |
| Public-sector and oil & gas specifics | Commissions, conflict-of-interest declarations, Arabic minutes, TUNEPS — niche, heavy. | Phase 2 vertical packs. |
| Integrity signals (M13) | False positives create legal/HR exposure. | Phase 2, with internal-control co-design. |
| External customer portal | Security exposure; requires pentest and DMZ. | Phase 2. |

## 5. Unnecessary complexity for an MVP

The spec's §22 stack (Flowable, Spring Integration/Camel, OpenSearch, RabbitMQ, S3 store, Keycloak, Python AI service,
Prometheus/Grafana) is a reasonable *target*, but introducing it up front multiplies operations cost for on-prem
clients. MVP equivalents (ADR-003):

| Spec component | MVP equivalent | Introduce when |
|---|---|---|
| Flowable BPMN | Approval-matrix rule table + explicit state machine in the domain | Clients need parallel/conditional circuits beyond the rule table |
| RabbitMQ | Transactional outbox table + scheduled worker | Throughput or cross-service messaging requires it |
| OpenSearch | SQL Server full-text search | Document volume or OCR search exceeds SQL Server FTS |
| S3 object storage | Storage interface; local/NAS file store | Multi-node deployment |
| Keycloak | Spring Security, OIDC-ready resource server | Production SSO (client IdP) |
| Python AI service | Pluggable provider behind an internal port | AI increment (Sprint 8) |
| Camel/Spring Integration | Scheduled file import/export | First API connector |

## 6. What an enterprise buyer will check first

1. "Where is my purchase?" answered in one click, with owner and delay (US-10).
2. Company isolation and role scoping that survives an audit.
3. Complete, tamper-evident decision history (US-11).
4. Approval from a phone (US-03).
5. Export to their ERP on day one (US-12).
6. French-language quality of the interface.

These drive the MVP definition in [../product/mvp.md](../product/mvp.md).
