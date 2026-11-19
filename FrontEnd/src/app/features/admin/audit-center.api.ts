import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

const API = '/api/v1/admin/audit';

/** One audit event, as `GET /admin/audit/events` returns it (read-only). */
export interface AuditEntry {
  id: number;
  chainKey: string;
  chainSeq: number;
  occurredAt: string;
  actorUserId: number | null;
  actorLabel: string;
  companyId: number | null;
  companyName: string | null;
  entityType: string;
  entityId: string;
  action: string;
  fromStatus: string | null;
  toStatus: string | null;
  comment: string | null;
  payloadJson: string | null;
}

export interface AuditPage {
  items: AuditEntry[];
  nextCursor: string | null;
}

/** Filters sent as query parameters; empty values are left out. `from` and `to` are ISO instants (UTC). */
export interface AuditFilter {
  from?: string;
  to?: string;
  companyId?: number | null;
  actorUserId?: number | null;
  action?: string;
  entityType?: string;
  entityId?: string;
}

export type VerificationOutcome = 'NONE' | 'RUNNING' | 'VALID' | 'INVALID' | 'ERROR';

export interface Verification {
  outcome: VerificationOutcome;
  startedBy: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  chainsTotal: number;
  chainsChecked: number;
  eventsChecked: number;
  invalidChain: string | null;
  firstInvalidEventId: number | null;
}

/** Audit Center API (global administrators only); see `AuditCenterController`. */
@Injectable({ providedIn: 'root' })
export class AuditCenterApi {
  private readonly http = inject(HttpClient);

  events(filter: AuditFilter, cursor: string | null, size = 50): Observable<AuditPage> {
    let params = toParams(filter).set('size', size);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<AuditPage>(`${API}/events`, { params });
  }

  /** The export URL for the same filters; downloaded by the browser with the session cookie. */
  exportUrl(filter: AuditFilter): string {
    const query = toParams(filter).toString();
    return `${API}/events/export${query ? `?${query}` : ''}`;
  }

  startVerification(): Observable<Verification> {
    return this.http.post<Verification>(`${API}/verifications`, {});
  }

  verification(): Observable<Verification> {
    return this.http.get<Verification>(`${API}/verifications/current`);
  }
}

export function toParams(filter: AuditFilter): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== null && `${value}`.trim() !== '') {
      params = params.set(key, `${value}`.trim());
    }
  }
  return params;
}

/** A calendar date (yyyy-mm-dd) as the start of that day in UTC; with `endOfDay`, the start of the next day. */
export function dayToInstant(day: string, endOfDay = false): string | undefined {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) {
    return undefined;
  }
  const date = new Date(`${day}T00:00:00Z`);
  if (endOfDay) {
    date.setUTCDate(date.getUTCDate() + 1);
  }
  return date.toISOString();
}
