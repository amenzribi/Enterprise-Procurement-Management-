import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { GoodsReceipt, ReceiptDossier, SupplierInvoice } from './receipt.models';

const API = '/api/v1/receipts';

@Injectable({ providedIn: 'root' })
export class ReceiptApi {
  private readonly http = inject(HttpClient);

  dossier(requestId: number): Observable<ReceiptDossier> {
    return this.http.get<ReceiptDossier>(`${API}/requests/${requestId}`);
  }

  createReceipt(requestId: number, body: unknown, key: string): Observable<GoodsReceipt> {
    return this.http.post<GoodsReceipt>(`${API}/requests/${requestId}/goods-receipts`, body, idempotencyHeaders(key));
  }

  confirm(receiptId: number, body: { version: number }): Observable<GoodsReceipt> {
    return this.http.post<GoodsReceipt>(`${API}/goods-receipts/${receiptId}/confirm`, body);
  }

  invoice(requestId: number, body: unknown, key: string): Observable<SupplierInvoice> {
    return this.http.post<SupplierInvoice>(`${API}/requests/${requestId}/invoice`, body, idempotencyHeaders(key));
  }

  acceptShortfall(requestId: number, body: { requestVersion: number; reason: string }): Observable<ReceiptDossier> {
    return this.http.post<ReceiptDossier>(`${API}/requests/${requestId}/shortfall-acceptance`, body);
  }

  attestMatch(requestId: number, body: { requestVersion: number }): Observable<ReceiptDossier> {
    return this.http.post<ReceiptDossier>(`${API}/requests/${requestId}/invoice/match-attested`, body);
  }

  markPaid(requestId: number, body: { requestVersion: number }): Observable<ReceiptDossier> {
    return this.http.post<ReceiptDossier>(`${API}/requests/${requestId}/invoice/paid`, body);
  }

  close(requestId: number, body: unknown, key: string): Observable<ReceiptDossier> {
    return this.http.post<ReceiptDossier>(`${API}/requests/${requestId}/close`, body, idempotencyHeaders(key));
  }
}
