import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth/auth.service';
import { Icon } from '../shared/ui/icon';

/** Who is signed in, for which company and with which roles, plus sign-out. */
@Component({
  selector: 'app-user-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, Icon, TranslatePipe],
  template: `
    @if (auth.me(); as me) {
      <button type="button" class="trigger" [cdkMenuTriggerFor]="menu">
        <span class="avatar" aria-hidden="true">{{ initials() }}</span>
        <span class="trigger-text">
          <span class="trigger-name">{{ me.displayName }}</span>
          <span class="trigger-company">{{ me.company.name }}</span>
        </span>
        <app-icon name="chevron-down" [size]="16" class="chevron" />
      </button>

      <ng-template #menu>
        <div class="menu" cdkMenu>
          <div class="identity">
            <p class="identity-name">{{ me.displayName }}</p>
            <p class="identity-email">{{ me.email }}</p>
            <dl class="identity-facts">
              <dt>{{ 'auth.company' | translate }}</dt>
              <dd>{{ me.company.name }}</dd>
              @if (me.department) {
                <dt>{{ 'auth.department' | translate }}</dt>
                <dd>{{ me.department.name }}</dd>
              }
              <dt>{{ 'auth.roles.label' | translate }}</dt>
              <dd>
                @for (role of me.roles; track role; let last = $last) {
                  {{ 'auth.roles.' + role | translate }}{{ last ? '' : ', ' }}
                }
              </dd>
            </dl>
          </div>
          <button type="button" class="menu-item" cdkMenuItem (cdkMenuItemTriggered)="signOut()">
            <app-icon name="log-out" [size]="16" />
            {{ 'auth.signOut' | translate }}
          </button>
        </div>
      </ng-template>
    }
  `,
  styles: `
    :host {
      display: inline-block;
    }

    .trigger {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      min-block-size: 2.5rem;
      padding: var(--space-1) var(--space-2) var(--space-1) var(--space-1);
      border: 1px solid transparent;
      border-radius: var(--radius-md);
      background: transparent;
      cursor: pointer;
      text-align: start;
    }

    .trigger:hover,
    .trigger[aria-expanded='true'] {
      background: var(--color-surface-sunken);
    }

    .avatar {
      display: grid;
      place-items: center;
      inline-size: 2rem;
      block-size: 2rem;
      border-radius: 50%;
      background: var(--color-accent-soft);
      color: var(--color-accent-hover);
      font-size: var(--text-xs);
      font-weight: var(--weight-semibold);
    }

    .trigger-text {
      display: flex;
      flex-direction: column;
      line-height: var(--leading-tight);
      max-inline-size: 12rem;
    }

    .trigger-name {
      font-weight: var(--weight-medium);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .trigger-company {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .chevron {
      color: var(--color-text-muted);
    }

    .menu {
      min-inline-size: 16rem;
      max-inline-size: min(22rem, calc(100vw - 2rem));
      margin-block-start: var(--space-1);
      padding: var(--space-2);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-md);
      background: var(--color-surface);
      box-shadow: var(--shadow-overlay);
    }

    .identity {
      padding: var(--space-2) var(--space-3) var(--space-3);
      border-block-end: 1px solid var(--color-line);
      margin-block-end: var(--space-2);
    }

    .identity-name {
      font-weight: var(--weight-semibold);
    }

    .identity-email {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
      overflow-wrap: anywhere;
    }

    .identity-facts {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: var(--space-1) var(--space-3);
      margin: var(--space-3) 0 0;
      font-size: var(--text-sm);
    }

    .identity-facts dt {
      color: var(--color-text-muted);
    }

    .identity-facts dd {
      margin: 0;
    }

    .menu-item {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      inline-size: 100%;
      min-block-size: 2.5rem;
      padding-inline: var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      cursor: pointer;
      text-align: start;
    }

    .menu-item:hover,
    .menu-item:focus {
      background: var(--color-surface-sunken);
    }

    @media (max-width: 40rem) {
      .chevron {
        display: none;
      }

      // Keep the name as the button's accessible name when only the avatar is shown.
      .trigger-text {
        position: absolute;
        inline-size: 1px;
        block-size: 1px;
        overflow: hidden;
        clip-path: inset(50%);
        white-space: nowrap;
      }
    }
  `,
})
export class UserMenu {
  protected readonly auth = inject(AuthService);

  protected readonly initials = computed(() => {
    const name = this.auth.me()?.displayName ?? '';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('');
  });

  protected signOut(): void {
    this.auth.signOut().subscribe();
  }
}
