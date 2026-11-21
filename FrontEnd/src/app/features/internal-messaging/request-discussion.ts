import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { toApiProblem } from '../../core/api/api-problem';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Skeleton } from '../../shared/ui/skeleton';
import { InternalMessagingApi } from './internal-messaging.api';
import { Discussion, Person } from './internal-messaging.models';
import { MessageThread } from './message-thread';

/**
 * The request's discussion on its record. Participation is explicit: the person who starts it and the current
 * approvers are in; others are added by a participant, and only if they can already see the request. Someone who can
 * see the request but is not a participant is told the discussion exists, never shown it.
 */
@Component({
  selector: 'app-request-discussion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, MessageThread, RouterLink, Skeleton, TranslatePipe],
  template: `
    <section class="panel discussion" aria-labelledby="discussion-title">
      <header class="head">
        <h2 class="panel-title" id="discussion-title">
          <app-icon name="messages" [size]="18" />
          {{ 'internalMessaging.discussion.title' | translate }}
        </h2>
        @if (conversationId(); as id) {
          <a class="open-full" [routerLink]="['/conversations', id]">{{ 'internalMessaging.discussion.openFull' | translate }}</a>
        }
      </header>

      @switch (state()) {
        @case ('loading') {
          <app-skeleton height="4rem" />
        }
        @case ('error') {
          <p class="muted">{{ 'internalMessaging.discussion.loadError' | translate }}</p>
          <button appButton variant="secondary" size="small" type="button" (click)="load()">{{ 'common.retry' | translate }}</button>
        }
        @default {
          @if (discussion(); as d) {
            @if (d.conversation; as c) {
              <div class="participants">
                <span class="label">{{ 'internalMessaging.discussion.participants' | translate: { count: c.participants.length } }}</span>
                <ul class="chips">
                  @for (p of c.participants; track p.id) {
                    <li class="chip" dir="auto">{{ p.displayName }}</li>
                  }
                </ul>
                @if (c.canAddParticipants) {
                  <button appButton variant="ghost" size="small" type="button" (click)="toggleAdd()" [attr.aria-expanded]="adding()">
                    <app-icon name="user-plus" [size]="16" />
                    {{ 'internalMessaging.discussion.add' | translate }}
                  </button>
                }
              </div>
              @if (adding()) {
                <div class="add" role="search">
                  <p class="hint">{{ 'internalMessaging.discussion.addHint' | translate }}</p>
                  <label class="visually-hidden" for="discussion-candidate">{{ 'internalMessaging.discussion.addSearch' | translate }}</label>
                  <input id="discussion-candidate" type="search" [placeholder]="'internalMessaging.discussion.addSearch' | translate"
                         (input)="candidateSearch.next($any($event.target).value)" />
                  <ul class="candidates">
                    @for (p of candidates(); track p.id) {
                      <li>
                        <button type="button" class="candidate" [disabled]="busy()" (click)="add(p)">
                          <span class="name" dir="auto">{{ p.displayName }}</span>
                          <span class="meta">{{ p.department }}</span>
                          <app-icon name="plus" [size]="16" />
                        </button>
                      </li>
                    } @empty {
                      <li class="muted">{{ 'internalMessaging.discussion.noCandidates' | translate }}</li>
                    }
                  </ul>
                  @if (addError(); as code) {
                    <p class="error" role="alert">{{ 'internalMessaging.errors.' + code | translate }}</p>
                  }
                </div>
              }
              <app-message-thread class="thread" [conversationId]="c.id" [allowAttach]="false" />
            } @else if (d.exists) {
              <p class="muted">{{ 'internalMessaging.discussion.notParticipant' | translate }}</p>
            } @else if (d.canStart) {
              <p class="muted">{{ 'internalMessaging.discussion.startHint' | translate }}</p>
              <button appButton variant="secondary" type="button" [busy]="busy()" [disabled]="busy()" (click)="start()">
                <app-icon name="messages" [size]="16" />
                {{ 'internalMessaging.discussion.start' | translate }}
              </button>
              @if (addError(); as code) {
                <p class="error" role="alert">{{ 'internalMessaging.errors.' + code | translate }}</p>
              }
            }
          }
        }
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
      margin-block-end: var(--space-3);
    }
    .panel-title {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      margin: 0;
    }
    .open-full {
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
    }
    .participants {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
    }
    .label {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
      font-weight: var(--weight-semibold);
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-1);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .chip {
      padding: 0.125rem var(--space-3);
      border-radius: var(--radius-pill);
      background: var(--color-surface-sunken);
      font-size: var(--text-sm);
    }
    .add {
      display: grid;
      gap: var(--space-2);
      margin-block: var(--space-3);
      padding: var(--space-3);
      border-radius: var(--radius-md);
      background: var(--color-paper);
    }
    .add input {
      min-block-size: 2.5rem;
      padding-inline: var(--space-3);
    }
    .hint,
    .muted {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
    .candidates {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .candidate {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      inline-size: 100%;
      min-block-size: 2.75rem;
      padding: var(--space-1) var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      color: var(--color-text);
      text-align: start;
      cursor: pointer;
    }
    .candidate:hover {
      background: var(--color-surface);
    }
    .candidate .name {
      flex: 1;
      font-weight: var(--weight-semibold);
    }
    .candidate .meta {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
    }
    .error {
      color: var(--color-danger);
      font-size: var(--text-sm);
      font-weight: var(--weight-medium);
    }
    .thread {
      margin-block-start: var(--space-3);
      margin-inline: calc(-1 * var(--space-6));
      margin-block-end: calc(-1 * var(--space-5));
      max-block-size: 34rem;
      border-block-start: 1px solid var(--color-line);
    }
    @media (max-width: 40rem) {
      .thread {
        margin-inline: calc(-1 * var(--space-4));
        margin-block-end: calc(-1 * var(--space-4));
      }
    }
  `,
})
export class RequestDiscussion {
  readonly requestId = input.required<number>();

