import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { AdminCompany, AdminUser } from '../approvals/approval.models';
import { ApprovalsApi } from '../approvals/approvals.api';
import { AuditCenterApi, AuditEntry, AuditFilter, Verification, dayToInstant } from './audit-center.api';

type PageState = 'loading' | 'ready' | 'error';

const CODE = /^[A-Z][A-Z0-9_]{0,59}$/;

/**
 * Audit Center (Sprint 2, P1.3): global administrators read the whole audit trail, export it and verify its hash
 * chains. Read-only: nothing on this page can change an event, and every search, export and verification is itself
 * recorded in the trail.
 */
@Component({
  selector: 'app-audit-center',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, Skeleton, TranslatePipe],
  template: `
    <app-page-header [heading]="'auditCenter.title' | translate" [description]="'auditCenter.description' | translate" />

    <section class="panel" aria-labelledby="audit-filters">
      <h2 class="panel-title" id="audit-filters">{{ 'auditCenter.filters.title' | translate }}</h2>
      <form class="form" [formGroup]="form" (ngSubmit)="search()" novalidate>
        <div class="grid">
          <div class="field">
            <label for="audit-from">{{ 'auditCenter.filters.from' | translate }}</label>
            <input id="audit-from" type="date" formControlName="from" />
          </div>
          <div class="field">
            <label for="audit-to">{{ 'auditCenter.filters.to' | translate }}</label>
            <input id="audit-to" type="date" formControlName="to" />
          </div>
          <div class="field">
            <label for="audit-company">{{ 'auditCenter.filters.company' | translate }}</label>
            <select id="audit-company" formControlName="companyId">
              <option value="">{{ 'auditCenter.filters.anyCompany' | translate }}</option>
              @for (company of companies(); track company.id) {
                <option [value]="company.id">{{ company.name }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="audit-actor">{{ 'auditCenter.filters.actor' | translate }}</label>
            <select id="audit-actor" formControlName="actorUserId">
              <option value="">{{ 'auditCenter.filters.anyActor' | translate }}</option>
              @for (user of users(); track user.id) {
                <option [value]="user.id">{{ user.displayName }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="audit-action">{{ 'auditCenter.filters.action' | translate }}</label>
            <input id="audit-action" type="text" dir="ltr" formControlName="action" placeholder="USER_ACTIVATED" maxlength="60" />
          </div>
          <div class="field">
            <label for="audit-entity-type">{{ 'auditCenter.filters.entityType' | translate }}</label>
            <input id="audit-entity-type" type="text" dir="ltr" formControlName="entityType" placeholder="PURCHASE_REQUEST" maxlength="60" />
          </div>
          <div class="field">
            <label for="audit-entity-id">{{ 'auditCenter.filters.entityId' | translate }}</label>
            <input id="audit-entity-id" type="text" dir="ltr" formControlName="entityId" maxlength="64" />
          </div>
        </div>
        <p class="muted small">{{ 'auditCenter.filters.hint' | translate }}</p>
        <p class="error small" role="alert">
          @if (invalidCode()) {
            {{ 'auditCenter.errors.code' | translate }}
          } @else if (filterError(); as code) {
            {{ 'errors.' + code | translate }}
          }
        </p>
        <div class="form-actions">
          <button appButton type="submit" [disabled]="state() === 'loading'">
            <app-icon name="search" [size]="16" />
            {{ 'auditCenter.filters.search' | translate }}
          </button>
          <button appButton variant="secondary" type="button" (click)="reset()">{{ 'auditCenter.filters.reset' | translate }}</button>
          <a appButton variant="secondary" [href]="exportUrl()" download>
            <app-icon name="download" [size]="16" />
            {{ 'auditCenter.export' | translate }}
          </a>
        </div>
        <p class="muted small">{{ 'auditCenter.exportHint' | translate }}</p>
      </form>
    </section>

    <section class="panel" aria-labelledby="audit-verification" aria-live="polite">
      <h2 class="panel-title" id="audit-verification">{{ 'auditCenter.verification.title' | translate }}</h2>
      @if (verification(); as v) {
        <p class="verification" [attr.data-outcome]="v.outcome">
          {{ 'auditCenter.verification.outcome.' + v.outcome | translate: { chains: v.chainsChecked, total: v.chainsTotal, events: v.eventsChecked } }}
        </p>
        @if (v.outcome === 'INVALID') {
          <p class="error">
            {{ 'auditCenter.verification.invalidAt' | translate: { chain: v.invalidChain ?? '—', event: v.firstInvalidEventId ?? '—' } }}
          </p>
        }
        @if (v.finishedAt) {
          <p class="muted small">
            {{ 'auditCenter.verification.lastRun' | translate: { by: v.startedBy ?? '—', at: (v.finishedAt | date: 'medium' : undefined : language.locale()) } }}
          </p>
        }
      }
      <p class="error small" role="alert">
        @if (verificationError(); as code) {
          {{ 'errors.' + code | translate }}
        }
      </p>
      <button appButton variant="secondary" type="button" (click)="verify()" [disabled]="verification()?.outcome === 'RUNNING'">
        <app-icon name="lock" [size]="16" />
        {{ 'auditCenter.verification.start' | translate }}
      </button>
    </section>

    <section class="panel" aria-labelledby="audit-events">
      <h2 class="panel-title" id="audit-events">{{ 'auditCenter.events' | translate }}</h2>
      @switch (state()) {
        @case ('loading') {
          <app-skeleton height="8rem" />
        }
        @case ('error') {
          <app-error-state [heading]="'auditCenter.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="search()" />
        }
        @default {
          @if (events().length === 0) {
            <app-empty-state icon="history" [heading]="'auditCenter.empty.title' | translate" [message]="'auditCenter.empty.message' | translate" />
          } @else {
            <div class="table-wrap" tabindex="0" role="region" aria-labelledby="audit-events">
              <table>
                <thead>
                  <tr>
                    <th scope="col">{{ 'auditCenter.columns.when' | translate }}</th>
                    <th scope="col">{{ 'auditCenter.columns.company' | translate }}</th>
                    <th scope="col">{{ 'auditCenter.columns.actor' | translate }}</th>
                    <th scope="col">{{ 'auditCenter.columns.action' | translate }}</th>
                    <th scope="col">{{ 'auditCenter.columns.entity' | translate }}</th>
                    <th scope="col">{{ 'auditCenter.columns.details' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (event of events(); track event.id) {
                    <tr>
                      <td class="tabular">{{ event.occurredAt | date: 'medium' : undefined : language.locale() }}</td>
                      <td>{{ event.companyName ?? ('auditCenter.groupWide' | translate) }}</td>
                      <td>{{ event.actorLabel }}</td>
                      <td><bdi dir="ltr">{{ event.action }}</bdi></td>
                      <td><bdi dir="ltr">{{ event.entityType }} {{ event.entityId }}</bdi></td>
                      <td>
                        <button appButton variant="ghost" type="button" [attr.aria-expanded]="expanded() === event.id" (click)="toggle(event.id)">
                          {{ (expanded() === event.id ? 'auditCenter.hide' : 'auditCenter.show') | translate }}
                        </button>
                        @if (expanded() === event.id) {
                          <dl class="details">
                            <dt>{{ 'auditCenter.columns.chain' | translate }}</dt>
                            <dd><bdi dir="ltr">{{ event.chainKey }} #{{ event.chainSeq }} (id {{ event.id }})</bdi></dd>
                            @if (event.fromStatus || event.toStatus) {
                              <dt>{{ 'auditCenter.columns.status' | translate }}</dt>
                              <dd><bdi dir="ltr">{{ event.fromStatus ?? '—' }} → {{ event.toStatus ?? '—' }}</bdi></dd>
                            }
                            @if (event.comment) {
                              <dt>{{ 'auditCenter.columns.comment' | translate }}</dt>
                              <dd>{{ event.comment }}</dd>
                            }
                            @if (event.payloadJson) {
                              <dt>{{ 'auditCenter.columns.payload' | translate }}</dt>
                              <dd><code dir="ltr">{{ event.payloadJson }}</code></dd>
                            }
                          </dl>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (nextCursor()) {
              <button appButton variant="secondary" type="button" (click)="more()" [disabled]="loadingMore()">
                {{ (loadingMore() ? 'auditCenter.loading' : 'auditCenter.more') | translate }}
              </button>
            }
          }
        }
      }
    </section>
  `,
  styleUrl: './admin.scss',
})
export class AuditCenterPage {
  private readonly api = inject(AuditCenterApi);
  private readonly approvals = inject(ApprovalsApi);
  private readonly http = inject(HttpClient);
  protected readonly language = inject(LanguageService);

