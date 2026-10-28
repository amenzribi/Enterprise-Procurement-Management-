import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, computed, inject, Injector, input, output, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { Icon, IconName } from '../../shared/ui/icon';
import { PersonOption } from '../requests/request.models';
import { APPROVAL_ACTIONS, ApprovalAction, ApprovalView, REASON_REQUIRED } from './approval.models';
import { ApprovalsApi } from './approvals.api';

export const ACTION_ICONS: Record<ApprovalAction, IconName> = {
  APPROVE: 'circle-check',
  QUESTION: 'question',
  RETURN: 'undo',
  REJECT: 'ban',
  DELEGATE: 'forward',
};

/** Error codes with a dedicated approval message; others fall back to the generic `errors.*` texts. */
const APPROVAL_ERRORS = new Set([
  'question-already-open',
  'no-open-question',
  'task-not-pending',
  'reason-required',
  'delegate-not-eligible',
  'stale-version',
  'invalid-state',
  'forbidden',
  'not-found',
]);

export const MAX_TEXT = 2000;

/**
 * The approver's decision panel (S2-AC-05) and the requester's answer form. It offers exactly the actions the server
 * lists for the current user. Each confirmed action carries one Idempotency-Key, reused if the user retries the same
 * action, so a double tap never decides twice (S2-AC-13).
 */
