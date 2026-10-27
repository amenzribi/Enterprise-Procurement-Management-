import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
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
