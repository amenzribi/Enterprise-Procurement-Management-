# 07. UI/UX and motion audit

Scope: the Angular 22 frontend (`FrontEnd/src`), its design tokens and styles, and the motion tooling named in the brief. Recommendations only; nothing was changed. Figures come from scripted counts over the source on 2026-11-15. **Not done in this audit:** no browser session, no contrast measurement, no frame-rate profiling. Items that need a browser are marked Hypothesis.

## Summary

The interface has a real design system and follows it. Tokens drive colour, spacing, duration and easing; logical CSS properties are used almost everywhere (656 against 7 physical); every route is lazy-loaded; reduced motion is honoured globally and at token level; EN, FR and AR have identical key sets (1,494 each).

**Motion libraries are not needed to reach a premium level here.** The app has no GSAP, ScrollTrigger, Lenis or React, and the cinematic GSAP/Lenis pattern suits marketing pages, not forms and tables. The gaps that would raise perceived quality are consistency gaps: untokenised breakpoints, no route transition, very large components, no list virtualisation. All can be closed with CSS and Angular features already in the stack.

## 1. Tools and skills: what exists

**Application dependencies** (`FrontEnd/package.json`, verified):

| Technology | In the application? |
|---|---|
| GSAP | No |
| ScrollTrigger (GSAP plugin) | No |
| Lenis | No |
| React, React Bits | No. React is excluded by `CLAUDE.md` ("No React or second frontend framework") |
| Angular CDK | Yes: dialog (13 files), overlay/menu (2), a11y (1) |
| Angular animations package (`@angular/animations`) | No. All motion is CSS |
| Icon library | `lucide`, 67 icons imported by name in `shared/ui/icon.ts` (tree-shaken) |

**Claude skills available in this workspace** (listed by the session; a skill is guidance for the assistant, not code in the product):

| Skill | Available | Used in this audit |
|---|---|---|
| `anthropic-skills:cinematic-gsap-lenis-motion-system` | Yes | Loaded and read; assessed in section 4 |
| `frontend-design:frontend-design`, `anthropic-skills:frontend-design` | Yes | Not invoked (no design work in this phase) |
| `design-superpowers:*` (design, design-review, creative, ds-*) | Yes | Not invoked |
| `anthropic-skills:ui-ux-pro-max` ("UI/UX Pro") | Yes | Not invoked |
| `design-skills:accessibility-audit`, `design-skills:interaction-design`, `audit-suite:web-animation-design`, `vectorlab-ux-skills:motion` / `reduced-motion` | Yes | Not invoked |
| React Bits | **No such skill.** The 21st component connector is available but serves React components | — |
| Motion Dev UI connector | Configured but failed to connect in this session | — |

## 2. Design system: state

| Aspect | Evidence | Assessment |
|---|---|---|
| Tokens | `styles/_tokens.scss`: colours, spacing, radius, shadows, `--duration-fast/base/slow/scene` (120/200/420/900 ms), `--ease-standard/out/cinematic` | Sound |
| Themes | Light on `:root`, dark on `:root[data-theme='dark']`, `color-scheme` set; `core/theme/theme.service.ts` follows the system setting or the saved choice | Sound |
| Logical properties | 656 uses; 7 physical, all in `features/auth/sign-in.scss:182-244` | Sound (UI-4) |
| RTL overrides | 7 `[dir='rtl']` rules, each for something that cannot be logical (transforms, an SVG, directional icons) | Sound |
| Motion tokens | 34× `--ease-standard`, 28× `--duration-fast`, 13× `--duration-base`; raw durations only in long decorative loops on sign-in (26–64 s) and a few one-offs (60, 180, 220, 260, 500, 700 ms) | Mostly sound (UI-6) |
| Reduced motion | Global rule `styles.scss:190-198` (all animations and transitions to 0.01 ms, one iteration) and duration tokens set to 0 in `styles/_tokens.scss:236-243`; 7 media blocks in total | Sound |
| Animated properties | No `transition: all`; no transition on width, height, margin or position found | Sound |
| Breakpoints | 12 distinct raw values (40rem ×10, 63.99rem, 30rem, 80rem, 52rem, 47.99rem, 35.99rem, 29.99rem, 71.99rem, 68rem, 64rem, 24rem) | Inconsistent (UI-2) |
| Hard-coded colours | 21 hex values outside the token file | Minor (UI-5) |
| z-index | 6 raw values (1, 10, 40, 50, 100, 200) | Minor (UI-5) |
| Translations | 1,494 keys in each of `en.json`, `fr.json`, `ar.json`; parity enforced by a unit test | Sound |
| Accessibility hooks | Skip link in the shell; 75 live-region/status/alert uses; 19 `:focus-visible` rules; 3 axe-based Playwright specs | Sound; contrast not measured |

