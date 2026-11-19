# Sprint 2 execution plan: infrastructure validation and Audit Center

**Status:** for the owner's approval, 2026-11-18; updated the same day for the Google Cloud hosting choice (section 5, ADR-018). Nothing is started or provisioned. **Base:** `feat/internal-messaging` at `72a2280`
(CI run 44276571868 green: backend 302/302, frontend 255/255). **Out of scope:** the AI assistant, until
infrastructure validation is complete; any production deployment; merging into `main` without approval.

## 1. Owner decisions (provisional, 2026-11-18) and what they imply

| # | Decision | Consequence for the plan |
|---|---|---|
| D1 | UAT: isolated environment, HTTPS, dedicated SQL Server database, no real production data | The host itself is still open (D1a). With no demo profile allowed in UAT, accounts and organisation data must be set up through the normal administration path (P2.6) |
| D2 | ADR-013: ClamAV, quarantine, **fail-closed** | Stricter than the earlier ADR plan, which let a deployed server start without a scanner if `APP_ALLOW_UNSCANNED_DOWNLOADS=true`. Corrected: in `uat`, `staging`, `preprod` and `production` the scanner is mandatory and that flag is refused |
| D3 | ADR-015: separate migration and runtime identities, least privilege | The runtime identity can **insert** audit events but never update, delete or truncate them, nor change the schema (`scripts/db/grant-application-roles.sql`). Needs SQL Server in mixed authentication mode on the UAT server |
| D4 | ADR-014: snapshot isolation tested on a disposable copy only | No change to any database people use without a separate approval |
| D5 | 100 concurrent users, p95 < 1 s for common API operations, server errors < 1 % | Replaces the audit's provisional thresholds. Proposed definitions in P5 (what "common operation" and "server error" mean) |
| D6 | RPO 24 h, RTO 4 h, subject to a restore drill | Daily full backups are enough for RPO 24 h; the drill must show a restore within 4 h |
| D7 | Global Administrator Audit Center in Sprint 2 | Reuses the existing audit chain (section 3) |

## 2. Review of the earlier draft: contradictions and gaps found

| # | Finding | Resolution in this plan |
|---|---|---|
| G1 | **Fail-closed contradiction** (D2) | P1.1: startup guard refuses a deployed environment without a scanner, and refuses `APP_ALLOW_UNSCANNED_DOWNLOADS=true` there |
| G2 | **Draft said "runtime login without audit-table rights"**: wrong, it must append | P2.3 wording: insert only, no update or delete, no schema change |
| G3 | **Monitoring cannot read the metrics**: they exist but only for a signed-in global administrator, and there is no Prometheus format | P1.4: Prometheus endpoint on a separate management port bound to the private network only; no session needed, not reachable from outside |
| G4 | **ADR-003** requires an ADR for new infrastructure: the reverse proxy, the monitoring stack and the UAT host are not covered by an ADR | P0.2: ADR-018 (UAT hosting, proxy, monitoring, backups) for approval |
| G5 | **UAT accounts**: the demo seeder cannot run in UAT; accounts need the two-administrator bootstrap and two-person activation (F-1, F-2), and set-up links need real e-mail | P2.5: SMTP relay or mail sink for UAT (decision D9); P2.6: provisioning runbook |
| G6 | **SQL Server Express has no SQL Agent**: scheduled backups need an operating-system scheduler | P3: backup scripts run by the OS scheduler, or Standard edition (cost) |
| G7 | **Chain verification does not fit the database limits**: `AuditLog.verifyAll` checks every chain in one read-only transaction; the limit is 30 s per transaction (`application.yml`) | P1.3: verification in batches per chain, resumable, run in the background |
| G8 | **No index for a group-wide, time-ordered audit list** (only by entity and by company) | P1.3: one Flyway migration adding an index; reviewed like any schema change |
| G9 | **Load tests from this laptop are unreliable** (the browser runs showed 60 to 100 % CPU from other applications) | P5: k6 runs from a separate machine |
| G10 | **Branch strategy**: Sprint 1 is not merged into `main` | Decision D10 |
| G11 | **UAT is password-only** (no SSO or MFA until later), and global administrators can still set temporary passwords (ADR-016 not applied) | P2.2: UAT reachable only through an IP allow-list or VPN (decision D11); ADR-016 offered as an option |

## 3. Existing audit infrastructure (reused by the Audit Center)

- `audit_event`: append-only (trigger `trg_audit_event_append_only`), hash-chained per company and one global chain
  (`audit_chain_head`, `chain_key`, `chain_seq`, `previous_hash`, `hash`), with actor, company, entity, action,
  statuses, comment and JSON payload. Indexes: by entity and by company and time.
