import { Dialog } from '@angular/cdk/dialog';
import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { openConfirm } from '../../shared/ui/confirm-dialog';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/**
 * Asks before leaving a form with unsaved changes (S1-AC-15). When the session has ended the app must reach the
 * sign-in page; the form content is kept in session storage for restore, so the guard lets that navigation pass.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (inject(AuthService).isLeavingForSignIn || !component?.hasUnsavedChanges()) {
    return true;
  }
  return openConfirm(inject(Dialog), {
    titleKey: 'requests.form.leave.title',
    messageKey: 'requests.form.leave.message',
    confirmKey: 'requests.form.leave.confirm',
    cancelKey: 'requests.form.leave.cancel',
    tone: 'danger',
  });
};