## 3. Findings

### UI-1. No list virtualisation or incremental rendering on large data — Medium — Verified (impact: Hypothesis)

- **Location:** no `cdk-virtual-scroll` anywhere; 11 `<table>` elements; lists render every row of the page they receive. Server pages are capped at 100 rows (200 for the workflow list, `BackEnd/.../request/RequestWorkflow.java:78`). The internal messaging conversation list returns up to 200 items unpaged. `features/requests/request-form.ts` allows up to 100 lines (`app.request.max-lines`), each a row of inputs.
- **Explanation:** with OnPush and signals, 100 static rows are cheap. A 100-line request form is not: each line carries several inputs, validators and translated labels. `track $index` is used in 8 `@for` loops; when rows are inserted or removed by position, Angular re-creates the wrong DOM nodes and loses focus or input state.
- **User impact:** typing lag and slow add/remove on long requests, mostly on modest laptops and phones.
- **Recommendation:** profile the form with 100 lines first. If input delay exceeds 100 ms: track lines by a stable key, split the line into its own OnPush component, and render lines outside the viewport lazily (`@defer (on viewport)` or CDK virtual scroll, both already available). Replace `track $index` wherever items can be reordered or removed.
- **Complexity:** M.
- **Verification:** Chrome performance trace of add-line, edit and remove-line with 100 lines; interaction-to-next-paint under 200 ms.

### UI-2. Breakpoints are not tokenised — Low — Verified

- **Location:** 12 distinct values across component styles (see table above).
- **Explanation:** CSS custom properties cannot be used inside `@media` conditions, so the values were written by hand and drifted: 63.99/64/68/71.99 rem and 29.99/30/35.99 rem are near-duplicates.
- **User impact:** at some widths one part of a page has switched to its compact layout and another has not.
- **Recommendation:** four named breakpoints as Sass variables or mixins in `styles/_tokens.scss`; replace the raw values.
- **Complexity:** S.
- **Verification:** grep shows only the named values; screenshots at 360, 768, 1024, 1280 and 1440 px in the three languages.

### UI-3. Very large components — Medium — Verified

- **Location (TS + template lines):** `features/requests/request-form.ts` 1,308; `features/admin/request-admin.ts` 954; `features/dashboards/dashboards-page.ts` 754; `features/requests/request-detail.ts` 669; `features/overview/overview.ts` 658; `features/receipt/receipt-actions.ts` 610; `features/admin/approval-rules.ts` 601; `features/admin/users-admin.ts` 579; `features/internal-messaging/message-thread.ts` 528.
- **Explanation:** most components keep template and styles inline, so one file holds layout, state, validation, API calls and CSS. Eleven feature folders have no unit spec (admin, assistant, logistics, messaging, not-found, notifications, ordering, organization, overview, planned-module, sourcing); they are covered only by browser tests.
- **Impact:** a UI redesign touches the files that also hold the business rules of the form; regressions are likely and reviews are slow.
- **Recommendation:** before any visual redesign, split the three largest into a container (data, state) and presentational parts (line editor, totals, attachments panel; dashboard cards). Do not change behaviour in the same step.
- **Complexity:** M each.
- **Verification:** existing unit and browser tests pass unchanged; no file over ~500 lines in those features.

### UI-4. Physical positioning in the sign-in illustration — Low — Verified

