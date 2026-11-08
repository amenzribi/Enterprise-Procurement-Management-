# Redesign: audit and prototype comparison

Date: 2026-11-08. Status: **Stage B complete. Rollout paused until a direction is selected.** No production route, style or component has changed.

## Preview

```powershell
python -m http.server 4500 --directory "docs/design/prototypes"
```

Open <http://localhost:4500/>. The bar at the top switches direction, screen, language and sign-in treatment. Opening `index.html` directly also works. Fonts load from Google Fonts in the preview only; production would self-host them.

Direct views: add `?d=a|b|c&s=dashboard|inbox|table|detail|signin&lang=en|ar`.
Screenshots: [`prototypes/shots/`](prototypes/shots/) as `{direction}-{screen}-{desktop|mobile|ar}.png`, and `{direction}-signin-{25d|static}.png`.

## Stage A: baseline

Captured from the current application against the disposable test database: [`baseline/`](baseline/). Data there is left by test runs, so "Parallel 18" and the 2032 dates are artefacts.

Not captured: an Arabic baseline (my language switch did not take effect) and a request detail page (the click reached the new-request form, saved as `request-form-desktop.png`).

### Observed, by role

| Role | Observation | Screen |
|---|---|---|
| Approver | Each inbox card takes about 150 px, so about 4 fit on a desktop screen. Amount, wait and due sit far from the title. | `approvals-inbox-desktop` |
| Approver | Comparing requests means opening each one: the inbox has no decision context. | `approvals-inbox-desktop` |
| Approver | "My work" repeats the same items in two columns, as tasks and as notifications. | `approvals-dashboard-desktop` |
| Approver | "Due in 2,192 days" is shown with the same weight as a real deadline. | `approvals-inbox-desktop` |
| Requester | At 1440 px, body rows end before the last header column, "Last modified" (FR: "Dernière modification"), which is left empty. | `requests-list-desktop` |
| Requester | The row's primary link is the long reference number; the title is secondary. | `requests-list-desktop` |
| All, mobile | Language switch and product name compete in the header; the name truncates. Page header and controls push the first item low. | `approvals-inbox-mobile` |
| All | The status badge and action badge look alike ("Approve" in My work is a label, not a button). | `approvals-dashboard-desktop` |

### Assumptions (not tested with users)

- Approvers decide faster with amount, budget and quote comparison beside the queue.
- Most approval sessions clear several items at once.
- Buyers and managers prefer density over large display type.

## Research

Refero MCP needs sign-in, so the public gallery was used. Recorded for each useful source:

| Source | Pattern adapted | Where | User benefit |
|---|---|---|---|
| [Vercel style](https://styles.refero.design/style/f24daf3a-d43f-4dec-85a9-8ac1d5148a03) | Hairline depth, mono labels, 6 px radius | A: all | Dense data stays calm |
| [Steep style](https://styles.refero.design/style/75fdb89f-ca64-41b3-af36-7a78bd09448e) | Serif display at weight 400, one warm accent per page | B: dashboard | Clear single focus |
| [Raycast entry](https://styles.refero.design/) (gallery summary only) | Dark command surface | C: all | Keyboard-led, long sessions |
| [Brex style](https://styles.refero.design/style/b58d92f6-68a8-4358-8fc9-6ea58e6d483b) | One accent reserved for the primary action | C: Approve only | Next action is unmistakable |
| [GradientDeck, Mollie DESIGN.md](https://ui.gradientdeck.com/styles/mollie/design.md) | DESIGN.md structure only | Stage C document | Tokens have stated roles |
| [GetLayers](https://www.getlayers.ai/) | Layered depth ([cards-cascade](https://www.getlayers.ai/layer/cards-cascade)) | Sign-in | Depth without a 3D engine |
| [Webflow loaders](https://webflow.com/made-in-webflow/loader) | Minimal loaders; gallery gives no timing data | Stage C loading states | Feedback only while really waiting |

**Licensing:** nothing was reused directly; the prototypes contain original code. GetLayers states that paid plans carry a commercial licence and produces standalone HTML or Next.js, which does not suit Angular. Refero, Webflow and GradientDeck pages state no licence, and the GradientDeck file documents a real company's styling, so only its document structure is used.

**Unavailable and their fallbacks:** Refero MCP (public gallery), `getlayers` plugin (public gallery), Playwright MCP (repository Playwright with Edge), Superdesign canvas (these prototypes), Figma (not needed). Not installed: `design-steward`, `design-skills`, `design-superpowers`. `ux-superpowers:user-journey` is available but was not run; the role table above covers its concern only informally. `agent-reach` was not needed.

## The three directions

Same synthetic data and tasks in each. Unresolved finding D-01 appears on the non-conforming record `DEMO-PR-2559`, as "no next step available".

| | **A. Ledger** | **B. Atelier** | **C. Nocturne** |
|---|---|---|---|
| Idea | Precise, dense, typeset | One thing at a time, editorial | Dark command surface, one accent |
| Inbox | Split pane: queue and decision together | One item, previous/next | Expanding rows, `j`/`k` keys |
| Motion concept | Decided row slides out, 180 ms | Item lifts and fades, 320 ms | Accent ring closes the row, 240 ms |
| Type | Geist with Geist Mono labels | Newsreader serif headings, Inter | Inter |

## Comparison

Ratings are my judgement from the renders, not user testing.

| | A. Ledger | B. Atelier | C. Nocturne |
|---|---|---|---|
| **Usability** | Best for clearing a queue: 7 items and the full decision on one screen | Good focus, but no side-by-side view of the queue | Fast with keys; expanded row pushes the queue down |
| **Visual quality** | Refined and restrained; least expressive | Most distinctive and "luxury" | Striking; can feel like a developer tool |
| **Density** | High | Low | Medium to high |
| **Accessibility** | axe: 0 issues, desktop | axe: 0 issues, desktop | axe: 0 issues, desktop; dark theme needs a contrast review of status colours in real use |
| **Performance** | No heavy assets; 2 font families | Serif plus Arabic serif add font weight | 1 family; lightest |
| **Implementation cost** (estimate) | Low to medium; closest to the current structure | Medium to high: large type stresses long FR and AR text | Medium to high: a full dark token set across every module |

Checked in this preview on all three: 12 desktop screens with axe (WCAG A/AA tags, 0 violations), no horizontal overflow on desktop or mobile, approve (7→6), filter (1 row), and workflow history (4 events). Runtime performance and bundle impact were **not measured**; the prototype is not Angular code. Arabic text is mine and needs native review. axe was not run on mobile, Arabic, or sign-in views.

### Sign-in

The 2.5D treatment is CSS perspective on three stacked sheets, with no WebGL, disabled under reduced motion. The static version removes the back sheets. **Recommendation: static**, or the CSS version at most. Sign-in is a brief, repeated step, and a 3D engine would add load for no task benefit.

## Recommendation

**A. Ledger.** It best serves your stated priority of finding pending approvals, comparing records and seeing the next action, and it costs the least to apply across every module. Its weakness is that it is the least expressive. If "luxury" matters more than density, B is the alternative, and I would keep A's split pane in it.

## Decision needed

Choose A, B or C, and static or 2.5D sign-in. Stage C will not start before that.
