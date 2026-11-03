import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { ShipmentView } from './logistics.models';

const API = '/api/v1/logistics';

export interface LogisticsInboxItem {
  requestId: number;
  requestNumber: string | null;
  requestTitle: string;
  requestStatus: string;
  poId: number;
  poNumber: string;
  poStatus: string;
  shipmentId?: number | null;
  shipmentNumber?: string | null;
  shipmentStatus?: string | null;
  shipmentKind?: string | null;
  poCreatedAt?: string | null;
  readyForReceipt: boolean;
}

export interface LogisticsInboxPage {
  items: LogisticsInboxItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class LogisticsApi {
  private readonly http = inject(HttpClient);

  inbox(q?: string, page = 0, size = 20): Observable<LogisticsInboxPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<LogisticsInboxPage>(`${API}/inbox`, { params });
  }

  list(requestId: number): Observable<ShipmentView[]> {
    return this.http.get<ShipmentView[]>(`${API}/requests/${requestId}/shipments`);
  }

  create(
    requestId: number,
    body: {
      requestVersion: number;
      kind?: string;
      carrierName?: string;
      milestonePlans?: { code: string; plannedDate: string }[];
    },
    key: string,
  ): Observable<ShipmentView> {
    return this.http.post<ShipmentView>(`${API}/requests/${requestId}/shipments`, body, idempotencyHeaders(key));
  }

  updateMilestone(
    shipmentId: number,
    milestoneId: number,
    body: { version: number; plannedDate?: string | null; actualDate?: string | null; note?: string | null },
  ): Observable<ShipmentView> {
    return this.http.put<ShipmentView>(`${API}/shipments/${shipmentId}/milestones/${milestoneId}`, body);
  }

  addDocument(
    shipmentId: number,
    body: { kind: string; referenceNumber?: string | null; note?: string | null },
  ): Observable<unknown> {
    return this.http.post(`${API}/shipments/${shipmentId}/documents`, body);
  }
}
