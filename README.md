# Enterprise Procurement Intelligence

Procurement request platform for multi-company groups: one entry point for every purchase request, from need to
closure. Spring Boot 4.1 (Java 21) + Angular 22 + Microsoft SQL Server. English, French and Arabic (RTL).

**Current state (2026-11-16):** internal messaging and hardening sprint 1 are in the working tree, uncommitted;
hardening sprint 1 and its remediation are complete and **await the owner's Go/No-Go**, see
[STATUS.md](STATUS.md)). The paragraph below is the older functional baseline.

**Functional baseline: Sprint 3 locally implemented** (central buying queue, buyer assignment, purchase mode, supplier
register, organisation admin, master-data import). Sprint 2 remains locally closed at `42c03b9`. The pilot-client
business gate in the Solution Guide v3.0 §15 is **not** recorded as passed and restricts rollout, not local
development. This is a developer delivery, not an independent QA/reviewer sign-off.
See [docs/product/roadmap.md](docs/product/roadmap.md). Java 25 was proposed (ADR-010) and **rejected**.

| Folder | Contents |
|---|---|
| `BackEnd/` | Spring Boot modular monolith (Maven Wrapper) |
| `FrontEnd/` | Angular single-page application (npm, lockfile) |
| `docs/` | Specification, analysis, architecture, decisions, roadmap, QA and reviews ([index](docs/README.md)) |

## Prerequisites (Windows + VS Code)

- JDK 21 LTS (`java -version`) — Temurin 21. ADR-010 rejected Java 25.
- Node.js 24.15+ or 22.22+ with npm (`node -v`)
- SQL Server 2019+ running locally (developed on SQL Server 2025 Developer Edition), TCP on port 1433, with
  `sqlcmd` available
- No global Maven or Angular CLI needed: the project uses the Maven Wrapper and the local Angular CLI.
- VS Code: accept the recommended extensions (`.vscode/extensions.json`).

## 1. Database (once)

The `dev` profile connects with **Windows integrated authentication**, so no password is stored anywhere. Create
the databases as your Windows user:

```powershell
sqlcmd -S localhost -E -C -Q "CREATE DATABASE procurement_dev COLLATE Latin1_General_100_CI_AS_SC_UTF8; CREATE DATABASE procurement_test COLLATE Latin1_General_100_CI_AS_SC_UTF8;"
```

Tables are created by Flyway when the backend starts; do not create them by hand.

The driver's native authentication DLL (`mssql-jdbc_auth`, x64) is downloaded by Maven and copied to
`BackEnd/target/native`; the run and test configurations point Java at it automatically.

**Using SQL Server authentication instead** (mixed mode must be enabled on the server): set environment variables
before starting the backend (never commit them):

```powershell
$env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=procurement_dev;encrypt=true;trustServerCertificate=true"
$env:DB_USERNAME = "procurement_app"
$env:DB_PASSWORD = "<your password>"
```

Alternatively, copy `BackEnd/config/application.example.yml` to `BackEnd/config/application.yml` (git-ignored).
Outside the `dev`/`local`/`test` profiles, the backend refuses to start unless `DB_URL` is set, and checks the
rest of its configuration (see "Configuration outside development" below).

## 2. Backend

Use PowerShell and `mvnw.cmd`. In Git Bash, `./mvnw` can fail to download Maven because of a certificate
revocation check.

```powershell
cd BackEnd
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
```

- `demo` loads clearly labelled demonstration data (a sample group whose company names end in "(Demo)")
  and eight demo users. Omit it for an empty organisation.

### Demo sign-in (local only)

No demo password is stored in the repository. Choose one for your machine before starting the backend:

```powershell
$env:DEMO_USER_PASSWORD = "<choose a local password>"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
```

If `DEMO_USER_PASSWORD` is not set, a random password is generated at each start and printed once in the backend
console. Demo accounts can only sign in while the `demo` profile is active.

| E-mail | Company | Can |
|---|---|---|
| requester.tn@demo.local | Atlas Mechanical Sfax (Demo) | Create and submit own requests |
| assistant.tn@demo.local | Atlas Mechanical Sfax (Demo) | Also enter requests on behalf of colleagues |
| manager.tn@demo.local | Atlas Mechanical Sfax (Demo) | View submitted requests of the Maintenance department |
| requester.fr@demo.local | Atlas Composites Lyon (Demo) | Create and submit own requests (cannot see Sfax data) |
| buyer@demo.local, purchasing.manager@demo.local | Atlas Sourcing & Trading (Demo) | Sign in only (purchasing screens arrive in later sprints) |
| ceo@demo.local | Atlas Holding (Demo) | View submitted requests of all group companies |
| admin@demo.local | Atlas Holding (Demo) | System diagnostics and API docs |

