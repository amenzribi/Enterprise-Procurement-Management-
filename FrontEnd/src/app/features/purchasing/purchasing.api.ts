import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { Assignment, BuyerOption, PurchasingAction, QueueItem, QueuePage, QueueQuery } from './purchasing.models';

const API = '/api/v1/purchasing';

@Injectable({ providedIn: 'root' })
export class PurchasingApi {
  private readonly http = inject(HttpClient);

  queue(query: QueueQuery): Observable<QueuePage> {
    let params = new HttpParams()
      .set('sort', query.sort)
      .set('dir', query.direction)
      .set('page', query.page)
      .set('size', query.size);
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.priority) {
      params = params.set('priority', query.priority);
    }
    if (query.purchaseMode) {
      params = params.set('purchaseMode', query.purchaseMode);
    }
    return this.http.get<QueuePage>(`${API}/queue`, { params });
  }

  count(): Observable<{ pending: number }> {
    return this.http.get<{ pending: number }>(`${API}/queue/count`);
  }

  buyers(companyId: number, categoryId?: number | null): Observable<BuyerOption[]> {
    let params = new HttpParams().set('companyId', companyId);
    if (categoryId) {
      params = params.set('categoryId', categoryId);
    }
    return this.http.get<BuyerOption[]>(`${API}/buyers`, { params });
  }

  assignments(requestId: number): Observable<Assignment[]> {
    return this.http.get<Assignment[]>(`${API}/requests/${requestId}/assignments`);
  }

  assign(requestId: number, body: PurchasingAction, key: string): Observable<QueueItem> {
    return this.http.post<QueueItem>(`${API}/requests/${requestId}/assign`, body, idempotencyHeaders(key));
  }

  accept(requestId: number, body: PurchasingAction, key: string): Observable<QueueItem> {
    return this.http.post<QueueItem>(`${API}/requests/${requestId}/accept`, body, idempotencyHeaders(key));
  }

  returnAssignment(requestId: number, body: PurchasingAction, key: string): Observable<QueueItem> {
    return this.http.post<QueueItem>(`${API}/requests/${requestId}/return-assignment`, body, idempotencyHeaders(key));
  }

  purchaseMode(requestId: number, body: PurchasingAction, key: string): Observable<QueueItem> {
    return this.http.post<QueueItem>(`${API}/requests/${requestId}/purchase-mode`, body, idempotencyHeaders(key));
  }

  completenessReturn(requestId: number, body: PurchasingAction, key: string): Observable<QueueItem> {
    return this.http.post<QueueItem>(`${API}/requests/${requestId}/completeness-return`, body, idempotencyHeaders(key));
  }
}
