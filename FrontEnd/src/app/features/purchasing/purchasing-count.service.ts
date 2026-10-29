import { Injectable, inject, signal } from '@angular/core';
import { PurchasingApi } from './purchasing.api';

/** Number of requests in the buying company's queue, for the navigation badge. Refreshed on navigation, never polled. */
@Injectable({ providedIn: 'root' })
export class PurchasingCountService {
  private readonly api = inject(PurchasingApi);
  private readonly value = signal(0);
  readonly count = this.value.asReadonly();

  refresh(): void {
    this.api.count().subscribe({
      next: (count) => this.value.set(Math.max(0, count.pending)),
      error: () => undefined,
    });
  }
}
