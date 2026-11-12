# Procurement 360 design system: Meridian

Adopted 2026-11-10. It replaces Ledger (2026-11-08), which was judged too plain. It has a light and a dark theme. Tokens live in `FrontEnd/src/styles/_tokens.scss`; shared components in `FrontEnd/src/app/shared/ui/`. Components use tokens only. Route coverage: [docs/design/redesign/route-checklist.md](docs/design/redesign/route-checklist.md).

## The idea

A request travels a full circle: need, approval, purchasing, sourcing, order, shipment, receipt, closure. The product is named for that circle, so the circle is the identity.

- **The ring** is the brand mark (an open ring and the point travelling it) and the progress indicator (`app-stage-ring`: one segment per lifecycle step, filled to the current one). It appears on sign-in, in the request list, on the record header and on the home page.
- **Ink frames the work.** Navigation, the sign-in stage and the single feature surface of a page are deep ink. Work surfaces are white on porcelain.
- **Ultramarine means "you can act".** It is used for buttons, links, selection and focus, and nothing else.
- **Saffron means "this is waiting for you".** It is used for pending counts, the current navigation item and the next-approval band. It is never a status colour.

## Principles

1. **One feature surface per page.** The next-approval band on the home page, the record header, the decision pane, the pipeline figure. Everything else stays quiet.
2. **Say where the request is.** A ring and a written status wherever a request is listed; the ring never replaces the words.
3. **Dense but calm.** 14 px interface text, 13 px tables, enough in a row to decide without opening the record.
4. **Motion answers an action.** The only ambient motion is the sign-in orbit.
5. **Never imply a path that does not work.** See "Unresolved workflow states".

## Themes

Both themes use the same token names. Light is on `:root`; dark is on `:root[data-theme='dark']`. `ThemeService` (`core/theme/`) sets the attribute: a choice made with `app-theme-toggle` is remembered on the device (`epi.theme`); until then the system setting decides and is followed live. The toggle is in the top bar, on sign-in and on every guest page.

Never test for a theme in a component. Use a token, and add one to both themes if none fits.

## Tokens

| Role | Token | Light | Dark |
|---|---|---|---|
| Ink (navigation, feature surfaces) | `--color-ink`, `-raised`, `-line` | `#11132b`, `#1c1f45`, `#2a2e5a` | `#070818`, `#1a1d42`, `#262a55` |
| Text on ink | `--color-on-ink`, `--color-on-ink-muted` | `#ffffff`, `#a9aecb` | same |
| Waiting for you | `--color-brand`, `--color-on-brand` | `#f0b429`, `#11132b` | same |
| Page | `--color-paper` | `#f3f4f9` | `#0d0f24` |
| Surface | `--color-surface` | `#ffffff` | `#15183a` |
| Row hover, tracks | `--color-surface-sunken` | `#eceef6` | `#1c2049` |
| Hairline, input border | `--color-line`, `--color-line-strong` | `#e0e3ee`, `#c3c8d8` | `#282c59`, `#40457d` |
| Text, secondary text | `--color-text`, `--color-text-muted` | `#16182f`, `#565b76` | `#eceef8`, `#a9aecb` |
| Interaction | `--color-accent`, `-hover`, `-soft` | `#2a3cc7`, `#1f2ea3`, `#e7eafb` | `#99a5ff`, `#b5beff`, `#23296a` |
| Text on the accent | `--color-text-inverse` | `#ffffff` | `#0b0d22` |
| Chosen option of a switch, numbered markers | `--color-selected`, `--color-on-selected` | `#11132b`, `#ffffff` | `#eceef8`, `#0d0f24` |
| Focus ring | `--color-focus` | `#2a3cc7` (saffron on ink) | `#f0b429` |
| Status | `--color-success`, `-warning`, `-danger`, `-info`, `-neutral`, each with `-soft`; `--color-on-danger` | see tokens file | see tokens file |
| Glass | `--glass-surface`, `-surface-strong`, `-line`, `-edge`, `-blur`, `-shadow` | white at 78 % and 90 % | surface at 72 % and 90 % |
| Sign-in stage | `--stage-*` | pale ultramarine field, ink rings | ink field, white rings |
| Chart categories | `--chart-1` to `--chart-5` | `#2a3cc7`, `#0f8296`, `#b97d00`, `#177245`, `#7a7fa0` | `#99a5ff`, `#5fcfe0`, `#f0b429`, `#72d19c`, `#8d93bd` |

