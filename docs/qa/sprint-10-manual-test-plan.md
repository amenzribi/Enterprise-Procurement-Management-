# Sprint 10 — Manual test plan

**Actual result** stays **Not executed by user** until Mohamed confirms.  
Application: http://localhost:4300 → http://localhost:8081  
Demo password: `%TEMP%\epi-e2e-pw.txt` (never commit)

---

### S10-MT-01 — Sign in as global administrator

| | |
|---|---|
| Requirement | S10-AC-01, S10-AC-03, S10-AC-08 |
| Account | `global.admin@demo.local` / demo password file |
| Actions | Sign in → open **Administration → Users** |
| Expected | USER_ADMIN screens load; request list remains company-scoped (no all-company dump) |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-02 — Create user and complete setup link

| | |
|---|---|
| Requirement | S10-AC-02, S10-AC-04 |
| Preconditions | Signed in as global admin; mail mode `file` |
| Actions | Create user with unique `@demo.local` e-mail → flush/wait for `.eml` in mail directory → open `/p#…` → set password (≥12, letter+digit) → sign in as new user |
| Expected | Setup succeeds once; reused link invalid; new user can sign in |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-03 — Assign role and verify allow/deny

| | |
|---|---|
| Requirement | S10-AC-02 |
| Actions | Grant `REQUESTER` for DEMO-TN-PLT → verify create request works; as Sami verify cannot open `/admin/users` |
| Expected | Permissions apply promptly after grant |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-04 — Change own password

| | |
|---|---|
| Requirement | S10-AC-04, S10-AC-05 |
| Account | Newly created user |
| Actions | **Change password** → update → old password fails; other browser session ends |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-05 — Admin password reset

| | |
|---|---|
| Requirement | S10-AC-04, S10-AC-05 |
| Actions | Global admin → **Send password reset** → redeem new link → previous password fails |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-06 — Remove role / deactivate

| | |
|---|---|
| Requirement | S10-AC-02, S10-AC-05 |
| Actions | Remove REQUESTER → access denied for create; deactivate → sign-in fails; open approvals not silently reassigned |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-07 — Last global admin protection

| | |
|---|---|
| Requirement | S10-AC-06 |
| Actions | With only one active GLOBAL_ADMIN, attempt deactivate self / remove GLOBAL_ADMIN grant |
| Expected | Conflict / blocked; you remain able to sign in as global admin |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-08 — Company admin boundary

| | |
|---|---|
| Requirement | S10-AC-01 |
| Account | `admin@demo.local` |
| Actions | Try to grant `GLOBAL_ADMIN` to a user |
| Expected | Forbidden |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-09 — Audit without secrets

| | |
|---|---|
| Requirement | S10-AC-07 |
| Actions | Inspect admin audit entries for create/reset/deactivate |
| Expected | No passwords or raw tokens in payloads |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S10-MT-10 — AR / mobile

| | |
|---|---|
| Requirement | S10-AC-08 |
| Actions | Users admin + change password at 360px width in Arabic |
| Expected | Usable RTL layout |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

## Practical walkthrough (demo)

1. Sign in as `global.admin@demo.local`.  
2. Open **Administration → Users**.  
3. Create a demo user; assign company + `REQUESTER`.  
4. Complete password setup from the file mailbox link (`/p#…`).  
5. Verify allowed/denied actions.  
6. Change password.  
7. Initiate and complete reset.  
8. Confirm prior sessions/credentials fail as expected.  
9. Change/remove a role; verify permissions.  
10. Deactivate; verify sign-in denied.  
11. Verify last-global-admin protection (safe — do not lock yourself out when only one remains without a second GA).  
12. Inspect non-secret audit history.
