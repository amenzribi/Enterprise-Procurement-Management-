import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { LanguageService } from '../../core/i18n/language.service';
import { ConnectivityCheck, SystemStatusService } from '../../core/system/system-status.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon, IconName } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Panel } from '../../shared/ui/panel';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge, StatusTone } from '../../shared/ui/status-badge';

type CheckState = 'loading' | 'ready' | 'error';
type HopState = 'pending' | 'up' | 'down' | 'unknown';

interface Hop {
  key: 'browser' | 'server' | 'database';
  icon: IconName;
  state: HopState;
}

const HOP_TONES: Record<HopState, StatusTone> = {
  pending: 'neutral',
  up: 'success',
  down: 'danger',
  unknown: 'neutral',
};

/**
 * Checks that the interface can reach the application server and, through it, the database.
 */
@Component({
  selector: 'app-system-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
    DatePipe,
    DecimalPipe,
    EmptyState,
    ErrorState,
    Icon,
    PageHeader,
    Panel,
    Skeleton,
    StatusBadge,
    TranslatePipe,
  ],
  templateUrl: './system-status.html',
  styleUrl: './system-status.scss',
})
export class SystemStatusPage {
  private readonly service = inject(SystemStatusService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly language = inject(LanguageService).current;

  protected readonly state = signal<CheckState>('loading');
  protected readonly result = signal<ConnectivityCheck | null>(null);
  protected readonly checkedAt = signal<Date | null>(null);
  private request?: Subscription;

  protected readonly hops = computed<Hop[]>(() => {
    const state = this.state();
    const database = this.result()?.status.database.status;
    return [
      { key: 'browser', icon: 'monitor', state: 'up' },
      {
        key: 'server',
        icon: 'server',
        state: state === 'loading' ? 'pending' : state === 'error' ? 'down' : 'up',
      },
      {
        key: 'database',
        icon: 'database',
        state:
          state === 'loading'
            ? 'pending'
            : state === 'error' || database === 'UNKNOWN'
              ? 'unknown'
              : database === 'UP'
                ? 'up'
                : 'down',
      },
    ];
  });

  protected readonly summary = computed(() => {
    const state = this.state();
    if (state === 'loading') return 'systemStatus.summary.checking';
    if (state === 'error') return 'systemStatus.summary.serverUnreachable';
    switch (this.result()?.status.database.status) {
      case 'UP':
        return 'systemStatus.summary.allConnected';
      case 'DOWN':
        return 'systemStatus.summary.databaseDown';
      default:
        return 'systemStatus.summary.databaseNotReported';
    }
  });

  protected readonly healthComponents = computed(() =>
    Object.entries(this.result()?.health?.components ?? {}).map(([name, component]) => ({
      name,
      status: component.status,
    })),
  );

  constructor() {
    this.check();
  }

  protected check(): void {
    this.request?.unsubscribe();
    this.state.set('loading');
    this.request = this.service
      .check()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.state.set('ready');
          this.checkedAt.set(new Date());
        },
        error: () => {
          this.result.set(null);
          this.state.set('error');
          this.checkedAt.set(new Date());
        },
      });
  }

  protected tone(state: HopState): StatusTone {
    return HOP_TONES[state];
  }

  protected healthStateKey(status: string): string {
    return ['UP', 'DOWN', 'OUT_OF_SERVICE'].includes(status)
      ? `systemStatus.health.states.${status}`
      : 'systemStatus.health.states.UNKNOWN';
  }

  protected componentTone(status: string): StatusTone {
    return status === 'UP' ? 'success' : status === 'DOWN' || status === 'OUT_OF_SERVICE' ? 'danger' : 'neutral';
  }
}