Text and status pairs meet WCAG 2.2 AA (4.5:1) on their surface and soft fills in both themes. Information is teal so that it never reads as the ultramarine action colour.

**Glass.** A translucent, blurred surface for things that float over other content: the top bar, the user and notification menus, and the sign-in panel. It is opaque enough that text contrast is decided by the glass, not by what is behind it. Tables, forms and record content are always solid. Fields inside the sign-in panel are solid.

**Typography.** Bricolage Grotesque Variable (`--font-display`) for page titles and the figures to read first. IBM Plex Sans Variable (`--font-sans`) for everything else. IBM Plex Sans Arabic for Arabic, so the three languages share one family. All self-hosted. Reference numbers use the text face with tabular figures; there is no monospace. Sizes: 12, 13, 14 (default), 16, 19, 30, 40 px. Tracking is removed in Arabic.

**Spacing and shape.** 4 px grid (`--space-1` to `--space-12`). Radius grows with size: 8 px controls, 10 px inner cells, 14 px panels, 22 px feature surfaces, pill for switches, badges and counts.

**Elevation.** Shadows are tinted with ink in light and are black in dark. `--shadow-card` for resting panels, `--shadow-raised` for the feature surface and sticky summaries, `--shadow-overlay` for menus, dialogs and toasts, `--shadow-button` for the primary button.

**Motion.** One family of timings: 120 ms (`--duration-fast`) for hover and colour, 200 ms (`--duration-base`) for state change, 420 ms (`--duration-slow`) for things arriving, 900 ms (`--duration-scene`) for the sign-in scene only. Easing: `--ease-standard` for movement on screen, `--ease-out` for things entering, `--ease-cinematic` for the sign-in scene. Menus and toasts share one entrance (`overlay-in`: a 6 px rise, no bounce). Buttons acknowledge a press with a 2 % scale. A decided approval slides out of the queue. `prefers-reduced-motion` removes all of it.

**Sign-in layout.** A masthead row (brand at the start, language and theme at the end) and, under it, the stage beside the glass panel. The form comes first: every vertical measure on the page is tied to the viewport height, so on a short window the orbit, the display type and the spacing shrink before the form does. Fields never go below 44 px. As height runs out the supporting sentence goes (46 rem), then the stage labels (38 rem), then the orbit (30 rem). At 1366 × 640 the whole form, the sign-in button and the recovery link are in view without scrolling, with 34 px between the masthead and the panel. The page is at least as tall as the viewport and grows past it when the content needs it (small phones, 200 % zoom, validation messages, an open keyboard), so the document scrolls and every control stays reachable. Only the decorative light is clipped (`overflow: clip`).

**Sign-in scene.** In order: the light and the orbit, the three lines of the promise (each rising out of its own line, 110 ms apart), the supporting sentence, then the glass panel. Afterwards the light drifts slowly, the saffron point travels the orbit once every 64 s, and on a mouse the layers shift by up to 18 px against each other. It is CSS and SVG with one passive pointer listener; no animation library. With reduced motion, or without `offset-path` or `backdrop-filter`, it is a still composition with a near-opaque panel.

**Loading.** Skeletons appear only while data is pending. No splash and no artificial delay.

## Components

