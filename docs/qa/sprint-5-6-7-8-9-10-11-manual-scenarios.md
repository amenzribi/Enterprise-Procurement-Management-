# Sprint 5–11 — Combined manual scenarios

| | |
|---|---|
| Application | http://localhost:4300 (proxy → http://localhost:8081) |
| Profiles | `dev,demo`; database `procurement_dev` |
| Demo password | `%TEMP%\epi-e2e-pw.txt` |
| User acceptance | **Pending** for Sprints **5–11** until Mohamed confirms |

Prior guides: [sprint-5-6-7-8-9-10-manual-scenarios.md](sprint-5-6-7-8-9-10-manual-scenarios.md), [sprint-11-manual-test-plan.md](sprint-11-manual-test-plan.md).

**Accounts:** Sami, Karim, Mehdi, Ines, Omar, Hedi, Claire, `admin@demo.local`, `global.admin@demo.local`, **`contact.skf@demo.local`** (supplier portal).

---

## A–F. Sprints 5–10

Follow prior combined guide (award → close → dashboards → link security → GLOBAL_ADMIN).  
**Actual result:** Not executed by user.

---

## G. Sprint 11 messaging

1. As Ines, open an RFQ for DEMO-SKF and **Open conversation**.  
2. Send EXTERNAL message; as `contact.skf@demo.local` open `/supplier/messages` and reply.  
3. Send INTERNAL note; confirm supplier cannot see it.  
4. As Sami / global.admin, confirm no messaging access by default.  
5. Revoke contact (then restore for continued demo use).  
6. Check Arabic + narrow viewport.

**Actual result:** Not executed by user.

---

## Out of scope

Messaging attachments (explicitly deferred), AI assistant (proposed S12), MFA, live ERP, real supplier e-mail send, §15 go-live.
