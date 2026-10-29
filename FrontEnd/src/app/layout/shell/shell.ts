import { CdkTrapFocus } from '@angular/cdk/a11y';
import { BreakpointObserver } from '@angular/cdk/layout';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  isDevMode,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { filter, map, skip } from 'rxjs';
import { Icon } from '../../shared/ui/icon';
import { LanguageSelect } from '../../shared/ui/language-select';
import { StatusBadge } from '../../shared/ui/status-badge';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ApprovalCountService } from '../../features/approvals/approval-count.service';
import { PurchasingCountService } from '../../features/purchasing/purchasing-count.service';
import { InAppNotifications } from '../../features/notifications/in-app-notifications';
import { NAVIGATION, NavGroup } from '../navigation';
import { NotificationBell } from '../notification-bell';
import { UserMenu } from '../user-menu';

const COLLAPSED_KEY = 'epi.sidebar.collapsed';
/** Below this width the sidebar becomes an off-canvas drawer. */
export const DRAWER_QUERY = '(max-width: 63.99rem)';

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTrapFocus,
    Icon,
    LanguageSelect,
    NotificationBell,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    StatusBadge,
    TranslatePipe,
    UserMenu,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  host: { '(document:keydown.escape)': 'closeDrawer()' },
})
export class Shell {
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);

  private readonly auth = inject(AuthService);
  protected readonly approvalCount = inject(ApprovalCountService);
  protected readonly purchasingCount = inject(PurchasingCountService);
  private readonly inAppNotifications = inject(InAppNotifications);

  /** Navigation filtered by the user's permissions; empty groups are hidden. */
  protected readonly navigation = computed<NavGroup[]>(() => {
    const me = this.auth.me();
    return NAVIGATION.map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.permission || hasPermission(me, item.permission)),
    })).filter((group) => group.items.length > 0);
  });
  protected readonly isDevMode = isDevMode();

  protected readonly isDrawerMode = toSignal(
    inject(BreakpointObserver)
      .observe(DRAWER_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly drawerOpen = signal(false);
  protected readonly collapsed = signal(this.readCollapsed());

  constructor() {
    const destroyRef = inject(DestroyRef);
    // Counts are refreshed on navigation (including the first one), never by background polling (S1DR-11).
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.refreshCounts());
    this.refreshCounts();

    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        skip(1),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => {
        this.drawerOpen.set(false);
        // After client-side navigation, move focus to the new page title so screen reader users hear it.
        afterNextRender(() => this.document.querySelector<HTMLElement>('main h1')?.focus(), {
          injector: this.injector,
        });
      });

    effect(() => {
      if (!this.isDrawerMode()) {
        this.drawerOpen.set(false);
      }
    });
  }

  private refreshCounts(): void {
    if (this.auth.isAuthenticated()) {
      this.approvalCount.refresh();
      this.purchasingCount.refresh();
      this.inAppNotifications.refreshCount();
    }
  }

  /**
   * With `<base href="/">`, a plain `#main` link would navigate to `/#main` and reload the app (QA-D-03), so the
   * skip link moves focus itself and keeps the current route.
   */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.document.getElementById('main')?.focus();
  }

  protected toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
    if (this.drawerOpen()) {
      afterNextRender(() => this.document.querySelector<HTMLElement>('.drawer-close')?.focus(), {
        injector: this.injector,
      });
    }
  }

  protected closeDrawer(): void {
    if (!this.drawerOpen()) {
      return;
    }
    this.drawerOpen.set(false);
    // Return focus to the control that opened the drawer.
    afterNextRender(() => this.document.querySelector<HTMLElement>('.menu-button')?.focus(), {
      injector: this.injector,
    });
  }

  protected toggleCollapsed(): void {
    this.collapsed.update((value) => !value);
    try {
      this.document.defaultView?.localStorage.setItem(COLLAPSED_KEY, String(this.collapsed()));
    } catch {
      // Preference is kept for this session only.
    }
  }

  private readCollapsed(): boolean {
    try {
      return this.document.defaultView?.localStorage.getItem(COLLAPSED_KEY) === 'true';
    } catch {
      return false;
    }
  }
}
