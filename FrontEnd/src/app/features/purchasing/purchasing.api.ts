import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import {
  Assignment,
  BuyerOption,
  PurchasingAction,
  QueueFilterOptions,
  QueueItem,
  QueuePage,
  QueueQuery,
} from './purchasing.models';

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
    const set = (key: string, value: string | number | boolean | null | undefined) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    };
    set('q', query.q);
    set('status', query.status);
    set('priority', query.priority);
    set('purchaseMode', query.purchaseMode);
    set('buyingCompanyId', query.buyingCompanyId);
    set('categoryId', query.categoryId);
    set('countryCode', query.countryCode);
    set('buyerUserId', query.buyerUserId);
    set('requesterUserId', query.requesterUserId);
    set('requiredFrom', query.requiredFrom);
    set('requiredTo', query.requiredTo);
    set('amountMin', query.amountMin);
    set('amountMax', query.amountMax);
    if (query.complete !== null && query.complete !== undefined) {
      params = params.set('complete', String(query.complete));
    }
    return this.http.get<QueuePage>(`${API}/queue`, { params });
  }

  filterOptions(): Observable<QueueFilterOptions> {
    return this.http.get<QueueFilterOptions>(`${API}/queue/filter-options`);
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

  askQuestion(requestId: number, body: PurchasingAction, key: string): Observable<QueueItem> {
    return this.http.post<QueueItem>(`${API}/requests/${requestId}/question`, body, idempotencyHeaders(key));
  }
}
