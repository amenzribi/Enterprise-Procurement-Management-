import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';

const API = '/api/v1/assistant';

export interface AssistantStatus {
  mode: string;
  available: boolean;
  label: string;
}

export interface AssistantSource {
  type: string;
  id: number;
  label: string;
  path: string;
}

export interface AssistantDraft {
  audience: string;
  bodyText: string;
  conversationId?: number | null;
  label: string;
}

export interface AskResponse {
  threadId: number;
  turnId: number;
  mode: string;
  answerMarkdown: string;
  intent: string;
  sources: AssistantSource[];
  draft?: AssistantDraft | null;
  answeredAt: string;
  durationMs: number;
}

@Injectable({ providedIn: 'root' })
export class AssistantApi {
  private readonly http = inject(HttpClient);

  status(): Observable<AssistantStatus> {
    return this.http.get<AssistantStatus>(`${API}/status`);
  }

  ask(question: string, requestId?: number, key?: string): Observable<AskResponse> {
    const body: Record<string, unknown> = { question };
    if (requestId != null) {
      body['requestId'] = requestId;
    }
    return this.http.post<AskResponse>(`${API}/ask`, body, key ? idempotencyHeaders(key) : {});
  }
}
