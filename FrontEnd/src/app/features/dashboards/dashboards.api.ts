import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ManagementPage, PipelineView } from './dashboards.models';

const BASE = '/api/v1/dashboards';

@Injectable({ providedIn: 'root' })
export class DashboardsApi {
  private readonly http = inject(HttpClient);

  pipeline(): Observable<PipelineView> {
    return this.http.get<PipelineView>(`${BASE}/pipeline`);
  }

  management(page = 0, size = 20): Observable<ManagementPage> {
    return this.http.get<ManagementPage>(`${BASE}/management`, { params: { page, size } });
  }

  auditPack(requestId: number): Observable<Blob> {
    return this.http.get(`${BASE}/requests/${requestId}/audit-pack`, { responseType: 'blob' });
  }

  exportRequestsCsv(): Observable<Blob> {
    return this.http.get(`${BASE}/exports/requests.csv`, { responseType: 'blob' });
  }

  exportPurchaseOrdersCsv(): Observable<Blob> {
    return this.http.get(`${BASE}/exports/purchase-orders.csv`, { responseType: 'blob' });
  }
}
