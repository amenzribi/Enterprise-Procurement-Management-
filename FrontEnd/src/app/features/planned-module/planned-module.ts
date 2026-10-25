import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { findPlannedItem } from '../../layout/navigation';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';

/**
 * Placeholder for a module that is not built yet. It states that plainly instead of showing a mock screen.
 * The route guarantees `module` is a known planned module.
 */
@Component({
  selector: 'app-planned-module',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, EmptyState, PageHeader, RouterLink, TranslatePipe],
  template: `
    @if (item(); as item) {
      <app-page-header
        [heading]="'nav.items.' + item.key | translate"
        [description]="'planned.descriptions.' + item.key | translate"
      />
      <app-empty-state
        [icon]="item.icon"
        [heading]="'planned.title' | translate: { sprint: item.plannedSprint }"
        [message]="'planned.message' | translate"
      >
        <a appButton variant="secondary" routerLink="/overview">{{ 'planned.backToOverview' | translate }}</a>
      </app-empty-state>
    }
  `,
})
export class PlannedModulePage {
  /** Bound from the `:module` route parameter. */
  readonly module = input.required<string>();
  protected readonly item = computed(() => findPlannedItem(this.module()));
}
