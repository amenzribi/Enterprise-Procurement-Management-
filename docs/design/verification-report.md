# Ledger verification report

Date: 2026-11-09. Self-verification by the developer session; **not independent QA or reviewer sign-off**.

**Tested state:** branch `stabilization-and-ledger-redesign`, commit `c67c733` plus uncommitted working-tree fixes. `git diff HEAD -- BackEnd FrontEnd` SHA-256 `2C5ECA04E78DA4DC…E07DF4`, before this report and the handoff were written. No source file changed afterwards.

**Baseline:** `1b59c4e` (before stabilization), and `c67c733` for the first browser pass.

## Readiness

| Level | Verdict |
|---|---|
| Internal demo on synthetic data | **Ready** |
| Hand-over to independent QA and review | **Ready**, once the working tree is committed |
| Pilot or production | **Not ready**: no independent QA, penetration test, MFA, restore drill or production-like load test |

## Checks

| Check | Result | State |
|---|---|---|
| Frontend lint | Passed | Final |
| Frontend unit tests | Passed, 221/221 | Final |
| Production build | Passed, 2nd attempt. The first attempt crashed Node (exit -1073741819, access violation) with no compile error. | Final |
| Backend `verify -Pdb-it` (SQL Server) | Passed, 174/174 | Final |
| Playwright, full suite | Passed, 58/58 | Final |
| Closure journeys repeated | Passed, 8/8 | Final |
| axe, WCAG A/AA and 2.2 AA | Passed: EN, FR, AR at 375 and 1280 px on My work, inbox, record, purchasing, requests | Final |
| Horizontal overflow at 375, 768, 1280, 1920 px | Passed, EN/FR/AR on the same five screens | Final |
| Code review: standards, spec, adversarial | Done; see findings | `c67c733` |
| Design review against `DESIGN.md` | Done by reading tokens and 5 screenshots | Final tokens; screenshots from the run before the last two fixes, neither visual |
| Screen reader | **Not run** | n/a |
| Motion review in a live browser | **Partly**: reduced-motion test passed; the 180 ms exit was not judged by eye | Final |
| Runtime performance against baseline | **Not run**: no baseline figures exist | n/a |
| Real touch device | **Not run**: emulated touch only | n/a |
| Native French and Arabic review | **Not run** | n/a |

The code review used three reviewers from one model family. No external reviewer is configured, and P0–P2 findings were checked by the developer session reading the code and by tests, not by a second model family.

### Behaviour covered by the browser spec

`FrontEnd/e2e/ledger-verification.spec.ts`:

- **Roles by direct URL:** anonymous to sign-in; a requester is refused purchasing, logistics, admin and dashboards; a buyer is refused user administration (UI and API 403); a user of another company gets not-found (UI and API 404).
- **States:** inbox loading skeleton, error with working retry, empty queue, decision-panel error with retry, session expiry to sign-in.
- **Keyboard and focus:** skip link, row selection, drawer and user menu close with Escape and restore focus, dialog holds and restores focus, focus after a decision, folded filters.
- **Touch:** tap to open a record on a phone, tap to select a row on a tablet.
- **Language:** right-to-left direction, isolated reference numbers, no untranslated keys.

Screenshots: `docs/design/verification/{lang}-{screen}-{width}.png`.

## Bundle

| | `1b59c4e` | `c67c733` | Final |
|---|---|---|---|
| Initial | 504.87 kB | 506.46 kB | 506.30 kB |
| Estimated transfer | 134.54 kB | 134.60 kB | 134.56 kB |

Still 6.30 kB over the 500 kB budget (warning, not error).

## Regressions and defects fixed

| Severity | Defect | How found | Fix | Regression check |
|---|---|---|---|---|
| P1 | A delivery with a line that did not arrive could not be recorded (0 returned 422 for the whole receipt) | Review | Lines entered as 0 are left out | Unit |
| P1 | After a partial receipt the form kept the old quantity, then 409 | Review | Quantities reset after saving | Unit |
| P1 | A partly shipped line prefilled above its shipped quantity and could block later receipts | Review | Server returns `receivable`; form uses it | Backend, unit |
| P1 | An invoice saved without attestation could never be closed | Review | New attest action | Backend, unit |
| P2 | Decimal remainders such as 0.3 − 0.1 sent an invalid quantity | Review | Server computes the remainder | Unit |
| P2 | Keyboard focus was lost after every pane decision | Browser | Queue stays rendered; focus moves to the next item | Browser |
| P2 | Deciding the last row of the last page showed a false "all clear" | Review | Moves to the last valid page | **Not automated** |
| P2 | "More filters" folded shut while in use | Browser | The panel keeps the user's state | Browser |
| P2 | French at 768 px scrolled sideways | Browser | Development badge hidden below 64 rem | Browser |
| P2 | Older responses could overwrite newer ones in sourcing, ordering, logistics and receipt panels | Browser, repeated runs | Newest load wins | Repeated journeys |
| P2 | "Create purchase order" could be clicked before the new request version arrived (409, raw code shown) | Browser, repeated runs | Panel stays busy until refreshed; error translated | Repeated journeys |
| P2 | Supplier link summary stayed open once evaluation had started | Review | Neutral 404 | Backend |
| P2 | Combined quote amounts could overflow the column (500) | Review | 422 | **Not automated** |
| P2 | Sidebar focus ring was green; `DESIGN.md` requires blue | Review | Uses the focus token | Code read |
| P3 | Raw scrim colour, mono on counts, unused Inter dependency | Review | Removed | Lint, build |
| Tests | Vacuous row assertion, silently skipped split-pane test, no cross-company inbox test, no test for the global-admin role guard | Review | Added or tightened | Backend, unit, browser |

## Remaining

| ID | Severity | Item |
|---|---|---|
| D-03 | Medium | A receiver can confirm their own receipt. Needs a product decision. |
| D-05 | Medium | Dashboards and exports stop at 5,000 requests without saying so. |
| D-06 | Medium | Submission bursts in one company can return a retryable 503. |
| D-07 | Medium | Expired quotes can be awarded, and quote quantity can differ from the request. Needs a product decision. |
| PERF-04 | Medium | The request-list count is the top remaining query. |
| R-1 | Low | Buyers do not get the next-action band on My work; it covers approvals only. |
| R-2 | Low | Request administration shows allowed actions as raw codes. |
| R-3 | Low | The inbox search placeholder is clipped in English on wide screens. |
| R-4 | Low | The selected-row marker uses an inset shadow rather than a hairline. |
| R-5 | Low | Reference numbers on the requests table need sideways scrolling at 768 px to reach later columns (contained in its own frame). |
| R-6 | Low | Sourcing, ordering, logistics, admin and messaging have tokens but no layout redesign. |
