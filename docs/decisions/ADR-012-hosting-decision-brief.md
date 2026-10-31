# ADR-012 — Hosting decision brief (for Mohamed)

| | |
|---|---|
| Related | [ADR-012-ai-assistant-hosting.md](ADR-012-ai-assistant-hosting.md) |
| Date | 2026-10-31 |
| Status | **Decision required** — no option selected |

## What must be decided

Choose one production posture for the internal procurement assistant:

| Option | Data leaves application? | Ops / hardware | Credentials | Fit to on-prem / private-cloud vision |
|---|---|---|---|---|
| **A. Self-hosted model endpoint** | Only if the endpoint is outside the app trust boundary; can stay private-network | GPU/CPU capacity, patching, model updates, ownership | Endpoint URL (+ optional key) in secret store | Strongest alignment |
| **B. Approved private/managed service** | Prompts and retrieved context go to vendor region/VPC | Vendor SLA; egress allow-list | Vendor API key in secret store | Needs DPA, residency, retention, logging review |
| **C. Remain demo/disabled** | None | None | None | Current safe default |

Until A or B is approved **and** configured, `app.assistant.mode=live` must stay unavailable (no silent fallback).

## Already implemented (no decision needed)

- Provider-neutral `AssistantModel` port
- Default `disabled`; demo profile uses labelled deterministic adapter
- Server-side scoped retrieval; no write tools; drafts not auto-sent
- Secrets out of git; rate/size/timeout bounds

## What remains after a live provider is approved

1. Wire a single approved adapter behind `AssistantModel` (no second path).
2. Configure secrets, network allow-list, timeouts, retention.
3. Re-run security tests: isolation, injection samples, draft audience, rate limits.
4. **Quality** checks EN/FR/AR with real answers (demo text does **not** count).
5. Operational runbook: who owns the endpoint, rotate keys, incident response.
6. Independent review / pen-test of the live path before production exposure.

## Explicit non-decisions

- Do not treat demo responses as evidence of live-model quality or injection resistance.
- Do not add OCR / autonomous actions under this decision.
