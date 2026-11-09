import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { AwardView, PurchaseOrderView } from './ordering.models';

const API = '/api/v1/ordering';

@Injectable({ providedIn: 'root' })
export class OrderingApi {
  private readonly http = inject(HttpClient);

  getAward(requestId: number): Observable<AwardView> {
    return this.http.get<AwardView>(`${API}/requests/${requestId}/award`);
  }

  award(
    requestId: number,
    body: { requestVersion: number; quotationId: number; reason: string; quantityReason?: string },
    key: string,
  ): Observable<AwardView> {
    return this.http.post<AwardView>(`${API}/requests/${requestId}/award`, body, idempotencyHeaders(key));
  }

  getPo(requestId: number): Observable<PurchaseOrderView> {
    return this.http.get<PurchaseOrderView>(`${API}/requests/${requestId}/purchase-order`);
  }

  createPo(
    requestId: number,
    body: {
      requestVersion: number;
      paymentTerms?: string | null;
      incoterm?: string | null;
      requiredDate?: string | null;
      deliveryAddress?: string | null;
    },
    key: string,
  ): Observable<PurchaseOrderView> {
    return this.http.post<PurchaseOrderView>(`${API}/requests/${requestId}/purchase-order`, body, idempotencyHeaders(key));
  }

  issue(poId: number, version: number, key: string): Observable<PurchaseOrderView> {
    return this.http.post<PurchaseOrderView>(`${API}/purchase-orders/${poId}/issue`, { version }, idempotencyHeaders(key));
  }

  confirm(poId: number, version: number, note: string, key: string): Observable<PurchaseOrderView> {
    return this.http.post<PurchaseOrderView>(
      `${API}/purchase-orders/${poId}/confirm`,
      { version, confirmationNote: note },
      idempotencyHeaders(key),
    );
  }
}
