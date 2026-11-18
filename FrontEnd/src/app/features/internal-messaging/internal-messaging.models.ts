/** Shapes of /api/v1/internal-messaging (BackEnd messaging/InternalMessagingViews.java). */

export interface Person {
  id: number;
  displayName: string;
  department: string | null;
  roles: string[];
}

/** A request card as this viewer may see it. When `available` is false every other field is null. */
export interface RequestCard {
  available: boolean;
  id: number | null;
  number: string | null;
  title: string | null;
  status: string | null;
  userStatus: string | null;
}

export type ConversationKind = 'DIRECT' | 'REQUEST';

export interface Message {
  id: number;
  conversationId: number;
  /** TEXT: written by a person. SYSTEM: `bodyText` is an event code and `subject` the person it is about. */
  kind: 'TEXT' | 'SYSTEM';
  sender: Person;
  subject: Person | null;
  mine: boolean;
  bodyText: string;
  request: RequestCard | null;
  clientMessageId: string;
  createdAt: string;
}

export interface MessagePage {
  items: Message[];
  hasOlder: boolean;
}

export interface ConversationSummary {
  id: number;
  kind: ConversationKind;
  counterpart: Person | null;
  request: RequestCard | null;
  participantCount: number;
  lastMessageKind: 'TEXT' | 'SYSTEM' | null;
  lastMessagePreview: string | null;
  lastMessageSender: Person | null;
  lastMessageAt: string | null;
  unread: number;
}

export interface ConversationDetail {
  id: number;
  kind: ConversationKind;
  counterpart: Person | null;
  request: RequestCard | null;
  participants: Person[];
  canAddParticipants: boolean;
  lastReadMessageId: number | null;
  createdAt: string;
}

export interface SendResult {
  message: Message;
  duplicate: boolean;
  /** The text was delivered but the request card was not: someone in the conversation cannot see the request. */
  cardWithheld: boolean;
}

export interface Approver {
  person: Person;
  stageName: string;
  onBehalfOf: Person | null;
}

export type ApproversState = 'AWAITING' | 'NOT_IN_APPROVAL' | 'NO_APPROVER' | 'SELF_ONLY';

export interface Approvers {
  state: ApproversState;
  approvers: Approver[];
}

export interface Discussion {
  conversation: ConversationDetail | null;
  exists: boolean;
  canStart: boolean;
}

export interface ApproverMessageResult {
  conversationId: number;
  kind: ConversationKind;
  message: Message;
  duplicate: boolean;
}

/** A message the user has written but the server has not confirmed yet. */
export interface PendingMessage {
  clientMessageId: string;
  conversationId: number;
  bodyText: string;
  requestId: number | null;
  state: 'sending' | 'failed';
  errorCode: string | null;
}

/** Events of the live stream. They carry ids only; content is always fetched through the API. */
export type LiveEvent =
  | { type: 'ready' }
  | { type: 'message'; conversationId: number; messageId: number }
  | { type: 'read'; conversationId: number };
