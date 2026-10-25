# Tooling and Resource Evaluation

Evaluated 2026-10-24 on the development laptop (Windows 11, VS Code). Only Angular-compatible resources are used in
the product; nothing React-based is installed.

## Development environment (verified)

| Tool | Version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.12 LTS | Spring Boot 4.1 supports 17–27 |
| Maven | via Maven Wrapper in `BackEnd/` | No global Maven needed |
| Node.js / npm | 24.20.0 / 11.19.0 | Angular CLI 22 requires Node ^22.22.3 or ^24.15.0 |
| Angular CLI | via `npx` / project dependency | No global install needed |
| SQL Server | 2025 (17.0.1000.7) Developer Edition, service `MSSQLSERVER`, TCP 1433, Windows authentication only | `sqlcmd` available |
| Docker | Not installed | Tests use a local `procurement_test` database instead of Testcontainers |
| Git | 2.55 | Repository initialised in Sprint 0 |

## Requested resources

| Requested | Finding | Decision |
|---|---|---|
| GSAP | Available as a skill (`cinematic-gsap-lenis-motion-system`); library is framework-agnostic | Not used: enterprise UI needs restrained CSS transitions, not cinematic motion (ADR-004). |
| "lemnis" | Interpreted as **Lenis** (smooth scrolling, paired with GSAP), not Motion. Motion Dev MCP failed to connect. | Not used: scroll hijacking harms dense tables and accessibility. |
| React Bits | React-only component collection | Not used; at most visual inspiration. |
| 21st.dev | Skill present; MCP tools not connected (needs `API_KEY_21ST`); catalogue is React | Not used in Sprint 0. Optional design inspiration if a key is configured. |
| Context7 | Not installed | Versions verified against start.spring.io metadata and the npm registry; docs from official sources. |
| "graphily skill UI" | Not found (possibly "graphify") | Unresolved. |
| UI/UX Pro | Available (`ui-ux-pro-max`) | Used for frontend design guidance, with `frontend-design`. |
| claude-mem | Not installed; Claude Code has built-in memory | Not needed. |
| "strike" | Not found; possibly **Stryker** (mutation testing) | Unresolved; candidate for QA hardening later (StrykerJS / PIT for Java). |
| find-skills | Available | Not needed: the session's installed-skill list was reviewed directly. |
