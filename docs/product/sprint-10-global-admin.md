# Sprint 10 — Global administration

**Status:** Locally delivered (developer). Manual UAT pending. Pilot gate unmet.  
**See also:** [sprint-10.md](sprint-10.md) for acceptance criteria, permission matrix and lifecycle policy.

## Delivered

- Role `GLOBAL_ADMIN` (group-wide); company `ADMIN` cannot grant it.
- Admin API `/api/v1/admin/accounts` + UI `/admin/users`.
- Password change; setup/reset via expiring single-use e-mail links (`/p#token`); credentials version session revocation.
- Demo account `global.admin@demo.local` via `DEMO_USER_PASSWORD`.
- Production one-shot bootstrap via `GLOBAL_ADMIN_BOOTSTRAP_PASSWORD` + `GLOBAL_ADMIN_BOOTSTRAP_EMAIL` when no GLOBAL_ADMIN exists.
- EN/FR/AR UI; ADR-007 preserved (GLOBAL_ADMIN is not an unscoped procurement viewer).

## Bootstrap (real install)

1. Ensure at least one company exists.  
2. Set `GLOBAL_ADMIN_BOOTSTRAP_PASSWORD` (and optional `GLOBAL_ADMIN_BOOTSTRAP_EMAIL`) for a single start.  
3. Application creates/activates GLOBAL_ADMIN with must-change password and records `identity_bootstrap`.  
4. Clear the env var; sign in and change password.  
5. Never enable a default production password. Demo profile uses `DemoUserSeeder` instead.
