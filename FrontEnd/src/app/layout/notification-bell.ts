import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageService } from '../core/i18n/language.service';
import { InAppNotification } from '../features/approvals/approval.models';
import { InAppNotifications } from '../features/notifications/in-app-notifications';
import { Icon } from '../shared/ui/icon';

type ListState = 'loading' | 'ready' | 'error';

/**
 * Bell with the unread count and the latest in-app notifications (S2-AC-10). Texts are composed from the
 * notification type so they follow the current display language. Choosing one marks it read and opens the request.
 */
@Component({
  selector: 'app-notification-bell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, DatePipe, Icon, TranslatePipe],
  template: `
    <button
      type="button"
      class="bell"
      [cdkMenuTriggerFor]="panel"
      (cdkMenuOpened)="load()"
      [attr.aria-label]="'notifications.open' | translate: { count: shownCount() }"
      [attr.title]="'notifications.exactCount' | translate: { count: service.unread() }"
    >
      <app-icon name="bell" [size]="20" />
      @if (service.unread() > 0) {
        <span class="count tabular" aria-hidden="true">{{ shownCount() }}</span>
      }
    </button>

    <ng-template #panel>
      <div class="menu" cdkMenu>
        <div class="menu-head">
          <p class="strong">{{ 'notifications.title' | translate }}</p>
          @if (service.unread() > 0) {
            <button type="button" class="link" cdkMenuItem (cdkMenuItemTriggered)="markAll()">
              <app-icon name="check-check" [size]="16" />
              {{ 'notifications.markAllRead' | translate }}
            </button>
          }
        </div>
        @switch (state()) {
          @case ('loading') {
            <p class="note" role="status">{{ 'notifications.loading' | translate }}</p>
          }
          @case ('error') {
            <p class="note" role="alert">{{ 'notifications.loadError' | translate }}</p>
          }
          @default {
            @if (items().length === 0) {
              <p class="note">{{ 'notifications.empty' | translate }}</p>
            }
            @for (item of items(); track item.id) {
              <button type="button" class="item" [class.is-unread]="!item.readAt" cdkMenuItem (cdkMenuItemTriggered)="open(item)">
                <span class="dot" aria-hidden="true"></span>
                <span class="item-text">
                  <span class="item-title">{{ item.title }}</span>
                  @if (item.body) {
                    <span class="item-body">{{ item.body }}</span>
                  }
                  <time class="item-time" [attr.datetime]="item.createdAt">{{ item.createdAt | date: 'short' : undefined : language.locale() }}</time>
                </span>
                @if (!item.readAt) {
                  <span class="visually-hidden">{{ 'notifications.unread' | translate }}</span>
                }
              </button>
            }
          }
        }
      </div>
    </ng-template>
  `,
  styles: `
    :host {
      display: inline-block;
    }

    .bell {
      position: relative;
      display: inline-grid;
      place-items: center;
      inline-size: 2.5rem;
      block-size: 2.5rem;
      border: 0;
      border-radius: var(--radius-md);
      background: transparent;
      color: var(--color-text-muted);
      cursor: pointer;
    }

    .bell:hover {
      color: var(--color-text);
      background: var(--color-surface-sunken);
    }

    .count {
      position: absolute;
      inset-block-start: 0.125rem;
      inset-inline-end: 0.125rem;
      min-inline-size: 1.125rem;
      padding-inline: 0.25rem;
      border-radius: var(--radius-pill);
      background: var(--color-danger);
      color: var(--color-on-danger);
      font-size: 0.6875rem;
      font-weight: var(--weight-semibold);
      line-height: 1.125rem;
      text-align: center;
    }

    .menu {
      inline-size: min(24rem, calc(100vw - 1rem));
      max-block-size: min(32rem, 80vh);
      overflow-y: auto;
      padding: var(--space-2);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-lg);
      background: var(--glass-surface-strong);
      box-shadow: var(--shadow-overlay);
      -webkit-backdrop-filter: var(--glass-blur);
      backdrop-filter: var(--glass-blur);
      transform-origin: top;
      animation: overlay-in var(--duration-base) var(--ease-out) both;
    }

    .menu-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-3);
    }

    .link {
      display: inline-flex;
      align-items: center;
      gap: var(--space-1);
      border: 0;
      background: transparent;
      color: var(--color-accent);
      font-size: var(--text-sm);
      cursor: pointer;
    }

    .note {
      padding: var(--space-4) var(--space-3);
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }

    .item {
      display: flex;
      gap: var(--space-3);
      align-items: flex-start;
      inline-size: 100%;
      padding: var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      text-align: start;
      cursor: pointer;
    }

    .item:hover,
    .item:focus {
      background: var(--color-surface-sunken);
      outline: none;
    }

    .item:focus-visible {
      outline: 2px solid var(--color-focus);
    }

    .dot {
      flex: none;
      inline-size: 0.5rem;
      block-size: 0.5rem;
      margin-block-start: 0.4rem;
      border-radius: 50%;
      background: transparent;
    }

    .is-unread .dot {
      background: var(--color-accent);
    }

    .is-unread .item-title {
      font-weight: var(--weight-semibold);
    }

    .item-body {
      color: var(--color-text-muted);
    }

    .item-text {
      display: grid;
      gap: var(--space-1);
      font-size: var(--text-sm);
    }

    .item-time {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
    }
  `,
})
export class NotificationBell {
  protected readonly service = inject(InAppNotifications);
  protected readonly language = inject(LanguageService);
  private readonly router = inject(Router);

  /** The count shown on the badge; the accessible name repeats it exactly (WCAG 2.5.3, QA2-D-01). */
  protected readonly shownCount = computed(() => (this.service.unread() > 99 ? '99+' : String(this.service.unread())));

  protected readonly state = signal<ListState>('loading');
  protected readonly items = signal<InAppNotification[]>([]);

  load(): void {
    this.state.set('loading');
    this.service.latest().subscribe({
      next: (page) => {
        this.items.set(page.items);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected open(item: InAppNotification): void {
    if (!item.readAt) {
      this.service.markRead(item.id).subscribe({ error: () => undefined });
    }
    // Only links inside this application are followed (the server sends paths such as /requests/7).
    if (item.link && item.link.startsWith('/') && !item.link.startsWith('//')) {
      void this.router.navigateByUrl(item.link);
    } else if (item.requestId) {
      void this.router.navigate(['/requests', item.requestId]);
    }
  }

  protected markAll(): void {
    this.service.markAllRead().subscribe({ error: () => undefined });
  }
}
