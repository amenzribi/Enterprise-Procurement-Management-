import { Routes } from '@angular/router';
import { authGuard, guestGuard, permissionGuard } from './core/auth/auth.guards';
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
    // Everything else is the signed-in workspace (S1-AC-01, S1-AC-13).
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'requests' },
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
