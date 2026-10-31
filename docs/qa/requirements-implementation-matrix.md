# Requirements → implementation matrix (Sprint 13)

| | |
|---|---|
| Baseline commits | `b967b3e` / `fabfe96` (+ Sprint 13 commits when landed) |
| Functional reference | Solution Guide EN v3.0 (Core); French spec v2.1 for detailed rules |
| Decisions | ADR-001…ADR-012; `docs/decisions/decision-log.md` |
| Label | Developer reconciliation — **not** independent QA or user acceptance |
| Manual UAT | **Pending** for Sprints 5–13 |

## Legend

| Status | Meaning |
|---|---|
| Implemented | Vertical slice present (API + UI + automated evidence) for Core intent |
| Partial | Core path exists but material gaps remain |
| Deferred | Explicitly postponed (P2 / Trading / Enterprise / backlog) |
| Blocked | Approved intent blocked on external decision (e.g. AI hosting) |
| Out of scope | Outside Functional Core / edition boundary |

**Do not read “Implemented” as production-ready or UAT-passed.**

## Distinctions (mandatory)

| Claimed / vision | Actual Core delivery |
|---|---|
| Full product vision (Trading, Enterprise, Teams/WhatsApp, PostgreSQL) | **Out of scope** of this Core build |
| Automated three-way match | **Manual invoice attestation** + match attest (algorithmic 3-way deferred) |
| Real uploaded file OCR / AI reading | **Attachments + references**; OCR/AI reading deferred/blocked |
| Live ERP APIs | **CSV/file exchange** only |
| Live AI answers | **Demo/disabled assistant** only (ADR-012) |
| Developer automated tests | **Not** independent review or user acceptance |

Autonomous approve / award / issue / pay / close / send by AI remain **prohibited**, not “missing features”.

---

## Organization & company boundaries

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| FR-ORG-01 | Spec §3; S3 | Implemented | `organization`, Flyway V3/V7; `/admin/organization` | Purchasing/org ITs; e2e purchasing | Pending | — |
| FR-ORG-02 | Spec §3.6–3.7 | Implemented | Company currency/lang/TZ/ERP/mode | Org admin paths | Pending | — |
| FR-IAM-03 | ADR-007 | Implemented | Central scope → 404/403 | Cross-company tests per module | Pending | Continuous vigilance |

## Users, roles, passwords

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| FR-IAM-01 | S1 | Implemented | Session/CSRF sign-in | Auth ITs; session e2e | Pending | OIDC production IdP |
| FR-IAM-02 | §20; S1–S11 | Implemented | Roles + company membership | Auth/UserAdmin/Messaging ITs | Pending | — |
| FR-IAM-04 MFA | §24; S9 gate | Blocked / Planned | — | — | Pending | IdP MFA / pilot |
| S10 GLOBAL_ADMIN | ADR / S10 | Implemented | Users admin, reset, revoke | UserAdmin IT; e2e | Pending | — |
| Demo password seed | Demo profile | Implemented (demo only) | `DemoUserSeeder` resets demo hashes each start when `DEMO_USER_PASSWORD` set | Demo profile | N/A | Must not run in production |
| Prod bootstrap | S10 | Implemented | One-shot `GLOBAL_ADMIN_BOOTSTRAP_*` | Startup tests | Pending | Clear env after first use |

## Request → approval

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| FR-REQ-01/03 (fields) | S1 | Implemented | `/api/v1/requests`, forms | Request ITs; request e2e | Pending | Near-duplicate warning G12 |
| FR-REQ-02 attachments | S2 | Implemented | Documents module | Attachment tests | Pending | Messaging attachments deferred |
| FR-REQ-04/05 | Spec | Deferred / Planned | — | — | — | Stock/Excel import |
| FR-APR-01…04 | S2 | Implemented | Approvals, rules, absences, one-tap | Approval ITs; approval e2e; a11y | Pending | Native AR review |
| FR-APR-05 BPMN | ADR-005 | Deferred | — | — | — | — |
| UC-04 tracker | S2 | Implemented | Tracker UI | Tracker specs | Pending | — |

