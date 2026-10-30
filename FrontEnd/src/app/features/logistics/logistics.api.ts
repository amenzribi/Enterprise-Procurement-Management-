import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { ShipmentView } from './logistics.models';

const API = '/api/v1/logistics';

@Injectable({ providedIn: 'root' })
export class LogisticsApi {
  private readonly http = inject(HttpClient);

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
