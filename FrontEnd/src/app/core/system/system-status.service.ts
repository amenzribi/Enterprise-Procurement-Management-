import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

/** Raw response of `GET /api/v1/system/status`. Fields may be missing when talking to an older or newer server. */
export interface SystemStatusResponse {
  application?: string;
  version?: string;
  serverTime?: string;
  database?: {
    status?: string;
    productVersion?: string | null;
    schemaVersion?: string | null;
  };
}

export type DatabaseState = 'UP' | 'DOWN' | 'UNKNOWN';

/** Status with every optional field normalised to null, so the page can show "Not available" explicitly. */
export interface SystemStatus {
  version: string | null;
  serverTime: string | null;
  database: {
    status: DatabaseState;
    productVersion: string | null;
    schemaVersion: string | null;
    /**
     * True when the server explicitly reported "no schema version" (database reachable, nothing migrated), as
     * opposed to the field being absent from the response.
     */
    noSchemaReported: boolean;
  };
}

/** Response of `GET /healthz` (components only, no details): served on the application port in every environment. */
export interface HealthReport {
  status: string;
  components?: Record<string, { status: string }>;
}

export interface ConnectivityCheck {
  status: SystemStatus;
  /** Null when the health endpoint could not be read; the status check alone still succeeded. */
  health: HealthReport | null;
  roundTripMs: number;
}

export function normaliseStatus(response: SystemStatusResponse | null | undefined): SystemStatus {
  const database = response?.database;
  const state = database?.status;
  return {
    version: response?.version || null,
    serverTime: response?.serverTime || null,
    database: {
      status: state === 'UP' || state === 'DOWN' ? state : 'UNKNOWN',
      productVersion: database?.productVersion || null,
      schemaVersion: database?.schemaVersion || null,
      noSchemaReported: state === 'UP' && database !== undefined && database.schemaVersion === null,
    },
  };
}

/**
 * Frontend -> backend -> database connectivity check. Errors from the status endpoint propagate (the server is
 * unreachable); a failing health endpoint only removes the health detail.
 */
@Injectable({ providedIn: 'root' })
export class SystemStatusService {
  private readonly http = inject(HttpClient);

  check(): Observable<ConnectivityCheck> {
    const started = performance.now();
    const status$ = this.http.get<SystemStatusResponse>('/api/v1/system/status').pipe(
      map((response) => ({
        status: normaliseStatus(response),
        roundTripMs: Math.round(performance.now() - started),
      })),
    );
    // Actuator answers 503 with a body when a component is DOWN; we only need the component list.
    const health$ = this.http
      .get<HealthReport>('/healthz')
      .pipe(catchError((error) => of((error?.error?.status ? error.error : null) as HealthReport | null)));

    return forkJoin([status$, health$]).pipe(map(([result, health]) => ({ ...result, health })));
  }
}
