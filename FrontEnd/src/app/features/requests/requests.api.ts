import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { internalStatusesOf } from './request-status';
import { FormOptions, Page, PurchaseRequest, RequestListItem, RequestPayload, RequestQuery } from './request.models';

const BASE = '/api/v1/requests';

@Injectable({ providedIn: 'root' })
export class RequestsApi {
  private readonly http = inject(HttpClient);

  /** `scope=mine`: requests where the user is requester or entered-by; `visible`: all requests they may see. */
  list(query: RequestQuery): Observable<Page<RequestListItem>> {
    let params = new HttpParams()
      .set('scope', query.scope)
      .set('sort', `${query.sort},${query.direction}`)
      .set('page', query.page)
      .set('size', query.size);
    if (query.status) {
      // The filter offers the user-facing statuses of Solution Guide v3.0 §8; the API filters on the internal ones.
      params = params.set('status', internalStatusesOf(query.status).join(','));
    }
    if (query.q) {
      params = params.set('q', query.q);
    }
    return this.http.get<Page<RequestListItem>>(BASE, { params });
  }

  get(id: number): Observable<PurchaseRequest> {
    return this.http.get<PurchaseRequest>(`${BASE}/${id}`);
  }

  formOptions(): Observable<FormOptions> {
    return this.http.get<FormOptions>(`${BASE}/form-options`);
  }

  create(payload: RequestPayload): Observable<PurchaseRequest> {
    return this.http.post<PurchaseRequest>(BASE, payload);
  }

  update(id: number, payload: RequestPayload): Observable<PurchaseRequest> {
    return this.http.put<PurchaseRequest>(`${BASE}/${id}`, payload);
  }

  submit(id: number, version: number): Observable<PurchaseRequest> {
    return this.http.post<PurchaseRequest>(`${BASE}/${id}/submit`, { version });
  }

  discard(id: number, version: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}`, { params: new HttpParams().set('version', version) });
  }
}
