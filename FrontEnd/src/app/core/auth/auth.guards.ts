import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Permission, hasPermission } from './auth.models';
import { AuthService } from './auth.service';

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

/** A signed-in user opening the sign-in page goes straight to their requests. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/requests']) : true;

/**
 * Hides pages of a permission the user does not hold behind the not-found page. The server enforces access on every
 * API call regardless; this only avoids showing a screen that would fail.
 */
export function permissionGuard(permission: Permission): CanActivateFn {
  return () =>
    hasPermission(inject(AuthService).me(), permission) ? true : inject(Router).createUrlTree(['/not-found']);
}
