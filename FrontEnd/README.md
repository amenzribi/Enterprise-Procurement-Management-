# Procurement Intelligence — web application

Angular 22 frontend of Enterprise Procurement Intelligence. Setup, start-up and test commands for the whole
project are in the [root README](../README.md); conventions are in [CLAUDE.md](../CLAUDE.md).

Quick reference (PowerShell, from this folder):

| Task | Command |
|---|---|
| Install from the lockfile | `npm ci` |
| Run with the API proxy (backend on port 8080) | `npm start`, then open http://localhost:4200 |
| Lint | `npm run lint` |
| Unit tests (Vitest, single run) | `npm test` |
| Production build | `npm run build` |

Translations live in `public/i18n/en.json` and `public/i18n/fr.json`; a unit test fails if their keys or
placeholders differ.
