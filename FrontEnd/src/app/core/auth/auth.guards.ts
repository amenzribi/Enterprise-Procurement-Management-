import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Permission, hasPermission } from './auth.models';
import { AuthService } from './auth.service';
import { workspaceHome } from './workspace-home';

/** Pages of the workspace require a signed-in user; others are sent to sign-in and brought back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) {
    return inject(Router).createUrlTree(['/sign-in'], { queryParams: { returnUrl: state.url } });
  }
  if (auth.me()?.mustChangePassword && !state.url.startsWith('/account/password')) {
    return inject(Router).createUrlTree(['/account/password']);
  }
  return true;
};

/** A signed-in user opening the sign-in page goes straight to their workspace home. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree([workspaceHome(auth.me())]);
};

/** Empty path → role-appropriate home (suppliers must not land on `/requests`). */
export const homeRedirectGuard: CanActivateFn = () =>
  inject(Router).createUrlTree([workspaceHome(inject(AuthService).me())]);

/**
 * Hides pages of a permission the user does not hold behind the not-found page. The server enforces access on every
 * API call regardless; this only avoids showing a screen that would fail.
 */
export function permissionGuard(permission: Permission): CanActivateFn {
  return () =>
    hasPermission(inject(AuthService).me(), permission) ? true : inject(Router).createUrlTree(['/not-found']);
}

/** Passes when the signed-in user holds any of the listed permissions. */
export function anyPermissionGuard(...permissions: Permission[]): CanActivateFn {
  return () => {
    const me = inject(AuthService).me();
    return permissions.some((p) => hasPermission(me, p))
      ? true
      : inject(Router).createUrlTree(['/not-found']);
  };
}
