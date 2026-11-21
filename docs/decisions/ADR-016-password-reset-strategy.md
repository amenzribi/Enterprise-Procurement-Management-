# ADR-016 — Password recovery: reset links, and the place of administrator-chosen passwords

| | |
|---|---|
| Status | **Direction approved** by the owner on 2026-11-16: reset links only (option A). Sprint 1 restricted temporary passwords to global administrators; their removal is **not implemented yet**. Plan: `ADR-013-to-017-implementation-plans.md` |
| Date | 2026-11-16 |
| Sprint | 1 (hardening) |
| Audit | S-4, S-10 |

## Context

An administrator who chooses a user's password can sign in as that user. For a procurement system that voids separation of duties: the person who administers accounts could approve as anyone. Two recovery paths exist:

- **Reset link** (`POST /admin/accounts/{id}/password-reset`): a single-use link, 24 h, sent to the account's own mailbox. The administrator never sees a secret.
- **Temporary password** (`POST /admin/accounts/{id}/temporary-password`): the administrator types a password; the user must change it at next sign-in.

## Applied in Sprint 1

- Temporary passwords: global administrators only, never on their own account, password policy enforced. Company administrators get 403 and use the link.
- A company administrator cannot reset, deactivate, edit or sign out another administrator.

## Options for what remains

| Option | For | Against |
|---|---|---|
| A. Remove temporary passwords entirely | Nobody can ever choose another person's password; one recovery path to explain and test | A user whose mailbox is unreachable cannot be recovered without e-mail; needs a documented manual procedure |
| B. Keep them for global administrators (current state) | Recovery without e-mail | One role can still impersonate any user for the time of a sign-in; relies on the audit event and on how few global administrators exist |
| C. Keep them, with a second global administrator approving each use | Recovery without e-mail, no single person can impersonate | More code and a workflow for a rare case |

**Proposed decision: option A**, once outgoing e-mail is proven in UAT. Until then option B stands.

Whatever the option:

- Every reset and every temporary password writes an audit event (already the case).
- The account owner is told by e-mail when a password was set or reset by someone else (**not yet implemented**).
- Before production: multi-factor sign-in or single sign-on with the client's identity provider (audit S-10), which also moves recovery to the identity provider.

## Consequences of option A

- Remove the endpoint, the form in user administration and their translations; keep the audit action name for history.
- The runbook gains "user without mailbox access": the global administrator corrects the e-mail address, which is audited, then sends a link.

## Approval needed

Option A, B or C.