- **Location:** `features/auth/sign-in.scss:182, 202, 213, 233, 237-238, 244`.
- **Explanation:** the orbit graphic positions its elements with `left`/`right`. It is a decorative diagram, so mirroring is a design choice; the station labels (`.is-left`/`.is-right`) contain text and do not flip in Arabic.
- **Recommendation:** decide whether the illustration mirrors in RTL; if labels stay, use `inset-inline-start/end` for them.
- **Complexity:** S.

### UI-5. Raw colours and z-index values outside tokens — Low — Verified

- **Location:** 21 hex colours and 6 raw `z-index` values in component styles.
- **Recommendation:** move to tokens (a z-index scale: base, sticky, drawer, overlay, toast). Dark-theme correctness of each raw colour needs a visual check.
- **Complexity:** S.

### UI-6. No transition between routes — Low — Verified

- **Location:** `app/app.config.ts` uses `provideRouter` without `withViewTransitions()`; no `startViewTransition` in the code.
- **Explanation:** pages swap instantly while overlays, lists and buttons animate. This is the most visible place where a small, consistent motion would add polish.
- **Recommendation:** Angular's built-in View Transitions support with a short cross-fade (`--duration-base`), disabled under reduced motion. No dependency.
- **Complexity:** S.
- **Verification:** visual check in Chromium, Firefox and Safari (falls back to no transition where unsupported); axe run unchanged.

### UI-7. Initial bundle above its budget — Low — Verified

- **Location:** `angular.json` budget 500 kB (warning), 1 MB (error); last build 524.60 kB initial. Largest chunks in `dist`: 216.7 kB, 180.8 kB (main), 157.0 kB.
- **Explanation:** all 35 routes are lazy, so the initial weight is the framework, the shell, CDK, translations loader and fonts. Three font families are bundled (IBM Plex Sans variable, Bricolage Grotesque variable, IBM Plex Sans Arabic in three weights); the Arabic faces load for every language.
- **Recommendation:** analyse the bundle (`ng build --stats-json`) before changing anything; load the Arabic font only when Arabic is active; confirm `font-display: swap` in the built CSS.
- **Complexity:** S.
- **Verification:** initial bundle under 500 kB; Lighthouse on the sign-in page.

### UI-8. Blur effects on always-visible surfaces — Low — Hypothesis

- **Location:** `backdrop-filter` on the shell header (`layout/shell/shell.scss:292-293`), the notification panel (`layout/notification-bell.ts:127-128`), the drawer backdrop (`shell.scss:399-400`) and sign-in (`sign-in.scss:361-362`).
- **Explanation:** backdrop blur over a scrolling page repaints on every frame. It is usually fine on desktop GPUs and can drop frames on low-end devices with long tables beneath.
- **Recommendation:** measure scrolling a 100-row list under the blurred header on a low-end device; fall back to a solid surface if frames drop.
- **Complexity:** S.

### UI-9. Subscriptions without explicit teardown — Low — Hypothesis

- **Location:** 190 `subscribe(` calls against 38 `takeUntilDestroyed`.
- **Explanation:** most are single HTTP calls, which complete on their own. Any subscription to a long-lived stream (router events, translation changes, a subject in a root service) from a component without teardown leaks that component.
- **Recommendation:** review the calls that are not `HttpClient` calls; a lint rule can enforce teardown.
- **Complexity:** S.
- **Verification:** heap snapshot after 50 navigations between list and detail: detached component count stays flat.

## 4. GSAP, ScrollTrigger and Lenis: suitability

The cinematic GSAP/Lenis skill was read in full. Its own "use when" is a site that needs "luxury editorial, Apple-level polish, creative studio portfolio, or immersive cinematic storytelling". Element by element, for this application:

