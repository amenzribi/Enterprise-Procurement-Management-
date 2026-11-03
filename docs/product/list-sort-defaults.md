# List sort defaults and intentional exceptions

## Default (newest first)

Business record lists default to **created / submitted time descending**, with a **stable secondary key** (`id` DESC) so equal timestamps paginate consistently.

| List | Default primary | Secondary |
|---|---|---|
| Purchase requests | `createdAt` DESC | `id` DESC |
| Purchasing queue | `submittedAt` DESC | `id` DESC |
| Approval inbox (pending) | `waitingSince` DESC (task created) | `taskId` DESC |
| Logistics inbox | PO `created_at` DESC | `po.id` DESC |
| Shipments on a request | `created_at` DESC | `id` DESC |
| Goods receipts on a request | `created_at` DESC | `id` DESC |
| Conversations | last message / id DESC | (existing) |

User-selected sort is preserved across pagination and filters; the active sort is shown in the list controls. Search (`q`) runs on the **authorised dataset**, not only the current page, and resets page to 0.

## Intentional exceptions (do not reverse)

| Context | Order | Why |
|---|---|---|
| Audit / approval event timelines | Chronological ASC | History must read in occurrence order |
| Messages inside a conversation | Chronological ASC (API fetches DESC then reverses) | Chat readability |
| Workflow / milestone steps | Defined template order (`sort_order`) | Process sequence |
| Request lines, PO lines, RFQ lines | `line_no` ASC | Document structure |
| Approval rule stages | `stage_no` ASC | Circuit definition |
| Reference pickers (currencies, UoM, categories) | Code / `sort_order` / name | Stable catalogues |
| Unit lists on organisation admin | Natural / code | Admin browsing |
