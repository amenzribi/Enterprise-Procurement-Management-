# ADR-018 — UAT hosting on Google Cloud: host, proxy, database, backups, monitoring, mail

| | |
|---|---|
| Status | **Proposed** (2026-11-18). The owner chose Google Cloud as the preferred UAT provider, using the free trial; this ADR is for approval. **Nothing provisioned.** |
| Date | 2026-11-18 |
| Sprint | 2 (infrastructure validation), phase P0 |
| Related | ADR-001 (SQL Server only), ADR-003 (no new infrastructure without an ADR), ADR-013, ADR-014, ADR-015; `docs/audit/08-deployment-readiness.md`; `docs/sprints/sprint-02-plan.md` |

## Context

Sprint 2 needs an isolated, HTTPS-only UAT environment with a dedicated SQL Server database, no real production data,
fail-closed virus scanning, off-host backups, monitoring with alerts, external testers, and a separate load generator
for up to 100 concurrent users. The application runs as one node (sessions, rate limits and live streams are in
memory). The owner wants to start on Google Cloud's free trial.

Facts that constrain the design (checked 2026-11-18; recheck on the day of provisioning):

- **Free trial:** USD 300 of credit, valid 90 days; it ends when the credit is used or after 90 days, whichever comes
  first. During the trial: no Windows Server images, no GPUs, no quota increases. If the account is not upgraded,
  the billing account is closed, resources are stopped and their data is marked for deletion.
- **Compute Engine blocks outbound port 25**; mail must go through a relay on 587 or 465.
- **SQL Server on Linux** supports Express and Developer editions (the CI already runs `mssql/server:2022` on Linux).

## Decision (proposed)

### Topology

```
Testers (allow-listed) ──HTTPS──> Caddy on the UAT VM (TLS, headers, static Angular, /api, /actuator/health)
                                        │ loopback
                                        ▼
                                  Spring Boot jar (systemd, Java 21)  ── management port, private only ──> Ops Agent
                                        │ loopback                        │ loopback
                                        ▼                                 ▼
                                  SQL Server 2022 (container)          clamd (loopback)
                                        │
                     nightly backup ──> Cloud Storage bucket (other region, versioned, retention) ──> owner's copy
Load generator VM (k6), only during P5, same region, removed afterwards
```

| Element | Choice | Why |
|---|---|---|
| Region | One EU region (europe-west1 Belgium or europe-west9 Paris) | Close to the users; no real data, but keep EU residency as the production default |
| UAT host | One Compute Engine VM **e2-standard-4** (4 vCPU, 16 GB), Ubuntu LTS, 100 GB balanced persistent disk, static IP | The trial forbids Windows; 16 GB holds SQL Server (about 6 GB), the JVM (2 to 3 GB), clamd (about 1.5 GB, double while signatures reload) and the OS. 2 vCPU or 8 GB is too small for a 100-user test |
| Database | SQL Server **2022**, same major version as CI and development, in the official container with a volume on the persistent disk; listening on loopback only. Edition: decision D8 | ADR-001; Cloud SQL for SQL Server would roughly double the monthly cost and consume the trial credit in about five weeks |
| Database identities | ADR-015: migration identity used only by the release step; runtime identity without schema rights, insert-only on the audit trail | Least privilege (owner decision D3) |
| Reverse proxy | Caddy: automatic Let's Encrypt certificate, security headers, server-sent events unbuffered, 21 MB upload limit | One binary, automatic certificates; configuration kept in the repository |
| Access | Google Cloud firewall: 443 only, from the testers' allow-listed addresses (D11); SSH only through Identity-Aware Proxy TCP forwarding, no public SSH | Password-only sign-in until SSO and MFA |
| Virus scanning | clamd and freshclam on the VM, loopback only; fail-closed (ADR-013 as tightened) | Owner decision D2 |
| Secrets | Secret Manager, read at start by the VM's service account; nothing in the image, repository or logs | Free at this volume |
| Backups | Nightly full backup and transaction-log backups by a systemd timer (Express has no SQL Agent; the same timer works for Developer); attachments and quarantine archived with them; upload to a Cloud Storage bucket in another region with versioning and a retention policy; weekly disk snapshot | Off-host; RPO 24 h (D6) |
| Monitoring | Cloud Monitoring with the Ops Agent (host metrics, application logs, Prometheus scrape of the private management port), an uptime check on `/actuator/health`, alerting by e-mail | Survives the loss of the VM; no extra service to run on it |
| Mail | A transactional SMTP relay on port 587 (D9), sender on a domain the owner controls | Testers receive their own set-up links; port 25 is blocked |
| Load generator | e2-standard-2 Spot VM in the same region, created for each P5 session and deleted afterwards | Keeps load generation off the system under test; costs cents per hour |

