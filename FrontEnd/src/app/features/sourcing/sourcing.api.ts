import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { ComparisonView, CreateRfqBody, PublicSummary, QuoteBody, RfqDetail } from './sourcing.models';

const API = '/api/v1/sourcing';
const PUBLIC = '/api/v1/quote-links';

@Injectable({ providedIn: 'root' })
export class SourcingApi {
  private readonly http = inject(HttpClient);

  getRfq(requestId: number): Observable<RfqDetail | null> {
    return this.http.get<RfqDetail | null>(`${API}/requests/${requestId}/rfq`);
  }

  createRfq(requestId: number, body: CreateRfqBody, key: string): Observable<RfqDetail> {
    return this.http.post<RfqDetail>(`${API}/requests/${requestId}/rfq`, body, idempotencyHeaders(key));
  }

  issueLink(rfqId: number, rfqSupplierId: number, key: string): Observable<{ token: string; path: string }> {
    return this.http.post<{ token: string; path: string }>(
      `${API}/rfqs/${rfqId}/suppliers/${rfqSupplierId}/link`,
      {},
      idempotencyHeaders(key),
    );
  }

  submitQuote(rfqId: number, body: QuoteBody, key: string): Observable<unknown> {
    return this.http.post(`${API}/rfqs/${rfqId}/quotes`, body, idempotencyHeaders(key));
  }

  comparison(rfqId: number): Observable<ComparisonView> {
    return this.http.get<ComparisonView>(`${API}/rfqs/${rfqId}/comparison`);
  }

  validateTechnical(requestId: number, requestVersion: number, comment: string, key: string): Observable<unknown> {
    return this.http.post(
      `${API}/requests/${requestId}/validate-technical`,
      { requestVersion, comment },
      idempotencyHeaders(key),
    );
  }

  validateCommercial(requestId: number, requestVersion: number, comment: string, key: string): Observable<unknown> {
    return this.http.post(
      `${API}/requests/${requestId}/validate-commercial`,
      { requestVersion, comment },
      idempotencyHeaders(key),
    );
  }

  publicSummary(token: string): Observable<PublicSummary> {
    return this.http.post<PublicSummary>(`${PUBLIC}/summary`, { token });
  }

  publicSubmit(body: Record<string, unknown>): Observable<PublicSummary> {
    return this.http.post<PublicSummary>(`${PUBLIC}/submit`, body);
  }
}