- `AuditLog`: `record` (append under the chain-head lock), `verifyChain`, `verifyAll`, `listForEntity`,
  `firstActor`. Verification has no endpoint today; the only reader in the product is the request audit pack (PDF).
- Everything Sprint 1 added writes here: sign-ins, account creation and activation, role grants, set-up links,
  bootstrap, four-eyes refusals are not events (they change nothing).

## 4. Phases, in order, with acceptance criteria and checkpoints

Each phase ends with a checkpoint in `STATUS.md`, `HANDOFF.md` and `docs/sprints/sprint-02-progress.md` (checks
actually run, with results), and code phases end with backend and frontend suites green locally and in CI. Commits
and pushes follow the same rule as Sprint 1: shown to the owner first.

### P0. Decisions and ADR (no code)

| Step | Acceptance |
|---|---|
| P0.1 Owner decisions D1a and D8 to D12 (section 5) | Recorded |
| P0.2 ADR-018 (`docs/decisions/ADR-018-uat-hosting-google-cloud.md`): Google Cloud UAT host, proxy, database, backups, monitoring, mail | Approved by the owner |
| P0.4 Owner creates the Google Cloud trial account (only the owner can accept its terms), a project, a USD 300 budget with alerts at 50, 75 and 90 %; the vCPU and IP quotas are read in the console | Budget alerts reach the owner; quotas allow 6 vCPU (UAT 4 + load generator 2) |
| P0.5 Domain for UAT (D16), SQL Server edition licensing confirmed (D8) | Recorded |
| P0.3 Load-test workload definition (P5.1) agreed | Recorded with the client's numbers if available |

### P1. Application readiness (code; local and CI only, no infrastructure)

| Step | Acceptance criteria |
|---|---|
| P1.1 **ClamAV, fail-closed** (ADR-013 steps 2 to 7): `ClamAvScanner` over `INSTREAM` with timeouts; any scanner error refuses the upload (503); startup refused in a deployed environment without `APP_CLAMAV_HOST`, or with `APP_ALLOW_UNSCANNED_DOWNLOADS=true`; files stored `CLEAN` before Sprint 1 rescanned once, audited; scanner health and refused-scan metric | Unit tests against a fake `clamd` (clean, infected, size limit, timeout, refused connection); guard tests for each deployed environment value; EICAR test against a real `clamd` where available; quarantined files never downloadable |
| P1.2 **Two database identities in CI** (ADR-015 steps 6 and 7): CI creates the migration and runtime logins and runs the suite as the runtime login; Flyway runs as a separate release step | CI green with the runtime login; `verify-runtime-permissions.sql` all PASS, including "cannot update or delete `audit_event`" and "cannot alter the schema" |
| P1.3 **Audit Center** (D7): API for global administrators: filter by period, company, actor, action, entity, with keyset pagination; CSV export; chain verification in batches per chain, in the background, with progress and result; index migration (G8); screen in the administration area | Global administrators only: company administrators and other roles 403, unauthenticated 401 (authorization matrix lines added); no write action exists; each search export and each verification run is itself an audit event; a deliberately altered event in a test database is reported with its id; verification of 1 million events completes without exceeding the 30 s transaction limit; EN, FR, AR keys with parity; loading, empty and error states; keyboard accessible; right-to-left layout |
| P1.4 **Monitoring endpoint** (G3): Prometheus format on a management port bound to a private address; health details for administrators; alerts' inputs (errors, latency, pool, streams, outbox, scans, lock failures with SQL error number) | Management port not reachable through the public proxy (test); public port exposes only `/actuator/health`; metrics list documented |
| P1.5 **ADR-014 on a disposable copy** (D4): read-then-write review of unversioned tables, full suites against a copy with snapshot isolation, a test proving reads no longer wait for writers | Results recorded; no database option changed outside the copy; recommendation for the owner |

**Checkpoint P1:** backend and frontend suites green locally and in CI; browser suite once uninterrupted
(CI or a quiet machine); independent review of P1.1 and P1.3.

### P2. UAT environment (needs D1a, ADR-018 and host access)

