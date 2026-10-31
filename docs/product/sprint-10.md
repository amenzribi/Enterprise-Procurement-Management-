# Sprint 10 — Global administration

**Status:** Locally delivered (developer). Manual UAT pending. Pilot gate unmet.  
**Edition:** Core extension (identity).  
**References:** Approved proposal `sprint-10-global-admin.md`; ADR-007; FR-IAM-*; S9 rate limits.  
**Depends on:** Delivered identity through Sprint 9.  
**Pilot gate §15:** Unmet — this sprint is not production approval.

## Permission matrix

| Actor | Users | Memberships / roles | Password setup/reset | Sessions | Grant GLOBAL_ADMIN | Procurement records |
|---|---|---|---|---|---|---|
| `GLOBAL_ADMIN` | Create/edit/activate across installation | Any supported role; any company in group | Yes | Bump credentials version (revokes sessions) | Yes | **No** auto-bypass (ADR-007) |
| `ADMIN` (ORG) | Users whose home company is in the actor’s organisation group | Company-scoped roles only; cannot grant `GLOBAL_ADMIN` or group-wide `ADMIN`/`AUDITOR`/`GROUP_MANAGEMENT` except as already allowed by product | Yes for non–global-admin targets | Same | **No** | Unchanged (org structure only via ORG_ADMIN) |
| Others | — | — | Change own password only | Own logout | — | — |

## Lifecycle policy

1. **Create** → account active or inactive; issue single-use setup token (e-mail link); optional must-change if a temporary password is used (not preferred).  
2. **Setup / reset** → cryptographically strong token, hashed at rest, TTL, single-use; reissue expires prior tokens; redeem sets password, clears must-change, bumps `credentials_version`.  
3. **Deactivate** → `is_active=0` + bump `credentials_version`; no silent reassignment of open approvals/buyer work; API returns open-assignment counts as warning payload. Prefer deactivate over delete.  
4. **Last GLOBAL_ADMIN** → cannot deactivate or remove the last active grant (row lock / transactional count).  
5. **Self** → cannot deactivate self; cannot remove own last GLOBAL_ADMIN grant.

## Acceptance criteria

| ID | Criterion |
|---|---|
| S10-AC-01 | Role `GLOBAL_ADMIN` (group-wide). Company `ADMIN` cannot grant it. |
| S10-AC-02 | Create/edit user; activate/deactivate; company home + role grants/removals with matrix above. |
| S10-AC-03 | Demo: `global.admin@demo.local` via `DEMO_USER_PASSWORD`. Production bootstrap via one-shot `GLOBAL_ADMIN_BOOTSTRAP_PASSWORD` only when no GLOBAL_ADMIN exists; never default password; does not recreate on every restart. |
| S10-AC-04 | Own password change; admin-initiated setup/reset via expiring single-use token; never display stored passwords; tokens not in logs/audit. |
| S10-AC-05 | `credentials_version` bumped on password change/reset/deactivate; sessions with stale version invalidated promptly. |
| S10-AC-06 | Cannot deactivate or demote the last active GLOBAL_ADMIN (including concurrent attempts). |
| S10-AC-07 | Audit USER_ACCOUNT / USER_ROLE / USER_SESSION admin actions without secrets. |
| S10-AC-08 | Admin UI EN/FR/AR + RTL/mobile; GLOBAL_ADMIN does not gain unscoped request visibility. |
| S10-AC-09 | Proxy-aware rate limit on public reset redeem; CSRF on authenticated admin/password APIs. |
| S10-AC-10 | `must_change_password` blocks ordinary APIs until password change (allow me, csrf, logout, password change). |

## Out of scope

- Impersonation, password viewing, MFA, Sprint 11 messaging, Sprint 12 AI.  
- Auto-reassignment of open tasks on deactivate.
