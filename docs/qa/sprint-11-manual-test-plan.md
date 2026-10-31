# Sprint 11 — Manual test plan

**Actual result** stays **Not executed by user** until Mohamed confirms.  
Application: http://localhost:4300 → http://localhost:8081  
Demo password: `%TEMP%\epi-e2e-pw.txt` (never commit)

---

### S11-MT-01 — Open RFQ conversation as buyer

| | |
|---|---|
| Requirement | S11-AC-01, S11-AC-03, S11-AC-11 |
| Preconditions | Assigned buyer on a request with an OPEN RFQ for DEMO-SKF |
| Account | `buyer@demo.local` / DEMO-CPA |
| Actions | Open request dossier → Sourcing → **Open conversation** |
| Expected | Conversation panel loads; supplier name shown; request list remains company-scoped |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-02 — Invite / use demo supplier contact

| | |
|---|---|
| Requirement | S11-AC-04, S11-AC-12 |
| Account | Buyer; supplier `contact.skf@demo.local` |
| Actions | Prefer demo contact already linked to DEMO-SKF. Optionally invite a unique `@demo.local` contact and complete `/p` setup |
| Expected | Contact can sign in to supplier portal only; quote-link alone does not open messaging |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-03 — External message buyer → supplier

| | |
|---|---|
| Requirement | S11-AC-02, S11-AC-05 |
| Actions | Audience **Visible to supplier** → send plain text → sign in as `contact.skf@demo.local` → open thread |
| Expected | Supplier sees the message with sender + time; unread clears after open |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-04 — Supplier reply

| | |
|---|---|
| Requirement | S11-AC-05, S11-AC-07 |
| Actions | Supplier sends reply → buyer reloads conversation |
| Expected | Buyer sees reply and unread indicator; in-app/mail alert without message body in e-mail |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-05 — Internal note hidden

| | |
|---|---|
| Requirement | S11-AC-02 |
| Actions | Buyer sends **Internal note** → supplier refreshes thread |
| Expected | Supplier never sees the internal text (API and UI) |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-06 — Isolation

| | |
|---|---|
| Requirement | S11-AC-01, S11-AC-03 |
| Actions | As `requester.tn@demo.local`, `global.admin@demo.local`, and a non-SKF context attempt to open the SKF conversation / portal |
| Expected | 403/404 / not-found UI; no cross-supplier leakage |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-07 — Revoke contact

| | |
|---|---|
| Requirement | S11-AC-10 |
| Actions | Buyer revokes SKF contact → supplier retries thread → restore contact via demo restart or SQL only if needed for further tests |
| Expected | Immediate denial; sessions invalidated via credentials_version |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-08 — Arabic / mobile

| | |
|---|---|
| Requirement | S11-AC-11 |
| Actions | Switch to AR; narrow viewport; open buyer panel and supplier portal |
| Expected | RTL layout; keyboard focus; no clipped controls |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S11-MT-09 — Attachments (explicit non-delivery)

| | |
|---|---|
| Requirement | S11-AC-08 |
| Actions | Confirm UI/API offer no messaging file upload |
| Expected | No messaging attachment controls; request attachments remain request-scoped |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

## Walkthrough

1. Eligible RFQ on a request (buyer assigned, RFQ OPEN for DEMO-SKF).  
2. Demo contact `contact.skf@demo.local` (already authorised) or invite.  
3. Buyer sends EXTERNAL message.  
4. Supplier opens `/supplier/messages` only for that context.  
5. Supplier replies.  
6. Buyer sees reply / unread.  
7. Internal note remains invisible to supplier.  
8. Other supplier / unauthorised user denied.  
9. Revoke → denial.  
10. Check Arabic/mobile.

Attachments: not delivered in Sprint 11. Notifications: verify local mail capture / in-app without body leak.