@Component({
  selector: 'app-approval-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, ReactiveFormsModule, TranslatePipe],
  template: `
    @if (myTask(); as task) {
      <section class="panel decide" aria-labelledby="decide-title">
        <h2 class="panel-title" id="decide-title">{{ 'approvals.decide.title' | translate }}</h2>
        @if (selected() === null) {
          <div class="choices">
            @for (action of orderedActions(); track action) {
              <button
                appButton
                type="button"
                [variant]="action === 'APPROVE' ? 'primary' : 'secondary'"
                [class.is-danger-outline]="action === 'REJECT'"
                (click)="choose(action)"
              >
                <app-icon [name]="icons[action]" [size]="16" />
                {{ 'approvals.actions.' + action | translate }}
              </button>
            }
          </div>
        } @else {
          <form class="form" (submit)="$event.preventDefault(); confirm(task.id, task.version)" novalidate>
            <p class="form-title strong">{{ 'approvals.actions.' + selected() | translate }}</p>
            @if (selected() === 'DELEGATE') {
              <label class="label" for="delegate-to">{{ 'approvals.decide.delegateTo' | translate }} *</label>
              @if (delegatesLoading()) {
                <p class="muted small">{{ 'approvals.decide.loadingDelegates' | translate }}</p>
              } @else if (delegates().length === 0) {
                <p class="muted small">{{ 'approvals.decide.noDelegates' | translate }}</p>
              } @else {
                <select id="delegate-to" [formControl]="delegateTo" [attr.aria-invalid]="showErrors() && delegateTo.invalid" aria-describedby="decide-error">
                  <option [ngValue]="null">{{ 'requests.form.choose' | translate }}</option>
                  @for (person of delegates(); track person.id) {
                    <option [ngValue]="person.id">{{ person.displayName }}</option>
                  }
                </select>
              }
            }
            <label class="label" for="decide-text">
              {{ textLabel() | translate }}
              @if (textRequired()) {
                *
              }
            </label>
            <textarea
              #text
              id="decide-text"
              rows="3"
              [formControl]="comment"
              [attr.maxlength]="maxText"
              [attr.aria-invalid]="showErrors() && textMissing()"
              aria-describedby="decide-error decide-hint"
            ></textarea>
            <p id="decide-hint" class="muted small">{{ hintKey() | translate }}</p>
            <p id="decide-error" class="error small" role="alert">
              @if (showErrors() && textMissing()) {
                {{ (selected() === 'QUESTION' ? 'approvals.decide.questionRequired' : 'approvals.decide.reasonRequired') | translate }}
              } @else if (showErrors() && selected() === 'DELEGATE' && delegateTo.invalid) {
                {{ 'approvals.decide.delegateRequired' | translate }}
              } @else if (error(); as code) {
                {{ errorKey(code) | translate }}
              }
            </p>
            <div class="form-actions">
              <button appButton variant="secondary" type="button" (click)="cancel()" [disabled]="busy()">
                {{ 'common.cancel' | translate }}
              </button>
              <button appButton type="submit" [disabled]="busy()" [class.is-danger]="selected() === 'REJECT'">
                <app-icon [name]="icons[selected()!]" [size]="16" />
                {{ (busy() ? 'approvals.decide.sending' : 'approvals.confirm.' + selected()) | translate }}
              </button>
            </div>
          </form>
        }
      </section>
    }

    @if (openQuestion(); as question) {
      <section class="panel answer" aria-labelledby="answer-title">
        <h2 class="panel-title" id="answer-title">
          <app-icon name="question" [size]="18" />
          {{ 'approvals.answer.title' | translate: { name: question.askedBy?.displayName ?? '' } }}
        </h2>
        <blockquote class="asked prose">{{ question.question }}</blockquote>
        <form class="form" (submit)="$event.preventDefault(); sendAnswer(question.taskId)" novalidate>
          <label class="label" for="answer-text">{{ 'approvals.answer.label' | translate }} *</label>
          <textarea
            id="answer-text"
            rows="3"
            [formControl]="answer"
            [attr.maxlength]="maxText"
            [attr.aria-invalid]="answerErrors() && answer.invalid"
            aria-describedby="answer-error"
          ></textarea>
          <p id="answer-error" class="error small" role="alert">
            @if (answerErrors() && answer.invalid) {
              {{ 'approvals.answer.required' | translate }}
            } @else if (error(); as code) {
              {{ errorKey(code) | translate }}
            }
          </p>
          <div class="form-actions">
            <button appButton type="submit" [disabled]="busy()">
              <app-icon name="reply" [size]="16" />
              {{ (busy() ? 'approvals.decide.sending' : 'approvals.answer.send') | translate }}
            </button>
          </div>
        </form>
      </section>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-4);
    }

    :host:empty {
      display: none;
    }

    .decide {
      border-color: var(--color-accent);
      box-shadow: 0 0 0 1px var(--color-accent-soft);
    }

    .panel-title {
      display: flex;
      align-items: center;
      gap: var(--space-2);
    }

    .choices {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }

    // Phones: full-width buttons, thumb-friendly, primary action first.
    @media (max-width: 35.99rem) {
      .choices {
        display: grid;
      }

      .choices button {
        min-block-size: 3rem;
      }
    }

    .is-danger-outline {
      color: var(--color-danger);
    }

    .is-danger {
      background: var(--color-danger);
      border-color: var(--color-danger);
    }

    .form {
      display: grid;
      gap: var(--space-2);
    }

    .label {
      font-weight: var(--weight-medium);
      font-size: var(--text-sm);
    }

    textarea,
    select {
      inline-size: 100%;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
    }

    textarea[aria-invalid='true'],
    select[aria-invalid='true'] {
      border-color: var(--color-danger);
    }

    .error {
      min-block-size: 1.25rem;
      color: var(--color-danger);
    }

    .form-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--space-2);
    }

    .asked {
      margin: 0 0 var(--space-3);
      padding: var(--space-2) var(--space-3);
      border-inline-start: 3px solid var(--color-info);
      background: var(--color-info-soft);
    }
  `,
})
export class ApprovalActions {
  private readonly api = inject(ApprovalsApi);
  private readonly notifications = inject(NotificationService);
  private readonly injector = inject(Injector);

  readonly requestId = input.required<number>();
  readonly approval = input<ApprovalView | null>(null);
  /** Emits after a successful action so the page reloads the request and its tracker. */
  readonly changed = output<ApprovalView | null>();

  protected readonly icons = ACTION_ICONS;
  protected readonly maxText = MAX_TEXT;
  protected readonly selected = signal<ApprovalAction | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showErrors = signal(false);
  protected readonly answerErrors = signal(false);
  protected readonly delegates = signal<PersonOption[]>([]);
  protected readonly delegatesLoading = signal(false);

  protected readonly comment = new FormControl('', { nonNullable: true });
  protected readonly delegateTo = new FormControl<number | null>(null, Validators.required);
  protected readonly answer = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  /** One key per confirmed action, kept for retries of that same action. */
  private pendingKey: string | null = null;
  private readonly textArea = viewChild<ElementRef<HTMLTextAreaElement>>('text');

  /** The approver's own task, shown only when the server allows at least one decision on it. */
  protected readonly myTask = computed(() => {
    const view = this.approval();
    return view?.myTask && this.orderedActions().length > 0 ? view.myTask : null;
  });

