# Sprint 9 — Manual test plan

**Convention for every sprint after this:** automated report + this-style manual plan + combined E2E guide.  
**Actual result** stays **Not executed by user** until Mohamed confirms.

Application: http://localhost:4300 → http://localhost:8081  
Demo password: `%TEMP%\epi-e2e-pw.txt` (shared; never commit)

---

### S9-MT-01 — Proxy-aware rate limit (direct)

| | |
|---|---|
| Requirement | S9-AC-01, S9-AC-02 / S2CR-08 |
| Preconditions | App running; `app.security.trusted-proxies` empty (default) |
| Account | Anonymous (CSRF cookie only) |
| Actions | Obtain a valid quote-link token as Ines; call `POST /api/v1/quote-links/summary` repeatedly with spoofed `X-Forwarded-For` rotating values |
| Expected | Still rate-limited by real peer address; cannot bypass by forging headers |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S9-MT-02 — Supplier link revoke on re-issue

| | |
|---|---|
| Requirement | S9-AC-03 / S4SL-09 |
| Preconditions | Open RFQ with supplier invited |
| Account | Ines `buyer@demo.local` then anonymous |
| Actions | Issue link A → open `/q#A` summary OK → Issue link B for same supplier → open A → open B |
| Expected | A invalid/not found; B works while RFQ OPEN |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S9-MT-03 — Closed RFQ public submit

| | |
|---|---|
| Requirement | S9-AC-05 / S4SL-08 |
| Account | Ines then anonymous |
| Actions | Valid link → close RFQ → submit quote via link |
| Expected | Neutral not-found / invalid page; no RFQ-state disclosure |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S9-MT-04 — One-tap approval still works

| | |
|---|---|
| Requirement | S2-AC-07 regression + S9-AC-02 |
| Account | Sami submit → Karim e-mail/one-tap path (or approval inbox) |
| Expected | Approve succeeds; abuse still limited |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

### S9-MT-05 — Arabic / mobile shell

| | |
|---|---|
| Requirement | NFR-11 regression |
| Account | Karim or Mehdi |
| Actions | AR + ~360px on request dossier and Dashboards |
| Expected | RTL, readable, no clipped primary actions |
| Actual result | Not executed by user |
| Pass/Fail / Evidence / Defect | |

---

## Distinctions

| Kind | Status |
|---|---|
| Automated tests | See [sprint-9-automated-verification.md](sprint-9-automated-verification.md) |
| Developer browser checks | Optional; not user acceptance |
| Suggested manual tests | This file |
| User acceptance | **Pending** |