| Step | Acceptance criteria |
|---|---|
| P2.1 Compute Engine VM per ADR-018 (e2-standard-4, Ubuntu LTS, EU region, labels), built from versioned files in `deploy/uat/` (Caddy, systemd units, SQL Server container, Ops Agent, backup timer) | Rebuild from the repository documented and repeatable; every resource labelled |
| P2.2 Network: firewall opens 443 only, to the allow-list (D11); SSH only through Identity-Aware Proxy; Caddy with a Let's Encrypt certificate, security headers, `/api` and `/actuator/health` only | External scan: TLS grade, headers present; ports 1433, 3310 (clamd), the management port and 22 closed from the internet |
| P2.3 SQL Server: dedicated UAT database; mixed authentication; migration and runtime identities (ADR-015 steps 1 to 5); migration password never in the running application's environment | `verify-runtime-permissions.sql` all PASS on the UAT database |
| P2.4 ClamAV installed (`clamd` on loopback, signature updates) | EICAR upload refused and quarantined; clean upload `CLEAN`; with `clamd` stopped, uploads refused with 503 (fail-closed) |
| P2.5 Mail through a relay on port 587 (D9; Compute Engine blocks port 25); secrets in Secret Manager read by the VM's service account (D12) | Link received by a test mailbox; no secret in the repository, the VM image or the logs |
| P2.6 Provisioning without the demo profile: two global administrators by bootstrap, organisation and users through the administration screens or import, all synthetic | Every account created through two-person activation; no real personal data |

**Checkpoint P2:** release runbook followed once; smoke test of the main journey through the proxy.

### P3. Backup and restore (D6)

| Step | Acceptance criteria |
|---|---|
| P3.1 Nightly full and transaction-log backups by a systemd timer; attachments and quarantine archived with them; uploaded to a versioned Cloud Storage bucket in another region with a retention policy; weekly disk snapshot | Backups on schedule for 3 consecutive days; retention written down; a missed backup raises an alert (P4) |
| P3.4 Export before the trial ends (ADR-018 shutdown plan): backups, attachments, configuration and evidence copied to storage the owner keeps outside Google Cloud, and restored once outside it | Restore outside Google Cloud succeeds |
| P3.2 **Restore drill** onto a separate database and storage | Completed within 4 h (RTO), timed; data no older than 24 h (RPO); row counts match; **every audit chain verifies** (Audit Center); the restored application starts and a user can sign in |
| P3.3 Rollback procedure for a release (migrations forward only) | Followed once |

### P4. Monitoring and alerting

| Step | Acceptance criteria |
|---|---|
| P4.1 Cloud Monitoring with the Ops Agent (host metrics, application logs, Prometheus scrape of the private management port); uptime check on `/actuator/health` | Dashboard: request rate, 5xx, p95 latency, connection pool, JVM, open streams, outbox backlog, scans, lock failures |
| P4.4 Billing: credit balance and budget alerts | Balance recorded at each checkpoint; alerts tested |
| P4.2 Alerts: application down, 5xx rate, disk space, backup failure, certificate expiry, scanner down, deadlocks or lock timeouts | Each alert triggered once on purpose and received by the named recipient |
| P4.3 Log retention and access | Retention agreed; logs contain request ids and SQL error numbers, never passwords, tokens or file contents |

### P5. Performance (D5)

| Step | Acceptance criteria |
|---|---|
| P5.1 Workload definition: "common API operations" = request list and detail, approval inbox and decision, purchasing queue, sign-in, receipt view; mix and think times agreed | Written and approved |
| P5.2 Synthetic seed at agreed volumes; k6 scripts in `perf/`; load generated from an e2-standard-2 Spot VM in the same region, created for each session and deleted afterwards (G9) | Reproducible from a clean database; the generator's own CPU stays under 70 % (otherwise the results measure the generator) |
| P5.3 Baseline (10 users), then 100 concurrent users, then a 2 h soak | At 100 users: p95 < 1 s for each common operation; server errors (5xx) < 1 % of requests, with **no 500 at all** and 503 `busy` counted separately; no deadlock in the SQL Server reports, or each analysed; heap and connections stable during the soak |
| P5.4 Overload test (beyond 100 users) | Capacity figure recorded; overload answered with clean 503, never 500 |
| P5.5 ADR-014 measured on a copy with the same workload (D4) | Recommendation with numbers; no change without approval |

### P6. Closure

| Step | Acceptance criteria |
|---|---|
| P6.1 Independent security review of P1 to P4 and the deployed configuration | No Blocker or Major open |
| P6.2 UAT GO/NO-GO report; then, and only then, the AI assistant can be planned | Owner decision recorded |

## 5. Google Cloud feasibility (owner's choice, 2026-11-18)

Detail and diagrams: `docs/decisions/ADR-018-uat-hosting-google-cloud.md`. Prices are from third-party price lists
(checked 2026-11-18) and must be confirmed in Google's pricing calculator before provisioning.

### 5.1 Fit with the stack

