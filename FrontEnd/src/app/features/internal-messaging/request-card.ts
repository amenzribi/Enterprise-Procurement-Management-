import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Icon } from '../../shared/ui/icon';
import { StatusBadge } from '../../shared/ui/status-badge';
import { isUserStatus, userStatusTone } from '../requests/request-status';
import { RequestCard } from './internal-messaging.models';

/**
 * A request attached to a message or heading a discussion. It shows only what the server returned for this viewer;
 * when the viewer cannot see the request, nothing about it is shown. Opening it goes through the request page, which
 * checks access again.
 */
@Component({
  selector: 'app-request-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, RouterLink, StatusBadge, TranslatePipe],
  template: `
    @if (card().available && card().id !== null) {
      <a class="card" [routerLink]="['/requests', card().id]">
        <span class="icon" aria-hidden="true"><app-icon name="file-text" [size]="18" /></span>
        <span class="text">
          <bdi class="number tabular">{{ card().number || ('requests.list.draftNumber' | translate) }}</bdi>
          <span class="title" dir="auto">{{ card().title }}</span>
        </span>
        @if (card().userStatus; as status) {
          <app-status-badge [tone]="tone(status)">{{ 'requests.userStatus.' + status | translate }}</app-status-badge>
        }
        <span class="visually-hidden">{{ 'internalMessaging.card.open' | translate }}</span>
      </a>
    } @else {
      <p class="card is-unavailable">
        <span class="icon" aria-hidden="true"><app-icon name="lock" [size]="18" /></span>
        <span class="text">{{ 'internalMessaging.card.unavailable' | translate }}</span>
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
      min-inline-size: 0;
    }
    .card {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      margin: 0;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-md);
      background: var(--color-surface);
      color: var(--color-text);
      text-decoration: none;
      transition: border-color var(--duration-fast) var(--ease-standard);
    }
    a.card:hover {
      border-color: var(--color-accent);
      color: var(--color-text);
    }
    .icon {
      display: grid;
      place-items: center;
      flex-shrink: 0;
      inline-size: 2rem;
      block-size: 2rem;
      border-radius: var(--radius-sm);
      background: var(--color-accent-soft);
      color: var(--color-accent);
    }
    .text {
      display: grid;
      flex: 1;
      min-inline-size: 0;
    }
    .number {
      color: var(--color-accent);
      font-size: var(--text-xs);
      font-weight: var(--weight-semibold);
    }
    .title {
      overflow: hidden;
      font-weight: var(--weight-semibold);
      text-align: match-parent;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .is-unavailable {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
    .is-unavailable .icon {
      background: var(--color-surface-sunken);
      color: var(--color-text-muted);
    }
  `,
})
export class RequestCardView {
  readonly card = input.required<RequestCard>();

  protected tone(status: string) {
    return userStatusTone(isUserStatus(status) ? status : null);
  }
}
