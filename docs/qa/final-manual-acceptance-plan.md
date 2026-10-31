# Final manual acceptance plan (Core — Sprints 5–13)

| | |
|---|---|
| Application | http://localhost:4300 → http://localhost:8081 |
| Demo password | `%TEMP%\epi-e2e-pw.txt` via `DEMO_USER_PASSWORD` — **never commit or paste into tickets** |
| Provider | Assistant **DEMO** under `dev,demo`; live AI **blocked** |
| Actual results | Remain **Not executed by user** until Mohamed confirms |
| UAT scope | Sprints **5–13** pending confirmation |

Use existing sprint guides for deep cases; this pack is the ordered acceptance path. Index: [sprint-5-to-12-manual-index.md](sprint-5-to-12-manual-index.md) (updated for S13).

**Accounts:** `requester.tn@demo.local` (Sami), `manager.tn@demo.local` (Karim), `purchasing.manager@demo.local` (Mehdi), `buyer@demo.local` (Ines), `receiver@demo.local` (Omar), `group.management@demo.local` (Hedi), `global.admin@demo.local`, `contact.skf@demo.local` (supplier portal only).

---

## Session A — Smoke (~20 min)

### FA-01 Smoke sign-in and shell
| | |
|---|---|
| Req | UC-01, NFR-02/03 |
| Priority | P0 |
| Account | Sami / Ines |
| Steps | Open 4300; sign in; switch EN→FR→AR; confirm RTL in AR; open Overview |
| Expected | Journey stages show **Available** (not Sprint 4–7 planned); health reachable |
| Actual | Not executed by user |
| Evidence / Defect | |

### FA-02 Assistant mode honesty
| | |
|---|---|
| Req | S12, ADR-012 |
| Priority | P0 |
| Account | Ines; then `contact.skf@demo.local` |
| Steps | Open Assistant; note mode; ask a starter; try as supplier |
| Expected | DEMO label / demo response text; supplier denied |
| Actual | Not executed by user |

---

## Session B — Happy path request→closure (~45–60 min)

Reuse one dossier through the journey (do not restart from draft for every later check).

### FA-10 Create and submit
| | |
|---|---|
| Req | FR-REQ-01, UC-02/03 |
| Account | Sami (DEMO-TN-PLT) |
| Steps | New request with ≥1 line; submit |
| Expected | Unique number; status Submitted/In approval; owner visible |
| Actual | Not executed by user |

### FA-11 Approve
| | |
|---|---|
| Req | FR-APR-02, UC-05 |
| Account | Karim |
| Steps | Inbox → approve |
| Expected | Moves to purchasing; audit/tracker updated |
| Actual | Not executed by user |

### FA-12 Assign / accept / RFQ / quote / validate / award / PO / ship / receive / invoice / close
| | |
|---|---|
| Req | FR-PUR, FR-SRC, FR-ORD, FR-LOG, FR-RCV, UC-07…25 |
| Accounts | Mehdi → Ines → Omar as needed |
| Steps | Follow purchasing → sourcing on dossier → award → issue PO → supplier confirm if required → shipment milestones → receipt (full) → invoice attest → close |
| Expected | Owner/status coherent; quantities consistent; closure blocked until prerequisites met |
| Actual | Not executed by user |
| Notes | Manual invoice attest ≠ automated three-way |

---

## Session C — Exceptions (~40 min)

### FA-20 Return / revision / resubmit
| Req | FR-APR-02 | Account | Karim then Sami |
| Actual | Not executed by user |

### FA-21 Reject with reason
| Req | FR-APR-02 | Expected | Final rejected; reason required |
| Actual | Not executed by user |

### FA-22 Absence delegation
| Req | FR-APR-03 | See S2/S5–9 manuals |
| Actual | Not executed by user |

### FA-23 Partial / damaged receipt
| Req | FR-RCV-01 | Account | Omar |
| Expected | Outstanding qty updated; replacement rules as implemented |
| Actual | Not executed by user |

### FA-24 Invoice discrepancy attest
| Req | FR-RCV-04 | Expected | Discrepancy captured; no silent auto-match |
| Actual | Not executed by user |

---

## Session D — Permissions (~35 min)

### FA-30 Cross-company
| Req | FR-IAM-03 | Guess foreign request id → 404 / not visible |
| Actual | Not executed by user |

### FA-31 GLOBAL_ADMIN without business role
| Req | S10 | `global.admin@demo.local` cannot purchasing/assistant business data |
| Actual | Not executed by user |

### FA-32 Supplier portal isolation
| Req | S11 | Contact sees only EXTERNAL; no internal APIs |
| Actual | Not executed by user |

### FA-33 Deactivate / revoke / role change
| Req | S10/S11 | Deactivated user cannot sign in; revoked contact loses messaging; role change ends assistant if buyer removed |
| Actual | Not executed by user |

### FA-34 User admin password lifecycle
| Req | S10 | Create/reset/change password flows without printing tokens |
| Actual | Not executed by user |

---

## Session E — Messaging (~25 min)

### FA-40 External thread + internal note
| Req | ADR-011 | Buyer EXTERNAL; INTERNAL hidden from supplier; reply from contact |
| Actual | Not executed by user |

### FA-41 No state mutation via messaging
| Expected | Messaging does not award/issue/close |
| Actual | Not executed by user |

---

## Session F — Dashboards / files (~20 min)

### FA-50 Pipeline / CEO / audit pack / ERP CSV
| Req | FR-DSH, FR-ERP-01 | Mehdi/Hedi as permitted; Sami forbidden from management dashboards |
| Actual | Not executed by user |

---

## Session G — Assistant demo vs blocked live (~20 min)

### FA-60 Demo grounded answer + draft
| Provider | DEMO | Ask status; draft EXTERNAL; confirm not sent |
| Actual | Not executed by user |

### FA-61 Injection sample
| Paste instruction in supplier message / description; assistant must not widen tools |
| Actual | Not executed by user |

### FA-62 Live AI (BLOCKED)
| Do not pass | Requires hosting decision + live adapter | Status: **Blocked** |

---

## Session H — i18n / a11y / mobile (~30 min)

### FA-70 EN/FR/AR spot check on Overview, Approvals, Messaging, Assistant
| Native AR approval not claimed |
| Actual | Not executed by user |

### FA-71 Keyboard / focus / contrast sample
| Actual | Not executed by user |

---

## Recommended first session

**Session A + FA-10…FA-12** (smoke + one full journey). Then Session D and E before declaring Core acceptable.

Estimated total guided UAT: **~4–5 hours** if reusing dossiers; longer if every exception starts from a new request.