### Cost (indicative; third-party price lists, to confirm in the Google Cloud pricing calculator)

| Item | Monthly, 24/7 |
|---|---|
| e2-standard-4, europe-west1 (about USD 0.147 per hour; us-central1 about 0.134) | about 108 |
| 100 GB balanced persistent disk | about 10 to 12 |
| Static external IPv4 address | about 4 |
| Cloud Storage backups (tens of GB, versioned) | under 3 |
| Weekly disk snapshots | about 3 to 5 |
| Monitoring, logging, uptime check, alerting (within or near the free allotments) | 0 to 5 |
| Secret Manager | about 0 |
| Load generator, only during P5 (Spot, about 20 hours) | about 1 to 3 |
| **Total** | **about 130 to 140 USD per month, about 4.5 USD per day** |

Outside the credit: the domain name (about 10 to 15 USD a year, at any registrar) and the mail relay if its free
tier is exceeded.

**Credit:** 30 days uses about 135 USD; 60 days about 270 USD, leaving about 30 USD of margin; the credit runs out
around day 65 to 70 at this footprint. Stopping the VM outside agreed test hours (disk and IP still charged) brings
the month to about 70 to 80 USD and makes 60 days comfortable and 90 days possible.

### Billing and trial safeguards

1. A budget of USD 300 on the billing account with alerts at 50, 75 and 90 percent, to the owner.
2. **The account stays a trial account**: upgrading to a paid account is an explicit owner decision, because from
   then on charges are real.
3. Weekly check of the credit balance in the checkpoint files.
4. Labels on every resource (`env=uat`, `project=procurement`), so a single filter lists and deletes them.

### Shutdown and export, before the credit or the 90 days end

| When | Action |
|---|---|
| Day 45 or 75 % of credit, whichever first | Owner decides: continue on a paid account, move to another host, or stop |
| Ten days before the end | Final backup; restore it once elsewhere (a local SQL Server) to prove the export; download the backups, attachments, Caddy and service configuration and the evidence to storage the owner keeps outside Google Cloud |
| End | Delete the VMs, disks, snapshots, bucket, IP and secrets; confirm the billing report shows nothing running |

## Alternatives considered

| Option | Not chosen because |
|---|---|
| Cloud SQL for SQL Server | About 100 to 130 USD a month more for the instance and storage; trial credit would last about five weeks; managed backups are attractive for production and remain an option there |
| Windows Server VM | Not available during the free trial |
| Self-hosted Prometheus and Grafana on the VM | Takes memory from the system under test and dies with it |
| Identity-Aware Proxy with an HTTPS load balancer in front of the application | Stronger access control with Google accounts, but about 18 to 25 USD a month more and testers need Google identities; reconsider if allow-listing proves impractical |
| A mail sink readable by all testers | Every tester could read every set-up link and take over any account; acceptable only for the internal performance environment |

## Consequences

- The first UAT runs on Linux and with SQL Server authentication: the integrated-authentication native library is
  not used there (development-only, audit D-9).
- The single-VM design matches the single-node application; it is not the production design.
- Everything is reproducible from files in the repository (`deploy/uat/`), so the environment can be rebuilt on
  another provider if the trial ends.

## Approval needed

This ADR as a whole; then, separately, the creation of the Google Cloud trial account and of each resource (P2).
