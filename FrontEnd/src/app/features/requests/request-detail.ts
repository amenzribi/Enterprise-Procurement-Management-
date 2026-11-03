import { Dialog } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { elapsedText } from '../../shared/relative-time';
import { ApprovalActions } from '../approvals/approval-actions';
import { PurchasingActions } from '../purchasing/purchasing-actions';
import { OrderingActions } from '../ordering/ordering-actions';
import { LogisticsActions } from '../logistics/logistics-actions';
import { ReceiptActions } from '../receipt/receipt-actions';
import { SourcingActions } from '../sourcing/sourcing-actions';
import { ApprovalCountService } from '../approvals/approval-count.service';
import { ApprovalTracker } from '../approvals/approval-tracker';
import { ApprovalView } from '../approvals/approval.models';
import { ApprovalsApi } from '../approvals/approvals.api';
import { openSubmitPreview } from '../approvals/submit-preview-dialog';
import { DashboardsApi } from '../dashboards/dashboards.api';
import { Attachments } from '../documents/attachments';
import { formatMoney } from './money';
import { PurchaseRequest, RequestAction, optionLabel } from './request.models';
import { stageOf, statusKey, userStatusKey, userStatusOf, userStatusTone } from './request-status';
import { RequestsApi } from './requests.api';

type PageState = 'loading' | 'ready' | 'not-found' | 'error';

export { elapsedText } from '../../shared/relative-time';

/**
 * One request as a dossier: number, status, current stage, who owns it and since when (S1-AC-11), actors, lines and
 * the transition history.
 */
@Component({
  selector: 'app-request-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ApprovalActions, ApprovalTracker, Attachments, Button, DatePipe, EmptyState, ErrorState, Icon, LogisticsActions, OrderingActions, PurchasingActions, ReceiptActions, RouterLink, Skeleton, SourcingActions, StatusBadge, TranslatePipe],
  templateUrl: './request-detail.html',
  styleUrl: './request-detail.scss',
})
export class RequestDetailPage {
  private readonly api = inject(RequestsApi);
  private readonly approvalsApi = inject(ApprovalsApi);
  private readonly dashboardsApi = inject(DashboardsApi);
  private readonly approvalCount = inject(ApprovalCountService);
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
  protected readonly auditPackBusy = signal(false);
  /** Approval rounds, stages and tasks for the tracker; null while loading or for drafts never submitted. */
  protected readonly approval = signal<ApprovalView | null>(null);
  /** Ticks every minute so "owner since" stays current without reloading. */
  protected readonly now = signal(Date.now());
  protected readonly copiedRef = signal(false);

  /** The user-facing status (Solution Guide v3.0 §8). */
  protected readonly userStatus = computed(() => {
    const request = this.request();
    return request ? userStatusOf(request) : null;
  });

  /** Exactly the actions the server allows (S1DR-18); the UI never re-derives permissions. */
  protected readonly actions = computed<RequestAction[]>(() => this.request()?.actions ?? []);

  /** Plain-language next step from server actions / approval data — never invents actors. */
  protected readonly nextActionSummary = computed(() => {
    const request = this.request();
    if (!request) {
      return null;
    }
    const actions = request.actions ?? [];
    if (actions.includes('SUBMIT')) {
      return this.translate.instant('requests.detail.next.submit');
    }
    if (actions.includes('EDIT')) {
      return this.translate.instant('requests.detail.next.edit');
    }
    const approval = this.approval();
    if (approval?.myTask) {
      return this.translate.instant('requests.detail.next.decide');
    }
    if (approval?.openQuestions?.length) {
      return this.translate.instant('requests.detail.next.answer');
    }
    if (actions.length > 0) {
      return this.translate.instant('requests.detail.next.available');
    }
    const owner = request.currentOwner?.displayName ?? request.requester?.displayName;
    if (owner) {
      return this.translate.instant('requests.detail.next.ownerElsewhere', { owner });
    }
    return null;
  });

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
    if (!this.request()) {
      this.pageState.set('loading');
    }
    this.api.get(this.id).subscribe({
      next: (request) => {
        this.request.set(request);
        this.pageState.set('ready');
        this.loadApproval(request);
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

  /** Reloads after an approval action: the request status, owner and tracker all change together. */
  protected onApprovalChanged(view: ApprovalView | null): void {
    if (view) {
      this.approval.set(view);
    }
    this.approvalCount.refresh();
    this.load();
  }

  protected submit(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    openSubmitPreview(this.dialog, request.id).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.submitting.set(true);
      this.api.submit(request.id, request.version).subscribe({
        next: (submitted) => {
          this.submitting.set(false);
          this.request.set(submitted);
          this.loadApproval(submitted);
          this.notifications.success('requests.submit.done', { number: submitted.number ?? '' });
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 401) {
            return;
          }
          if (problem.status === 422 && problem.errors.length > 0) {
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
          // Approval configuration problems (missing-exchange-rate, no-approval-rule, no-eligible-approver) and others.
          this.notifications.error(`errors.${problem.code}`);
        },
      });
    });
  }

  private loadApproval(request: PurchaseRequest): void {
    if (request.status === 'DRAFT' && !request.number) {
      this.approval.set(null);
      return;
    }
    this.approvalsApi.approval(request.id).subscribe({
      next: (view) => this.approval.set(view),
      // The tracker still shows the step line without approval details.
      error: () => this.approval.set(null),
    });
  }

  protected tone(): ReturnType<typeof userStatusTone> {
    return userStatusTone(this.userStatus());
  }

  protected statusLabel(): string {
    const status = this.userStatus();
    return status ? userStatusKey(status) : statusKey(this.request()?.status ?? '');
  }

  protected money(amount: string | number | null | undefined): string {
    const request = this.request();
    return request ? formatMoney(amount ?? null, request.currency, this.minorUnits(), this.locale()) || '—' : '—';
  }

  protected label(option: Parameters<typeof optionLabel>[0]): string {
    return optionLabel(option, this.language.current());
  }

  protected locale(): string {
    return this.language.locale();
  }

  protected translateStatus(status: string | null | undefined): string {
    return status ? this.translate.instant(statusKey(status)) : '';
  }

  protected copyReference(number: string): void {
    void navigator.clipboard.writeText(number).then(
      () => {
        this.copiedRef.set(true);
        this.notifications.success('requests.detail.referenceCopied');
        setTimeout(() => this.copiedRef.set(false), 2000);
      },
      () => this.notifications.error('requests.detail.copyFailed'),
    );
  }

  protected downloadAuditPack(): void {
    const request = this.request();
    if (!request || this.auditPackBusy()) {
      return;
    }
    this.auditPackBusy.set(true);
    this.dashboardsApi.auditPack(request.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `audit-pack-${request.number ?? request.id}.pdf`;
        anchor.rel = 'noopener';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
        this.auditPackBusy.set(false);
        this.notifications.success('dashboards.auditPackDownloaded');
      },
      error: (error: unknown) => {
        this.auditPackBusy.set(false);
        this.notifications.error(`errors.${toApiProblem(error).code}`);
      },
    });
  }
}