  private readonly api = inject(InternalMessagingApi);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly discussion = signal<Discussion | null>(null);
  protected readonly conversationId = computed(() => this.discussion()?.conversation?.id ?? null);
  protected readonly adding = signal(false);
  protected readonly candidates = signal<Person[]>([]);
  protected readonly busy = signal(false);
  protected readonly addError = signal<string | null>(null);
  protected readonly candidateSearch = new Subject<string>();

  constructor() {
    effect(() => {
      this.requestId();
      untracked(() => this.load());
    });
    this.candidateSearch.pipe(debounceTime(250), takeUntilDestroyed()).subscribe((q) => this.findCandidates(q));
  }

  load(): void {
    this.state.set('loading');
    this.api.discussion(this.requestId()).subscribe({
      next: (d) => {
        this.discussion.set(d);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected start(): void {
    this.busy.set(true);
    this.addError.set(null);
    this.api.startDiscussion(this.requestId()).subscribe({
      next: (conversation) => {
        this.busy.set(false);
        this.discussion.set({ conversation, exists: true, canStart: false });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.addError.set(toApiProblem(error).code ?? 'unexpected');
        this.load();
      },
    });
  }

  protected toggleAdd(): void {
    this.adding.update((v) => !v);
    this.addError.set(null);
    if (this.adding()) {
      this.findCandidates('');
    }
  }

  protected add(person: Person): void {
    const id = this.conversationId();
    if (id === null) {
      return;
    }
    this.busy.set(true);
    this.addError.set(null);
    this.api.addParticipant(id, person.id).subscribe({
      next: (conversation) => {
        this.busy.set(false);
        this.adding.set(false);
        this.discussion.set({ conversation, exists: true, canStart: false });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = toApiProblem(error);
        this.addError.set(problem.errors?.[0]?.code ?? problem.code ?? 'unexpected');
      },
    });
  }

  private findCandidates(q: string): void {
    const id = this.conversationId();
    if (id === null) {
      return;
    }
    this.api.candidates(id, q).subscribe({
      next: (people) => this.candidates.set(people),
      error: () => this.candidates.set([]),
    });
  }
}
