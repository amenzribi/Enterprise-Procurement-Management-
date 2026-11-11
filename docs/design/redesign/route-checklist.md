# Meridian redesign: route checklist

Every route in `FrontEnd/src/app/app.routes.ts`. No route was added, removed or renamed. System: [DESIGN.md](../../../DESIGN.md). Screenshots: `before/` (Ledger, light only) and `after/` (Meridian, light and dark, desktop and phone) in this folder.

**How a route was redesigned**

- **Rebuilt:** layout, template and styles reworked for this route.
- **Restyled:** its own styles were edited; the template is unchanged or nearly so.
- **System:** no edit to the route's own files. It takes the new look and both themes from tokens, global form and table styles, the shell and shared components.

**How it was checked**

- **Seen:** a screenshot of the running QA application was reviewed after the change. L = light, D = dark.
- **Scan:** axe WCAG 2.x A/AA scan in light and dark at 1440 px (`C:\qa\p360-redesign\axe.mjs`). All scanned routes pass in both themes after one fix (notification count in dark).
- **Tests:** exercised by the Playwright suite, which passed 63 of 63 on the redesign.

## Outside the workspace

| Route | Screen | Roles | Redesign | Checked |
|---|---|---|---|---|
| `/sign-in` | Sign-in | Everyone | Rebuilt: full-screen stage, lifecycle orbit, glass panel, theme switch | Seen L and D, EN; AR seen before the glass panel; Scan; Tests |
| `/forgot-password` | Request a reset link | Everyone | Restyled: shared public frame | Scan; not seen |
| `/p#token` | Set or reset password | Link holders | Restyled: shared public frame | Not seen, not scanned |
| `/a#token` | One-tap approval | Approvers, from e-mail | Restyled: brand mark, raised card | Scan (invalid-link state); Tests (valid link) |
| `/q#token` | Supplier quote | Suppliers, from e-mail | Restyled: shared public frame | Tests (ordering journey); not seen |

## Workspace

| Route | Screen | Roles | Redesign | Checked |
|---|---|---|---|---|
| (shell) | Sidebar, top bar, drawer, user menu, notifications, theme switch | All signed-in | Rebuilt sidebar and brand; glass top bar and menus; one active item per location | Seen L and D, EN and AR, desktop and phone; menus and open drawer not seen; Tests |
| `/` | Redirect to the role's home | All | Unchanged (guard) | Tests |
| `/overview` | Home ("My work") | All | Rebuilt: next-approval band with due date, role shortcuts, lists with rings and late marks | Seen L and D; AR seen before the due-date cues; Scan; Tests |
| `/requests` | Request list | All | Rebuilt styles; ring in status column | Seen D; Scan; Tests |
| `/requests/new`, `/requests/:id/edit` | Request form | Requesters, assistants | Restyled: numbered sections, 44 px fields, raised summary | Seen L desktop, D phone; Scan; Tests |
| `/requests/:id` | Record | All with access | Rebuilt header and styles; ring; history | Seen L desktop (requester, purchasing manager), AR phone; Scan; Tests |
| … on the record | Decision, line classification, purchasing, sourcing, order, logistics, receipt, attachments, tracker | Approver, buyer, purchasing manager, receiver | Decision panel and tracker restyled; the others System | Purchasing and sourcing panels seen L; order, logistics and receipt panels not seen, covered by Tests |
| `/approvals` | Approvals inbox | Approvers | Restyled: queue, selected row, decision pane | Seen L and D; Scan; Tests |
| `/absences` | Absences and delegation | Approvers | System | Scan; not seen |
| `/purchasing` | Purchasing inbox | Buyer, purchasing manager | System (shares the inbox styles) | Seen L (empty); Scan; Tests |
| `/logistics` | Logistics inbox | Buyer, purchasing manager | System | Scan; Tests; not seen |
| `/messages` | Supplier messages (buyer) | Buyer | System | Tests; not seen |
| `/supplier/messages`, `/supplier/messages/:id` | Supplier portal | Supplier contact | System | Tests (messaging journey); not seen |
| `/assistant` | Assistant | Holders of ASSISTANT_USE | System | Tests; not seen |
| `/dashboards` | Dashboard | Management, purchasing | Rebuilt: figure, bars, table | Seen L; Scan; Tests |
| `/suppliers` | Supplier register | Purchasing, admin | System | Scan; not seen |
| `/admin/users` | Users | Administrators | System | Seen L; Scan |
| `/admin/requests`, `/admin/requests/:id` | Request administration | Administrators | System | Tests; not seen |
| `/admin/organization` | Organisation | Administrators | System | Not seen, not scanned |
| `/admin/import` | Master-data import | Administrators | System | Not seen, not scanned |
| `/admin/approval-rules` | Approval rules | System administrators | System | Seen L; Scan |
| `/admin/exchange-rates` | Exchange rates | System administrators | System | Not seen, not scanned |
| `/system-status` | System status | All | System | Seen L; Scan |
| `/account/password` | Change password | All | Restyled | Not seen, not scanned |
| `/planned/:module` | Planned module notice | All | System | Not reachable (no planned module in the navigation) |
| `/**` | Not found | All | System | Not seen |

## Dialogs and transient states

| Element | Redesign | Checked |
|---|---|---|
| Confirm dialog, submit preview, submit success | System | Tests (used in journeys); not seen |
| Toasts, session-expiry warning | Toast restyled; warning System | Tests (session spec); not seen |
| Loading skeletons, empty and error states | Empty and error restyled | Empty seen L and D; error and skeleton not seen |

## Still to look at by eye

Dark theme on: the order, logistics and receipt panels, every administration screen, supplier portal, assistant, dialogs and toasts. They use tokens only, and the scanned ones pass contrast, but nobody has looked at them in dark.
