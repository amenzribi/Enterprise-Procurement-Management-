# Internal employee messaging

Branch `feat/internal-messaging`, started from `c7b901d719ac2b86ff438a1d34ff70e1ce314bb3` on 2026-11-13. Plan: [docs/superpowers/plans/2026-11-13-internal-messaging.md](../superpowers/plans/2026-11-13-internal-messaging.md).

Employees of one company message each other one-to-one, discuss a purchase request, and a requester can message the request's current approver from the record. Supplier messaging (ADR-011) is unchanged and separate.

## What exists

| Feature | Where |
|---|---|
| Messages inbox: list, search, open conversation, new message (directory by name, department, role) | `/conversations`, `/conversations/:id`; navigation "Messages" with the unread count |
| One-to-one conversation between two employees of one company | Inbox |
| Request discussion: explicit participants, add a colleague who can already see the request | Record page, "Discussion" panel; also in the inbox |
| "Message approver" on a request awaiting approval: recipient choice, editable suggestion, request attached, sent only on Send | Record page, dossier actions |
| Request card attached to a message | Composer, paperclip; only requests the sender can see |
| Live updates: new messages, unread count, read marker across tabs | Server-Sent Events stream |

Not in this release (later): file attachments, group channels, reactions, typing indicators, presence, AI assistance, editing or deleting messages, leaving or removing a participant, e-mail notification of messages.

## Data (V22)

New tables only; the supplier tables of V14 are untouched, so INTERNAL/EXTERNAL supplier messages keep their boundary.

| Table | Purpose | Key rules |
|---|---|---|
| `internal_conversation` | One row per conversation. `kind` DIRECT or REQUEST | DIRECT: `company_id` plus the two users in ascending order, unique per company and pair. REQUEST: `request_id`, unique per request; `company_id` is the requesting company |
| `internal_participant` | Who is in a conversation | Unique per conversation and user. `last_read_message_id` only moves forward. `removed_at`: no screen sets it in this release, but every query honours it (see access rules) |
| `internal_message` | Messages | `id` from a sequence: the order and tie-breaker. `client_message_id` unique per conversation and sender. `kind` TEXT or SYSTEM (participant changes). `linked_request_id`: a request card |

Existing users and requests need no backfill: a conversation exists only once someone starts it. Rollback before any message exists: drop the three tables and sequences. After messages exist, dropping them deletes employee correspondence.

**Retention: pending a product decision.** No policy for employee messages is documented anywhere in the project. Nothing deletes messages automatically, and nothing will until the owner decides the period, whether messages count as business records, and what happens on account deletion.

## Access rules (server, every call)

The user and their company come from the session (`CurrentUserProvider`), never from the request body. Every conversation id passes one gate (`InternalMessagingService.load`).

| Rule | Result when broken |
|---|---|
| Supplier contacts cannot use any internal endpoint and never appear in the directory | 403; also blocked by `SupplierPortalRestrictionFilter` |
| A conversation is readable only by its current participants | 404, also for company ADMIN and GLOBAL_ADMIN |
| DIRECT: both people are active employees of one company; the reader must still belong to that company | 404 |
| REQUEST: the reader must also see the request now, under the request screens' rule (`RequestAccessPolicy` through `RequestMessagingQueries`) | 404; the discussion leaves their list and their stream |
| Adding a participant: active, not a supplier, home company is the request's requesting or buying company, and can already see the request | 422 `not-eligible` or `no-request-access` |
| A request card grants nothing. It is attached only if the sender and every other participant can see the request; otherwise the text goes without it (`cardWithheld`). Each viewer gets the card resolved again; without access it carries no id, reference, title or status | — |
| "Message approver": the recipient must be a current approver when Send is pressed | 409 `approver-changed`, nothing sent |
| "Message approver" never joins an existing discussion the sender is not part of | 409 `discussion-not-participant` |
| A removed participant (`removed_at` set) is out, even while they can still see the request | 404 on every conversation endpoint; not listed, not counted as unread, no live events. The discussion panel says it exists, without content. A participant adding them again restores the row: audited, shown as a system line, access back with the full history |
| Deactivation | Every API call returns 401, the stream included (`CurrentUserProvider` reloads the account). Events stop at once (each delivery re-checks the account); open streams close at the next heartbeat. After reactivation and a new sign-in, the browser resyncs from its newest message id |
| Role or request access withdrawn | The discussion is 404 and no longer streamed, while direct messages continue. Given back: readable again, and the resync returns what was missed |
| Sign-out | The stream closes at the next heartbeat; the old cookie gets 401, the stream included |

**Current approvers** are the assignees of the pending tasks of the active approval round, the same rule as the record's "next actors". Delegation is already in that data: a delegated or absence task has the delegate as assignee and the original approver in `original_user_id`, which the dialog shows as "on behalf of". The query lives in the messaging store, not the approval module, because approval → notification → messaging would be a module cycle.

