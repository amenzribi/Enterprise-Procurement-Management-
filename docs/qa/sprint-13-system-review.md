# Sprint 13 — System review findings

| | |
|---|---|
| Reviewer label | **Developer self-review** (plus parallel explore agents). **Not** independent QA/security sign-off |
| Baseline | `b967b3e` / `fabfe96` |
| Date | 2026-10-31 |

## Method

- Requirements reconciliation against Solution Guide Core + ADRs + register.
- Code inspection of identity, scope, ordering, receipt, messaging, assistant, nav/overview.
- Parallel agent inventory and defect hunt (results folded below).
- Browser/process health on 4300→8081 (demo profile).

## Findings

### S13-F-06 — Invite could attach SUPPLIER_CONTACT to privileged accounts (Critical)

| | |
|---|---|
| Severity | **Critical** — **fixed** |
| Evidence | `MessagingService.invite` granted `SUPPLIER_CONTACT` to any existing e-mail; portal filter then blocks all business APIs |
| Correction | Refuse invite when the account already has any non-`SUPPLIER_CONTACT` role (`account-not-eligible`) |
| Verification | `MessagingIntegrationTests.inviteRejectsInternalAccountsAndUnknownSuppliers` |

### S13-F-07 — Contact APIs lacked supplier visibility scope (High)

| | |
|---|---|
| Severity | **High** — **fixed** |
| Evidence | `assertBuyerCanManageSupplier` only checked supplier existence |
| Correction | `findVisibleSupplier(groupId, companyId)` aligned with supplier register rules |
| Verification | Same IT (unknown id → 404); existing happy-path contact tests |

### S13-F-08 — Issued PO milestones mutable via draft update race (High)

| | |
|---|---|
| Severity | **High** — **fixed** |
| Evidence | `replaceMilestones` deleted by PO id without draft guard; concurrent with `issue` |
| Correction | Draft-only DELETE join; `updateDraft` aborts when header update affects 0 rows |
| Verification | Ordering IT suite + logic review |

### S13-F-01 — Delivered Core stages still labelled “planned” in navigation/overview

| | |
|---|---|
| Severity | **High** (acceptance / honesty blocker) |
| Evidence | `FrontEnd/src/app/layout/navigation.ts` had `plannedSprint: 4..7` for sourcing/PO/logistics/receipts while dossier actions shipped in S4–S7; overview derived “planned” badges from that |
| Impact | Users and UAT see false “not built yet” for working capabilities |
| Correction | Routes set to `/requests` with role permissions; planned markers removed; overview copy updated EN/FR/AR; nav unit tests updated |
| Verification | Frontend unit tests; manual overview/nav check |

### S13-F-02 — Demo profile resets demo account passwords on every start

| | |
|---|---|
| Severity | Medium (ops clarity) |
| Evidence | `DemoUserSeeder` javadoc and behaviour: sets all `is_demo` passwords from `DEMO_USER_PASSWORD` each start |
| Impact | Expected for local demo/QA; catastrophic if demo profile enabled in production |
| Correction | Document in handover + acceptance plan; production uses non-demo profiles + one-shot GLOBAL_ADMIN bootstrap only |
| Verification | Docs; no code change required for Core |

### S13-F-03 — Live AI / MFA / pen-test / §15 / restore drill unmet

| | |
|---|---|
| Severity | Gate (not a code defect) |
| Evidence | ADR-012; roadmap gates; NFR-01/04/10; S9 notes |
| Impact | Production readiness cannot be claimed |
| Correction | Tracked; hosting decision brief published |
| Verification | N/A |

### S13-F-04 — Messaging attachments and AI OCR still absent

| | |
|---|---|
| Severity | Low / deferred |
| Evidence | ADR-011 deferred attachments; AI reading deferred |
| Impact | Spec vision vs Core delivery gap — already labelled deferred |
| Correction | Keep deferred; do not claim complete |

### S13-F-05 — Algorithmic three-way match not implemented

| | |
|---|---|
| Severity | Deferred (P2) |
| Evidence | S7 manual invoice + match attest only |
| Impact | Must not be sold as automated 3-way |
| Correction | Matrix + register wording |

## Areas reviewed without new Critical defects in this pass

Company isolation patterns, supplier portal filter, assistant no-write + role gate, award/PO flows covered by Ordering IT + e2e, public quote/approval link rate-limit regression tests from S9, GLOBAL_ADMIN permissions exclude purchasing/assistant by default.

Additional agent findings (if any High) are appended in the verification report after agent completion.

## UI/UX changes this sprint

- Navigation + overview honesty for S4–S7 journey.
- Overview “available” checklist extended (sourcing→receipt, messaging, dashboards).
- Assistant already shows demo/disabled mode label (kept).
- Follow-up: supplier contacts land on `/supplier/messages` (shared `workspaceHome`) instead of `/requests`.

## Browser checks performed

| View | Role (intended) | Lang | Result |
|---|---|---|---|
| Health | — | — | Backend UP; FE 200 |
| Overview / nav (after fix) | Buyer | EN | Unit-covered; live browser pass recorded after restart |
| Assistant | Buyer | EN | Prior S12 e2e; re-check after S13 if FE restarted |
| Full role matrix | — | EN/FR/AR | Covered by **manual** plan (not executed by user) |

Native-speaker Arabic approval: **not claimed**.
