# GLOBAL_ADMIN: what it can do, verified on the local review instance

Checked on 2026-11-11 against the review instance (http://localhost:4201, backend :8081, database `procurement_qa_redesign`, demo profile). Sources: `identity/Role.java`, `UserAdminService.java`, `UserAdminController.java`, `request/RequestAdminController.java`, `GlobalAdminBootstrap.java`, and read-only API calls made while signed in as each account. No administration action was executed during this check: only sign-in, navigation and `GET` requests.

## Signing in locally

Demo accounts exist only while the backend runs with the `demo` profile. Every demo account takes the one password given to the backend in `DEMO_USER_PASSWORD` when it starts. The password is not stored in the repository; whoever starts the instance chooses it (see `HANDOFF.md`, "Restart").

| Review role | E-mail | Lands on |
|---|---|---|
| Global administrator | `global.admin@demo.local` | My work |
| Company administrator (all four demo companies) | `admin@demo.local` | My work |
| Requester | `requester.tn@demo.local` | My work |
| Approver, department manager | `manager.tn@demo.local` | My work |
| Approver, budget | `budget.tn@demo.local` | My work |
| Buyer | `buyer@demo.local` | My work |
| Purchasing manager | `purchasing.manager@demo.local` | My work |
| Receiver | `receiver.tn@demo.local` | My work |
| Group management | `ceo@demo.local` | My work |
| Supplier contact | `contact.skf@demo.local` | Supplier portal |

All fourteen demo accounts were signed in through the sign-in page on 2026-11-10 and again for this check.

In production there is no demo account. The first GLOBAL_ADMIN is created once, when `GLOBAL_ADMIN_BOOTSTRAP_PASSWORD` is set and no GLOBAL_ADMIN exists (S10-AC-03). There is no default password.

## What GLOBAL_ADMIN reaches

Navigation shown to `global.admin@demo.local`: My work, System status, Purchase requests, Approvals, Request administration, Approval rules, Exchange rates, Organisation, Users, Change password.

| Area | Screen | API | Result as GLOBAL_ADMIN |
|---|---|---|---|
| Users | `/admin/users` | `GET /api/v1/admin/accounts` | 200: 14 accounts across the 4 demo companies |
| Companies and structure | `/admin/organization` | `GET /api/v1/admin/organization/companies` | 200: 4 companies |
| Request oversight | `/admin/requests` | `GET /api/v1/admin/requests` | 200: 114 requests |
| Approval rules, exchange rates | `/admin/approval-rules`, `/admin/exchange-rates` | — | Screens open; their API calls were not listed in this check |

**Actions the API offers** (from the controllers; not executed here):

- **Accounts:** create, edit, activate or deactivate, grant a role, remove a role grant, send a password-reset link, set a temporary password, revoke sessions.
- **Requests:** archive and restore, cancel, return for revision, correct a draft, correct a submitted request, remove and restore, delete, cleanup with a preview step. Each row carries the actions allowed for it (`allowedActions`, `actionMatrix`).

**Roles** are granted per company (and per department where the role needs one). GLOBAL_ADMIN, GROUP_MANAGEMENT and SUPPLIER_CONTACT are group-wide grants. There is no separate permission editor: permissions follow from roles (`MeAssembler`).

## What GLOBAL_ADMIN is refused

It administers the installation; it is not a business user. Measured as `global.admin@demo.local`:

| API | Result |
|---|---|
| `GET /api/v1/purchasing/queue` | 403 |
| `GET /api/v1/dashboards/pipeline` | 403 |
| `GET /api/v1/suppliers` | 403 |
| `GET /api/v1/assistant/status` | 403 |
| `GET /api/v1/messaging/conversations` | 403 |
| `GET /api/v1/requests?scope=visible` | 200 with 0 requests (business visibility is empty) |
| `GET /api/v1/approvals/inbox` | 200 with 0 tasks |

**Protected rules in the service:**

- A GLOBAL_ADMIN cannot remove their own GLOBAL_ADMIN grant (409 `cannot-remove-own-global-admin`).
- A company ADMIN cannot grant GLOBAL_ADMIN, ADMIN, GROUP_MANAGEMENT or SUPPLIER_CONTACT (403), and can only grant roles in companies they administer.
- SUPPLIER_CONTACT is invited from messaging, not from user administration.

## Enforcement and company isolation

- **Server-side refusal:** `requester.tn@demo.local` receives 403 on `/admin/accounts`, `/admin/organization/companies` and `/admin/requests`. The screens are also hidden from the navigation, but the refusal is the server's.
- **Company ADMIN:** `admin@demo.local` holds an ADMIN grant in each of the four demo companies, so it sees all of them. That is consistent with its grants and is not a test of isolation.
- **Not verified live:** an ADMIN limited to one company. No demo account has that shape. Isolation for that case rests on the backend tests from sprint 10, which were not run in this UI phase.
- **Not verified live:** every write action above, and the "last GLOBAL_ADMIN" case (whether the final GLOBAL_ADMIN can be deactivated by another one).

## Gaps for the functionality phase

| Gap | Proposed behaviour |
|---|---|
| No single-company ADMIN in the demo data | Add a demo account with ADMIN in one company, so isolation can be reviewed by hand |
| No screen lists who holds which role | A role view: per role, the holders and their company; export as CSV |
| GLOBAL_ADMIN sees "Purchase requests" and "Approvals" in the navigation, both always empty | Hide them for accounts with no business role, or explain the empty state |
| Request administration has no link from the record | For holders of REQUEST_ADMIN, a link from a record to its administration page |
| Administration actions are not summarised anywhere for audit | An administration activity view (who changed which account or grant, when), read from the audit events already written |
| Last-GLOBAL_ADMIN protection is unconfirmed | Confirm with a backend test; if absent, refuse to remove or deactivate the last active one |
