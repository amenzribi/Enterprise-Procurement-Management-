# 10. System inventory

Generated from the source on 2026-11-15 (branch `feat/internal-messaging`, base `c7b901d` plus the uncommitted internal-messaging work). Class descriptions are the first sentence of each class comment, as written in the code; classes without a comment show none. Counts were produced by script and checked against the SQL Server catalog.

## 1. At a glance

| Item | Count |
|---|---|
| Backend modules (top-level packages) | 20 |
| Backend main Java files / lines | 209 / 24,141 |
| REST controllers / endpoints | 27 / 180 |
| Database tables / foreign keys / indexes / CHECK constraints / sequences | 74 (+ Flyway history) / 213 / 191 / 94 / 59 |
| Flyway migrations | 22 versioned (V22 uncommitted) + 6 repeatable demo scripts |
| Backend test files / `@Test` methods | 37 / 211 |
| Frontend TypeScript files (non-spec) / lines | 117 / 20,822 |
| Frontend routes (lazy) | 35 (34 lazy; the shell is eager) |
| Frontend unit spec files / Playwright specs | 32 / 18 |
| Translation keys per language (EN, FR, AR) | 1,494 |

## 2. Backend modules

| Module | Files | Lines | Responsibility | Imports from modules |
|---|---|---|---|---|
| `approval` | 18 | 3,350 | Approval circuits: rules and stages, rounds and tasks, decisions, questions, delegation and absences, reminders and escalation, one-tap approval links, exchange rates used for thresholds. | audit, identity, notification, organization, platform, request |
| `assistant` | 8 | 607 | AI assistant behind a provider-neutral port. Disabled by default; the demo profile uses a deterministic local adapter. | audit, identity, messaging, ordering, platform, request |
| `audit` | 6 | 355 | Append-only, hash-chained audit log, one chain per company plus a global chain; chain verification. | platform |
| `dashboard` | 6 | 501 | Dashboards and the ERP export of purchase orders. | audit, identity, organization, platform, request |
| `document` | 6 | 582 | Request attachments: upload, list, download, soft delete; file storage and scanner ports. | audit, identity, platform, request |
| `identity` | 37 | 3,145 | Sign-in, sessions, CSRF, current user and roles, password setup/reset/policy, login throttling, user administration, GLOBAL_ADMIN bootstrap, demo users, supplier-portal restriction filter. | audit, organization, platform |
| `importdata` | 3 | 259 | Master-data import jobs (preview and apply). | audit, identity, organization, platform, supplier |
| `logistics` | 6 | 722 | Shipments, shipment lines, milestones and shipment documents. | audit, identity, notification, organization, platform, request |
| `messaging` | 15 | 2,605 | Supplier messaging and supplier portal (ADR-011); internal employee messaging with Server-Sent Events (V22, uncommitted). | audit, identity, platform, request |
| `notification` | 11 | 843 | Transactional outbox for e-mail, in-app notifications, mail transports (file, SMTP, failing). | identity, messaging, platform |
| `ordering` | 7 | 979 | Awards, purchase orders, PO lines, payment milestones, price memory. | audit, identity, notification, organization, platform, request |
| `orgadmin` | 2 | 107 | Administration of companies, sites, departments and cost centres. | identity, organization, platform |
| `organization` | 9 | 732 | Organisation reference data: group, companies, sites, departments, cost centres; company scope. | audit, platform |
| `platform` | 14 | 807 | Shared technical code: API error handling, idempotency, rate limiter, client address, transaction locks, time helpers, scheduling configuration. | none |
| `purchasing` | 5 | 731 | Central buying queue, buyer assignment and acceptance, purchase mode, completeness returns and questions. | audit, identity, notification, organization, platform, request |
| `receipt` | 6 | 1,214 | Goods receipts, service acceptance, shortfall acceptance, supplier invoices, request closure. | audit, identity, notification, organization, platform, request |
| `request` | 36 | 5,109 | Purchase requests: drafts, lines, submission, numbering, status and stage transitions, lists and scope, request administration (archive, removal, correction), workflow views. | audit, identity, organization, platform |
| `sourcing` | 6 | 961 | RFQs, supplier invitations, quotations (including public quote links), comparison. | audit, identity, notification, organization, platform, request |
| `supplier` | 3 | 320 | Supplier register, visibility, company links and categories. | audit, identity, platform |
| `system` | 5 | 212 | System status endpoint and database probe. | none |

Imports are Java imports only. Several modules also read other modules' tables through SQL, which the Spring Modulith test cannot see; see report 02, finding A-1.

## 3. Classes by module