## Purchasing, suppliers, RFQ, evaluation

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| FR-PUR-01…04 | S3 | Implemented | Purchasing inbox/actions | Purchasing IT; e2e | Pending | — |
| FR-PUR-05/06 grouping / AI suggest | G12 / P2 | Deferred | — | — | — | Pilot confirm |
| FR-SRC-01…04 | S3–S4 | Implemented | Suppliers + RFQ + public `/q` | Sourcing IT; quote-link security IT | Pending | Pen-test before prod public links |
| FR-SRC-05 AI quote read | P2 | Deferred / Blocked | — | — | — | Provider |
| FR-EVL-01 | S4–S5 | Partial | Tech/commercial validation + award path | Sourcing/Ordering ITs | Pending | Committees P2 |
| FR-EVL-02 | P2 | Deferred | — | — | — | — |

## Award, PO, shipment, receipt, invoice, closure

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| FR-ORD-01 + UC-15…17 | S5 | Implemented | Ordering on request dossier | Ordering IT; ordering-logistics e2e | Pending | LC/guarantees Trading |
| FR-LOG-01/02 | S6 | Implemented (milestones + doc refs) | Logistics actions | Logistics IT; e2e | Pending | AI transport reading deferred |
| FR-RCV-01/02/04 + UC-20…25 | S7 | Implemented (manual attest) | Receipt/invoice/close | Receipt IT; e2e | Pending | Algorithmic 3-way deferred |
| FR-RCV-03 three-way | P2 / v3 G15 lite | Deferred as automated | Manual match attest only | — | Pending | — |

## Dashboards, ERP files, audit

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| FR-DSH-01/02 | S8 | Implemented | `/dashboards` | Dashboard IT; e2e | Pending | — |
| FR-AUD-01 | S2+ | Implemented | Tracker + audit chain | Audit hasher; chain checks | Pending | — |
| FR-ERP-01/02 files | S3/S8 | Implemented | CSV export / import wizard | Import/dashboard paths | Pending | Live ERP APIs deferred |
| FR-ERP-03 connectors | P2 | Deferred | — | — | — | — |

## Messaging & assistant

| Ref | Source | Status | Backend / UI | Automated evidence | Manual | Remaining |
|---|---|---|---|---|---|---|
| S11 messaging | ADR-011 | Implemented | Buyer + supplier portal | Messaging IT; messaging e2e | Pending | Attachments deferred |
| FR-AI-01 draft from text | S8/S12 | Blocked | — | — | — | Hosting |
| S12 assistant | ADR-012 | Partial (demo/disabled) | `/assistant`, DEMO adapter | Assistant IT; assistant e2e | Pending | Live provider decision |

## Non-functional / gates

| Ref | Status | Notes |
|---|---|---|
| NFR-01 latency | Planned / unmet | No load measurement evidence |
| NFR-02 responsive | Partial | Shell + many journeys; continue a11y/mobile checks |
| NFR-03/11 i18n+RTL | Implemented (keys) | Native-speaker AR outstanding |
| NFR-05 audit | Implemented | — |
| NFR-06 secrets | Implemented | Env/demo file only |
| NFR-09 WCAG | Partial | Automated a11y e2e samples; not full AA audit |
| NFR-10 backup/DR | Unmet | Restore drill not run |
| §15 pilot | Unmet | Business gate |
| Pen-test | Unmet | Public `/a` and `/q` especially |

## Use-case rollup (v3.0 Core)

| UC | Status |
|---|---|
| UC-01…07, 10–11, 13–21, 23, 25–28 | Implemented (with noted partials) |
| UC-08 grouping, UC-09 transfer | Deferred / G12 open |
| UC-12 AI quote check | Deferred (manual compare exists) |
| UC-22 automated match | Deferred (manual attest exists) |
| UC-24 recharge billing | Trading |
| UC-29…30 | Out of scope |

## Coverage gap note

Passing automated suites exercise many happy paths and isolation cases; they **do not** prove every matrix row. Untested or thinly tested rows remain risk until manual acceptance and independent review.
