import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Skeleton } from '../../shared/ui/skeleton';
import { InternalMessagingApi } from './internal-messaging.api';
import { Approver, ApproverMessageResult, ApproversState } from './internal-messaging.models';
import { MAX_BODY, newClientMessageId } from './message-list';
import { MessagingLiveService } from './messaging-live.service';

export interface MessageApproverData {
  requestId: number;
  number: string | null;
  title: string;
}

/**
 * "Message approver": the current approver comes from the server's approval state (stage and delegation included),
 * the request is attached, the text is a suggestion the sender edits, and nothing goes until Send. If the approver
 * changes before Send, the server refuses, the list is refreshed and the change is explained.
 */
@Component({
  selector: 'app-message-approver-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, Skeleton, TranslatePipe],
  template: `
    <h2 id="message-approver-title" class="title">{{ 'internalMessaging.approver.title' | translate }}</h2>
    <p class="request">
      <app-icon name="file-text" [size]="16" />
      <bdi class="number tabular">{{ data.number }}</bdi>
      <span class="request-title" dir="auto">{{ data.title }}</span>
    </p>

    @switch (state()) {
      @case ('loading') {
        <app-skeleton height="5rem" />
      }
      @case ('sent') {
        <p class="done" role="status">
          <app-icon name="circle-check" [size]="18" />
          {{ 'internalMessaging.approver.sent' | translate: { name: chosen()?.person?.displayName ?? '' } }}
        </p>
        <div class="actions">
          <button appButton variant="secondary" type="button" (click)="ref.close()">{{ 'common.close' | translate }}</button>
          <button appButton type="button" (click)="openConversation()">
            <app-icon name="messages" [size]="16" />
            {{ 'internalMessaging.approver.openConversation' | translate }}
          </button>
        </div>
      }
      @case ('unavailable') {
        <p class="explain" role="status">{{ 'internalMessaging.approver.state.' + approverState() | translate }}</p>
        <div class="actions">
          <button appButton variant="secondary" type="button" (click)="ref.close()">{{ 'common.close' | translate }}</button>
          <button appButton variant="ghost" type="button" (click)="openInbox()">{{ 'internalMessaging.approver.openInbox' | translate }}</button>
        </div>
      }
      @default {
        @if (changed()) {
          <p class="changed" role="alert">
            <app-icon name="triangle-alert" [size]="16" />
            {{ 'internalMessaging.approver.changed' | translate }}
          </p>
        }
        <form class="form" (submit)="$event.preventDefault(); send()" novalidate>
          @if (approvers().length > 1) {
            <fieldset class="recipients">
              <legend>{{ 'internalMessaging.approver.choose' | translate }}</legend>
              @for (a of approvers(); track a.person.id) {
                <label class="recipient" [class.is-chosen]="chosen()?.person?.id === a.person.id">
                  <input type="radio" name="approver" [value]="a.person.id" [checked]="chosen()?.person?.id === a.person.id" (change)="chosen.set(a)" />
                  <span class="recipient-text">
                    <span class="name" dir="auto">{{ a.person.displayName }}</span>
                    <span class="meta">{{ a.stageName }}@if (a.onBehalfOf) { · {{ 'internalMessaging.approver.onBehalfOf' | translate: { name: a.onBehalfOf.displayName } }} }</span>
                  </span>
                </label>
              }
            </fieldset>
          } @else if (chosen(); as a) {
            <p class="to">
              <span class="label">{{ 'internalMessaging.approver.to' | translate }}</span>
              <span class="name" dir="auto">{{ a.person.displayName }}</span>
              <span class="meta">{{ a.stageName }}@if (a.onBehalfOf) { · {{ 'internalMessaging.approver.onBehalfOf' | translate: { name: a.onBehalfOf.displayName } }} }</span>
            </p>
          }
          <label class="label" for="approver-message">{{ 'internalMessaging.approver.message' | translate }}</label>
          <textarea id="approver-message" rows="4" [attr.maxlength]="max" [value]="body()" (input)="body.set($any($event.target).value)"></textarea>
          @if (error(); as code) {
            <p class="error" role="alert">{{ 'errors.' + code | translate }}</p>
          }
          <div class="actions">
            <button appButton variant="ghost" type="button" (click)="openInbox()">{{ 'internalMessaging.approver.openInbox' | translate }}</button>
            <span class="spacer"></span>
            <button appButton variant="secondary" type="button" (click)="ref.close()">{{ 'common.cancel' | translate }}</button>
            <button appButton type="submit" [busy]="sending()" [disabled]="sending() || !chosen() || !body().trim()">
              <app-icon name="send" [size]="16" />
              {{ 'internalMessaging.composer.send' | translate }}
            </button>
          </div>
        </form>
      }
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-4);
      inline-size: min(34rem, 94vw);
      max-block-size: calc(100dvh - var(--space-8));
      overflow-y: auto;
      padding: var(--space-6);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-xl);
      background: var(--color-surface);
      box-shadow: var(--shadow-overlay);
    }
    .title {
      font-size: var(--text-lg);
    }
    .request {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      margin: 0;
      padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-md);
      background: var(--color-paper);
      color: var(--color-text-muted);
    }
    .number {
      color: var(--color-accent);
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
    }
    .request-title {
      overflow: hidden;
      color: var(--color-text);
      font-weight: var(--weight-semibold);
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .form {
      display: grid;
      gap: var(--space-3);
    }
    .recipients {
      display: grid;
      gap: var(--space-2);
      margin: 0;
      padding: 0;
      border: 0;
    }
    legend,
    .label {
      margin-block-end: var(--space-1);
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
    }
    .recipient {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-md);
      cursor: pointer;
    }
    .recipient.is-chosen {
      border-color: var(--color-accent);
      background: var(--color-accent-soft);
    }
    .recipient:has(input:focus-visible) {
      outline: 2px solid var(--color-focus);
      outline-offset: 2px;
    }
    .recipient-text,
    .to {
      display: grid;
    }
    .to {
      margin: 0;
    }
    .name {
      font-weight: var(--weight-semibold);
    }
    .meta {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
    }
    textarea {
      inline-size: 100%;
      padding: var(--space-2) var(--space-3);
      font-size: var(--text-md);
      resize: vertical;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
    }
    .spacer {
      flex: 1;
    }
    .changed,
    .done,
    .explain {
      display: flex;
      gap: var(--space-2);
      align-items: flex-start;
      margin: 0;
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
    }
    .changed {
      background: var(--color-warning-soft);
      color: var(--color-warning);
    }
    .done {
      background: var(--color-success-soft);
      color: var(--color-success);
    }
    .explain {
      background: var(--color-info-soft);
      color: var(--color-text);
    }
    .error {
      color: var(--color-danger);
      font-size: var(--text-sm);
      font-weight: var(--weight-medium);
    }
    @media (max-width: 30rem) {
      :host {
        padding: var(--space-4);
      }
      .actions > * {
        flex: 1 1 auto;
      }
      .spacer {
        display: none;
      }
    }
  `,
})
export class MessageApproverDialog {
  protected readonly data = inject<MessageApproverData>(DIALOG_DATA);
  protected readonly ref = inject(DialogRef);
  private readonly api = inject(InternalMessagingApi);
  private readonly live = inject(MessagingLiveService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  protected readonly max = MAX_BODY;

  protected readonly state = signal<'loading' | 'ready' | 'unavailable' | 'sent'>('loading');
  protected readonly approverState = signal<ApproversState>('AWAITING');
  protected readonly approvers = signal<Approver[]>([]);
  protected readonly chosen = signal<Approver | null>(null);
  protected readonly changed = signal(false);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly body = signal(this.live.draft(this.draftKey()) || this.translate.instant('internalMessaging.approver.suggestion'));
  private result: ApproverMessageResult | null = null;
  // One id per message: a retry after a network failure is stored once.
  private clientMessageId = newClientMessageId();

  constructor() {
    this.loadApprovers();
  }

  protected send(): void {
    const approver = this.chosen();
    const text = this.body().trim();
    if (!approver || !text) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.live.setDraft(this.draftKey(), this.body());
    this.api.messageApprover(this.data.requestId, approver.person.id, text, this.clientMessageId).subscribe({
      next: (result) => {
        this.result = result;
        this.sending.set(false);
        this.live.setDraft(this.draftKey(), '');
        this.state.set('sent');
      },
      error: (error: unknown) => {
        this.sending.set(false);
        const problem = toApiProblem(error);
        if (problem.code === 'approver-changed') {
          // Nothing was sent. Show who decides now, and keep the text.
          this.changed.set(true);
          this.clientMessageId = newClientMessageId();
          this.loadApprovers();
        } else {
          this.error.set(problem.code);
        }
      },
    });
  }

  protected openConversation(): void {
    const id = this.result?.conversationId;
    this.ref.close();
    void this.router.navigate(id ? ['/conversations', id] : ['/conversations']);
  }

  protected openInbox(): void {
    this.ref.close();
    void this.router.navigate(['/conversations']);
  }

  private loadApprovers(): void {
    this.api.approvers(this.data.requestId).subscribe({
      next: (result) => {
        this.approverState.set(result.state);
        this.approvers.set(result.approvers);
        const previous = this.chosen()?.person.id;
        this.chosen.set(result.approvers.find((a) => a.person.id === previous) ?? (result.approvers.length === 1 ? result.approvers[0] : null));
        this.state.set(result.state === 'AWAITING' ? 'ready' : 'unavailable');
      },
      error: () => {
        this.approverState.set('NO_APPROVER');
        this.state.set('unavailable');
      },
    });
  }

  private draftKey(): string {
    return 'approver:' + this.data.requestId;
  }
}

export function openMessageApprover(dialog: Dialog, data: MessageApproverData): Observable<unknown> {
  return dialog.open(MessageApproverDialog, {
    data,
    ariaLabelledBy: 'message-approver-title',
    autoFocus: 'textarea',
    restoreFocus: true,
  }).closed;
}
