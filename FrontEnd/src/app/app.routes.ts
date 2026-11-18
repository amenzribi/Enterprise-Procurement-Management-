import { Routes } from '@angular/router';
import { anyPermissionGuard, authGuard, employeeGuard, guestGuard, homeRedirectGuard, permissionGuard } from './core/auth/auth.guards';
import { unsavedChangesGuard } from './features/requests/unsaved-changes.guard';
import { findPlannedItem } from './layout/navigation';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  {
    path: 'sign-in',
    title: 'auth.signIn.title',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/sign-in').then((m) => m.SignInPage),
  },
  {
    path: 'forgot-password',
    title: 'auth.forgot.title',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/forgot-password').then((m) => m.ForgotPasswordPage),
  },
  {
    // One-tap approval from an e-mail (S2-AC-07): outside the signed-in workspace; the token is in the URL fragment.
    path: 'a',
    title: 'oneTap.title',
    loadComponent: () => import('./features/approvals/one-tap-approval').then((m) => m.OneTapApprovalPage),
  },
  {
    // Supplier quote link (S4-AC-03): no account; token in the URL fragment.
    path: 'q',
    title: 'sourcing.public.title',
    loadComponent: () => import('./features/sourcing/public-quote').then((m) => m.PublicQuotePage),
  },
  {
    // Password setup/reset from e-mail (S10-AC-04): token in the URL fragment.
    path: 'p',
    title: 'passwordSetup.title',
    loadComponent: () => import('./features/auth/password-setup').then((m) => m.PasswordSetupPage),
  },
  {
    // Everything else is the signed-in workspace (S1-AC-01, S1-AC-13).
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [homeRedirectGuard],
        // Guard always redirects; component is never rendered.
        loadComponent: () => import('./features/overview/overview').then((m) => m.OverviewPage),
      },
      {
        path: 'account/password',
        title: 'changePassword.title',
        loadComponent: () => import('./features/auth/change-password').then((m) => m.ChangePasswordPage),
      },
      {
        path: 'admin/users',
        title: 'usersAdmin.title',
        canActivate: [permissionGuard('USER_ADMIN')],
        loadComponent: () => import('./features/admin/users-admin').then((m) => m.UsersAdminPage),
      },
      {
        path: 'admin/requests',
        title: 'requestAdmin.title',
        canActivate: [anyPermissionGuard('REQUEST_ADMIN', 'REQUEST_DRAFT_ADMIN')],
        loadComponent: () => import('./features/admin/request-admin').then((m) => m.RequestAdminPage),
      },
      {
        path: 'admin/requests/:id',
        title: 'requestAdmin.detailTitle',
        canActivate: [anyPermissionGuard('REQUEST_ADMIN', 'REQUEST_DRAFT_ADMIN')],
        loadComponent: () => import('./features/admin/request-admin').then((m) => m.RequestAdminDetailPage),
      },
      {
        path: 'overview',
        title: 'nav.items.overview',
        loadComponent: () => import('./features/overview/overview').then((m) => m.OverviewPage),
      },
      {
        path: 'system-status',
        title: 'nav.items.systemStatus',
        loadComponent: () => import('./features/system-status/system-status').then((m) => m.SystemStatusPage),
      },
      {
        path: 'requests',
        title: 'requests.list.title',
        loadComponent: () => import('./features/requests/requests-list').then((m) => m.RequestsListPage),
      },
      {
        path: 'requests/new',
        title: 'requests.form.newTitle',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./features/requests/request-form').then((m) => m.RequestFormPage),
      },
      {
        path: 'requests/:id/edit',
        title: 'requests.form.editTitle',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./features/requests/request-form').then((m) => m.RequestFormPage),
      },
      {
        path: 'requests/:id',
        title: 'requests.detail.title',
        loadComponent: () => import('./features/requests/request-detail').then((m) => m.RequestDetailPage),
      },
      {
        // Internal employee messaging (docs/architecture/internal-messaging.md).
        path: 'conversations',
        title: 'internalMessaging.inbox.title',
        canActivate: [employeeGuard],
        loadComponent: () => import('./features/internal-messaging/inbox-page').then((m) => m.MessagesInboxPage),
      },
      {
        path: 'conversations/:id',
        title: 'internalMessaging.inbox.title',
        canActivate: [employeeGuard],
        loadComponent: () => import('./features/internal-messaging/inbox-page').then((m) => m.MessagesInboxPage),
      },
      {
        path: 'approvals',
        title: 'approvals.inbox.title',
        loadComponent: () => import('./features/approvals/approvals-inbox').then((m) => m.ApprovalsInboxPage),
      },
      {
        path: 'purchasing',
        title: 'purchasing.inbox.title',
        canActivate: [permissionGuard('PURCHASING_ACT')],
        loadComponent: () => import('./features/purchasing/purchasing-inbox').then((m) => m.PurchasingInboxPage),
      },
      {
        path: 'logistics',
        title: 'logistics.inbox.title',
        canActivate: [permissionGuard('PURCHASING_ACT')],
        loadComponent: () => import('./features/logistics/logistics-inbox').then((m) => m.LogisticsInboxComponent),
      },
      {
        path: 'messages',
        title: 'messaging.buyer.title',
        canActivate: [permissionGuard('MESSAGING_BUYER')],
        loadComponent: () => import('./features/messaging/buyer-messages-page').then((m) => m.BuyerMessagesPage),
      },
      {
        path: 'assistant',
        title: 'assistant.title',
        canActivate: [permissionGuard('ASSISTANT_USE')],
        loadComponent: () => import('./features/assistant/assistant-page').then((m) => m.AssistantPage),
      },
      {
        path: 'supplier/messages',
        title: 'messaging.portal.title',
        canActivate: [permissionGuard('MESSAGING_SUPPLIER')],
        loadComponent: () => import('./features/messaging/supplier-messages-page').then((m) => m.SupplierMessagesPage),
      },
      {
        path: 'supplier/messages/:id',
        title: 'messaging.portal.title',
        canActivate: [permissionGuard('MESSAGING_SUPPLIER')],
        loadComponent: () => import('./features/messaging/supplier-messages-page').then((m) => m.SupplierMessagesPage),
      },
      {
        path: 'dashboards',
        title: 'dashboards.title',
        canActivate: [permissionGuard('DASHBOARD_VIEW')],
        loadComponent: () => import('./features/dashboards/dashboards-page').then((m) => m.DashboardsPage),
      },
      {
        path: 'suppliers',
        title: 'suppliers.title',
        canActivate: [permissionGuard('SUPPLIER_READ')],
        loadComponent: () => import('./features/suppliers/suppliers').then((m) => m.SuppliersPage),
      },
      {
        path: 'admin/organization',
        title: 'org.title',
        canActivate: [permissionGuard('ORG_ADMIN')],
        loadComponent: () => import('./features/organization/organization-admin').then((m) => m.OrganizationAdminPage),
      },
      {
        path: 'admin/import',
        title: 'imports.title',
        canActivate: [permissionGuard('IMPORT_RUN')],
        loadComponent: () => import('./features/importdata/import-page').then((m) => m.ImportPage),
      },
      {
        path: 'absences',
        title: 'absences.title',
        loadComponent: () => import('./features/approvals/absences').then((m) => m.AbsencesPage),
      },
      {
        path: 'admin/approval-rules',
        title: 'rules.title',
        canActivate: [permissionGuard('SYSTEM_ADMIN')],
        loadComponent: () => import('./features/admin/approval-rules').then((m) => m.ApprovalRulesPage),
      },
      {
        path: 'admin/exchange-rates',
        title: 'rates.title',
        canActivate: [permissionGuard('SYSTEM_ADMIN')],
        loadComponent: () => import('./features/admin/exchange-rates').then((m) => m.ExchangeRatesPage),
      },
      {
        path: 'planned/:module',
        // Unknown module keys fall through to the not-found page.
        canMatch: [(_route, segments) => !!findPlannedItem(segments[1]?.path ?? null)],
        title: (route) => `nav.items.${route.paramMap.get('module')}`,
        loadComponent: () => import('./features/planned-module/planned-module').then((m) => m.PlannedModulePage),
      },
      {
        path: '**',
        title: 'notFound.title',
        loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFoundPage),
      },
    ],
  },
];
