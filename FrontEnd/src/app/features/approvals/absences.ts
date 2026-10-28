import { Dialog } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { openConfirm } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { PersonOption } from '../requests/request.models';
import { Absence } from './approval.models';
import { ApprovalsApi } from './approvals.api';

type PageState = 'loading' | 'ready' | 'error';

/** Today in the browser's time zone as `YYYY-MM-DD`. Exported for tests. */
export function todayIso(now = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

/**
 * An absence is entered as whole days in the browser's time zone and sent as instants: from the start of the first
 * day to the start of the day after the last one (exclusive). Exported for tests.
 */
export function absencePeriod(startDate: string, endDate: string): { startsAt: string; endsAt: string } {
  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  end.setDate(end.getDate() + 1);
  return { startsAt: start.toISOString(), endsAt: end.toISOString() };
}

/** The last day of an absence, for display: the day before its exclusive end. Exported for tests. */
export function lastDay(endsAt: string): string {
  return new Date(Date.parse(endsAt) - 1).toISOString();
}

/** End date must not be before the start date; the server re-checks overlaps (`absence-overlap`). */
export const periodValidator: ValidatorFn = (group): ValidationErrors | null => {
  const start = group.get('startDate')?.value as string | null;
  const end = group.get('endDate')?.value as string | null;
  return start && end && end < start ? { endBeforeStart: true } : null;
};

/**
 * "My absences": while I am away, new approval tasks go to my delegate and are marked "on behalf of" me
 * (S2-AC-06). The server checks that the delegate is eligible and that periods do not overlap.
 */
@Component({
  selector: 'app-absences',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, Skeleton, TranslatePipe],
  template: `
    <app-page-header [heading]="'absences.title' | translate" [description]="'absences.description' | translate" />

    <div class="layout">
      <section class="panel" aria-labelledby="absence-new">
        <h2 class="panel-title" id="absence-new">{{ 'absences.new' | translate }}</h2>
        <form class="form" [formGroup]="form" (ngSubmit)="create()" novalidate>
          <div class="field">
            <label for="absence-delegate">{{ 'absences.delegate' | translate }} *</label>
            <select id="absence-delegate" formControlName="delegateId" [attr.aria-invalid]="invalid('delegateId')" aria-describedby="absence-delegate-error">
              <option [ngValue]="null">{{ 'requests.form.choose' | translate }}</option>
              @for (person of delegates(); track person.id) {
                <option [ngValue]="person.id">{{ person.displayName }}</option>
              }
            </select>
            <p id="absence-delegate-error" class="error small">
              @if (invalid('delegateId')) {
                {{ 'requests.errors.required' | translate }}
              }
            </p>
          </div>
          <div class="dates">
            <div class="field">
              <label for="absence-start">{{ 'absences.startDate' | translate }} *</label>
              <input id="absence-start" type="date" formControlName="startDate" [min]="today" [attr.aria-invalid]="invalid('startDate')" />
            </div>
            <div class="field">
              <label for="absence-end">{{ 'absences.endDate' | translate }} *</label>
              <input
                id="absence-end"
                type="date"
                formControlName="endDate"
                [min]="form.controls.startDate.value || today"
                [attr.aria-invalid]="invalid('endDate') || (submitted() && form.hasError('endBeforeStart'))"
                aria-describedby="absence-dates-error"
              />
            </div>
          </div>
          <p id="absence-dates-error" class="error small">
            @if (submitted() && (form.controls.startDate.invalid || form.controls.endDate.invalid)) {
              {{ 'absences.datesRequired' | translate }}
            } @else if (submitted() && form.hasError('endBeforeStart')) {
              {{ 'absences.endBeforeStart' | translate }}
            }
          </p>
          <div class="field">
            <label for="absence-reason">{{ 'absences.reason' | translate }}</label>
            <input id="absence-reason" type="text" formControlName="reason" maxlength="500" />
          </div>
          @if (error(); as code) {
            <p class="error" role="alert">{{ 'absences.errors.' + code | translate }}</p>
          }
          <button appButton type="submit" [disabled]="saving()">
            <app-icon name="calendar" [size]="16" />
            {{ (saving() ? 'requests.form.saving' : 'absences.save') | translate }}
          </button>
        </form>
      </section>

      <section class="panel" aria-labelledby="absence-list">
        <h2 class="panel-title" id="absence-list">{{ 'absences.planned' | translate }}</h2>
        @switch (state()) {
          @case ('loading') {
            <app-skeleton height="4rem" />
          }
          @case ('error') {
            <app-error-state [heading]="'absences.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="load()" />
          }
          @default {
            @if (absences().length === 0) {
              <app-empty-state icon="calendar" [heading]="'absences.empty.title' | translate" [message]="'absences.empty.message' | translate" />
            } @else {
              <ul class="list">
                @for (absence of absences(); track absence.id) {
                  <li class="item">
                    <div>
                      <p class="strong tabular">
                        {{ absence.startsAt | date: 'mediumDate' : undefined : language.locale() }}
                        –
                        {{ lastDayOf(absence) | date: 'mediumDate' : undefined : language.locale() }}
                      </p>
                      <p class="muted small">{{ 'absences.delegatedTo' | translate: { name: absence.delegate.displayName } }}</p>
                      @if (absence.reason) {
                        <p class="small">{{ absence.reason }}</p>
                      }
                      @if (absence.adminOverride && absence.createdBy) {
                        <p class="muted small">{{ 'absences.byAdmin' | translate: { name: absence.createdBy.displayName } }}</p>
                      }
                    </div>
                    <button appButton variant="ghost" size="small" type="button" (click)="remove(absence)">
                      <app-icon name="trash" [size]="16" />
                      {{ 'absences.remove' | translate }}
                    </button>
                  </li>
                }
              </ul>
            }
          }
        }
      </section>
    </div>
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 24rem) minmax(0, 1fr);
      gap: var(--space-6);
      align-items: start;
    }

    @media (max-width: 47.99rem) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .form {
      display: grid;
      gap: var(--space-3);
    }

    .dates {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-3);
    }

    .field {
      display: grid;
      gap: var(--space-1);
      font-size: var(--text-sm);
    }

    .field label {
      font-weight: var(--weight-medium);
    }

    input,
    select {
      min-block-size: 2.5rem;
      padding-inline: var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
    }

    [aria-invalid='true'] {
      border-color: var(--color-danger);
    }

    .error {
      color: var(--color-danger);
    }

    .error:empty {
      display: none;
    }

    .list {
      display: grid;
      gap: var(--space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-md);
    }
  `,
})
export class AbsencesPage {
  private readonly api = inject(ApprovalsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  protected readonly language = inject(LanguageService);

  protected readonly today = todayIso();
  protected readonly state = signal<PageState>('loading');
  protected readonly absences = signal<Absence[]>([]);
  protected readonly delegates = signal<PersonOption[]>([]);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  private pendingKey: string | null = null;

  protected readonly form = new FormGroup(
    {
      delegateId: new FormControl<number | null>(null, Validators.required),
      startDate: new FormControl(this.today, { nonNullable: true, validators: [Validators.required] }),
      endDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      reason: new FormControl('', { nonNullable: true }),
    },
    { validators: periodValidator },
  );

  constructor() {
    this.load();
    this.api.absenceDelegates().subscribe({ next: (people) => this.delegates.set(people), error: () => undefined });
    this.form.valueChanges.subscribe(() => (this.pendingKey = null));
  }

  load(): void {
    this.state.set('loading');
    this.api.myAbsences().subscribe({
      next: (absences) => {
        this.absences.set(absences);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected lastDayOf(absence: Absence): string {
    return lastDay(absence.endsAt);
  }

  protected invalid(name: 'delegateId' | 'startDate' | 'endDate'): boolean {
    return this.submitted() && this.form.controls[name].invalid;
  }

  protected create(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    this.pendingKey ??= newIdempotencyKey();
    this.saving.set(true);
    this.api
      .createAbsence(
        { delegateUserId: value.delegateId!, ...absencePeriod(value.startDate, value.endDate), reason: value.reason.trim() || null },
        this.pendingKey,
      )
      .subscribe({
        next: (absence) => {
          this.saving.set(false);
          this.pendingKey = null;
          this.absences.update((list) => [...list, absence].sort((a, b) => a.startsAt.localeCompare(b.startsAt)));
          this.form.reset({ delegateId: null, startDate: this.today, endDate: '', reason: '' });
          this.submitted.set(false);
          this.notifications.success('absences.saved');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 401) {
            return;
          }
          const code = problem.errors[0]?.code ?? problem.code;
          this.error.set(KNOWN.has(code) ? code : 'save-failed');
        },
      });
  }

  protected remove(absence: Absence): void {
    openConfirm(this.dialog, {
      titleKey: 'absences.removeTitle',
      messageKey: 'absences.removeMessage',
      confirmKey: 'absences.remove',
      tone: 'danger',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deleteAbsence(absence.id).subscribe({
        next: () => {
          this.absences.update((list) => list.filter((item) => item.id !== absence.id));
          this.notifications.success('absences.removed');
        },
        error: () => this.notifications.error('absences.errors.save-failed'),
      });
    });
  }
}

const KNOWN = new Set(['absence-overlap', 'delegate-not-eligible', 'self-delegation', 'invalid-period', 'save-failed']);
