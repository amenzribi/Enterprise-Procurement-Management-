export type MessageAudience = 'INTERNAL' | 'EXTERNAL';
export type ConversationContextType = 'RFQ' | 'PO';

export interface ConversationSummary {
  id: number;
  contextType: ConversationContextType;
  contextId: number;
  supplierId: number;
  supplierCode: string;
  supplierName: string;
  status: string;
  requestId: number;
  buyingCompanyId: number;
  unreadCount: number;
  createdAt: string;
  lastMessageAt?: string | null;
}

export interface ConversationDetail extends ConversationSummary {
  writable: boolean;
}

export interface MessageView {
  id: number;
  audience: MessageAudience;
  bodyText: string;
  senderUserId: number;
  senderDisplayName: string;
  createdAt: string;
}

export interface MessagePage {
  items: MessageView[];
  nextBeforeId?: number | null;
}

export interface SupplierContact {
  id: number;
  userId: number;
  email: string;
  displayName: string;
  active: boolean;
  invitedAt: string;
  revokedAt?: string | null;
}