  protected readonly state = signal<PageState>('loading');
  protected readonly events = signal<AuditEntry[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly loadingMore = signal(false);
  protected readonly expanded = signal<number | null>(null);
  protected readonly companies = signal<AdminCompany[]>([]);
  protected readonly users = signal<AdminUser[]>([]);
  protected readonly filterError = signal<string | null>(null);
  protected readonly invalidCode = signal(false);
  protected readonly verification = signal<Verification | null>(null);
  protected readonly verificationError = signal<string | null>(null);
  private readonly applied = signal<AuditFilter>({});
  private poll: ReturnType<typeof setTimeout> | null = null;

  protected readonly form = new FormGroup({
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
    companyId: new FormControl('', { nonNullable: true }),
    actorUserId: new FormControl('', { nonNullable: true }),
    action: new FormControl('', { nonNullable: true }),
    entityType: new FormControl('', { nonNullable: true }),
    entityId: new FormControl('', { nonNullable: true }),
  });

  protected readonly exportUrl = computed(() => this.api.exportUrl(this.applied()));

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
    this.approvals.adminCompanies().subscribe({ next: (list) => this.companies.set(list), error: () => undefined });
    this.http.get<AdminUser[]>('/api/v1/admin/users').subscribe({ next: (list) => this.users.set(list), error: () => undefined });
    this.refreshVerification();
    this.search();
  }

