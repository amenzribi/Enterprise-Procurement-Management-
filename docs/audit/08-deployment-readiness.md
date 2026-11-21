# 08. Deployment readiness (UAT with a shareable HTTPS URL)

Scope: what the repository contains today for deployment, what a secure UAT environment needs, and the options. Nothing was deployed or purchased. **Cost figures are indicative orders of magnitude from general knowledge, not quotes; check current price lists before deciding.**

## 1. Summary

**The application is not ready to deploy to UAT today.** The blocking reason is not the code's quality but the absence of everything around it: no build pipeline, no packaging, no production-like profile, no backup, no monitoring, and several defaults that are unsafe outside a developer machine. The work is modest and well understood, about one sprint with the security fixes that UAT must not start without.

| Area | State today | Ready? |
|---|---|---|
| Build artefacts | Built by hand; the jar runs with `java -jar` (verified 2026-11-14); the frontend builds to static files | Partly |
| Packaging and pipeline | No Dockerfile, no CI workflow, no deployment script | No |
| Configuration | Environment variables for database, mail, storage, public URL, trusted proxies; startup fails without `DB_URL` outside dev | Yes |
| Profiles | Only `dev`, `demo`, `local`; every instance so far ran `dev,demo` | No |
| TLS and domain | None; no reverse-proxy configuration | No |
| Frontend hosting | The backend does not serve the Angular files (`resources/static` is empty); no CORS is configured, so both must share one origin | Design decided, nothing built |
| Database | Flyway migrates at startup with the application's own login; RCSI off; collation not enforced | No |
| Secrets | None in the repository; passed as environment variables | Yes (needs a store) |
| Backup and restore | No script, no runbook; "restore drill" is an open release gate in `STATUS.md` | No |
| Monitoring | `health` endpoint only | No |
| Test users | Demo seed with one shared password printed at startup; GLOBAL_ADMIN bootstrap from environment exists | Partly |
| Rollback | No procedure; migrations are forward-only | No |

## 2. Target architecture for UAT

One origin, one node (report 04, P-7):

```
Browser ──HTTPS──> Reverse proxy (TLS, security headers, static Angular files)
                        │  /api/*, /actuator/health
                        ▼
                  Spring Boot jar (port 8080, not exposed)
                        │
                        ▼
                  SQL Server (private network)      File storage (local disk or share)
```

Requirements that follow from the code:

| Requirement | Reason (evidence) |
|---|---|
| Frontend and API on the same origin | No CORS configuration; session cookie `SameSite=Lax`; CSRF cookie read by the page |
| HTTPS end to end to the proxy | Session cookie is `Secure` unless the dev profile is active (`application.yml`) |
| Proxy must not buffer `/api/v1/internal-messaging/stream` | Server-Sent Events; read timeout above 30 minutes or buffering off for that path |
| `APP_TRUSTED_PROXIES` set to the proxy's address, **after** fixing S-3 | Otherwise rate limits key on the proxy, or can be bypassed (report 03, S-3) |
| `APP_PUBLIC_BASE_URL` = the public HTTPS URL | Used in e-mail links |
| `MAIL_MODE=smtp` with a UAT mailbox or a mail sink | Default writes links to disk (report 03, S-16) |
| `STORAGE_DIRECTORY` on a persistent, backed-up volume | Attachments are files, not database rows |
| Upload limit 21 MB at the proxy | Matches `spring.servlet.multipart` |
| One application instance | Sessions, limits and live streams are in memory |
| Explicit JVM heap | Files are held in memory (report 04, P-5) |

## 3. Hosting options

| Option | Description | For | Against | Indicative monthly cost |
|---|---|---|---|---|
| **A. One virtual machine** (Windows or Linux) | nginx, Caddy or IIS as proxy; the jar as a service; SQL Server on the same VM or the client's existing server | Closest to the on-premise target in ADR-002; simplest; SQL Server behaves exactly as in development | Manual patching; single point of failure (acceptable for UAT) | VM with 4 vCPU and 16 GB: roughly 120–200 USD; SQL Server Express is free (10 GB limit per database); Standard edition licensing is the large item if required |
| **B. Managed platform (Azure)** | Azure App Service (Java 21) or a container app for the API; Azure Static Web Apps or the same proxy for the frontend; Azure SQL Database | Managed TLS, backups and patching; quick to share a URL | **Azure SQL has `READ_COMMITTED_SNAPSHOT` on by default**, so report 05, DB-3 must be verified first; live streams need "always on" and a single instance; file storage needs a mounted share; Windows integrated authentication is not available (use SQL or Entra authentication) | Roughly 100–300 USD for small tiers (API plan, a small database tier, storage) |
| **C. The client's infrastructure** | Same as A on the client's servers | No hosting cost; data stays with the client | Depends on the client's schedule and access rules | — |

