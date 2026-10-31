# Sprint 8 — Visibility and ERP files

**Status:** Locally implemented (developer). Manual UAT pending.  
**Edition:** Core (visibility lite).  
**References:** Solution Guide v3.0 UC-26, UC-27, UC-16 (ERP **files**); FR-DSH-01, FR-DSH-02, FR-ERP-01; G13 audit pack.  
**Depends on:** Request lifecycle through Sprint 7 (data already captured for aggregation).  
**Pilot gate:** Unmet — local development only.

## Business objective

Give authorised managers and purchasing a **pipeline dashboard** and a **where / who / how much / late** list scoped by company visibility (group-wide for GROUP_MANAGEMENT). Allow download of a **request audit pack** and **CSV exports** of approved requests and purchase orders for ERP hand-off. No live ERP connectors and no AI draft (no approved hosting/provider — same class as deferred S4/S6/S7 AI).

## Conflict note

Requirements register tagged FR-DSH-* and FR-ERP-01 as S7; Sprint 7 delivery explicitly deferred dashboards/ERP to S8. **Roadmap S8 and that S7 deferral win.** Register rows are updated to S8.

## In scope

| ID | Criterion |
|---|---|
| S8-AC-01 | **Pipeline.** Counts of visible non-draft requests by `userStatus` (and optionally stage). Empty state when none. Company isolation: out-of-scope companies never appear. |
| S8-AC-02 | **Management list.** Paginated rows: number, title, userStatus, owner display, owner since, required date, amount+currency, late flag (requiredDate &lt; today UTC and not CLOSED/CANCELLED/REJECTED). Amounts never summed across currencies. |
| S8-AC-03 | **CEO / group view.** Users with `REQUEST_VIEW_GROUP` see an extra panel of counts by requesting company (still only visible rows). Others do not see other companies’ data. |
| S8-AC-04 | **Audit pack.** From a visible request dossier, download a PDF (text report) of header facts + audit events for that request. Cross-company → 404. |
| S8-AC-05 | **ERP CSV export.** PURCHASING_MANAGE / ADMIN export: (a) requests from APPROVED onwards, (b) purchase orders ISSUED/CONFIRMED, for buying companies in their grant. UTF-8 CSV; confidential lines limited to fields needed for hand-off. No live connector. |
| S8-AC-06 | **Permissions & i18n.** `DASHBOARD_VIEW`, `ERP_EXPORT`; EN/FR/AR + RTL; nav `/dashboards` replaces planned stub. |

## Out of scope

- AI request draft from text/photo (FR-AI-01) — **blocked** until AI hosting/provider decision.
- Live ERP APIs (FR-ERP-03); inventory valuation; recharge billing.
- Sprint 9 security/pentest work.
- Algorithmic three-way match / AI invoice reading (remain deferred).

## Demo path

Mehdi or Hedi opens **Dashboards** → pipeline + management list → Hedi sees company breakdown. On a closed request, Sami/Ines downloads **Audit pack**. Mehdi runs **ERP export** CSVs. Claire cannot see Tunisia-only rows or CPA exports outside her visibility.
