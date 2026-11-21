# ADR-017 — Who may record exchange rates

| | |
|---|---|
| Status | **Direction approved** by the owner on 2026-11-16: global administrators only (option A). **Not implemented yet**; Sprint 1 added the audit event only. Plan: `ADR-013-to-017-implementation-plans.md` |
| Date | 2026-11-16 |
| Sprint | 1 (hardening) |
| Audit | S-6, S-7 |

## Context

A request's amount is converted to its company's currency to choose the approval rule. Exchange rates belong to the group, not to a company. Today any ADMIN, including the administrator of a single subsidiary, can add a rate (`POST /api/v1/admin/exchange-rates`). A rate is immutable; a correction is a new row. Since Sprint 1 each new rate writes an audit event (`EXCHANGE_RATE_ADDED`).

The risk: a subsidiary's administrator records a rate that makes a large foreign-currency request fall under a lower approval threshold, in their own or another company, then records the true rate again. The audit event now shows it, but nothing prevents it.

## Options

| Option | For | Against |
|---|---|---|
| A. Global administrators only | Group data changed only by a group role; matches ADR-007 | A subsidiary must ask the group for a missing rate; a request in a currency without a rate cannot be submitted until then |
| B. A dedicated permission (for example finance or treasury), separate from administration | Right owner in most groups; separates user administration from financial parameters | A new role to create, assign and explain |
| C. Keep any ADMIN, add a second person's validation before a rate is used | No bottleneck at group level | A workflow for a daily operation |
| D. Automatic daily rates from a reference source, manual entry as an exception for global administrators | Removes the manual step and most of the risk | An outgoing integration to choose, secure and monitor (ADR-003) |

## Proposed decision

**Option A now**, because it is a small change and closes the gap; **option B or D with the client** once the organisation of UAT shows who really maintains rates.

Under option A:

- `POST /admin/exchange-rates` answers 403 to a company administrator. Reading rates stays as it is.
- The rate form is hidden from company administrators; the page stays readable.
- A bound on the change against the previous rate of the same pair (for example a warning above 10 %) is worth adding with either option.

## Consequences

- The tests and demo flows that add a rate as `admin@demo.local` move to the global administrator.
- The missing-rate message shown to a requester should say whom to ask.

## Approval needed

Option A, B, C or D.