  /** Exactly the decisions the server lists for the current user (S2-AC-05), approve first. */
  protected readonly orderedActions = computed(() => {
    const allowed = this.approval()?.actions ?? [];
    return APPROVAL_ACTIONS.filter((action) => allowed.includes(action));
  });

  /** The oldest open question the requester may answer (server action ANSWER). */
  protected readonly openQuestion = computed(() => {
    const view = this.approval();
    if (!view?.actions?.includes('ANSWER')) {
      return null;
    }
    return [...(view.openQuestions ?? [])].sort((a, b) => a.askedAt.localeCompare(b.askedAt))[0] ?? null;
  });

  protected readonly textRequired = computed(() => {
    const action = this.selected();
    return action === 'QUESTION' || (action !== null && REASON_REQUIRED.includes(action));
  });

  protected readonly textLabel = computed(() => {
    switch (this.selected()) {
      case 'QUESTION':
        return 'approvals.decide.questionLabel';
      case 'RETURN':
      case 'REJECT':
        return 'approvals.decide.reasonLabel';
      default:
        return 'approvals.decide.commentLabel';
    }
  });

  protected readonly hintKey = computed(() => `approvals.hints.${this.selected() ?? 'APPROVE'}`);

  protected textMissing(): boolean {
    return this.textRequired() && this.comment.value.trim() === '';
  }

  protected choose(action: ApprovalAction): void {
    this.selected.set(action);
    this.comment.reset('');
    this.delegateTo.reset(null);
    this.error.set(null);
    this.showErrors.set(false);
    this.pendingKey = null;
    if (action === 'DELEGATE') {
      this.loadDelegates();
    }
    afterNextRender(() => this.textArea()?.nativeElement.focus(), { injector: this.injector });
  }

  protected cancel(): void {
    this.selected.set(null);
    this.error.set(null);
    this.pendingKey = null;
  }

  protected confirm(taskId: number, version: number): void {
    const action = this.selected();
    if (!action || this.busy()) {
      return;
    }
    this.showErrors.set(true);
    if (this.textMissing() || (action === 'DELEGATE' && this.delegateTo.invalid)) {
      return;
    }
    const text = this.comment.value.trim() || null;
    this.pendingKey ??= newIdempotencyKey();
    this.busy.set(true);
    this.error.set(null);
    const request =
      action === 'DELEGATE'
        ? this.api.delegate(taskId, this.delegateTo.value!, text, version, this.pendingKey)
        : this.api.decide(
            taskId,
            action,
            {
              version,
              comment: action === 'APPROVE' ? text : null,
              reason: action === 'RETURN' || action === 'REJECT' ? text : null,
              question: action === 'QUESTION' ? text : null,
            },
            this.pendingKey,
          );
    request.subscribe({
      next: (view) => {
        this.busy.set(false);
        this.selected.set(null);
        this.pendingKey = null;
        this.notifications.success(`approvals.done.${action}`);
        this.changed.emit(view ?? null);
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  protected sendAnswer(taskId: number): void {
    if (this.busy()) {
      return;
    }
    this.answerErrors.set(true);
    if (this.answer.value.trim() === '') {
      return;
    }
    this.pendingKey ??= newIdempotencyKey();
    this.busy.set(true);
    this.error.set(null);
    this.api.answer(this.requestId(), taskId, this.answer.value.trim(), this.pendingKey).subscribe({
      next: (view) => {
        this.busy.set(false);
        this.pendingKey = null;
        this.answer.reset('');
        this.answerErrors.set(false);
        this.notifications.success('approvals.answer.sent');
        this.changed.emit(view ?? null);
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  protected errorKey(code: string): string {
    return APPROVAL_ERRORS.has(code) ? `approvals.errors.${code}` : `errors.${code}`;
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    const problem = toApiProblem(error);
    if (problem.status === 401) {
      return;
    }
    this.error.set(problem.code);
    if (problem.status === 409 || problem.status === 404) {
      // The task was decided or reassigned meanwhile: start over from the server's current state.
      this.pendingKey = null;
      this.selected.set(null);
      this.notifications.error(this.errorKey(problem.code));
      this.changed.emit(null);
    }
  }

  private loadDelegates(): void {
    const task = this.myTask();
    if (!task) {
      return;
    }
    this.delegatesLoading.set(true);
    this.api.delegates(task.id).subscribe({
      next: (people) => {
        this.delegates.set(people);
        this.delegatesLoading.set(false);
      },
      error: () => {
        this.delegates.set([]);
        this.delegatesLoading.set(false);
      },
    });
  }
}