| Component | On Google Cloud | Note |
|---|---|---|
| Angular 22 | Static files served by Caddy on the VM | Same origin as the API, as the application requires |
| Spring Boot, Java 21 | Jar as a systemd service on the VM | One node, as the application requires |
| SQL Server | SQL Server 2022 on Linux, official container, on the VM | Windows images are not allowed during the trial; Linux supports Express and Developer |
| ClamAV | clamd and freshclam on the VM, loopback | About 1.5 GB of memory, double during signature reloads |
| HTTPS reverse proxy | Caddy, Let's Encrypt | Needs a domain name (D16) |
| Monitoring and logs | Cloud Monitoring, Ops Agent, Cloud Logging | Off-host; alerts by e-mail |
| Application audit trail | In the database (Audit Center, P1.3) | Google's own audit logs cover the cloud account, not the application |
| Off-host backups | Cloud Storage bucket in another region | Plus a copy outside Google Cloud before the trial ends |
| External testers | Firewall allow-list on 443 | Or Identity-Aware Proxy with a load balancer (about 18 to 25 USD a month more) |
| Load generation, 100 users | Spot VM in the same region, only during P5 | Not on the UAT VM, not on the laptop |

### 5.2 VM size

| Machine | vCPU, memory | About USD per month, 24/7, europe-west1 | Verdict |
|---|---|---|---|
| e2-standard-2 | 2, 8 GB | 54 | Too small: SQL Server, JVM and clamd do not fit 8 GB with room for 100 users |
| e2-highmem-2 | 2, 16 GB | 72 | Enough memory, but 2 vCPU makes the 100-user test measure CPU starvation |
| **e2-standard-4** | **4, 16 GB** | **108** | **Recommended** for UAT and the load test |
| n2-standard-4 | 4, 16 GB | 155 | Steadier CPU for performance figures; worth it only if production will run on dedicated cores |
| e2-standard-8 | 8, 32 GB | 215 | Not needed; burns the credit in about six weeks |

### 5.3 Cost and credit

About **130 to 140 USD per month** running 24/7 (VM 108, disk 10 to 12, IP 4, backups and snapshots 6 to 8,
monitoring 0 to 5, load generator 1 to 3). Outside the credit: the domain (about 10 to 15 USD a year) and the mail
relay beyond its free tier.

| Period | Credit used (24/7) | Credit used (VM stopped outside test hours) |
|---|---|---|
| 30 days | about 135 USD | about 75 USD |
| 60 days | about 270 USD (30 USD margin) | about 150 USD |
| 90 days | exceeds 300 USD around day 65 to 70 | about 225 USD |

**Verdict:** 30 days is comfortably covered; 60 days is covered only with little margin at 24/7, and comfortably if
the VM is stopped outside agreed test hours. The trial also ends at 90 days whatever is left.

### 5.4 Trial restrictions and billing risks

| Restriction or risk | Handling |
|---|---|
| USD 300, 90 days, ends at whichever comes first; then resources are stopped and data marked for deletion | Shutdown and export plan (ADR-018), decision point at day 45 or 75 % of credit |
| No Windows Server images, no GPUs, no quota increases | Linux host; total of 6 vCPU, to be checked against the account's quota in the console (P0.4) |
| Upgrading to a paid account makes charges real | Not done without the owner's explicit decision; budget alerts at 50, 75, 90 % |
| Outbound port 25 blocked | Relay on port 587 (D9) |
| Resources left running after tests (load generator, snapshots) | Labels, a weekly cost check in the checkpoints, the load generator deleted after each session |
| A paid upgrade by mistake | The account owner is the only billing administrator; budget alerts also after any upgrade |

### 5.5 SQL Server edition for a non-production UAT

| | Express | Developer |
|---|---|---|
| Licence | Free, including production use: no licensing question | Free for development and testing only. Its licence lets end users perform acceptance tests; anything beyond acceptance testing counts as production. Using it for business testers is described as a grey area by Microsoft's own forum: **confirm with the licensing contact** (D8) |
| Linux | Yes | Yes |
| CPU | Lesser of 1 socket or 4 cores | No edition limit (2022 Developer has the Enterprise feature set; 2025 adds Standard Developer and Enterprise Developer) |
| Memory | Buffer pool 1,410 MB | No edition limit |
| Database size | 10 GB per database (2022); 50 GB (2025) | No edition limit |
| Scheduled jobs | No SQL Agent (systemd timer instead) | SQL Agent available; the plan uses the timer anyway, for one procedure |
| Performance-test validity | **Weak**: at 100 users the 1.4 GB cache is the bottleneck, so the results describe Express, not the production edition | **Good**, if the Developer variant matches the planned production edition (Standard features: 2025 Standard Developer; with 2022, results may flatter a production Standard edition) |
| UAT volumes (synthetic) | Fits in 10 GB | Fits |

