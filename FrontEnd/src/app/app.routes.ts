import { Routes } from '@angular/router';
import { findPlannedItem } from './layout/navigation';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'overview' },
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
];