**Recommendation:** option A or C for UAT, with the proxy and service definitions kept in the repository so the same files serve production. Choose B only if the client wants cloud hosting in production, and then run the full suites against Azure SQL first.

**SQL Server licensing:** Developer Edition is licensed for development and testing only, not for production use. Confirm with the client's licensing contact whether UAT by business users is covered; Express is a safe default for UAT volumes.

**Domain and certificate:** a subdomain of a domain the owner or client controls (for example `uat.<domain>`), an A or CNAME record to the host, and an automatic certificate (Let's Encrypt through Caddy or certbot, or the platform's managed certificate). No wildcard needed.

## 4. Findings

### D-1. No pipeline and no reproducible build — High — Verified

- **Location:** `.github/` (only `modernize/` hook scripts); no `Dockerfile`, `docker-compose`, `Jenkinsfile` or deployment script anywhere outside `node_modules`.
- **Impact:** what runs in UAT cannot be tied to a commit; tests are run by hand, from a copy of the repository.
- **Recommendation:** a pipeline that, on every push: builds the backend and runs `verify` with a SQL Server service container; lints, tests and builds the frontend; runs dependency scans; publishes two versioned artefacts (jar, static bundle) with the commit hash in `/actuator/info` (the `build-info` goal already runs in the Maven build). Deployment to UAT is a manual, approved step that takes those artefacts. Nightly: the Playwright suite against UAT or a staging copy.
- **Complexity:** M.
- **Verification:** `GET /actuator/info` on UAT shows the commit that the pipeline built.

### D-2. No environment profile between development and production — High — Verified

- **Location:** `application-dev.yml`, `application-demo.yml`, `application-local.yml`; no `uat` or `prod`.
- **Impact:** the obvious way to start UAT is the documented `dev,demo` command, which disables secure cookies and enables Swagger and the testing endpoints (report 03, S-15).
- **Recommendation:** UAT runs with **no** `dev` profile. Provide `application-uat.yml` for the few differences (for example the demo seed if demo data is wanted, by adding the `demo` Flyway location explicitly) and a startup guard. Document the full list of environment variables in one place.
- **Complexity:** S.

### D-3. Migrations run with the application's own database login — High — Verified

- **Location:** one datasource (`application.yml`); see report 05, DB-1.
- **Recommendation for deployment:** run Flyway as a separate step of the release with a migration login (`spring.flyway.user/password`, or the Flyway command line), then start the application with a runtime login that cannot alter the schema. Create the database with the documented collation and set RCSI as decided.
- **Complexity:** S.

### D-4. No backup, restore or rollback procedure — High — Verified

