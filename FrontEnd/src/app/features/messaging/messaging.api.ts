import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import {
  ConversationDetail,
  ConversationSummary,
  MessageAudience,
  MessagePage,
  SupplierContact,
  MessageView,
} from './messaging.models';

const BUYER = '/api/v1/messaging';
const PORTAL = '/api/v1/supplier-portal';

@Injectable({ providedIn: 'root' })
export class MessagingApi {
  private readonly http = inject(HttpClient);

  listBuyer(supplierId?: number): Observable<ConversationSummary[]> {
    const q = supplierId != null ? `?supplierId=${supplierId}` : '';
    return this.http.get<ConversationSummary[]>(`${BUYER}/conversations${q}`);
  }

  open(contextType: string, contextId: number, supplierId: number, key: string): Observable<ConversationDetail> {
    return this.http.post<ConversationDetail>(
      `${BUYER}/conversations`,
      { contextType, contextId, supplierId },
      idempotencyHeaders(key),
    );
  }

  getBuyer(id: number): Observable<ConversationDetail> {
    return this.http.get<ConversationDetail>(`${BUYER}/conversations/${id}`);
  }

  messagesBuyer(id: number, beforeId?: number): Observable<MessagePage> {
    const q = beforeId != null ? `?beforeId=${beforeId}` : '';
    return this.http.get<MessagePage>(`${BUYER}/conversations/${id}/messages${q}`);
  }

  sendBuyer(
    id: number,
    audience: MessageAudience,
    bodyText: string,
    clientMessageId: string,
    key: string,
  ): Observable<MessageView> {
    return this.http.post<MessageView>(
      `${BUYER}/conversations/${id}/messages`,
      { audience, bodyText, clientMessageId },
      idempotencyHeaders(key),
    );
  }

  markReadBuyer(id: number, messageId: number): Observable<void> {
    return this.http.post<void>(`${BUYER}/conversations/${id}/read`, { messageId });
  }

  listContacts(supplierId: number): Observable<SupplierContact[]> {
    return this.http.get<SupplierContact[]>(`${BUYER}/suppliers/${supplierId}/contacts`);
  }

  invite(supplierId: number, email: string, displayName: string, key: string): Observable<SupplierContact> {
    return this.http.post<SupplierContact>(
      `${BUYER}/suppliers/${supplierId}/contacts`,
      { email, displayName },
      idempotencyHeaders(key),
    );
  }

  revoke(contactId: number, key: string): Observable<void> {
    return this.http.post<void>(`${BUYER}/contacts/${contactId}/revoke`, {}, idempotencyHeaders(key));
  }

  listPortal(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(`${PORTAL}/conversations`);
  }

  getPortal(id: number): Observable<ConversationDetail> {
    return this.http.get<ConversationDetail>(`${PORTAL}/conversations/${id}`);
  }

  messagesPortal(id: number, beforeId?: number): Observable<MessagePage> {
    const q = beforeId != null ? `?beforeId=${beforeId}` : '';
    return this.http.get<MessagePage>(`${PORTAL}/conversations/${id}/messages${q}`);
  }

  sendPortal(id: number, bodyText: string, clientMessageId: string, key: string): Observable<MessageView> {
    return this.http.post<MessageView>(
      `${PORTAL}/conversations/${id}/messages`,
      { audience: 'EXTERNAL', bodyText, clientMessageId },
      idempotencyHeaders(key),
    );
  }

  markReadPortal(id: number, messageId: number): Observable<void> {
    return this.http.post<void>(`${PORTAL}/conversations/${id}/read`, { messageId });
  }
}