Sessions end after 30 minutes of inactivity or 8 hours. Five failed sign-ins for the same e-mail from the same
address block further attempts for 15 minutes.
- API: http://localhost:8080/api/v1/system/status · Health: http://localhost:8080/actuator/health
- API docs (dev): http://localhost:8080/api/v1/docs

Tests:

```powershell
.\mvnw.cmd verify            # unit, slice and module-boundary tests; SQL Server tests are skipped
.\mvnw.cmd verify -Pdb-it    # also runs the SQL Server integration tests against procurement_test
```

The SQL Server tests wipe `procurement_test` and refuse to run against a database whose name does not end in `_test`.

## 3. Frontend

```powershell
cd FrontEnd
npm ci
npm start                    # http://localhost:4200 (proxies /api and /actuator to :8080)
```

Checks: `npm run lint`, `npm test`, `npm run build`.

End-to-end tests (Playwright, uses the installed Microsoft Edge) run against started servers:

```powershell
$env:DEMO_USER_PASSWORD = "<the same value the backend uses>"
npm run e2e
```

**If ports 8080/4200 are taken** by another project, start the backend with `$env:SERVER_PORT = "8081"`, copy
`proxy.conf.json` to a git-ignored file pointing at 8081, run `npx ng serve --port 4300 --proxy-config <file>`, and set
`$env:E2E_BASE_URL = "http://localhost:4300"` before `npm run e2e`.

Open **System status** in the sidebar to verify the chain browser → backend → database.

## Configuration outside development (since hardening sprint 1)

Every instance says what it is with `APP_ENVIRONMENT`. The application refuses to start, with a message naming
each problem, when the declaration is missing or the configuration is unsafe for it (`DeploymentConfigurationGuard`).
Nothing has been deployed yet; the rules below are what the code enforces.

| `APP_ENVIRONMENT` | Profiles allowed | Rules |
|---|---|---|
| not set | `dev`, `local`, `test` only | A developer machine: the database must be on this machine (`localhost`, `127.0.0.1`, `.` or `(local)`, with no `serverName=` redirection) and the application listens on `127.0.0.1` only (the default of those profiles). Without a development profile, an unset `APP_ENVIRONMENT` is refused |
| `development` | `dev`, `local`, `test` | A developer machine with a remote database, or reachable from other devices (`SERVER_ADDRESS=0.0.0.0`). The declaration is the operator's statement; never use it on a server |
| `demo` | `demo`, no development profile | A demonstration server: deployed rules below, demonstration data allowed |
| `uat`, `staging`, `preprod`, `production` | Neither development profiles nor `demo` | Deployed rules below. Any other value is refused |

Deployed rules: secure session cookies (`app.security.cookie-secure` and `server.servlet.session.cookie.secure`), no
testing hooks, no API documentation, `MAIL_MODE` stated, `APP_PUBLIC_BASE_URL` in HTTPS.
Not checked yet: `APP_ALLOW_UNSCANNED_DOWNLOADS=true` and an empty `APP_TRUSTED_PROXIES` behind a proxy are accepted;
both are the operator's responsibility (see the table below).

