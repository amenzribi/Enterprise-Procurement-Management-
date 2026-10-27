import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Pages of the workspace require a signed-in user; others are sent to sign-in and brought back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/sign-in'], { queryParams: { returnUrl: state.url } });
};

/** A signed-in user opening the sign-in page goes straight to their requests. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/requests']) : true;