| Technique in the skill | Fit for Procurement 360 | Reason |
|---|---|---|
| Lenis smooth scrolling | **Do not use** | Replaces native scrolling for the whole page. Conflicts with CDK overlays and scroll blocking, nested scroll areas (message thread, tables, dialogs), keyboard and assistive-technology scrolling, and "scroll to the invalid field" on forms. People filling long forms need native scroll |
| Pinned scenes, horizontal galleries, scroll-scrubbed parallax | **Do not use** | Storytelling devices; the app has no narrative pages |
| Custom cursor, magnetic buttons, mouse parallax | **Do not use** | Decoration that slows precise work; the skill itself disables them on touch |
| Split-text reveals (words/lines, blur) | **Do not use in the app.** Possible on the sign-in headline only | Splitting text into spans breaks Arabic shaping if done per letter and complicates bidirectional text and screen readers; 0.75–1.1 s reveals delay people who open the same screen fifty times a day |
| Scroll reveals of cards (fade-up on entering the viewport) | Limited: dashboards and the overview, once | Achievable with CSS (`animation-timeline: view()` where supported) or one small `IntersectionObserver` directive. ScrollTrigger is not required |
| Easing and timing taste (no bounce, restraint, stagger guides reading order) | **Adopt** | Already matches `--ease-cinematic` and the token durations |
| Performance rules (transform and opacity only, clean up on route change) | **Adopt** | Already followed |

**Where GSAP itself would add real value:** only for sequenced, interruptible timelines that CSS handles poorly. One candidate exists: the sign-in illustration, which already runs six keyframe animations in CSS. No screen in the working application needs it. Cost if added: about 25 kB gzipped for the core (ScrollTrigger more), a licence check for the client's use, cleanup discipline in a zoneless app (`gsap.context()` reverted in `DestroyRef`), and a second motion system beside the tokens. **Recommendation: do not add GSAP, ScrollTrigger or Lenis in the next phase.** Revisit only if a specific approved design cannot be built with CSS.

## 5. React Bits and Angular

React Bits components are React components; they cannot run in Angular without shipping React, which the project rules forbid and which would add a second runtime for decoration. The effects worth having can be rebuilt natively:

| Effect commonly taken from React Bits | Angular/CSS equivalent already possible |
|---|---|
| Animated counters on dashboard figures | A small signal-based directive with `requestAnimationFrame`; skip under reduced motion |
| Staggered list or card entrance | CSS `animation-delay` from an index custom property; the message thread already animates new messages (`message-thread.scss:67`) |
| Skeleton shimmer | Exists: `shared/ui/skeleton.ts:35` |
| Spotlight or gradient borders on cards | CSS gradients and `@property`; no script |
| Text gradients, shiny text | CSS `background-clip: text`; sparingly, headings only |
| Page transitions | Angular View Transitions (UI-6) |
| Magnetic and tilt effects | Not recommended for this product |

## 6. Recommended premium direction (for approval, not implemented)

1. **Consistency first:** breakpoints, z-index and colour tokens (UI-2, UI-5). Low risk, visible at every width.
2. **One motion vocabulary, CSS only:**
   - route cross-fade (UI-6);
   - list rows entering with a 20–30 ms stagger capped at about 10 rows;
   - dialogs and drawers on `--ease-cinematic`;
   - status changes (approved, received) with one 200 ms colour and icon transition.
   - Nothing longer than `--duration-slow` (420 ms) inside the working app; `--duration-scene` stays on sign-in.
3. **Data density with calm:** numeric columns in tabular figures (already used in places), sticky table headers, row hover without layout shift, skeletons that match final row height so content does not jump.
4. **Large forms:** section navigation with scroll-spy using native smooth scroll (`scroll-behavior`, already reset under reduced motion), a persistent save state, inline validation that does not move the layout.
5. **RTL and themes as acceptance criteria:** every new motion checked in Arabic (direction of slide-ins follows `inline` axis) and in dark mode; no animation carries meaning that is not also in text.
6. **Budgets:** initial bundle back under 500 kB before adding anything; interaction-to-next-paint under 200 ms on the 100-line form; no frame over 16 ms from decorative effects.

## 7. Not verified

- Colour contrast of token pairs in both themes (needs measurement; the axe specs cover the pages they visit).
- Real rendering performance of large forms and tables (needs profiling in a browser).
- Screen-reader behaviour of live regions and dialogs (needs manual testing with NVDA or VoiceOver).
- Visual consistency page by page in the three languages (earlier handoffs list screens "still to look at by eye").
- Safari and Firefox behaviour of `backdrop-filter` and View Transitions.