| Variable | Required | Meaning |
|---|---|---|
| `APP_ENVIRONMENT` | Yes | See above |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Yes | Database. With two logins (ADR-015) this is the runtime login |
| `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` | With ADR-015 | Migration login |
| `APP_PUBLIC_BASE_URL` | Yes, HTTPS | Public address of the application, used in e-mail links |
| `MAIL_MODE` | Yes | `smtp` (with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`) or `file`. There is no default: `file` writes e-mails, which contain live links, to `MAIL_DIRECTORY` |
| `APP_TRUSTED_PROXIES` | Behind a reverse proxy | The proxy's address as the application sees it. Without it every user shares the proxy's address for rate limits |
| `APP_ALLOW_UNSCANNED_DOWNLOADS` | No | Leave unset. Without an antivirus scanner (ADR-013) uploaded files are stored as `PENDING` and cannot be downloaded; `true` allows unscanned downloads and is a risk to accept in writing |
| `DB_LOCK_TIMEOUT_MS`, `DB_TRANSACTION_TIMEOUT` | No | Defaults `10000` and `30s`. A request blocked longer gets 503 "busy" |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | No | `ecs` for JSON log lines |

Do not configure `spring.datasource.hikari.jdbc-url` on a developer machine without `APP_ENVIRONMENT=development`:
the guard treats it as a possible remote database.

### Bootstrap of the first global administrators (F-2)

Every later account, activation and administration role needs a second person, so an installation starts with
**two global administrators held by two different people**:

| Variable | Meaning |
|---|---|
| `GLOBAL_ADMIN_BOOTSTRAP_EMAIL`, `GLOBAL_ADMIN_BOOTSTRAP_PASSWORD` | First global administrator; must change the password at first sign-in. Remove the password variable afterwards |
| `GLOBAL_ADMIN_BOOTSTRAP_SECOND_EMAIL` | Second global administrator, a different person: created active with no usable password; a set-up link is sent to that mailbox (`MAIL_MODE=smtp`) |

The bootstrap runs once, only when no active global administrator exists and outside the `demo` profile, and writes
`GLOBAL_ADMIN_BOOTSTRAPPED` audit events. It is the only path that creates administrators without a second person;
the installation operator is trusted for this step. With one address only, the log warns that the installation cannot
create or activate accounts or administrators without a second administrator.

### Administration rules (since hardening sprint 1)

| Rule | Who can still do it |
|---|---|
| A company administrator cannot change their own roles; a global administrator cannot give themselves a sensitive role | A global administrator; another global administrator |
| A company administrator cannot add or remove roles of, reset, deactivate, edit or sign out another administrator | A global administrator |
| Four eyes, permanent, for **every** administrator: whoever created an account never gives it a sensitive role (`DEPARTMENT_MANAGER`, `BUDGET_APPROVER`, `MANAGEMENT_APPROVER`, `BUYER`, `RECEIVER`, `PURCHASING_MANAGER`), never names it as named approver in a rule, and never names it as delegate | Another administrator of the company, or a global administrator |
| Nobody names themselves as approver in a rule or as delegate | Another administrator |
| A supplier contact account never holds an internal role, is never a named approver and never a delegate | Nobody |
| A company administrator sees and changes only accounts of their companies; others answer 404 | A global administrator |
| **Two-person activation (F-1):** a new account is created inactive; whoever created it, or an administrator whose own account that person created, cannot activate it; the set-up link is sent at activation; history in the audit trail (`USER_CREATED`, `USER_ACTIVATED`, `PASSWORD_SETUP_ISSUED`) | Another administrator of the company, or a global administrator |
| ADMIN and GLOBAL_ADMIN are never granted by the account's creator nor to oneself (F-2) | Another global administrator |
| "Creator" means the whole creation chain, five levels deep: whoever created the creator of an account counts as its creator for activation, roles, named approvers and delegations | — |
| The creator never reactivates an account they created, whatever its roles (stricter than required, deliberately) | Another administrator |
| No temporary password on an account not yet activated, nor on one the administrator created: the set-up link is the first credential | — |
| Temporary passwords: global administrators only, never on their own account nor on an account holding a sensitive or administrator role (ADR-016 will remove them) | — |

**Onboarding in a company with a single administrator:** that administrator creates the account; a global
administrator activates it and gives it any buyer, receiver, purchasing manager or approver role. An account a global administrator
created gets its sensitive role from a company administrator or another global administrator. The creator is read from
`user_account.created_by_user_id` (since V24) or, for older accounts, from the account's `USER_CREATED` audit event;
accounts created outside user administration (demo seed, first administrator) have no creator and are ordinary
colleagues. Ordinary roles (`REQUESTER`, `ENTER_ON_BEHALF`) remain the creator's daily work.

Behaviour to know:

- Every response carries `X-Request-Id`; every log line of a request starts with `[request id,user id]`.
- `/actuator/metrics` is for global administrators. `/actuator/health` answers anyone with the status only.
- Uploads: a PDF or image must carry its own extension. In development files show "not virus-checked".
- Database scripts for the two logins and for snapshot isolation are in `scripts/db/`; none is applied automatically.
- CI: `.github/workflows/ci.yml` (backend with SQL Server, frontend lint, tests, build).

**Supplier contacts, a documented exception to two-person activation:** a buyer invites a supplier contact from
messaging, and the account is active at once. It holds only `SUPPLIER_CONTACT`, which cannot be removed, never
receives an internal role, and is never an approver or a delegate; it can only exchange messages on its supplier's
conversations.

### Procurement separation of duties (F-5)

| Step | Refused to |
|---|---|
| Approving a request | Its requester and the person who entered it |
| Being assigned as buyer, accepting the assignment; sourcing, awarding, ordering and logistics, even as purchasing manager | The requester or the person who entered the request (`buyer-is-requester`) |
| Recording, confirming a goods receipt; accepting services; accepting a shortfall; waiving an outstanding quantity at closure | The buyer of the order: the assigned buyer and whoever issued the purchase order (`buyer-cannot-receive`) |
| Attesting the invoice match | The person who recorded the invoice (`invoice-recorder`); recording never attests. The same purchasing manager may attest and pay (an owner option: require a third person) |
| Authorising payment | Anyone who is not a purchasing manager of the buying company (`payment-needs-manager`), the invoice recorder (`invoice-recorder`) and the buyer of the order (`payment-not-independent`); recording never pays |

State, evidence and open findings: [docs/sprints/sprint-01-remediation-report.md](docs/sprints/sprint-01-remediation-report.md).

## Conventions

See [CLAUDE.md](CLAUDE.md) for stack, commands, conventions and delivery rules.