- **Location:** no script or document; databases are in the FULL recovery model (catalog), so the transaction log grows without log backups.
- **Impact:** UAT data (the client's test scenarios, sign-offs) can be lost; a failed release cannot be undone; the log file can fill the disk.
- **Recommendation:**
  - *Backup:* nightly full database backup and log backups (or SIMPLE recovery for UAT), plus a copy of the attachment directory taken at the same time; retention 14 days.
  - *Restore drill:* restore to a second database and start the application against it; record the time taken. This closes an open release gate.
  - *Rollback:* migrations are forward-only, so a release is rolled back by restoring the pre-release backup and redeploying the previous artefacts. Take a backup immediately before every migration. Prefer additive migrations (the project already does) so the previous application version still runs on the new schema.
- **Complexity:** S to M.
- **Verification:** a dated restore-drill record in `docs/qa/`.

### D-5. No monitoring or error reporting — High — Verified

- **Location:** report 02, A-2 and report 04, P-4.
- **Recommendation for UAT:** an external check of `/actuator/health` every minute with an alert; log files rotated and kept 30 days; metrics exposed to administrators; disk-space alerts for the database, log and attachment volumes; the outbox backlog as a metric (stuck e-mail is the first thing users notice).
- **Complexity:** M (shared with A-2).

### D-6. Security headers and caching are nobody's job yet — Medium — Verified

- **Location:** no proxy configuration exists; `FrontEnd/src/index.html` has no policy meta tags; API responses carry Spring's defaults only (report 03, S-18).
- **Recommendation (proxy configuration to write):** HSTS; `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'` (test that the bundled fonts and inline component styles still load: Angular injects component styles, which needs `style-src 'self' 'unsafe-inline'` or nonce support); `Referrer-Policy: same-origin`; `X-Content-Type-Options: nosniff`; long-lived caching for hashed files (`*.js`, `*.css`, fonts) and `no-cache` for `index.html` and `/i18n/*.json` (the translation files are not content-hashed, so users must not keep stale copies after a release).
- **Complexity:** S.
- **Verification:** `curl -I` on the UAT URL; a browser check that no policy violation is reported.

### D-7. Test-user provisioning is demo-only — Medium — Verified

- **Location:** `identity/DemoUserSeeder.java` (14 accounts, one password from `DEMO_USER_PASSWORD`, printed at startup); `identity/GlobalAdminBootstrap.java` (first administrator from environment, one-shot, password policy applied); user administration with reset links exists.
- **Recommendation:** for UAT with the client's people: start without demo users or with them clearly separated; bootstrap one GLOBAL_ADMIN; create named accounts through the administration screen so each tester receives a setup link (needs working e-mail); never share one password. Keep demo accounts only in a separate demonstration instance.
- **Complexity:** S.

### D-8. Environment isolation is by convention — Low — Verified

- **Location:** instances so far differ only by port and database name on one workstation (`HANDOFF.md`).
- **Recommendation:** separate database server or at least separate logins per environment; different session cookie names are not needed if hostnames differ; a visible environment banner already exists in the shell ("Development environment") and should read "UAT".
- **Complexity:** S.

### D-9. The native authentication library is a development-only concern — Low — Verified

- **Location:** `pom.xml` copies `mssql-jdbc_auth-13.4.0.x64.dll` to `target/native`; starting the jar with integrated authentication failed on 2026-11-14 until `-Djava.library.path` was set.
- **Recommendation:** UAT uses SQL authentication (`DB_USERNAME`, `DB_PASSWORD`), which needs no native library and works on Linux. State this in the runbook.

## 5. Secrets and configuration

| Secret or setting | Variable | Where to keep it |
|---|---|---|
| Database URL, runtime login | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Host secret store or service environment file readable only by the service account; never in the repository or the pipeline log |
| Migration login | `spring.flyway.user`, `spring.flyway.password` (to add) | Pipeline secret, used only in the migration step |
| SMTP | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_MODE=smtp` | Same |
| First administrator | Bootstrap variables read by `GlobalAdminBootstrap` | Set once, then removed |
| Public URL, proxies, storage | `APP_PUBLIC_BASE_URL`, `APP_TRUSTED_PROXIES`, `STORAGE_DIRECTORY`, `MAIL_DIRECTORY` | Plain configuration |
| Assistant | `APP_ASSISTANT_MODE=disabled` | Plain configuration |

Rotate the database and SMTP passwords when UAT ends. The demo password used for local review instances must not be reused.

## 6. Release procedure to write (runbook outline)

1. Pipeline builds and tests; artefacts carry the commit hash.
2. Announce a maintenance window (a restart signs everyone out).
3. Back up the database and the attachment directory.
4. Run migrations with the migration login; check `flyway_schema_history`.
5. Deploy the jar and the static files; restart the service.
6. Smoke test: health, sign-in, open a request, one write, one e-mail, live message.
7. If the smoke test fails: stop the service, restore the backup, redeploy the previous artefacts, record the incident.

## 7. Preconditions before the first UAT user

These come from the other reports and are repeated here because they decide the go-live date:

| # | Precondition | Source |
|---|---|---|
| 1 | Files: extension tied to type; scanner decision taken (real scanner, or uploads restricted and the risk accepted in writing) | 03: S-1, S-2 |
| 2 | Client address resolution fixed before the proxy is trusted | 03: S-3 |
| 3 | Administrator takeover and self-delegation closed; missing audit events added | 03: S-4, S-5, S-6 |
| 4 | No `dev` profile; mail mode explicit | 03: S-15, S-16 |
| 5 | Two database logins; RCSI decision applied and suites rerun | 05: DB-1, DB-3 |
| 6 | Pipeline, backup and restore drill, health check and logs | D-1, D-4, D-5 |
| 7 | Request ids and basic metrics | 02: A-2 |

## 8. Not verified

- Any actual deployment: proxy behaviour with Server-Sent Events, header policies with the Angular bundle, certificate issuance.
- Behaviour on Linux (developed and tested on Windows only; the jar is portable in principle, file paths use `Path`).
- Azure SQL compatibility.
- Current prices and the client's licensing position.
- SMTP delivery (only file mode has been used).
