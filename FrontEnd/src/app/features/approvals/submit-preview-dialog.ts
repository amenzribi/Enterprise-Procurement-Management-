import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { ApprovalPreview } from './approval.models';
import { ApprovalsApi } from './approvals.api';

export interface SubmitPreviewData {
  requestId: number;
}

/**
 * Submit confirmation that shows who will approve, in which order, before the request is sent (S2DR-17). When the
 * company has no matching rule, no exchange rate or no eligible approver, the reason is shown and submitting is not
 * offered.
 */
@Component({
  selector: 'app-submit-preview-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, TranslatePipe],
  template: `
    <h2 id="submit-preview-title" class="dialog-title">{{ 'requests.submit.title' | translate }}</h2>
    <div id="submit-preview-message" class="dialog-body">
      <p class="muted">{{ 'requests.submit.message' | translate }}</p>
      @switch (state()) {
        @case ('loading') {
          <p class="muted" role="status">{{ 'approvals.preview.loading' | translate }}</p>
        }
        @case ('blocked') {
          <p class="blocked" role="alert">
            <app-icon name="triangle-alert" [size]="16" />
            {{ 'errors.' + (blockedCode() ?? 'unexpected-error') | translate }}
          </p>
        }
        @default {
          @if (preview(); as p) {
            <p class="strong">{{ 'approvals.preview.title' | translate }}</p>
            <ol class="stages">
              @for (stage of p.stages; track stage.stageNo) {
                <li>
                  <span class="strong">{{ stage.name || ('tracker.approverType.' + stage.approverType | translate) }}</span>
                  @if (stage.approvers.length) {
                    — {{ names(stage) }}
                  }
                  <span class="muted small"> · {{ 'tracker.policy.' + stage.policy | translate }}</span>
                </li>
              }
            </ol>
            @if (p.amounts?.companyAmount !== null && p.amounts?.companyAmount !== undefined && p.amounts?.companyCurrency) {
              <p class="muted small">
                {{ 'tracker.amountForApproval' | translate }} <bdi class="tabular">{{ money(p) }}</bdi>
              </p>
            }
          }
        }
      }
    </div>
    <div class="dialog-actions">
      <button appButton variant="secondary" type="button" (click)="ref.close(false)">{{ 'common.cancel' | translate }}</button>
      @if (state() !== 'blocked') {
        <button appButton type="button" cdkFocusInitial [disabled]="state() === 'loading'" (click)="ref.close(true)">
          <app-icon name="send" [size]="16" />
          {{ 'requests.submit.confirm' | translate }}
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
      inline-size: min(32rem, calc(100vw - 2rem));
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
      box-shadow: var(--shadow-overlay);
    }

    .dialog-title {
      font-size: var(--text-lg);
    }

    .dialog-body {
      display: grid;
      gap: var(--space-3);
      margin-block-start: var(--space-3);
    }

    .stages {
      display: grid;
      gap: var(--space-2);
      margin: 0;
      padding-inline-start: var(--space-5);
    }

    .blocked {
      display: flex;
      gap: var(--space-2);
      align-items: flex-start;
      padding: var(--space-3);
      border-radius: var(--radius-sm);
      background: var(--color-warning-soft);
      color: var(--color-warning);
    }

    .dialog-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--space-2);
      margin-block-start: var(--space-6);
    }
  `,
})
export class SubmitPreviewDialog {
  protected readonly data = inject<SubmitPreviewData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
  private readonly language = inject(LanguageService);

  protected readonly state = signal<'loading' | 'ready' | 'blocked'>('loading');
  protected readonly preview = signal<ApprovalPreview | null>(null);
  protected readonly blockedCode = signal<string | null>(null);

  constructor() {
    inject(ApprovalsApi)
      .preview(this.data.requestId)
      .subscribe({
        next: (preview) => {
          this.preview.set(preview);
          if (preview.problem) {
            // A configuration problem: submitting would fail the same way.
            this.blockedCode.set(preview.problem);
            this.state.set('blocked');
          } else {
            this.state.set('ready');
          }
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status === 422) {
            // A configuration problem: submitting would fail the same way.
            this.blockedCode.set(problem.code);
            this.state.set('blocked');
          } else {
            // The preview is informative only; the server still decides at submission.
            this.state.set('ready');
          }
        },
      });
  }

  protected names(stage: ApprovalPreview['stages'][number]): string {
    return stage.approvers.map((item) => item.approver.displayName).join(', ');
  }

  protected money(preview: ApprovalPreview): string {
    const currency = preview.amounts!.companyCurrency!;
    return formatMoney(preview.amounts!.companyAmount ?? null, currency, minorUnitsOf(currency), this.language.locale());
  }
}

/** Opens the preview for a saved request; emits true only when the user confirmed submitting. */
export function openSubmitPreview(dialog: Dialog, requestId: number): Observable<boolean> {
  return dialog
    .open<boolean, SubmitPreviewData>(SubmitPreviewDialog, {
      data: { requestId },
      role: 'alertdialog',
      ariaLabelledBy: 'submit-preview-title',
      ariaDescribedBy: 'submit-preview-message',
      autoFocus: '[cdkFocusInitial]',
      restoreFocus: true,
    })
    .closed.pipe(map((result) => result === true));
}
