import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { AskResponse, AssistantApi, AssistantStatus } from './assistant.api';

@Component({
  selector: 'app-assistant-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, ErrorState, PageHeader, RouterLink, Skeleton, TranslatePipe],
  template: `
    <app-page-header [heading]="'assistant.title' | translate" [description]="'assistant.subtitle' | translate" />

    @if (loadingStatus()) {
      <app-skeleton />
    } @else if (statusError(); as code) {
      <app-error-state [heading]="'errors.' + code | translate" />
    } @else if (status(); as st) {
      <p class="mode" role="status">
        <strong>{{ 'assistant.modeLabel' | translate }}:</strong>
        {{ st.mode }}
        — {{ st.label }}
      </p>

      @if (!st.available) {
        <p class="muted">{{ 'assistant.unavailable' | translate }}</p>
      } @else {
        <div class="starters" role="group" [attr.aria-label]="'assistant.starters' | translate">
          @for (key of starterKeys; track key) {
            <button type="button" class="chip" (click)="useStarter(key)">{{ ('assistant.starter.' + key) | translate }}</button>
          }
        </div>

        <form class="form" (submit)="$event.preventDefault(); ask()" novalidate>
          <label class="label" for="assistant-q">{{ 'assistant.question' | translate }}</label>
          <textarea id="assistant-q" rows="3" maxlength="2000" [value]="question()" (input)="question.set($any($event.target).value)"></textarea>
          <div class="actions">
            <button appButton type="submit" [disabled]="busy() || !question().trim()">{{ 'assistant.ask' | translate }}</button>
          </div>
        </form>

        @if (busy()) {
          <p class="muted" aria-live="polite">{{ 'assistant.thinking' | translate }}</p>
        }
        @if (askError(); as err) {
          <p class="error" role="alert">{{ 'errors.' + err | translate }}</p>
        }
        @if (answer(); as a) {
          <article class="answer" aria-live="polite">
            <pre class="body">{{ a.answerMarkdown }}</pre>
            @if (a.sources?.length) {
              <h2 class="sources-title">{{ 'assistant.sources' | translate }}</h2>
              <ul>
                @for (s of a.sources; track s.type + '-' + s.id) {
                  <li><a [routerLink]="s.path">{{ s.label }}</a> ({{ s.type }})</li>
                }
              </ul>
            }
            @if (a.draft; as d) {
              <section class="draft">
                <h2>{{ d.label }}</h2>
                <p class="muted small">{{ 'assistant.draftHint' | translate }}</p>
                <pre class="body">{{ d.bodyText }}</pre>
                <button appButton type="button" variant="secondary" (click)="copyDraft(d.bodyText)">
                  {{ 'assistant.copyDraft' | translate }}
                </button>
              </section>
            }
          </article>
        }
      }
    }
  `,
  styles: `
    .mode { margin-block-end: var(--space-4); }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
    .starters { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-block-end: var(--space-4); }
    .chip { border: 1px solid var(--color-border); background: transparent; padding: 0.35rem 0.75rem; cursor: pointer; border-radius: var(--radius-sm); }
    .form { display: grid; gap: var(--space-2); margin-block-end: var(--space-4); }
    textarea { width: 100%; }
    .answer { border: 1px solid var(--color-border); padding: var(--space-4); }
    .body { white-space: pre-wrap; font-family: inherit; margin: 0; }
    .draft { margin-block-start: var(--space-4); padding-block-start: var(--space-3); border-block-start: 1px dashed var(--color-border); }
    .error { color: var(--color-danger); }
    .sources-title { font-size: var(--text-sm); margin-block-start: var(--space-3); }
  `,
})
export class AssistantPage implements OnInit {
  private readonly api = inject(AssistantApi);
  private readonly notify = inject(NotificationService);

  protected readonly starterKeys = ['status', 'pos', 'summarize', 'draft'] as const;
  protected readonly status = signal<AssistantStatus | null>(null);
  protected readonly loadingStatus = signal(true);
  protected readonly statusError = signal<string | null>(null);
  protected readonly question = signal('');
  protected readonly busy = signal(false);
  protected readonly askError = signal<string | null>(null);
  protected readonly answer = signal<AskResponse | null>(null);

  ngOnInit(): void {
    this.api.status().subscribe({
      next: (st) => {
        this.status.set(st);
        this.loadingStatus.set(false);
      },
      error: (err) => {
        this.statusError.set(toApiProblem(err).code);
        this.loadingStatus.set(false);
      },
    });
  }

  protected useStarter(key: string): void {
    const map: Record<string, string> = {
      status: 'Where is my authorized purchase request now?',
      pos: 'Which of my assigned POs are awaiting supplier confirmation?',
      summarize: 'Summarize this dossier and cite its records.',
      draft: 'Draft a supplier follow-up without internal notes.',
    };
    this.question.set(map[key] ?? '');
  }

  protected ask(): void {
    if (!this.question().trim()) {
      return;
    }
    this.busy.set(true);
    this.askError.set(null);
    this.api.ask(this.question().trim(), undefined, newIdempotencyKey()).subscribe({
      next: (res) => {
        this.answer.set(res);
        this.busy.set(false);
      },
      error: (err) => {
        this.askError.set(toApiProblem(err).code);
        this.busy.set(false);
      },
    });
  }

  protected copyDraft(text: string): void {
    void navigator.clipboard.writeText(text).then(
      () => this.notify.success('assistant.draftCopied'),
      () => this.notify.error('assistant.draftCopyFailed'),
    );
  }
}
