# Sprint 5–9 — Combined manual scenarios

| | |
|---|---|
| Application | http://localhost:4300 (proxy → http://localhost:8081) |
| Profiles | `dev,demo`; database `procurement_dev` |
| Demo password | `%TEMP%\epi-e2e-pw.txt` |
| User acceptance | **Pending** for Sprints **5–9** until Mohamed confirms |

Prior combined guide (S5–8): [sprint-5-6-7-8-manual-scenarios.md](sprint-5-6-7-8-manual-scenarios.md). Detailed S9 cases: [sprint-9-manual-test-plan.md](sprint-9-manual-test-plan.md).

**Accounts** (password = shared demo file): Sami `requester.tn@demo.local`, Karim `manager.tn@demo.local`, Mehdi `purchasing.manager@demo.local`, Ines `buyer@demo.local`, Omar `receiver.tn@demo.local`, Hedi `ceo@demo.local`, Claire `requester.fr@demo.local`.

---

## A–D. Sprints 5–8 (unchanged journey)

Follow sections A–D of the S5–8 guide: Award → PO → confirm → ship → deliver → receipt → accept → invoice/attest → close → dashboards/ERP/audit pack.  
**Actual result for each:** Not executed by user (until you test).

---

## E. Sprint 9 security

1. **Ines** — open RFQ, **Issue supplier link** twice for the same supplier. Old fragment token → invalid; new token → summary OK.  
2. Close RFQ → public submit with new token → invalid/not found.  
3. With default config (no trusted proxies), forging `X-Forwarded-For` must not bypass quote-link rate limits (use API tool or browser extensions carefully; developer may assist).  
4. Regression: one-tap / approval inbox still works for Karim.  
5. AR + mobile on dossier.

**Actual result:** Not executed by user.

---

## F. Combined E2E + S9

`S5-S9 full journey`: complete A→C close, then D dashboards, then E link revoke check on a **new** sourcing request (do not reopen a closed award path).

---

## Out of scope for these scenarios

GLOBAL_ADMIN (proposed S10), messaging (S11), AI (S12), live ERP, real supplier e-mail, MFA, production go-live.
