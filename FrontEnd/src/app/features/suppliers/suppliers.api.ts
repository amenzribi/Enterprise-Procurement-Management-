import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { Page } from '../requests/request.models';

export interface Supplier {
  id: number;
  code: string;
  legalName: string;
  countryCode: string;
  visibility: 'SHARED' | 'COMPANY' | string;
  owningCompanyId?: number | null;
  taxId?: string | null;
  erpCode?: string | null;
  email?: string | null;
  city?: string | null;
  active: boolean;
  version: number;
}

export interface SupplierBody {
  code: string;
  legalName: string;
  countryCode: string;
  visibility: string;
  owningCompanyId?: number | null;
  taxId?: string | null;
  erpCode?: string | null;
  email?: string | null;
  city?: string | null;
  version?: number;
}

export type SupplierPage = Page<Supplier>;

/** Badge label key for a supplier's visibility. Exported for tests. */
export function supplierVisibilityKey(visibility: string): string {
  return visibility === 'SHARED' ? 'suppliers.visibility.SHARED' : 'suppliers.visibility.COMPANY';
}

@Injectable({ providedIn: 'root' })
export class SuppliersApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/suppliers';

  list(q: string | null, visibility: string | null, page: number, size = 20): Observable<SupplierPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) {
      params = params.set('q', q);
    }
    if (visibility) {
      params = params.set('visibility', visibility);
    }
    return this.http.get<SupplierPage>(this.base, { params });
  }

  create(body: SupplierBody, key: string): Observable<Supplier> {
    return this.http.post<Supplier>(this.base, body, idempotencyHeaders(key));
  }

  update(id: number, body: SupplierBody, key: string): Observable<Supplier> {
    return this.http.put<Supplier>(`${this.base}/${id}`, body, idempotencyHeaders(key));
  }

  deactivate(id: number, key: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/deactivate`, {}, idempotencyHeaders(key));
  }
}