### `approval`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `Absences` | Service | 189 | Absence delegation (S2-AC-06): |
| `ApprovalAdminController` | Controller | 194 | Administration (ADMIN of the caller's group): |
| `ApprovalController` | Controller | 130 | Approver inbox, task actions, request tracker data, approver preview and answers to questions (S2-AC-04..11). |
| `ApprovalEngine` | Service | 499 | Approval circuits (S2-AC-02..07). |
| `ApprovalLinkController` | Controller | 97 | One-tap e-mail approval endpoints (S2-AC-07, S2DR-07, S9-AC-01/02). |
| `ApprovalLinkResolver` | Component | 45 | Creates the one-tap token when the e-mail is sent (S2CR-01): |
| `ApprovalLinks` | Service | 164 | One-tap e-mail approval (S2-AC-07, S2DR-07). |
| `ApprovalNotices` | Component | 145 | Builds and enqueues the approval notifications (S2-AC-10) with the request summary of S2-AC-07. |
| `ApprovalQueries` | Service | 403 | Read side of approvals: |
| `ApprovalReminders` | Service + job | 118 | Reminders when a task reaches its SLA and escalations at twice the SLA (S2-AC-09). |
| `ApprovalRulesAdmin` | Service | 322 | Administration of approval rules and exchange rates (S2-AC-02/03, UC-28), ADMIN of the group only. |
| `ApprovalStore` | Component | 520 | Plain-SQL access to the approval tables. |
| `ApprovalViews` | Views (DTO) | 119 | Response bodies of the approval API. |
| `ApproverResolver` | Component | 182 | Who approves a stage, who may receive a delegated task, and where an absent approver's task goes. |
| `LegacyCircuitJob` | Component | 91 | One-time, idempotent startup job (S2DR-01, QA2-R7): |
| `LinkTokens` | Component | 55 | One-tap approval tokens (S2-AC-07, S2DR-07): |
| `TestingController` | Controller | 71 |  |

### `assistant`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `AssistantController` | Controller | 60 |  |
| `AssistantModel` | Interface | 20 | Provider-neutral model port (ADR-012). |
| `AssistantService` | Service | 310 |  |
| `AssistantSettings` | Configuration | 41 |  |
| `AssistantStore` | Store (SQL) | 62 |  |
| `AssistantViews` | Views (DTO) | 30 |  |
| `DemoAssistantModel` | Component | 79 | Deterministic local adapter for tests and demo. |

### `audit`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `AuditChainVerification` | Record | 9 | Result of re-computing audit hash chains. |
| `AuditEventView` | Record | 8 | Read model of one audit event for packs and admin views. |
| `AuditHasher` | Class | 87 | Computes the SHA-256 hash of an audit event from a fixed, documented canonical serialization (DR-04). |
| `AuditLog` | Service | 228 | Appends events to the hash-chained audit trail and verifies its integrity. |
| `AuditRecord` | Record | 18 | An auditable action to append to the trail. |

### `dashboard`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `DashboardController` | Controller | 67 |  |
| `DashboardService` | Service | 163 |  |
| `DashboardViews` | Views (DTO) | 26 |  |
| `ErpExportService` | Service | 164 |  |
| `SimplePdf` | Class | 76 | Minimal PDF writer for plain-text audit packs (no third-party PDF library — ADR-003). |

### `document`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `AttachmentController` | Controller | 87 | Attachments API (S2-AC-12). |
| `AttachmentScanner` | Component | 33 | Antivirus port (spec §24, S2DR-11). |
| `Attachments` | Service | 282 | Attachments of a request or of one of its lines (S2-AC-12, S2DR-11). |
| `ContentSniffer` | Class | 117 | Decides the content type from the bytes, never from the client (S2DR-11). |
| `FileStorage` | Component | 57 | Storage port for attachment content (ADR-003: |

### `identity`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `ApiSecurityHandlers` | Class | 40 | JSON answers instead of redirects or browser authentication pop-ups (S1DR-13). |
| `AuthController` | Controller | 145 | Sign-in, sign-out and CSRF bootstrap for the SPA. |
| `AuthenticatedUser` | Record | 9 | Principal stored in the HTTP session. |
| `CredentialStore` | Component | 89 | Local password credentials. |
| `CurrentUser` | Record | 62 | The authenticated user with their organisation and role grants, resolved from the session on every request and never from client-supplied identifiers (ADR-007). |
| `CurrentUserProvider` | Component | 80 | Resolves the acting user from the authenticated session (never from request parameters or bodies). |
| `CurrentUserRefresh` | Filter | 92 | Reloads the signed-in account and its roles on every request (S1CR-02). |
| `DemoUserSeeder` | Component | 221 |  |
| `GlobalAdminBootstrap` | Component | 120 | Production one-shot bootstrap of the first GLOBAL_ADMIN when GLOBAL_ADMIN_BOOTSTRAP_PASSWORD is set and no GLOBAL_ADMIN exists yet (S10-AC-03). |
| `LoginThrottle` | Class | 156 | Sign-in throttling instead of account locking (S1DR-03): |
| `MeAssembler` | Component | 126 | Builds MeResponse, including the coarse permissions the UI uses to show or hide actions. |
| `MeController` | Controller | 57 |  |
| `MeResponse` | Record | 18 | The signed-in user as returned by GET /api/v1/me and POST /api/v1/auth/login. |
| `MustChangePasswordFilter` | Filter | 43 | When must_change_password is set, only identity bootstrap endpoints remain available (S10-AC-10). |
| `PasswordController` | Controller | 66 |  |
| `PasswordPolicy` | Component | 39 | Password strength rules for Sprint 10. |
| `PasswordResetStore` | Store (SQL) | 57 |  |
| `PasswordResetTokens` | Component | 59 | Creates password setup/reset tokens (S10-AC-04). |
| `PasswordService` | Service | 135 | Own password change, forgot-password request and public token redeem (S10-AC-04/05/09). |
| `PasswordSetupRequested` | Record | 8 | Published when an administrator issues a password setup or reset e-mail (handled by the notification module). |
| `ProblemResponses` | Class | 35 | Writes RFC 9457 problem details from security filters, which run outside Spring MVC's exception handling. |
| `Role` | Enum | 44 | Roles of spec §20 / Solution Guide v3.0 §4 implemented so far. |
| `RoleGrant` | Record | 13 | A role held in a company (and optionally a department). |
| `SecurityConfiguration` | Configuration | 144 | Session-based security for the Angular SPA (sprint-1 design, S1DR-01/02/13). |
| `SecuritySettings` | Configuration | 32 | Security parameters (app.security. |
| `SessionCredentials` | Class | 24 | Session-bound credentials epoch (S10-AC-05). |
| `SessionTimeouts` | Filter | 83 | Enforces the idle and absolute session timeouts with the application clock (S1-AC-01, S1DR-12), so both are testable and independent of when the session obje... |
| `SignInService` | Class | 81 | Decides whether a sign-in attempt succeeds (S1-AC-01, S1DR-03/04/05). |
| `SupplierPortalRestrictionFilter` | Filter | 50 | SUPPLIER_CONTACT principals may only use the supplier portal messaging APIs and identity bootstrap endpoints (ADR-011). |
| `UserAdminController` | Controller | 88 | User administration API (Sprint 10). |
| `UserAdminService` | Service | 390 | Installation and company-scoped user administration (Sprint 10). |
| `UserAdminStore` | Store (SQL) | 236 |  |
| `UserAdminViews` | Views (DTO) | 46 |  |
| `UserDirectory` | Service | 220 | Read access to user accounts and their role grants. |
| `UserPreferences` | Service | 24 |  |
| `UserSummary` | Record | 8 | Public view of a user account for other modules (e.g. |

### `importdata`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `ImportController` | Controller | 48 |  |
| `ImportService` | Service | 206 |  |

### `logistics`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `LogisticsController` | Controller | 74 |  |
| `LogisticsService` | Service | 353 |  |
| `LogisticsStore` | Store (SQL) | 186 |  |
| `LogisticsViews` | Views (DTO) | 46 |  |
| `ShipmentNumbering` | Component | 58 | Shipment numbers {COMPANY-SH-{YYYY}-{seq6}}. |

### `messaging`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `InternalMessagePosted` | Record | 17 | Published inside the transaction that stored an internal message or a participant change; delivered to live streams only after that transaction commits (Inte... |
| `InternalMessageStream` | Service + job | 222 | Live delivery for internal messaging over Server-Sent Events (docs/architecture/internal-messaging.md). |
| `InternalMessagingController` | Controller | 133 | Internal employee messaging API (docs/architecture/internal-messaging.md). |
| `InternalMessagingService` | Service | 689 | Internal employee messaging (docs/architecture/internal-messaging.md). |
| `InternalMessagingStore` | Store (SQL) | 407 | SQL for internal messaging (V22). |
| `InternalMessagingViews` | Views (DTO) | 94 | JSON shapes of the internal messaging API (docs/architecture/internal-messaging.md). |
| `InternalReadMarked` | Record | 7 | A user moved their read marker. |
| `MessagePosted` | Record | 8 | Published after a message is saved so notifications can notify peers without coupling modules. |
| `MessagingAssistantQueries` | Service | 64 | Read-only messaging lookups for the internal assistant (Sprint 12). |
| `MessagingController` | Controller | 102 |  |
| `MessagingService` | Service | 404 |  |
| `MessagingStore` | Store (SQL) | 345 |  |
| `MessagingViews` | Views (DTO) | 41 |  |
| `SupplierPortalController` | Controller | 67 |  |

### `notification`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `MailTransport` | Configuration | 125 | Sends one rendered e-mail. |
| `MessagePostedListener` | Component | 77 | Notifies peers after a message commits. |
| `Notice` | Record | 30 | A notification to one user, rendered in the recipient's language when enqueued. |
| `NoticeLinkResolver` | Interface | 28 | Resolves a deferred link placeholder ({{link:kind:arg:...}}) when the e-mail is sent, so secrets such as one-tap approval tokens never sit in the outbox (S2C... |
| `NotificationController` | Controller | 107 | In-app notifications of the signed-in user (S2-AC-10). |
| `NotificationTemplates` | Component | 136 | Renders notifications from the AR/FR/EN message bundles in notification/messages .properties. |
| `Notifications` | Service | 92 | Enqueues notifications: |
| `OutboxSender` | Service + job | 176 | Sends pending outbox e-mails, at least once (S2-AC-10, S2CR-06): |
| `PasswordResetLinkResolver` | Component | 35 | Creates the password setup/reset token when the e-mail is sent (same deferred-secret pattern as approval links). |
| `PasswordResetMailListener` | Component | 31 |  |

### `ordering`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `OrderingAssistantQueries` | Service | 106 | Read-only PO lookups for the internal assistant (Sprint 12). |
| `OrderingController` | Controller | 92 |  |
| `OrderingService` | Service | 380 |  |
| `OrderingStore` | Store (SQL) | 290 |  |
| `OrderingViews` | Views (DTO) | 42 |  |
| `PoNumbering` | Component | 64 | PO numbers {COMPANY_CODE-PO-{YYYY}-{seq6}} from document_counter kind PURCHASE_ORDER. |

### `orgadmin`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `OrganizationAdminController` | Controller | 101 |  |

### `organization`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `Company` | Entity | 155 | A legal entity of the group. |
| `CompanyRepository` | Repository | 11 |  |
| `CompanyType` | Enum | 7 |  |
| `ExchangeRates` | Service | 105 | Exchange rates of a group (S2-AC-03, S2DR-06). |
| `OrganizationAdmin` | Service | 237 |  |
| `OrganizationDirectory` | Service | 115 | Read-only organisation lookups for other modules (companies, departments, sites, cost centres). |
| `PurchasingMode` | Enum | 10 | How a company buys (spec §3.7). |
| `WorkingCalendar` | Record | 87 | Working week of a company in its own time zone (S2DR-10), used for approval SLAs: |

### `platform`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `config/DatabaseConfigurationGuard` | Configuration | 35 | Fails fast with an actionable message when no database URL is configured. |
| `config/SchedulingConfiguration` | Configuration | 16 | Background jobs (outbox e-mails, approval reminders). |
| `persistence/SqlServerErrors` | Class | 36 | Classification of SQL Server errors by native error number, independent of how JDBC, Hibernate or Spring wrapped them. |
| `persistence/TransactionLocks` | Component | 79 | Named exclusive locks held until the current transaction ends (SQL Server sp_getapplock). |
| `persistence/UtcTimes` | Class | 27 | Instants in plain JDBC code: |
| `time/AdjustableClock` | Configuration | 57 | System UTC time plus an adjustable offset, for manual QA of SLAs, reminders and link expiry (test hook A1). |
| `web/ApiExceptionHandler` | Controller | 141 | Renders every API error as an RFC 9457 problem detail with a stable code property the frontend can translate. |
| `web/ApiProblemException` | Exception | 63 | A business error rendered as an RFC 9457 problem detail with a stable, translatable code. |
| `web/ClientAddressResolver` | Component | 115 | Resolves the client address for rate limiting (S2CR-08 / S9-AC-01). |
| `web/FieldViolation` | Record | 23 | One invalid field. |
| `web/Idempotency` | Component | 137 | Idempotent state-changing actions (S2-AC-13, DR-21). |
| `web/RequestRateLimiter` | Component | 48 | In-memory sliding fixed-window limiter for public link endpoints (S9-AC-02). |
| `web/ValidationFailedException` | Exception | 25 | 422 validation-failed with the list of invalid fields in the errors property. |

### `purchasing`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `BuyerAssignmentStore` | Store (SQL) | 94 |  |
| `PurchasingController` | Controller | 131 |  |
| `PurchasingService` | Service | 460 |  |
| `PurchasingViews` | Views (DTO) | 41 |  |

### `receipt`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `ReceiptController` | Controller | 89 |  |
| `ReceiptNumbering` | Component | 58 | Receipt numbers {COMPANY-GR-{YYYY}-{seq6}}. |
| `ReceiptService` | Service | 702 |  |
| `ReceiptStore` | Store (SQL) | 285 |  |
| `ReceiptViews` | Views (DTO) | 75 |  |

### `request`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `Origin` | Enum | 11 | Internal (spending) or external (customer, sales) request (spec §3.5). |
| `Priority` | Enum | 7 |  |
| `PurchaseRequest` | Entity | 625 | A purchase request (spec §6.1, §23) with its lines. |
| `PurchaseRequestLine` | Entity | 192 |  |
| `PurchaseRequestRepository` | Repository | 27 | Unscoped access to purchase requests. |
| `PurchaseType` | Enum | 10 | Local or international purchase (spec §9.1), which decides the sourcing circuit later. |
| `RequestAccessPolicy` | Component | 148 | Who may see and change purchase requests in Sprint 1 (docs/product/sprint-1.md, visibility table; ADR-007). |
| `RequestAdminActions` | Class | 239 | Administrative action matrix for request administration. |
| `RequestAdminController` | Controller | 126 | Request administration — GLOBAL_ADMIN (full) and company ADMIN (draft scope). |
| `RequestAdminLinks` | Component | 87 | Linked purchasing evidence for administrative inspection (counts and light listings). |
| `RequestAdminService` | Service | 810 | Request administration: |
| `RequestAdminViews` | Views (DTO) | 99 | DTOs for request administration (/api/v1/admin/requests). |
| `RequestAdministrativelyCancelledEvent` | Record | 11 | Published when GLOBAL_ADMIN cancels or returns a request administratively. |
| `RequestAssistantQueries` | Service | 80 | Read-only request lookups for the internal assistant (Sprint 12). |
| `RequestController` | Controller | 191 |  |
| `RequestLogisticsQueries` | Service | 107 | Request-module SQL for the logistics confirmed-PO inbox. |
| `RequestMapper` | Component | 150 | Builds API views from entities. |
| `RequestMessagingQueries` | Component | 53 | What internal messaging may know about a request, for one user (docs/architecture/internal-messaging.md). |
| `RequestNumbering` | Component | 72 | Request numbers {COMPANY_CODE-PR-{YYYY}-{seq6}} from document_counter, inside the submitting transaction. |
| `RequestParticipant` | Entity | 72 | A user taking part in a request beyond its authors (approver, delegate). |
| `RequestParticipantRepository` | Repository | 7 |  |
| `RequestPayload` | Record | 58 | Body of POST /api/v1/requests (create draft) and PUT /api/v1/requests/{id} (replace draft). |
| `RequestReferenceData` | Component | 67 | Reference data used by requests: |
| `RequestService` | Service | 496 | Drafting, submission and lookup of purchase requests. |
| `RequestSnapshot` | Record | 40 | A read-only view of a purchase request for other modules (approval, document). |
| `RequestStatus` | Enum | 78 | One ordered status list shared by request headers and lines (S1DR-08). |
| `RequestSubmittedEvent` | Record | 14 | Published synchronously inside the submission transaction, after the request row is locked and moved to IN_APPROVAL and before the audit event (lock order re... |
| `RequestTransition` | Entity | 117 | One row of a request's stage timeline (S1DR-09), written with the status change and its audit event. |
| `RequestTransitionRepository` | Repository | 11 |  |
| `RequestValidator` | Component | 232 | Field rules for purchase requests. |
| `RequestViews` | Views (DTO) | 85 | Response bodies of the request API. |
| `RequestWorkflow` | Service | 644 | The request module's API for other modules (approval, document). |
| `ScopedRequests` | Component | 116 | The only way to read purchase requests: |
| `Stage` | Enum | 10 | Lifecycle stages of spec §5 ("Étape"), derived from the status. |
| `UserStatus` | Enum | 12 | The status shown to users (Solution Guide v3.0 §8): |

### `sourcing`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `QuoteLinkController` | Controller | 72 | Anonymous supplier quote link (S4-AC-03, S9-AC-02). |
| `SourcingController` | Controller | 97 |  |
| `SourcingService` | Service | 484 |  |
| `SourcingStore` | Store (SQL) | 224 |  |
| `SourcingViews` | Views (DTO) | 79 |  |

### `supplier`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `SupplierController` | Controller | 71 |  |
| `SupplierService` | Service | 244 |  |

### `system`

| Class | Type | Lines | Responsibility (from the class comment) |
|---|---|---|---|
| `DatabaseProbe` | Interface | 13 | Checks database connectivity without ever throwing. |
| `JdbcDatabaseProbe` | Component | 116 | Two cheap, time-limited queries per check: |
| `SystemStatus` | Record | 26 | Snapshot returned by GET /api/v1/system/status. |
| `SystemStatusController` | Controller | 52 |  |

## 4. REST endpoints

All under `/api/v1`. Authorization for each is covered in report 03.


**`approval/ApprovalAdminController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/admin/companies` | 55 |
| GET | `/api/v1/admin/users` | 68 |
| GET | `/api/v1/admin/approval-rules` | 85 |
| GET | `/api/v1/admin/approval-rules/{id}` | 90 |
| GET | `/api/v1/admin/approval-rules/{id}/versions` | 95 |
| POST | `/api/v1/admin/approval-rules` | 100 |
| PUT | `/api/v1/admin/approval-rules/{id}` | 109 |
| POST | `/api/v1/admin/approval-rules/{id}/activate` | 114 |
| POST | `/api/v1/admin/approval-rules/{id}/retire` | 119 |
| GET | `/api/v1/admin/exchange-rates` | 124 |
| POST | `/api/v1/admin/exchange-rates` | 129 |
| GET | `/api/v1/admin/approvals/awaiting-circuit` | 134 |
| GET | `/api/v1/admin/absences` | 139 |
| POST | `/api/v1/admin/absences` | 144 |
| DELETE | `/api/v1/admin/absences/{id}` | 157 |
| GET | `/api/v1/me/absences` | 163 |
| POST | `/api/v1/me/absences` | 168 |
| DELETE | `/api/v1/me/absences/{id}` | 176 |
| GET | `/api/v1/me/absences/delegates` | 185 |

**`approval/ApprovalController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/approvals/inbox` | 43 |
| GET | `/api/v1/approvals/inbox/count` | 50 |
| POST | `/api/v1/approvals/tasks/{id}/approve` | 55 |
| POST | `/api/v1/approvals/tasks/{id}/reject` | 61 |
| POST | `/api/v1/approvals/tasks/{id}/return` | 67 |
| POST | `/api/v1/approvals/tasks/{id}/question` | 73 |
| POST | `/api/v1/approvals/tasks/{id}/delegate` | 79 |
| GET | `/api/v1/approvals/tasks/{id}/delegates` | 86 |
| GET | `/api/v1/requests/{id}/approval` | 91 |
| GET | `/api/v1/requests/{id}/approval-preview` | 96 |
| POST | `/api/v1/requests/{id}/answers` | 101 |

**`approval/ApprovalLinkController.java`**

| Method | Path | Line |
|---|---|---|
| POST | `/api/v1/approval-links/summary` | 56 |
| POST | `/api/v1/approval-links/approve` | 64 |

**`approval/TestingController.java`**

| Method | Path | Line |
|---|---|---|
| POST | `/api/v1/testing/jobs/run` | 44 |
| POST | `/api/v1/testing/clock` | 52 |

**`assistant/AssistantController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/assistant/status` | 41 |
| GET | `/api/v1/assistant/threads` | 46 |
| POST | `/api/v1/assistant/ask` | 51 |

**`dashboard/DashboardController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/dashboards/pipeline` | 30 |
| GET | `/api/v1/dashboards/management` | 35 |
| GET | `/api/v1/dashboards/requests/{requestId}/audit-pack` | 40 |
| GET | `/api/v1/dashboards/exports/requests.csv` | 49 |
| GET | `/api/v1/dashboards/exports/purchase-orders.csv` | 58 |

**`document/AttachmentController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/requests/{id}/attachments` | 43 |
| POST | `/api/v1/requests/{id}/attachments` | 48 |
| GET | `/api/v1/attachments/{id}/content` | 67 |
| DELETE | `/api/v1/attachments/{id}` | 81 |

**`identity/AuthController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/auth/csrf` | 80 |
| POST | `/api/v1/auth/login` | 86 |
| POST | `/api/v1/auth/logout` | 114 |

**`identity/MeController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/me` | 36 |
| PUT | `/api/v1/me/preferences` | 44 |

**`identity/PasswordController.java`**

| Method | Path | Line |
|---|---|---|
| POST | `/api/v1/auth` | 26 |
| POST | `/api/v1/auth/password-reset/request` | 33 |
| POST | `/api/v1/auth/password-reset/redeem` | 39 |

**`identity/UserAdminController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/admin/accounts` | 32 |
| GET | `/api/v1/admin/accounts/{id}` | 39 |
| POST | `/api/v1/admin/accounts` | 44 |
| PUT | `/api/v1/admin/accounts/{id}` | 50 |
| POST | `/api/v1/admin/accounts/{id}/active` | 55 |
| POST | `/api/v1/admin/accounts/{id}/roles` | 60 |
| POST | `/api/v1/admin/accounts/{id}/roles/{grantId}/remove` | 65 |
| POST | `/api/v1/admin/accounts/{id}/password-reset` | 70 |
| POST | `/api/v1/admin/accounts/{id}/temporary-password` | 76 |
| POST | `/api/v1/admin/accounts/{id}/revoke-sessions` | 82 |

**`importdata/ImportController.java`**

| Method | Path | Line |
|---|---|---|
| POST | `/api/v1/imports` | 34 |
| GET | `/api/v1/imports/{id}` | 44 |

**`logistics/LogisticsController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/logistics/inbox` | 35 |
| GET | `/api/v1/logistics/requests/{requestId}/shipments` | 42 |
| POST | `/api/v1/logistics/requests/{requestId}/shipments` | 47 |
| GET | `/api/v1/logistics/shipments/{shipmentId}` | 58 |
| PUT | `/api/v1/logistics/shipments/{shipmentId}/milestones/{milestoneId}` | 63 |
| POST | `/api/v1/logistics/shipments/{shipmentId}/documents` | 69 |

**`messaging/InternalMessagingController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/internal-messaging/directory` | 44 |
| GET | `/api/v1/internal-messaging/conversations` | 50 |
| POST | `/api/v1/internal-messaging/conversations/direct` | 55 |
| GET | `/api/v1/internal-messaging/conversations/{id}` | 60 |
| GET | `/api/v1/internal-messaging/conversations/{id}/messages` | 65 |
| POST | `/api/v1/internal-messaging/conversations/{id}/messages` | 72 |
| POST | `/api/v1/internal-messaging/conversations/{id}/read` | 79 |
| GET | `/api/v1/internal-messaging/conversations/{id}/candidates` | 85 |
| POST | `/api/v1/internal-messaging/conversations/{id}/participants` | 90 |
| GET | `/api/v1/internal-messaging/unread` | 96 |
| GET | `/api/v1/internal-messaging/requests/{requestId}/discussion` | 101 |
| POST | `/api/v1/internal-messaging/requests/{requestId}/discussion` | 106 |
| GET | `/api/v1/internal-messaging/requests/{requestId}/approvers` | 111 |
| POST | `/api/v1/internal-messaging/requests/{requestId}/message-approver` | 116 |
| GET | `/api/v1/internal-messaging/stream` | 124 |

**`messaging/MessagingController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/messaging/conversations` | 35 |
| POST | `/api/v1/messaging/conversations` | 40 |
| GET | `/api/v1/messaging/conversations/{id}` | 49 |
| GET | `/api/v1/messaging/conversations/{id}/messages` | 54 |
| POST | `/api/v1/messaging/conversations/{id}/messages` | 59 |
| POST | `/api/v1/messaging/conversations/{id}/read` | 70 |
| GET | `/api/v1/messaging/suppliers/{supplierId}/contacts` | 76 |
| POST | `/api/v1/messaging/suppliers/{supplierId}/contacts` | 81 |
| POST | `/api/v1/messaging/contacts/{contactId}/revoke` | 89 |

**`messaging/SupplierPortalController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/supplier-portal/conversations` | 35 |
| GET | `/api/v1/supplier-portal/conversations/{id}` | 40 |
| GET | `/api/v1/supplier-portal/conversations/{id}/messages` | 45 |
| POST | `/api/v1/supplier-portal/conversations/{id}/messages` | 50 |
| POST | `/api/v1/supplier-portal/conversations/{id}/read` | 61 |

**`notification/NotificationController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/notifications` | 44 |
| GET | `/api/v1/notifications/unread-count` | 72 |
| POST | `/api/v1/notifications/{id}/read` | 80 |
| POST | `/api/v1/notifications/read-all` | 90 |

**`ordering/OrderingController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/ordering/requests/{requestId}/award` | 34 |
| POST | `/api/v1/ordering/requests/{requestId}/award` | 43 |
| GET | `/api/v1/ordering/requests/{requestId}/purchase-order` | 51 |
| POST | `/api/v1/ordering/requests/{requestId}/purchase-order` | 60 |
| PUT | `/api/v1/ordering/purchase-orders/{poId}` | 69 |
| POST | `/api/v1/ordering/purchase-orders/{poId}/issue` | 74 |
| POST | `/api/v1/ordering/purchase-orders/{poId}/confirm` | 83 |

**`orgadmin/OrganizationAdminController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/admin/organization/companies` | 37 |
| POST | `/api/v1/admin/organization/companies` | 42 |
| PUT | `/api/v1/admin/organization/companies/{id}` | 50 |
| GET | `/api/v1/admin/organization/companies/{id}/sites` | 58 |
| POST | `/api/v1/admin/organization/companies/{id}/sites` | 63 |
| GET | `/api/v1/admin/organization/companies/{id}/departments` | 71 |
| POST | `/api/v1/admin/organization/companies/{id}/departments` | 76 |
| GET | `/api/v1/admin/organization/companies/{id}/cost-centres` | 84 |
| POST | `/api/v1/admin/organization/companies/{id}/cost-centres` | 89 |

**`purchasing/PurchasingController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/purchasing/queue` | 38 |
| GET | `/api/v1/purchasing/queue/filter-options` | 58 |
| GET | `/api/v1/purchasing/queue/count` | 63 |
| GET | `/api/v1/purchasing/buyers` | 68 |
| GET | `/api/v1/purchasing/requests/{id}/assignments` | 73 |
| POST | `/api/v1/purchasing/requests/{id}/assign` | 78 |
| POST | `/api/v1/purchasing/requests/{id}/accept` | 86 |
| POST | `/api/v1/purchasing/requests/{id}/return-assignment` | 94 |
| POST | `/api/v1/purchasing/requests/{id}/purchase-mode` | 102 |
| POST | `/api/v1/purchasing/requests/{id}/line-classification` | 110 |
| POST | `/api/v1/purchasing/requests/{id}/completeness-return` | 115 |
| POST | `/api/v1/purchasing/requests/{id}/question` | 123 |

**`receipt/ReceiptController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/receipts/requests/{requestId}` | 32 |
| POST | `/api/v1/receipts/requests/{requestId}/goods-receipts` | 37 |
| POST | `/api/v1/receipts/goods-receipts/{receiptId}/confirm` | 46 |
| POST | `/api/v1/receipts/requests/{requestId}/service-acceptance` | 52 |
| POST | `/api/v1/receipts/requests/{requestId}/invoice` | 57 |
| POST | `/api/v1/receipts/requests/{requestId}/shortfall-acceptance` | 65 |
| POST | `/api/v1/receipts/requests/{requestId}/invoice/paid` | 70 |
| POST | `/api/v1/receipts/requests/{requestId}/invoice/match-attested` | 75 |
| POST | `/api/v1/receipts/requests/{requestId}/close` | 80 |

**`request/RequestAdminController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/admin/requests` | 34 |
| GET | `/api/v1/admin/requests/{id}` | 42 |
| POST | `/api/v1/admin/requests/{id}/archive` | 47 |
| POST | `/api/v1/admin/requests/{id}/restore` | 52 |
| POST | `/api/v1/admin/requests/{id}/cancel` | 57 |
| POST | `/api/v1/admin/requests/{id}/return-for-revision` | 62 |
| POST | `/api/v1/admin/requests/{id}/correct-draft` | 68 |
| POST | `/api/v1/admin/requests/{id}/correct-submitted` | 74 |
| POST | `/api/v1/admin/requests/{id}/remove` | 80 |
| POST | `/api/v1/admin/requests/{id}/restore-removed` | 85 |
| DELETE | `/api/v1/admin/requests/{id}` | 91 |
| POST | `/api/v1/admin/requests/cleanup/preview` | 98 |
| POST | `/api/v1/admin/requests/cleanup/execute` | 105 |
| GET | `/api/v1/admin/requests/action-matrix` | 110 |

**`request/RequestController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/requests/form-options` | 52 |
| GET | `/api/v1/requests/statuses` | 57 |
| GET | `/api/v1/requests` | 73 |
| GET | `/api/v1/requests/{id}` | 91 |
| POST | `/api/v1/requests` | 96 |
| PUT | `/api/v1/requests/{id}` | 102 |
| POST | `/api/v1/requests/{id}/submit` | 107 |
| DELETE | `/api/v1/requests/{id}` | 115 |

**`sourcing/QuoteLinkController.java`**

| Method | Path | Line |
|---|---|---|
| POST | `/api/v1/quote-links/summary` | 44 |
| POST | `/api/v1/quote-links/submit` | 50 |

**`sourcing/SourcingController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/sourcing/requests/{requestId}/rfq` | 33 |
| POST | `/api/v1/sourcing/requests/{requestId}/rfq` | 42 |
| POST | `/api/v1/sourcing/rfqs/{rfqId}/suppliers/{rfqSupplierId}/link` | 50 |
| POST | `/api/v1/sourcing/rfqs/{rfqId}/quotes` | 57 |
| GET | `/api/v1/sourcing/rfqs/{rfqId}/comparison` | 65 |
| POST | `/api/v1/sourcing/rfqs/{rfqId}/close` | 70 |
| POST | `/api/v1/sourcing/requests/{requestId}/validate-technical` | 78 |
| POST | `/api/v1/sourcing/requests/{requestId}/validate-commercial` | 88 |

**`supplier/SupplierController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/suppliers` | 34 |
| GET | `/api/v1/suppliers/{id}` | 41 |
| POST | `/api/v1/suppliers` | 46 |
| PUT | `/api/v1/suppliers/{id}` | 54 |
| POST | `/api/v1/suppliers/{id}/deactivate` | 62 |

**`system/SystemStatusController.java`**

| Method | Path | Line |
|---|---|---|
| GET | `/api/v1/system/status` | 40 |

## 5. Database tables

| Table | Migration | Columns | Purpose |
|---|---|---|---|
| `country` | V1 | 3 | ISO country reference |
| `currency` | V1 | 4 | ISO-4217 currency reference with minor units |
| `organization_group` | V1 | 7 | The group that owns companies; consolidation currency |
| `company` | V1 | 20 | Legal entities of the group: type, currency, language, time zone, purchasing mode |
| `site` | V1 | 11 | Sites of a company |
| `department` | V1 | 9 | Departments of a company |
| `cost_center` | V1 | 9 | Cost centres of a company |
| `user_account` | V1 | 14 | Users: home company, site, department, language, active and demo flags |
| `document_counter` | V1 | 4 | Per company, kind and year counters for document numbers |
| `audit_chain_head` | V1 | 5 | Last sequence and hash of each audit chain; the row locked on every audit write |
| `audit_event` | V1 | 16 | Append-only, hash-chained audit events |
| `user_credential` | V4 | 4 | Password hash, must-change flag |
| `user_role` | V4 | 6 | Role grants per user, optionally scoped to a company and department |
| `unit_of_measure` | V4 | 4 | Units of measure reference |
| `request_category` | V4 | 7 | Purchase categories of a group |
| `purchase_request` | V4 | 53 | Purchase request header: number, status, stage, companies, owner, amounts, archive and removal flags |
| `purchase_request_line` | V4 | 15 | Request lines: description, quantity, unit, estimated price, goods or service kind |
| `purchase_request_transition` | V4 | 10 | Status and stage history of a request (timeline) |
| `purchase_request_participant` | V5 | 4 | Users who gained visibility of a request through an action (approver, buyer, ...) |
| `exchange_rate` | V5 | 9 | Exchange rates used to compare amounts with approval thresholds |
| `approval_rule` | V5 | 14 | Versioned approval rules per group, company, category and purchase type |
| `approval_rule_stage` | V5 | 10 | Stages of a rule: approver type, policy, threshold, SLA, escalation |
| `approval_round` | V5 | 16 | One approval run of a request with the amounts and rate frozen at submission |
| `approval_task` | V5 | 23 | A task for one approver in a round: assignee, delegation origin, status, SLA times |
| `approval_task_event` | V5 | 13 | History of a task: decisions, questions, delegation, reassignment |
| `approval_task_reminder` | V5 | 3 | Reminders and escalations already sent for a task |
| `approval_link_token` | V5 | 7 | Hashed one-tap approval link tokens |
| `user_absence` | V5 | 10 | Absences and delegates |
| `notification_outbox` | V5 | 16 | Transactional outbox of e-mails with claim and retry state |
| `in_app_notification` | V5 | 9 | In-app notifications per user |
| `idempotency_record` | V5 | 6 | Stored result of a write per actor and idempotency key |
| `attachment` | V5 | 15 | Request attachments: storage key, hash, content type, scan status, soft delete |
| `buyer_assignment` | V7 | 9 | Assignment of a request to a buyer and its status |
| `supplier` | V7 | 15 | Supplier register with visibility |
| `supplier_company` | V7 | 2 | Which companies may use a supplier |
| `supplier_category` | V7 | 2 | Categories a supplier serves |
| `request_supplier_ref` | V7 | 2 | Suppliers suggested on a request |
| `import_job` | V7 | 13 | Master-data import jobs |
| `import_row` | V7 | 7 | Rows of an import job with their validation result |
| `buyer_category_affinity` | V8 | 3 | Preferred buyers per category |
| `rfq` | V9 | 12 | Requests for quotation |
| `rfq_line` | V9 | 7 | RFQ lines linked to request lines |
| `rfq_supplier` | V9 | 4 | Suppliers invited to an RFQ |
| `quotation` | V9 | 19 | Supplier quotations with cost components |
| `quotation_line` | V9 | 6 | Quotation lines |
| `quote_link_token` | V9 | 6 | Hashed tokens for public supplier quote links |
| `award` | V10 | 19 | Award decision with landed cost |
| `award_line` | V10 | 10 | Awarded lines |
| `purchase_order` | V10 | 28 | Purchase orders: number, status, supplier, amounts |
| `purchase_order_line` | V10 | 10 | Purchase order lines |
| `po_payment_milestone` | V10 | 7 | Payment milestones of a purchase order |
| `price_memory` | V10 | 9 | Last awarded prices per supplier and category |
| `shipment` | V11 | 15 | Shipments of a purchase order |
| `shipment_line` | V11 | 8 | Shipped quantities per order line |
| `shipment_milestone` | V11 | 7 | Planned and actual dates of shipment milestones |
| `shipment_document` | V11 | 11 | Documents attached to a shipment |
| `goods_receipt` | V12 | 14 | Goods receipts with the two-person confirmation |
| `goods_receipt_line` | V12 | 13 | Received and accepted quantities per shipment line |
| `receipt_evidence` | V12 | 9 | Evidence files of a receipt |
| `service_acceptance` | V12 | 6 | Acceptance of service lines |
| `supplier_invoice` | V12 | 12 | Supplier invoices recorded against an order |
| `supplier_score` | V12 | 8 | Supplier evaluation at closure |
| `password_reset_token` | V13 | 8 | Hashed password setup and reset tokens |
| `identity_bootstrap` | V13 | 3 | Marker of the one-time GLOBAL_ADMIN bootstrap |
| `supplier_contact` | V14 | 9 | Supplier contacts invited to the portal |
| `conversation` | V14 | 12 | Supplier conversations (ADR-011) |
| `conversation_participant` | V14 | 6 | Participants of a supplier conversation |
| `conversation_message` | V14 | 7 | Supplier conversation messages with INTERNAL or EXTERNAL audience |
| `assistant_thread` | V15 | 6 | Assistant threads per user |
| `assistant_turn` | V15 | 9 | Assistant questions and answers |
| `shortfall_acceptance` | V19 | 5 | Decision to close with a quantity shortfall |
| `internal_conversation` | V22 | 10 | Internal employee conversations: direct or request discussion (V22) |
| `internal_participant` | V22 | 7 | Participants and read markers of internal conversations (V22) |
| `internal_message` | V22 | 8 | Internal messages with client message id for idempotency (V22) |

Column counts are as created plus later `ALTER TABLE ... ADD`. Keys are `BIGINT` from sequences (59 sequences). Constraint and index findings are in report 05.

## 6. Scheduled jobs, listeners and filters

| Item | Location | What it does |
|---|---|---|
| Approval reminders | `approval/ApprovalReminders.java:55` | Every minute (`app.jobs.reminder-delay`): reminders and escalations for overdue approval tasks |
| Outbox sender | `notification/OutboxSender.java:73` | Every 15 s: claims up to 20 pending e-mails (`UPDLOCK, READPAST`), sends, marks sent or schedules a retry |
| Outbox purge | `notification/OutboxSender.java:78` | Every 6 h |
| Stream heartbeat | `messaging/InternalMessageStream.java:143` | Every 25 s: keeps live streams open, closes those of ended sessions or inactive accounts |
| Message delivery | `messaging/InternalMessageStream.java:106,128` | After-commit listeners for `InternalMessagePosted` and `InternalReadMarked` |
| Filter `CurrentUserRefresh` | `identity/CurrentUserRefresh.java` | Reloads the signed-in account and its roles on every request (S1CR-02). |
| Filter `MustChangePasswordFilter` | `identity/MustChangePasswordFilter.java` | When must_change_password is set, only identity bootstrap endpoints remain available (S10-AC-10). |
| Filter `SessionTimeouts` | `identity/SessionTimeouts.java` | Enforces the idle and absolute session timeouts with the application clock (S1-AC-01, S1DR-12), so both are testable and independent of when the session obje... |
| Filter `SupplierPortalRestrictionFilter` | `identity/SupplierPortalRestrictionFilter.java` | SUPPLIER_CONTACT principals may only use the supplier portal messaging APIs and identity bootstrap endpoints (ADR-011). |

## 7. Dependencies

**Backend (`BackEnd/pom.xml`)**

| Dependency | Version | Use |
|---|---|---|
| spring-boot-starter-parent | 4.1.2 | Platform and dependency management |
| Java | 21 | Language level |
| spring-boot-starter-webmvc | managed | REST API on embedded Tomcat |
| spring-boot-starter-security | managed | Session authentication, CSRF, headers |
| spring-boot-starter-data-jpa | managed | Hibernate 7 for the request aggregate (5 entities, 4 repositories); 40 classes use `JdbcTemplate` |
| spring-boot-starter-flyway + flyway-sqlserver | managed | Schema migrations |
| spring-boot-starter-validation | managed | Bean validation |
| spring-boot-starter-mail | managed | SMTP transport (production mode) |
| spring-boot-starter-actuator | managed | `health` and `info` only |
| spring-modulith-starter-core | 2.1.1 | Module structure and its verification test |
| springdoc-openapi-starter-webmvc-ui | 3.1.1 | OpenAPI and Swagger UI, enabled by the dev profile only |
| mssql-jdbc (+ mssql-jdbc_auth 13.4.0 native DLL) | managed | SQL Server driver; the DLL is for Windows integrated authentication |

**Frontend (`FrontEnd/package.json`)**

| Package | Version | Kind |
|---|---|---|
| @angular/cdk | ^22.2.4 | runtime |
| @angular/common | ^22.2.0 | runtime |
| @angular/compiler | ^22.2.0 | runtime |
| @angular/core | ^22.2.0 | runtime |
| @angular/forms | ^22.2.0 | runtime |
| @angular/platform-browser | ^22.2.0 | runtime |
| @angular/router | ^22.2.0 | runtime |
| @fontsource-variable/bricolage-grotesque | 5.3.0 | runtime |
| @fontsource-variable/ibm-plex-sans | 5.3.0 | runtime |
| @fontsource/ibm-plex-sans-arabic | ^5.3.0 | runtime |
| @ngx-translate/core | ^18.0.0 | runtime |
| @ngx-translate/http-loader | ^18.0.0 | runtime |
| lucide | ^1.61.0 | runtime |
| rxjs | ~7.8.0 | runtime |
| tslib | ^2.3.0 | runtime |
| @angular/build | ^22.2.4 | development |
| @angular/cli | ^22.2.4 | development |
| @angular/compiler-cli | ^22.2.0 | development |
| @axe-core/playwright | ^4.14.0 | development |
| @eslint/js | ^10.0.1 | development |
| @playwright/test | ^1.64.1 | development |
| angular-eslint | 22.6.0 | development |
| eslint | ^10.12.0 | development |
| jsdom | ^30.0.0 | development |
| prettier | ^3.8.1 | development |
| typescript | ~6.0.2 | development |
| typescript-eslint | 8.71.0 | development |
| vitest | ^5.0.0 | development |

Not present: GSAP, ScrollTrigger, Lenis, React, any charting or table library, any state-management library.

## 8. Frontend structure

| Folder | TS files | Lines | Spec files |
|---|---|---|---|
| `core/` | 18 | 1,378 | 8 |
| `layout/` | 4 | 793 | 4 |
| `shared/` | 15 | 1,260 | 1 |
| `features/admin/` | 4 | 2,102 | 0 |
| `features/approvals/` | 9 | 2,652 | 4 |
| `features/assistant/` | 2 | 256 | 0 |
| `features/auth/` | 4 | 463 | 1 |
| `features/dashboards/` | 3 | 829 | 1 |
| `features/documents/` | 2 | 460 | 1 |
| `features/importdata/` | 1 | 206 | 1 |
| `features/internal-messaging/` | 9 | 2,068 | 1 |
| `features/logistics/` | 4 | 724 | 0 |
| `features/messaging/` | 5 | 569 | 0 |
| `features/not-found/` | 1 | 19 | 0 |
| `features/notifications/` | 1 | 40 | 0 |
| `features/ordering/` | 3 | 471 | 0 |
| `features/organization/` | 1 | 325 | 0 |
| `features/overview/` | 1 | 658 | 0 |
| `features/planned-module/` | 1 | 37 | 0 |
| `features/purchasing/` | 6 | 1,098 | 2 |
| `features/receipt/` | 3 | 732 | 1 |
| `features/requests/` | 10 | 2,281 | 4 |
| `features/sourcing/` | 4 | 623 | 0 |
| `features/suppliers/` | 2 | 341 | 1 |
| `features/system-status/` | 1 | 148 | 1 |

**Routes (`FrontEnd/src/app/app.routes.ts`)**

| Path | Guards | Line |
|---|---|---|
| `/sign-in` | guestGuard | 9 |
| `/forgot-password` | guestGuard | 15 |
| `/a` (one-tap approval link) | none: public; token in the URL fragment | 22 |
| `/q` (supplier quote link) | none: public; token in the URL fragment | 28 |
| `/p` (password setup/reset) | none: public; token in the URL fragment | 34 |
| `/` | authGuard | 40 |
| `/` | homeRedirectGuard | 46 |
| `/account/password` | authGuard (from the shell) | 53 |
| `/admin/users` | permissionGuard('USER_ADMIN') | 58 |
| `/admin/requests` | anyPermissionGuard('REQUEST_ADMIN', 'REQUEST_DRAFT_ADMIN') | 64 |
| `/admin/requests/:id` | anyPermissionGuard('REQUEST_ADMIN', 'REQUEST_DRAFT_ADMIN') | 70 |
| `/overview` | authGuard (from the shell) | 76 |
| `/system-status` | authGuard (from the shell) | 81 |
| `/requests` | authGuard (from the shell) | 86 |
| `/requests/new` | authGuard (from the shell) | 91 |
| `/requests/:id/edit` | authGuard (from the shell) | 97 |
| `/requests/:id` | authGuard (from the shell) | 103 |
| `/conversations` | employeeGuard | 109 |
| `/conversations/:id` | employeeGuard | 115 |
| `/approvals` | authGuard (from the shell) | 121 |
| `/purchasing` | permissionGuard('PURCHASING_ACT') | 126 |
| `/logistics` | permissionGuard('PURCHASING_ACT') | 132 |
| `/messages` | permissionGuard('MESSAGING_BUYER') | 138 |
| `/assistant` | permissionGuard('ASSISTANT_USE') | 144 |
| `/supplier/messages` | permissionGuard('MESSAGING_SUPPLIER') | 150 |
| `/supplier/messages/:id` | permissionGuard('MESSAGING_SUPPLIER') | 156 |
| `/dashboards` | permissionGuard('DASHBOARD_VIEW') | 162 |
| `/suppliers` | permissionGuard('SUPPLIER_READ') | 168 |
| `/admin/organization` | permissionGuard('ORG_ADMIN') | 174 |
| `/admin/import` | permissionGuard('IMPORT_RUN') | 180 |
| `/absences` | authGuard (from the shell) | 186 |
| `/admin/approval-rules` | permissionGuard('SYSTEM_ADMIN') | 191 |
| `/admin/exchange-rates` | permissionGuard('SYSTEM_ADMIN') | 197 |
| `/planned/:module` | authGuard (from the shell) | 203 |
| `/**` | authGuard (from the shell) | 210 |
