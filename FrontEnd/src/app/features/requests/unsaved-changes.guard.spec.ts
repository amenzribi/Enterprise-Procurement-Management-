import { Dialog } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { HasUnsavedChanges, unsavedChangesGuard } from './unsaved-changes.guard';

describe('unsavedChangesGuard', () => {
  const closed = new Subject<boolean | undefined>();
  const dialog = { open: vi.fn(() => ({ closed })) };
  const auth = { isLeavingForSignIn: false };

  beforeEach(() => {
    dialog.open.mockClear();
    auth.isLeavingForSignIn = false;
    TestBed.configureTestingModule({
      providers: [
        { provide: Dialog, useValue: dialog },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  const run = (dirty: boolean) =>
    TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(
        { hasUnsavedChanges: () => dirty } as HasUnsavedChanges,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot,
      ),
    );

  it('lets the user leave a form without changes', () => {
    expect(run(false)).toBe(true);
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('asks before leaving a form with unsaved changes and stays when the user cancels', async () => {
    const result = firstValueFrom(run(true) as Observable<boolean>);
    closed.next(false);

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(await result).toBe(false);
  });

  it('leaves when the user confirms', async () => {
    const result = firstValueFrom(run(true) as Observable<boolean>);
    closed.next(true);

    expect(await result).toBe(true);
  });

  it('never blocks the redirect to sign-in after the session ended', () => {
    auth.isLeavingForSignIn = true;
    expect(run(true)).toBe(true);
    expect(dialog.open).not.toHaveBeenCalled();
  });
});
