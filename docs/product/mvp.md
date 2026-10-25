# MVP Definition — "Essentials for Groups"

## Target customer

A multi-company group (2–20 companies, 1–4 countries) with a central purchasing function, an ERP or none, and today
running requests through e-mail/WhatsApp/paper. This is the spec's "Groupe avec centrale d'achat" model (§2); a
single company is the degenerate case of a one-company group (§3.1).

## The promise the MVP must keep

> One request, one number, one owner at every step, and an immediate answer to "where is my purchase?"

## In scope

| Area | Scope | Spec | User stories |
|---|---|---|---|
| Foundation | Multi-company organisation, users, roles, scopes, audit, EN/FR | M15, §3, §20, §24 | — |
| Request | Multi-line request, attachments, unique number, validations before submission, duplicate warning | M1 (§6.1, §6.3 partial), M14 | US-01 |
| Approval | Rule-table circuits per company/category/amount; approve, reject (reason), return for revision, request information, delegate; SLA reminders; phone-usable | M2 | US-03, US-04 |
| Central purchasing | Inbox with filters, completeness check, return to requester, assignment, accept/decline assignment, purchase mode (direct / centralised / on behalf) | M3, §3.7 | US-05, US-15 |
| Sourcing | Supplier registry (basic), RFQ, manual quotation entry, exchange rates, landed-cost estimate, comparison table | M4, M9 (registry) | US-06 |
| Evaluation | Technical, commercial and management validation with reasons and thresholds | M5 | US-07 |
| Order | PO recorded in-platform (or external PO number), status tracking, contract attachment | M6 (tracking) | — |
| Logistics (lite) | Shipment milestones local/international, planned vs actual dates, late alerts, transport documents attached | M7 (§12.1, §12.2 without AI) | US-08 |
| Receipt & closure | Full/partial/non-conforming receipt with photos, service acceptance, closure conditions | M8 | US-09 |
| Visibility | Pipeline dashboard and management "where is it / who / how much / late / when" view | M11 | US-10, US-11 |
| ERP | CSV/Excel export of approved requests and POs; import suppliers, articles, statuses | M16 (files) | US-12 |
| AI (bounded) | Free-text → structured draft request, always reviewed by the requester | M12 (§6.2 first bullet) | US-02 |

## Out of MVP (Phase 2)

Budget control (M10), supplier portal, integrity signals (M13), NL assistant, document-reading AI (quotes,
transport, invoices), external customer requests/portal and sales quotes (M17), internal recharging invoices (US-16),
BPMN designer, API ERP connectors, Arabic/RTL, public-sector and oil & gas packs.

## MVP acceptance criteria (release level)

1. A requester in a subsidiary creates a multi-line request with attachments, submits it, and receives a number.
2. The request follows the subsidiary's configured circuit; each approver action (approve, reject, revise, request
   information, delegate) is recorded with user, timestamp, comment, previous and new status.
3. Approved requests appear in the central purchasing inbox; the purchasing manager assigns a buyer and purchase mode.
4. The buyer records at least three quotations in different currencies; the comparison shows converted totals with
   the stored rate, lead time, payment terms, compliance.
5. Technical and commercial validation, plus management validation above the configured threshold, are recorded.
6. A PO is recorded and tracked through shipment milestones with planned and actual dates; lateness is flagged.
7. A partial receipt and a non-conformity are recorded; the request closes only when all closure conditions hold.
8. A manager answers "where / who / how much / validated / late / when" for any request in one click.
9. Users never see data of companies outside their scope (automated cross-company tests for every endpoint).
10. Every screen is complete in English and French; the user's language choice persists.
11. Approved requests and POs export to CSV/Excel; suppliers and articles import from CSV/Excel.
12. Free-text drafting meets the quality gate defined in the AI sprint on a labelled EN/FR evaluation set, and never
    submits on its own.
13. Common screens respond in under 2 seconds with the agreed demo data volume (§25).