| Component | Use |
|---|---|
| `app-brand-mark` | The ring mark. White on ink by default; set `color` for light surfaces. |
| `app-stage-ring` | Lifecycle progress. Tones: active, done, stopped, idle. Sizes 22 to 64 px; `showCount` writes the step number. Decorative, always next to a written status. |
| `button[appButton]` | Primary (one per view), secondary, ghost; default and small. `[busy]` shows a turning ring in place of the icon while the button's request is in flight; the caller still disables it. |
| `app-page-header` | Page title in the display face, one-line purpose, actions slot. |
| `app-panel`, `.panel` | A group of related content. White, hairline, card shadow. |
| `app-status-badge` | Soft pill: dot and word in the status colour. |
| `app-language-select` | Segmented pill; the current language uses the selected tokens. |
| `app-theme-toggle` | Round button that switches light and dark; it names the theme it switches to. |
| `app-empty-state`, `app-error-state`, `app-skeleton` | Empty, failed and pending states. |
| `app-public-frame` | Ink brand bar, language switch and one centred card for pages outside the workspace. |
| `app-confirm-dialog`, toaster, session warning | Overlays; overlay shadow. |

**Shared treatments** (global in `styles.scss`): text inputs, selects and text areas share one border, radius, hover and focus ring; table headers share one style; `.service-flag` is a warning note.

## Patterns

- **Navigation:** ink sidebar grouped by the procurement journey; the current item has a saffron bar and icon; counts are saffron pills. It collapses to a rail and becomes a drawer below 64 rem (18 rem wide, rounded trailing edge, 44 px links, the page dimmed and softened behind it; Escape closes it and returns focus to the menu button). The top bar is glass and carries a location trail (journey group, then page, taken from the navigation), language, theme, notifications and the account menu.
- **Home:** next-approval band on ink (only when something waits; it states the wait and the due date, in saffron when late), shortcuts to the role's destinations (taken from the navigation, same permissions), then three lists. Late approvals carry a red badge and due line.
- **Approvals inbox:** queue beside the decision from 64 rem; below that each row opens the record. The decision pane shows amount, requester, wait and due date in four cells above the actions.
- **Lists:** a filter bar (one contained strip: search, filters, sort) above a table in a panel with a porcelain header row; ring and badge in the status column; cards on phones; the page range or page number as a quiet pill beside the previous and next buttons.
- **Record:** header with the ring, reference, title, status, four fact cells and the actions; next-action note; role panels; tracker, lines and attachments beside people, classification and history.
- **Forms:** numbered sections (the form is filled in order), labels above fields, 44 px controls, errors under the field and in a summary, sticky total and actions. Saving shows the ring on the button that was pressed and disables the others; the result is a toast. Attachments: a tinted, dashed "add a file" control, a progress bar while uploading, each file as a row with its icon.
- **Work panels on the record** (purchasing, sourcing, order, logistics, receipt): forms keep a 46 rem measure and their action sits at the start of the row. Shipment milestones are a timeline: filled when reached, red when late, hollow when still to come.
- **Dashboard:** the total inside a ring split into five lifecycle phases, with each phase's meaning, count and share written beside it; then one bar per status that opens the request list filtered to that status; open amounts per currency; requests by company; the management table with a "late on this page" count. Every figure states its scope in words. Charts are SVG and CSS (no chart library is installed); colour is never the only carrier, and the written figures are the tabular alternative.
- **Sign-in:** one full-screen stage with the lifecycle orbit and the promise, and the form in a glass panel; on phones the stage light and the panel only.
- **Menus:** Escape closes a menu whether focus is inside it or still on its trigger.
- **Dialogs:** the page dims and softens behind them (`--color-scrim`, 3 px blur); they use the feature radius and the shared `overlay-in` entrance.
- **Reference numbers:** wrapped in `<bdi>` so they stay left to right in Arabic.
- **Icons:** Lucide, functional only. No decorative emojis, sparkles or robot motifs.

## Rules

- Use logical CSS properties. Arabic is right to left. Directional icons are mirrored by `app-icon`; do not mirror them again.
- Every user-visible string goes in `public/i18n/{en,fr,ar}.json`.
- Every screen handles loading, empty, error and access-denied states.
- On ink, set the focus outline to `--color-brand`.
- No raw colour values in components, including `rgb()` shadows and `#fff`. Text on a status or accent fill uses its `on-` or inverse token, because those fills are light in the dark theme.

## Unresolved workflow states

Do not design these as if they work. See [the stabilization review](docs/reviews/stabilization-review.md).

| Finding | What the interface must not imply |
|---|---|
| D-05 | That dashboards and exports are complete beyond 5,000 requests |
