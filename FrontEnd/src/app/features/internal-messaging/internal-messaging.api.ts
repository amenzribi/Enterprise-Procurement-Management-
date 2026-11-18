import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApproverMessageResult,
  Approvers,
  ConversationDetail,
  ConversationSummary,
  Discussion,
  MessagePage,
  Person,
  SendResult,
} from './internal-messaging.models';

export const INTERNAL_MESSAGING_API = '/api/v1/internal-messaging';
const API = INTERNAL_MESSAGING_API;

/** Internal employee messaging. Every call is authorised by the server from the session; ids are checked there. */
@Injectable({ providedIn: 'root' })
export class InternalMessagingApi {
  private readonly http = inject(HttpClient);

  directory(filter: { q?: string; department?: string; role?: string }): Observable<Person[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) {
      if (value?.trim()) {
        params = params.set(key, value.trim());
      }
    }
    return this.http.get<Person[]>(`${API}/directory`, { params });
  }

  conversations(q?: string): Observable<ConversationSummary[]> {
    const params = q?.trim() ? new HttpParams().set('q', q.trim()) : undefined;
    return this.http.get<ConversationSummary[]>(`${API}/conversations`, { params });
  }

  openDirect(userId: number): Observable<ConversationDetail> {
    return this.http.post<ConversationDetail>(`${API}/conversations/direct`, { userId });
  }

  conversation(id: number): Observable<ConversationDetail> {
    return this.http.get<ConversationDetail>(`${API}/conversations/${id}`);
  }

  messages(id: number, cursor: { beforeId?: number; afterId?: number } = {}): Observable<MessagePage> {
    let params = new HttpParams();
    if (cursor.beforeId != null) {
      params = params.set('beforeId', cursor.beforeId);
    }
    if (cursor.afterId != null) {
      params = params.set('afterId', cursor.afterId);
    }
    return this.http.get<MessagePage>(`${API}/conversations/${id}/messages`, { params });
  }

  send(id: number, bodyText: string, clientMessageId: string, requestId: number | null): Observable<SendResult> {
    return this.http.post<SendResult>(`${API}/conversations/${id}/messages`, { bodyText, clientMessageId, requestId });
  }

  markRead(id: number, messageId: number): Observable<void> {
    return this.http.post<void>(`${API}/conversations/${id}/read`, { messageId });
  }

  unread(): Observable<{ total: number }> {
    return this.http.get<{ total: number }>(`${API}/unread`);
  }

  discussion(requestId: number): Observable<Discussion> {
    return this.http.get<Discussion>(`${API}/requests/${requestId}/discussion`);
  }

  startDiscussion(requestId: number): Observable<ConversationDetail> {
    return this.http.post<ConversationDetail>(`${API}/requests/${requestId}/discussion`, {});
  }

  candidates(conversationId: number, q: string): Observable<Person[]> {
    const params = q.trim() ? new HttpParams().set('q', q.trim()) : undefined;
    return this.http.get<Person[]>(`${API}/conversations/${conversationId}/candidates`, { params });
  }

  addParticipant(conversationId: number, userId: number): Observable<ConversationDetail> {
    return this.http.post<ConversationDetail>(`${API}/conversations/${conversationId}/participants`, { userId });
  }

  approvers(requestId: number): Observable<Approvers> {
    return this.http.get<Approvers>(`${API}/requests/${requestId}/approvers`);
  }

  messageApprover(
    requestId: number,
    recipientUserId: number,
    bodyText: string,
    clientMessageId: string,
  ): Observable<ApproverMessageResult> {
    return this.http.post<ApproverMessageResult>(`${API}/requests/${requestId}/message-approver`, {
      recipientUserId,
      bodyText,
      clientMessageId,
    });
  }
}