**Where the message goes:** same company as the sender → the direct conversation; another company (a group-level approver) → the request discussion, created with the current approvers if it does not exist.

## API

All under `/api/v1/internal-messaging`, session cookie and CSRF header as for the rest of the API.

| Method and path | Purpose |
|---|---|
| `GET /directory?q&department&role` | Colleagues of the caller's company (max 30) |
| `GET /conversations?q` | The caller's conversations, newest first (max 200) |
| `POST /conversations/direct {userId}` | Open or return the one direct conversation |
| `GET /conversations/{id}` | Detail: counterpart or request card, participants, read marker |
| `GET /conversations/{id}/messages?beforeId|afterId` | 40 per page; `afterId` returns everything newer, for resync |
| `POST /conversations/{id}/messages {bodyText, clientMessageId, requestId?}` | 201 new, 200 a retry of the same client id, 409 same id with another text |
| `POST /conversations/{id}/read {messageId}` | Move the read marker forward |
| `GET /unread` | Total unread across readable conversations |
| `GET`, `POST /requests/{id}/discussion` | The request's discussion; start it |
| `GET /conversations/{id}/candidates?q`, `POST /conversations/{id}/participants {userId}` | Add a colleague |
| `GET /requests/{id}/approvers` | `AWAITING`, `NOT_IN_APPROVAL`, `NO_APPROVER` or `SELF_ONLY`, with approvers and stages |
| `POST /requests/{id}/message-approver {recipientUserId, bodyText, clientMessageId}` | Send to the chosen current approver |
| `GET /stream` | Server-Sent Events |

Bounds: text 1 to 4,000 characters (blank refused), 30 sends a minute per user, client id 8 to 64 characters of `[A-Za-z0-9_-]`.

**Lock order.** Every transaction that writes a message or a participant first takes an update lock on the conversation row, then writes the message, then the audit chain head. Without it, simultaneous retries of one message deadlocked (the losing insert holds a shared lock on the conversation row for its foreign key while waiting for the winner's unique key; the winner needs that row) and one of them got 503 `busy`. Sends to one conversation are therefore serialised; different conversations do not wait for each other.

Audit (same transaction, ADR-006): `DIRECT_OPENED`, `DISCUSSION_STARTED`, `PARTICIPANT_ADDED` on `INTERNAL_CONVERSATION`, `POSTED` on `INTERNAL_MESSAGE`. Message text is never written to the audit log.

## Live delivery

- **Transport:** Server-Sent Events from Spring MVC (`SseEmitter`), authenticated by the session cookie; nothing in the URL. No new infrastructure (ADR-003).
- **Events carry ids only:** `ready`, `message {conversationId, messageId}` (event id `m-<messageId>`), `read {conversationId}`. The browser fetches content through the API, which re-checks access. A stream never carries text, names or request details.
- **After commit:** events are published inside the sending transaction and delivered by an `AFTER_COMMIT` listener on a small bounded executor; a message is stored before anyone is told about it. Each recipient's account, participation and request access are re-checked just before sending.
- **Resync, not replay:** on every (re)connection the stream sends `ready`; each open thread asks for messages after the newest id it holds, the list and unread count reload. Messages are merged by id, so missed or repeated events change nothing. Streams end after 30 minutes and reconnect.
- **Read marker:** set only for messages displayed (end of the list visible, tab visible), never moved back. Other tabs of the same user get a `read` event and refresh their count.
- **Limits:** 5 streams per user (the oldest closes), 500 in total; a 25-second heartbeat closes streams whose session ended (sign-out) or whose account is inactive.
- **Sign-out and account change:** the browser closes the stream and clears drafts and unsent messages, which are kept in memory only.
- **Single node:** see the next section.

## Limitation: one backend node

Live delivery works only when every browser is connected to the same backend process as the sender. Stream connections are held in that JVM's memory (`InternalMessageStream`); there is no cross-node channel.

- With two or more backend nodes, a message stored through node A is **not pushed** to a recipient whose stream is held by node B. Nothing is lost: the message is in the database, and that browser shows it at its next resync (reconnect, navigation, the 30-minute stream end, or opening the conversation). The unread badge on node B is stale until then.
- The current deployment is a single node, where this does not occur.
- **Decision needed before running more than one node:** sticky sessions plus a per-node poll of `internal_message.id` every few seconds, or a shared broker (needs an ADR under ADR-003). Until then, deploy one backend node.

## E-mail (disabled for this phase)

No e-mail is sent for internal messages, in development, tests or production. Extension point: `InternalMessagePosted` (public record). A future digest listens to it after commit, writes to the existing notification outbox and re-checks each recipient's access, as the stream does.

## Tests

`BackEnd/src/test/java/com/epi/procurement/messaging/InternalMessagingIntegrationTests.java` (SQL Server), `FrontEnd/src/app/features/internal-messaging/message-list.spec.ts`, `FrontEnd/e2e/internal-messaging.spec.ts`.
