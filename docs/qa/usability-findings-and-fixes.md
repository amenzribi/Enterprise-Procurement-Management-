# Usability sprint — findings and fixes

Developer self-verification. **Not** user-accepted UAT unless Mohamed confirms specific scenarios.

## What changed (why)

1. **Mon travail** (`/overview`) is the internal home — pending approvals, purchasing queue items, my requests, unread notices.
2. **Newest-first** list defaults (`createdAt` / `submittedAt` / waitingSince) with stable `id` secondary order; exceptions documented in `docs/product/list-sort-defaults.md`.
3. **Submission success** dialog with request reference, copy, open.
4. **Dossier** shows labelled request reference + copy; next-action panel from server actions / approval data.
5. **Forgot password** (public, neutral response) + admin **temporary password** (must change at next login).
6. **Users admin** role catalogue explanations and combination guidance; org admin company-vs-site notes.
7. **Logistics inbox** (`/logistics`) for confirmed POs + search by references; dossier logistics UX clarifies prerequisites / quantities / partial shipments.
8. French guides under `docs/guides/`.

## Defects fixed

| Issue | Fix |
|---|---|
| Next actor hard to find after submit | My work + newest-first approvals/purchasing |
| Reference not prominent after submit | Success dialog + copy + dossier label |
| Logistics only buried on dossier | Dedicated inbox + clearer dossier panel |
| No self-service forgot password | `/forgot-password` + `POST /api/v1/auth/password-reset/request` |
| Role meaning unclear for GLOBAL_ADMIN | Role help on Users screen + FR matrix |
| Lists not newest-first by default | Backend + frontend defaults |

## Remaining limitations

- Working calendar is not a separate master (timezone only).
- Sourcing / PO / receipts nav still primarily dossier-centred (logistics now has its own inbox).
- Partial shipments: quantity checks exist; UX documents behaviour rather than a full multi-shipment planner.
- Responsive: shell drawer + shared tokens; full physical-device testing **not** claimed — browser viewport emulation only when performed.
- Manual UAT S5–13 still pending Mohamed.
- Preserve closed UAT dossier `DEMO-TN-PLT-PR-2026-000490`.

## Assistant

Demo adapter returns a labelled empty-data message when no authorised records match. Live AI remains disabled; demo responses stay labelled.

**Follow-up:** Summarise/draft starters previously sent no `requestId` and free-text retrieval failed on “Summarize this dossier…”. Fixed by (1) a request-reference field on the assistant page, (2) appending the business reference to the question, and (3) backend resolution of `…-PR-YYYY-######` numbers. Confirmed via `AssistantIntegrationTests.summarizeWithBusinessReferenceRetrievesAuthorisedDossier`.

## Evidence classes

| Class | Meaning |
|---|---|
| Automated | `mvnw verify -Pdb-it`, FE lint/unit/build, Playwright e2e |
| Developer browser | Viewport checks by agent |
| User-accepted | Only scenarios Mohamed previously confirmed |