  /** The form's filters, or null when a code is not an upper-case word. Exported behaviour is tested. */
  filterFromForm(): AuditFilter | null {
    const value = this.form.getRawValue();
    const action = value.action.trim().toUpperCase();
    const entityType = value.entityType.trim().toUpperCase();
    if ((action && !CODE.test(action)) || (entityType && !CODE.test(entityType))) {
      return null;
    }
    return {
      from: dayToInstant(value.from),
      to: dayToInstant(value.to, true),
      companyId: value.companyId ? Number(value.companyId) : null,
      actorUserId: value.actorUserId ? Number(value.actorUserId) : null,
      action,
      entityType,
      entityId: value.entityId.trim(),
    };
  }

  protected search(): void {
    const filter = this.filterFromForm();
    this.invalidCode.set(filter === null);
    this.filterError.set(null);
    if (filter === null) {
      return;
    }
    this.applied.set(filter);
    this.state.set('loading');
    this.expanded.set(null);
    this.api.events(filter, null).subscribe({
      next: (page) => {
        this.events.set(page.items);
        this.nextCursor.set(page.nextCursor);
        this.state.set('ready');
      },
      error: (error: unknown) => {
        const problem = toApiProblem(error);
        if (problem.status === 422 || problem.status === 400) {
          this.filterError.set(problem.code);
          this.state.set('ready');
        } else {
          this.state.set('error');
        }
      },
    });
  }

  protected more(): void {
    const cursor = this.nextCursor();
    if (!cursor || this.loadingMore()) {
      return;
    }
    this.loadingMore.set(true);
    this.api.events(this.applied(), cursor).subscribe({
      next: (page) => {
        this.events.update((events) => [...events, ...page.items]);
        this.nextCursor.set(page.nextCursor);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  protected reset(): void {
    this.form.reset();
    this.search();
  }

  protected toggle(id: number): void {
    this.expanded.update((current) => (current === id ? null : id));
  }

  protected verify(): void {
    this.verificationError.set(null);
    this.api.startVerification().subscribe({
      next: (state) => {
        this.verification.set(state);
        this.schedulePoll();
      },
      error: (error: unknown) => {
        const problem = toApiProblem(error);
        if (problem.status !== 401) {
          this.verificationError.set(problem.code);
        }
      },
    });
  }

  private refreshVerification(): void {
    this.api.verification().subscribe({
      next: (state) => {
        this.verification.set(state);
        if (state.outcome === 'RUNNING') {
          this.schedulePoll();
        }
      },
      error: () => undefined,
    });
  }

  private schedulePoll(): void {
    this.stopPolling();
    this.poll = setTimeout(() => this.refreshVerification(), 2000);
  }

  private stopPolling(): void {
    if (this.poll !== null) {
      clearTimeout(this.poll);
      this.poll = null;
    }
  }
}
