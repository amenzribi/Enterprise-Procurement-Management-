import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';

export interface SubmitSuccessData {
  requestId: number;
  number: string;
  title: string;
}

@Component({
  selector: 'app-submit-success-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, TranslatePipe],
  template: `
    <h2 id="submit-success-title" class="dialog-title">{{ 'requests.submit.successTitle' | translate }}</h2>
    <p id="submit-success-message" class="dialog-message">
      {{ 'requests.submit.successMessage' | translate: { number: data.number, title: data.title } }}
    </p>
    <div class="reference" role="group" [attr.aria-label]="'requests.submit.referenceLabel' | translate">
      <span class="label">{{ 'requests.submit.referenceLabel' | translate }}</span>
      <bdi class="number tabular">{{ data.number }}</bdi>
      <button appButton type="button" variant="secondary" (click)="copy()">
        <app-icon name="copy" [size]="16" />
        {{ (copied() ? 'requests.submit.copied' : 'requests.submit.copy') | translate }}
      </button>
    </div>
    <div class="dialog-actions">
      <button appButton type="button" variant="secondary" (click)="ref.close('list')">
        {{ 'requests.submit.backToList' | translate }}
      </button>
      <button appButton type="button" (click)="open()">
        <app-icon name="file-plus" [size]="16" />
        {{ 'requests.submit.openRequest' | translate }}
      </button>
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-4);
      padding: var(--space-5);
      max-inline-size: min(28rem, 92vw);
      background: var(--color-surface);
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-lg, 0 12px 40px rgb(0 0 0 / 0.18));
    }
    .dialog-title {
      margin: 0;
      font-size: var(--text-lg);
    }
    .dialog-message {
      margin: 0;
      color: var(--color-text-muted);
    }
    .reference {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface-muted, var(--color-surface));
    }
    .label {
      font-size: var(--text-sm);
      color: var(--color-text-muted);
      inline-size: 100%;
    }
    .number {
      font-size: var(--text-lg);
      font-weight: var(--weight-semibold);
      user-select: all;
      overflow-wrap: anywhere;
      flex: 1 1 auto;
    }
    .dialog-actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      justify-content: flex-end;
    }
  `,
})
export class SubmitSuccessDialog {
  protected readonly data = inject<SubmitSuccessData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<'open' | 'list'>>(DialogRef);
  private readonly router = inject(Router);
  protected readonly copied = signal(false);

  protected copy(): void {
    void navigator.clipboard.writeText(this.data.number).then(
      () => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      },
      () => undefined,
    );
  }

  protected open(): void {
    this.ref.close('open');
    void this.router.navigate(['/requests', this.data.requestId]);
  }
}

export function openSubmitSuccess(dialog: Dialog, data: SubmitSuccessData): Observable<'open' | 'list' | undefined> {
  return dialog.open<'open' | 'list', SubmitSuccessData>(SubmitSuccessDialog, {
    data,
    role: 'alertdialog',
    ariaLabelledBy: 'submit-success-title',
    ariaDescribedBy: 'submit-success-message',
    disableClose: true,
  }).closed;
}
