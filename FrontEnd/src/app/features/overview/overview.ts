import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Panel } from '../../shared/ui/panel';
import { StatusBadge } from '../../shared/ui/status-badge';
import { JOURNEY_STAGES, findNavItem } from '../../layout/navigation';

/**
 * Honest landing page for the foundation release: what the platform is for, what works today and what
 * comes next. No indicators are shown until real data exists.
 */
@Component({
  selector: 'app-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, PageHeader, Panel, RouterLink, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'overview.title' | translate" [description]="'overview.description' | translate" />

    <div class="grid">
      <app-panel [heading]="'overview.available.title' | translate">
        <ul class="checklist">
          @for (item of available; track item) {
            <li>
              <app-icon name="check" [size]="16" class="check" />
              <span>{{ 'overview.available.items.' + item | translate }}</span>
            </li>
          }
        </ul>
        <div class="ctas">
          <a appButton variant="primary" routerLink="/requests">
            <app-icon name="file-plus" [size]="16" />
            {{ 'overview.available.openRequests' | translate }}
          </a>
          <a appButton variant="secondary" routerLink="/system-status">
            <app-icon name="activity" [size]="16" />
            {{ 'overview.available.checkConnection' | translate }}
          </a>
        </div>
      </app-panel>

      <app-panel [heading]="'overview.journey.title' | translate">
        <p class="lead">{{ 'overview.journey.description' | translate }}</p>
        <ol class="journey">
          @for (stage of journey; track stage.key) {
            <li class="stage">
              <span class="stage-name">{{ 'overview.journey.stages.' + stage.key | translate }}</span>
              @if (stage.sprint) {
                <app-status-badge tone="neutral">
                  {{ 'nav.plannedSprint' | translate: { sprint: stage.sprint } }}
                </app-status-badge>
              } @else {
                <app-status-badge tone="success">{{ 'overview.journey.availableNow' | translate }}</app-status-badge>
              }
            </li>
          }
        </ol>
      </app-panel>
    </div>

    <p class="demo-note">
      <app-icon name="triangle-alert" [size]="16" />
      <span>{{ 'overview.demoNote' | translate }}</span>
    </p>
  `,
  styles: `
    :host {
      display: block;
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 22rem), 1fr));
      gap: var(--space-6);
      align-items: start;
    }

    .checklist {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .checklist li {
      display: flex;
      gap: var(--space-3);
      align-items: flex-start;
    }

    .check {
      margin-block-start: 0.1875rem;
      color: var(--color-success);
    }

    .ctas {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin-block-start: var(--space-6);
    }

    .lead {
      margin-block-end: var(--space-4);
      color: var(--color-text-muted);
    }

    .journey {
      margin: 0;
      padding: 0;
      list-style: none;
      counter-reset: stage;
    }

    .stage {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding-block: var(--space-3);
      border-block-start: 1px solid var(--color-line);
      counter-increment: stage;
    }

    .stage:first-child {
      border-block-start: 0;
      padding-block-start: 0;
    }

    .stage-name::before {
      content: counter(stage) '.';
      display: inline-block;
      min-inline-size: 1.5rem;
      color: var(--color-text-muted);
      font-variant-numeric: tabular-nums;
    }

    .demo-note {
      display: flex;
      gap: var(--space-2);
      align-items: flex-start;
      margin-block-start: var(--space-6);
      max-inline-size: 75ch;
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
  `,
})
export class OverviewPage {
  protected readonly available = ['signIn', 'requests', 'isolation', 'shell', 'languages', 'statusCheck'] as const;
  /** Stage sprints come from the navigation, the single source of the roadmap shown in the interface. */
  protected readonly journey = JOURNEY_STAGES.map((stage) => ({
    key: stage.key,
    sprint: findNavItem(stage.navItem)?.plannedSprint,
  }));
}
