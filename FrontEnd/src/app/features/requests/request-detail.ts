import { Dialog } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { openConfirm } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { formatMoney } from './money';
import { PurchaseRequest, RequestAction, optionLabel } from './request.models';
import { stageOf, statusKey, statusTone } from './request-status';
import { RequestsApi } from './requests.api';

type PageState = 'loading' | 'ready' | 'not-found' | 'error';

/**
 * Human-readable elapsed time ("3 hours", "2 days") for "owner since", in the current language.
 * Exported for tests.
 */
export function elapsedText(fromIso: string, now: number, locale: string): string {
  const from = Date.parse(fromIso);
  if (!Number.isFinite(from)) {
    return '';
  }
  const seconds = Math.max(0, Math.round((now - from) / 1000));
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ];
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto', style: 'long' });
  for (const [unit, size] of units) {
    if (seconds >= size) {
      return format.format(-Math.floor(seconds / size), unit);
    }
  }
  return format.format(0, 'minute');
}

/**
 * One request as a dossier: number, status, current stage, who owns it and since when (S1-AC-11), actors, lines and
 * the transition history.
 */
@Component({
  selector: 'app-request-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  templateUrl: './request-detail.html',
  styleUrl: './request-detail.scss',
})
export class RequestDetailPage {
  private readonly api = inject(RequestsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  protected readonly language = inject(LanguageService);

  protected readonly pageState = signal<PageState>('loading');
  protected readonly loadError = signal<string | null>(null);
  protected readonly request = signal<PurchaseRequest | null>(null);
  protected readonly submitting = signal(false);
  /** Ticks every minute so "owner since" stays current without reloading. */
  private readonly now = signal(Date.now());

  /** Exactly the actions the server allows (S1DR-18); the UI never re-derives permissions. */
  protected readonly actions = computed<RequestAction[]>(() => this.request()?.actions ?? []);

  protected readonly stage = computed(() => {
    const request = this.request();
    return request ? (request.stage ?? stageOf(request.status)) : null;
  });

  protected readonly ownerSince = computed(() => {
    const since = this.request()?.ownerSince;
    return since ? elapsedText(since, this.now(), this.locale()) : '';
  });

  protected readonly minorUnits = computed(() => this.request()?.currencyMinorUnits ?? 2);

  protected readonly enteredOnBehalf = computed(() => {
    const request = this.request();
    return !!request && request.enteredBy?.id !== request.requester?.id;
  });

  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  constructor() {
    const ticker = setInterval(() => this.now.set(Date.now()), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(ticker));
    this.load();
  }

  protected load(): void {
    if (!Number.isInteger(this.id)) {
      this.pageState.set('not-found');
      return;
    }
    this.pageState.set('loading');
    this.api.get(this.id).subscribe({
      next: (request) => {
        this.request.set(request);
        this.pageState.set('ready');
      },
      error: (error: unknown) => {
        const problem = toApiProblem(error);
        if (problem.status === 404) {
          this.pageState.set('not-found');
        } else {
          this.loadError.set(problem.code);
          this.pageState.set('error');
        }
      },
    });
  }

  protected submit(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    openConfirm(this.dialog, {
      titleKey: 'requests.submit.title',
      messageKey: 'requests.submit.message',
      confirmKey: 'requests.submit.confirm',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.submitting.set(true);
      this.api.submit(request.id, request.version).subscribe({
        next: (submitted) => {
          this.submitting.set(false);
          this.request.set(submitted);
          this.notifications.success('requests.submit.done', { number: submitted.number ?? '' });
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 401) {
            return;
          }
          if (problem.status === 422) {
            // Required fields are missing: the form shows them field by field.
            this.notifications.error('requests.submit.incomplete');
            void this.router.navigate(['/requests', request.id, 'edit']);
            return;
          }
          if (problem.status === 409) {
            this.notifications.error(`requests.conflict.${problem.code === 'invalid-state' ? 'invalid-state' : 'stale-version'}.title`);
            this.load();
            return;
          }
          this.notifications.error(`errors.${problem.code}`);
        },
      });
    });
  }

  protected tone(status: string) {
    return statusTone(status);
  }

  protected statusLabel(status: string): string {
    return statusKey(status);
  }

  protected money(amount: string | number | null | undefined): string {
    const request = this.request();
    return request ? formatMoney(amount ?? null, request.currency, this.minorUnits(), this.locale()) || '—' : '—';
  }

  protected label(option: Parameters<typeof optionLabel>[0]): string {
    return optionLabel(option, this.language.current());
  }

  protected locale(): string {
    return this.language.current() === 'fr' ? 'fr-FR' : 'en-GB';
  }

  protected translateStatus(status: string | null | undefined): string {
    return status ? this.translate.instant(statusKey(status)) : '';
  }
}
