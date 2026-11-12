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
import { BrandMark } from '../../shared/ui/brand-mark';
import { Icon } from '../../shared/ui/icon';
import { LanguageSelect } from '../../shared/ui/language-select';
import { StatusBadge } from '../../shared/ui/status-badge';
import { ThemeToggle } from '../../shared/ui/theme-toggle';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ApprovalCountService } from '../../features/approvals/approval-count.service';
import { PurchasingCountService } from '../../features/purchasing/purchasing-count.service';
import { InAppNotifications } from '../../features/notifications/in-app-notifications';
import { NAVIGATION, NavGroup, NavItem } from '../navigation';
import { NotificationBell } from '../notification-bell';
import { UserMenu } from '../user-menu';

interface ShellNavGroup extends NavGroup {
  items: (NavItem & { sharesRoute: boolean })[];
}

const COLLAPSED_KEY = 'epi.sidebar.collapsed';
/** Below this width the sidebar becomes an off-canvas drawer. */
export const DRAWER_QUERY = '(max-width: 63.99rem)';

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BrandMark,
    CdkTrapFocus,
    Icon,
    LanguageSelect,
    NotificationBell,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    StatusBadge,
    ThemeToggle,
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
  protected readonly navigation = computed<ShellNavGroup[]>(() => {
    const me = this.auth.me();
    const seen = new Set<string>();
    return NAVIGATION.map((group) => ({
      ...group,
      items: group.items
        .filter((item) => !item.permission || hasPermission(me, item.permission))
        .map((item) => {
          // Several journey entries open the same page; only the first is marked as the current location.
          const sharesRoute = !!item.route && seen.has(item.route);
          if (item.route) {
            seen.add(item.route);
          }
          return { ...item, sharesRoute };
        }),
    })).filter((group) => group.items.length > 0);
  });
  protected readonly isDevMode = isDevMode();

  private readonly router = inject(Router);
  private readonly url = signal(this.router.url);
  /**
   * Where the person is, as "journey group, page": the navigation entry whose route is the longest prefix of the
   * current address. Entries that share another entry's route are skipped, as they are for the active marker.
   */
  protected readonly location = computed(() => {
    const path = this.url().split(/[?#]/)[0];
    let best: { group: string; item: string; length: number } | null = null;
    for (const group of this.navigation()) {
      for (const item of group.items) {
        const route = item.route;
        if (!route || item.sharesRoute || !(path === route || path.startsWith(route + '/'))) {
          continue;
        }
        if (!best || route.length > best.length) {
          best = { group: group.key, item: item.key, length: route.length };
        }
      }
    }
    return best;
  });

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
      .subscribe(() => {
        this.url.set(this.router.url);
        this.refreshCounts();
      });
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
