# Procurement 360 design system: Ledger

Selected 2026-11-08 from three prototypes ([comparison](docs/design/prototype-comparison.md)). Tokens live in `FrontEnd/src/styles/_tokens.scss`. Components use tokens only.

## Principles

1. **The next action is the loudest thing on the screen.** The green accent is for interaction, so a primary button always means "this is what you can do now".
2. **Hairlines, not shadows.** Depth comes from 1 px lines. Only things that float above the page (dialogs, menus, toasts) get a shadow.
3. **Dense but calm.** Show enough to decide without opening the record: amount, requester, wait, due date.
4. **Status is a dot and a word.** Meaning is in the text; colour reinforces it.
5. **Never imply a path that does not work.** See "Unresolved workflow states" below.

## Tokens

| Role | Token | Value |
|---|---|---|
| Page | `--color-paper` | `#fafafa` |
| Surface | `--color-surface` | `#ffffff` |
| Row hover, selected | `--color-surface-sunken` | `#f2f2f2` |
| Hairline | `--color-line` | `#e6e6e6` |
| Input border | `--color-line-strong` | `#cfcfcf` |
| Text | `--color-text` | `#171717` |
| Secondary text | `--color-text-muted` | `#5c5c5c` |
| Interaction only | `--color-accent` / `-hover` | `#0b6b5e` / `#08564b` |
| Focus ring | `--color-focus` | `#2f5fb3` |
| Status | `--color-success`, `-warning`, `-danger`, `-info`, `-neutral` | see tokens file |

**Typography.** Geist Variable for interface text. Geist Mono Variable for reference numbers and field labels only. IBM Plex Sans Arabic for Arabic. All are self-hosted. Sizes run from 12 px metadata to 28 px page titles. Weight 500 is the heaviest used for headings. Tracking is removed in Arabic, where it breaks letter joining.

**Spacing and shape.** 4 px grid (`--space-1` to `--space-12`). Radius 6 px for controls, 8 px for containers, pill only for counts on collapsed navigation.

**Density.** Queue rows carry reference, title, amount and wait. Tables use 12 px mono headers and 13 px cells.

**Focus.** 2 px blue ring, 2 px offset. It is blue so that it never reads as the green action colour.

**Motion.** 120 ms for hover and colour, 180 ms for state change, standard easing `cubic-bezier(0.2, 0, 0, 1)`. Motion answers an action. The one signature movement is a decided row sliding out of the queue. Direction is mirrored in Arabic. `prefers-reduced-motion` removes it.

**Loading.** Skeletons appear only while data is really pending. No artificial delay and no splash.

## Patterns

- **Approval inbox:** queue beside the decision from 64 rem up; one column below that, where each row opens the record.
- **Next action:** an accent-outlined band at the top of My work, shown only when something is waiting.
- **Reference numbers:** mono, wrapped in `<bdi>` so they stay left-to-right in Arabic.
- **Sign-in:** static. No WebGL, no parallax.
- **Icons:** Lucide, functional only. No decorative emojis, sparkles or robot motifs.

## Rules

- Use logical CSS properties. Arabic is right to left.
- Every user-visible string goes in `public/i18n/{en,fr,ar}.json`.
- Every screen handles loading, empty, error and access-denied states.

## Unresolved workflow states

Do not design these as if they work. See [the stabilization review](docs/reviews/stabilization-review.md).

| Finding | What the interface must not imply |
|---|---|
| D-05 | That dashboards and exports are complete beyond 5,000 requests |
