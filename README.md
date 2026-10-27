# Enterprise Procurement Intelligence

Procurement request platform for multi-company groups: one entry point for every purchase request, from need to
closure. Spring Boot (Java 25) + Angular 22 + Microsoft SQL Server. English and French.

**Current state: Sprint 0 (foundation).** No procurement features yet. The app runs, connects to SQL Server,
migrates the schema, and shows a bilingual application shell with a live system-status check. See
[docs/product/roadmap.md](docs/product/roadmap.md) for what comes next.

| Folder | Contents |
|---|---|
| `BackEnd/` | Spring Boot modular monolith (Maven Wrapper) |
| `FrontEnd/` | Angular single-page application (npm, lockfile) |
| `docs/` | Specification, analysis, architecture, decisions, roadmap, QA and reviews ([index](docs/README.md)) |

## Prerequisites (Windows + VS Code)

- JDK 25 LTS (`java -version`)
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
Outside the `dev`/`local`/`test` profiles, the backend refuses to start unless `DB_URL` is set.

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

## Conventions

See [CLAUDE.md](CLAUDE.md) for stack, commands, conventions and delivery rules.
