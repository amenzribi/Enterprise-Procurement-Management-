import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { Button } from './button';

export interface ConfirmData {
  titleKey: string;
  messageKey: string;
  confirmKey: string;
  cancelKey?: string;
  params?: Record<string, unknown>;
  /** "danger" for destructive choices; the cancel button then receives initial focus. */
  tone?: 'primary' | 'danger';
}

/** Modal confirmation for consequential actions (submit, discard, leave with unsaved changes). */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    <h2 id="confirm-dialog-title" class="dialog-title">{{ data.titleKey | translate: data.params }}</h2>
    <p id="confirm-dialog-message" class="dialog-message">{{ data.messageKey | translate: data.params }}</p>
    <div class="dialog-actions">
      <button appButton variant="secondary" type="button" (click)="ref.close(false)" [attr.cdkFocusInitial]="data.tone === 'danger' ? '' : null">
        {{ (data.cancelKey ?? 'common.cancel') | translate }}
      </button>
      <button
        appButton
        type="button"
        [class.is-danger]="data.tone === 'danger'"
        (click)="ref.close(true)"
        [attr.cdkFocusInitial]="data.tone === 'danger' ? null : ''"
      >
        {{ data.confirmKey | translate: data.params }}
      </button>
    </div>
  `,
  styles: `
    :host {
      display: block;
      inline-size: min(28rem, calc(100vw - 2rem));
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
      box-shadow: var(--shadow-overlay);
    }

    .dialog-title {
      font-size: var(--text-lg);
    }

    .dialog-message {
      margin-block-start: var(--space-3);
      color: var(--color-text-muted);
      font-size: var(--text-md);
    }

    .dialog-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--space-2);
      margin-block-start: var(--space-6);
    }

    .is-danger {
      background: var(--color-danger);
    }

    .is-danger:hover {
      background: color-mix(in srgb, var(--color-danger) 85%, black);
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
}

/** Opens the confirmation and emits true only when the user confirmed. */
export function openConfirm(dialog: Dialog, data: ConfirmData): Observable<boolean> {
  return dialog
    .open<boolean, ConfirmData>(ConfirmDialog, {
      data,
      role: 'alertdialog',
      ariaLabelledBy: 'confirm-dialog-title',
      ariaDescribedBy: 'confirm-dialog-message',
      autoFocus: '[cdkFocusInitial]',
      restoreFocus: true,
    })
    .closed.pipe(map((result) => result === true));
}
