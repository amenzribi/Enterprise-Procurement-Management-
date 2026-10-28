import { Injectable, inject, signal } from '@angular/core';
import { ApprovalsApi } from './approvals.api';

/**
 * Number of requests waiting for the current user's decision, for the navigation badge (S2-AC-08). It is refreshed on
 * navigation and after each decision — never by background polling, which would keep an idle session alive.
 */
@Injectable({ providedIn: 'root' })
export class ApprovalCountService {
  private readonly api = inject(ApprovalsApi);
  private readonly value = signal(0);
  private readonly overdueValue = signal(0);
  readonly count = this.value.asReadonly();
  readonly overdue = this.overdueValue.asReadonly();

  refresh(): void {
    this.api.inboxCount().subscribe({
      next: (count) => {
        this.value.set(Math.max(0, count.pending));
        this.overdueValue.set(Math.max(0, count.overdue));
      },
      // The badge is a convenience: on failure keep the last known value.
      error: () => undefined,
    });
  }
}
