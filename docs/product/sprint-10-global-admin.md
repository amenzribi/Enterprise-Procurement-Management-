# Sprint 10 (proposed) — Global administration

**Status:** Proposal only — not delivered. Requested 2026-10-31.  
**Why not Sprint 9:** S9 delivers deferred link-security (S2CR-08 / supplier-link). Full GLOBAL_ADMIN is a coherent identity vertical (schema, reset tokens, session epoch, UI, last-admin protection) that must not be half-shipped.

## Objective

Installation-scoped `GLOBAL_ADMIN` manages users and organisational grants without becoming an unscoped procurement data viewer.

## Acceptance criteria (draft)

| ID | Criterion |
|---|---|
| S10-AC-01 | Role `GLOBAL_ADMIN`; cannot be granted by company ORG_ADMIN. |
| S10-AC-02 | Create user; activate/deactivate; company membership + role grants. |
| S10-AC-03 | Secure bootstrap for first GLOBAL_ADMIN (env-based, no default production password). |
| S10-AC-04 | Password change; admin-initiated reset (expiring single-use token or must-change temp); never display stored passwords. |
| S10-AC-05 | Session revocation / credentials version after reset or deactivation. |
| S10-AC-06 | Cannot deactivate or demote the last active GLOBAL_ADMIN. |
| S10-AC-07 | Audit role/membership/status/reset actions without secrets in payloads. |
| S10-AC-08 | Admin UI EN/FR/AR; GLOBAL_ADMIN does not auto-gain all company request visibility (ADR-007 preserved). |

## Bootstrap (real install)

1. Set a one-time env secret (e.g. `GLOBAL_ADMIN_BOOTSTRAP_PASSWORD`) only for first start.  
2. Application creates inactive-or-must-change admin if none exists.  
3. Clear the env var after first successful sign-in and password change.  
4. Demo: `global.admin@demo.local` via `DEMO_USER_PASSWORD` / same temp-file mechanism — **only with demo profile**.
