import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';

@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, EmptyState, PageHeader, RouterLink, TranslatePipe],
  template: `
    <app-page-header [heading]="'notFound.title' | translate" />
    <app-empty-state icon="circle-x" [heading]="'notFound.heading' | translate" [message]="'notFound.message' | translate">
      <a appButton variant="primary" routerLink="/overview">{{ 'notFound.backToOverview' | translate }}</a>
    </app-empty-state>
  `,
})
export class NotFoundPage {}
