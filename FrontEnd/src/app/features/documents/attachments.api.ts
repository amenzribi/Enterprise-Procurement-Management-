import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PersonOption } from '../requests/request.models';

/** One attachment of a request or of one of its lines (S2-AC-12). */
export interface Attachment {
  id: number;
  requestId?: number;
  lineId?: number | null;
  fileName: string;
  contentType?: string | null;
  sizeBytes: number;
  version?: number | null;
  /** Download is offered only once the virus scan reported the file clean (S2DR-11). */
  scanStatus?: string | null;
  /** Set by the server: scanned clean, or not scanned where the environment allows that (development). */
  downloadable?: boolean;
  uploadedBy?: PersonOption | null;
  uploadedAt: string;
  downloadUrl?: string | null;
  canDelete?: boolean;
}

/** Limits mirrored from the server (S2-AC-12, S2DR-11); the server checks again and is authoritative. */
export const MAX_FILE_BYTES = 20 * 1024 * 1024;
export const MAX_FILES_PER_REQUEST = 30;
/** Mirrors the server's content check: PDF, images, Office Open XML without macros, text and CSV. */
export const ACCEPTED_TYPES = '.pdf,.png,.jpg,.jpeg,.gif,.webp,.docx,.xlsx,.pptx,.csv,.txt';
const ALLOWED_EXTENSIONS = new Set(ACCEPTED_TYPES.split(',').map((extension) => extension.slice(1)));

/** Client-side pre-check so the user gets an immediate message; returns an error code or null. */
export function checkFile(file: File, existing: number): string | null {
  if (existing >= MAX_FILES_PER_REQUEST) {
    return 'too-many-files';
  }
  if (file.size > MAX_FILE_BYTES) {
    return 'file-too-large';
  }
  if (file.size === 0) {
    return 'file-empty';
  }
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  return ALLOWED_EXTENSIONS.has(extension) ? null : 'file-type-not-allowed';
}

@Injectable({ providedIn: 'root' })
export class AttachmentsApi {
  private readonly http = inject(HttpClient);

  list(requestId: number): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(`/api/v1/requests/${requestId}/attachments`);
  }

  upload(requestId: number, file: File, lineId: number | null): Observable<HttpEvent<Attachment>> {
    const body = new FormData();
    body.append('file', file, file.name);
    if (lineId !== null) {
      body.append('lineId', String(lineId));
    }
    return this.http.post<Attachment>(`/api/v1/requests/${requestId}/attachments`, body, {
      reportProgress: true,
      observe: 'events',
    });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/attachments/${id}`);
  }

  /** Same-origin download link; the browser sends the session cookie and the server checks access. */
  contentUrl(attachment: Attachment): string {
    const url = attachment.downloadUrl;
    return url && url.startsWith('/') && !url.startsWith('//') ? url : `/api/v1/attachments/${attachment.id}/content`;
  }
}