**Recommendation:** Developer for UAT and the load test, once the licensing contact confirms acceptance testing by
business testers; otherwise Express for UAT and a separate, internal Developer instance for P5. The engine stays
SQL Server (ADR-001); the version stays 2022, the version used by CI and development, unless the owner decides
otherwise (D17).


## 6. Decisions: status after the Google Cloud choice

| # | Decision | Proposed | Can be approved now? |
|---|---|---|---|
| D1a | UAT host | Google Cloud trial, one e2-standard-4 VM, Ubuntu LTS, one EU region (europe-west1 or europe-west9) | **Yes**, with ADR-018 |
| D8 | SQL Server edition | Developer (2022), subject to licensing confirmation; fallback Express for UAT plus internal Developer for P5 | **No**: needs the licensing contact's answer |
| D9 | UAT e-mail | A transactional relay on port 587 with a free tier, sender on the UAT domain; no shared mail sink for external testers (anyone could read any set-up link) | Yes in principle; the provider is chosen in P0 |
| D10 | Branch strategy | Merge Sprint 1 into `main` by pull request after your review, then a `sprint-2` branch from `main` | **Yes**: needs only your review of the pull request |
| D11 | UAT access | Firewall allow-list of testers' addresses on 443; SSH only through Identity-Aware Proxy. If testers' addresses change too often: Identity-Aware Proxy with a load balancer (about 18 to 25 USD a month more) | Yes for the allow-list; the fallback needs a separate approval |
| D12 | Secrets | Secret Manager, read by the VM's service account | **Yes** |
| D13 | ADR-016 (reset links only) in Sprint 2 | Include, in P1 | **Yes** |
| D14 | External anchoring of the audit chain heads | Defer; residual risk recorded | **Yes** |
| D15 | Monitoring | Cloud Monitoring with the Ops Agent, uptime check, alerts by e-mail | **Yes**, with ADR-018 |
| D16 | Domain for UAT | A subdomain of a domain you control (for example `uat.<domain>`) | **No**: needs a domain you own or will buy |
| D17 | SQL Server version | Stay on 2022 (CI and development) | Yes; moving to 2025 would be a separate decision |
| D18 | Trial account and billing | You create the trial account and budget alerts; no upgrade to a paid account without a separate decision | **Your action** at P0.4 |

## 7. Costs

Google Cloud, indicative: **about 130 to 140 USD per month at 24/7 (about 4.5 USD per day), covered by the USD 300
credit for about 65 to 70 days**, or about 70 to 80 USD per month if the VM is stopped outside test hours (section
5.3). Outside the credit: domain about 10 to 15 USD a year; mail relay free tier. Software (ClamAV, Caddy, k6, SQL
Server Express or Developer) costs nothing.

## 8. Remaining security risks after Sprint 2

| Risk | Status |
|---|---|
| Password-only sign-in until SSO and MFA | Reduced by access restriction (D11); SSO and MFA before production |
| Two colluding administrators | Residual by design of four eyes |
| Audit chain heads not anchored outside the database | Residual unless D14 |
| Temporary passwords set by global administrators | Closed if D13 |
| One application node (sessions and streams in memory) | Accepted for UAT; recorded for production |
| Trial ends or credit runs out before validation is complete | Decision point at day 45 or 75 % of credit; export plan; environment rebuildable from `deploy/uat/` elsewhere |
| Developer edition used beyond acceptance testing | Licensing confirmed first (D8); synthetic data only; UAT never used for real business |

## 9. Sequence and checkpoints

1. **P0** decisions, ADR-018, trial account and budget alerts (owner), domain, licensing answer → checkpoint, owner approval.
2. **P1** application readiness (code, CI) → checkpoint, review, commit and push after approval.
3. **P2** UAT environment → checkpoint, smoke test.
4. **P3** backup and restore drill → checkpoint.
5. **P4** monitoring and alerts → checkpoint.
6. **P5** performance → checkpoint, ADR-014 recommendation.
7. **P6** security review and UAT GO/NO-GO → owner decision.

P1 needs no infrastructure and can start as soon as the P0 decisions it depends on (D10, D13) are approved; P2 to P5 need the trial account, the domain and the licensing answer. To make the most of the 90-day trial, the account should be created only when P1 is finished, so the credit is not consumed while code is being written.
