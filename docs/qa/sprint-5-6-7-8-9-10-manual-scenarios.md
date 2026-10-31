# Sprint 5–10 — Combined manual scenarios

| | |
|---|---|
| Application | http://localhost:4300 (proxy → http://localhost:8081) |
| Profiles | `dev,demo`; database `procurement_dev` |
| Demo password | `%TEMP%\epi-e2e-pw.txt` |
| User acceptance | **Pending** for Sprints **5–10** until Mohamed confirms |

Prior guides: [sprint-5-6-7-8-9-manual-scenarios.md](sprint-5-6-7-8-9-manual-scenarios.md), [sprint-10-manual-test-plan.md](sprint-10-manual-test-plan.md).

**Accounts:** Sami, Karim, Mehdi, Ines, Omar, Hedi, Claire, `admin@demo.local`, **`global.admin@demo.local`**.

---

## A–E. Sprints 5–9

Follow prior combined guide (award → close → dashboards → link security).  
**Actual result:** Not executed by user.

---

## F. Sprint 10 administration

1. Sign in as **global.admin@demo.local**.  
2. Create user → setup link → assign role → change/reset password → deactivate.  
3. As `admin@demo.local`, confirm cannot grant GLOBAL_ADMIN.  
4. Confirm last GLOBAL_ADMIN cannot be deactivated.  
5. Confirm GLOBAL_ADMIN does not see other companies’ requests without an explicit business role.

**Actual result:** Not executed by user.

---

## Out of scope

Buyer–supplier messaging (proposed S11), AI assistant (proposed S12), MFA, live ERP, real supplier e-mail, §15 go-live.
