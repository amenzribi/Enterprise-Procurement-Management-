import { Reference } from '../../core/auth/auth.models';
import { Amount, PersonOption } from '../requests/request.models';

/**
 * Wire types of the Sprint 2 approval API, as implemented by the backend (`ApprovalViews`). Optional fields tolerate
 * servers that omit them.
 */

export const APPROVAL_ACTIONS = ['APPROVE', 'QUESTION', 'RETURN', 'REJECT', 'DELEGATE'] as const;
export type ApprovalAction = (typeof APPROVAL_ACTIONS)[number];
/** Actions the server may list for the current user on a request; ANSWER is the requester's. */
export type RequestApprovalAction = ApprovalAction | 'ANSWER';

/** Actions that need a written reason before they can be sent (S2-AC-05). */
export const REASON_REQUIRED: readonly ApprovalAction[] = ['RETURN', 'REJECT'];

export type ApproverType = 'DEPARTMENT_MANAGER' | 'BUDGET_APPROVER' | 'MANAGEMENT_APPROVER' | 'NAMED_USER';
export type StagePolicy = 'ANY' | 'ALL';

export type TaskStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'DELEGATED' | 'CANCELLED' | 'SUPERSEDED';
export type StageStatus = 'WAITING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'SKIPPED' | 'CANCELLED';
export type RoundStatus = 'ACTIVE' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'CANCELLED';

/** Amounts of a round or a preview: the request amount and its value in company currency (S2-AC-03). */
export interface ApprovalAmounts {
  requestAmount?: Amount | null;
  requestCurrency?: string | null;
  companyAmount?: Amount | null;
  companyCurrency?: string | null;
  rate?: Amount | null;
  rateDate?: string | null;
}

export interface ApprovalTask {
  id: number;
  version: number;
  assignee: PersonOption;
  /** The absent or delegating approver this task was routed from, when any. */
  onBehalfOf?: PersonOption | null;
  routingNote?: string | null;
  status: TaskStatus;
  createdAt: string;
  dueAt?: string | null;
  escalationAt?: string | null;
  overdue?: boolean;
  questionOpen?: boolean;
  decidedAt?: string | null;
  decidedBy?: PersonOption | null;
  comment?: string | null;
}

export interface ApprovalStage {
  stageNo: number;
  name?: string | null;
  policy: StagePolicy;
  approverType: ApproverType;
  slaHours?: number | null;
  status: StageStatus;
  tasks: ApprovalTask[];
}

export interface ApprovalRound {
  id: number;
  roundNo: number;
  status: RoundStatus;
  ruleName?: string | null;
  ruleVersion?: number | null;
  amounts?: ApprovalAmounts | null;
  currentStageNo?: number | null;
  startedAt: string;
  completedAt?: string | null;
  stages: ApprovalStage[];
}

/** One entry of the decision history: creations, decisions, questions, answers, delegations, reminders. */
export interface ApprovalEvent {
  id: number;
  roundNo: number;
  taskId?: number | null;
  kind: string;
  actor?: PersonOption | null;
  onBehalfOf?: PersonOption | null;
  target?: PersonOption | null;
  text?: string | null;
  fromStatus?: string | null;
  toStatus?: string | null;
  /** How the decision was taken, e.g. `EMAIL_LINK` for the one-tap e-mail approval. */
  channel?: string | null;
  occurredAt: string;
}

/** The current user's own pending task on the request. */
export interface MyTask {
  id: number;
  version: number;
  stageNo?: number | null;
  stageName?: string | null;
  questionOpen?: boolean;
}

/** An open question the requester (or the person who entered the request) can answer. */
export interface OpenQuestion {
  taskId: number;
  askedBy?: PersonOption | null;
  question: string;
  askedAt: string;
}

/** `GET /requests/{id}/approval`: tracker data and what the current user may do. */
export interface ApprovalView {
  requestId: number;
  status?: string | null;
  userStatus?: string | null;
  currentRoundNo?: number | null;
  nextActors?: PersonOption[];
  waitingSince?: string | null;
  rounds: ApprovalRound[];
  events?: ApprovalEvent[];
  actions?: RequestApprovalAction[];
  myTask?: MyTask | null;
  openQuestions?: OpenQuestion[];
}

/** One row of `GET /approvals/inbox`. */
export interface InboxItem {
  taskId: number;
  taskVersion: number;
  status: TaskStatus;
  requestId: number;
  number?: string | null;
  title: string;
  userStatus?: string | null;
  requester?: PersonOption | null;
  company?: Reference | null;
  amount?: Amount | null;
  currency: string;
  companyAmount?: Amount | null;
  companyCurrency?: string | null;
  stageNo?: number | null;
  stageName?: string | null;
  policy?: StagePolicy | null;
  waitingSince: string;
  dueAt?: string | null;
  overdue?: boolean;
  questionOpen?: boolean;
  onBehalfOf?: PersonOption | null;
  decidedAt?: string | null;
  requiredDate?: string | null;
  serviceLineCount?: number;
}

export type InboxView = 'pending' | 'decided';
export const INBOX_SORTS = ['dueAt', 'waitingSince', 'amount', 'number', 'requiredDate'] as const;
export type InboxSort = (typeof INBOX_SORTS)[number];

export interface InboxQuery {
  view: InboxView;
  q: string | null;
  sort: InboxSort;
  direction: 'asc' | 'desc';
  page: number;
  size: number;
}

/** `GET /requests/{id}/approval-preview`: who will approve, shown before submitting. */
export interface ApprovalPreview {
  /** A configuration problem that would make submission fail (missing rate, no rule, no approver). */
  problem?: string | null;
  ruleName?: string | null;
  amounts?: ApprovalAmounts | null;
  stages: {
    stageNo: number;
    name?: string | null;
    policy: StagePolicy;
    approverType: ApproverType;
    approvers: { approver: PersonOption; onBehalfOf?: PersonOption | null; routingNote?: string | null }[];
  }[];
}

/** `POST /approval-links/summary`: the minimal summary shown on the one-tap page (S2-AC-07, S2DR-07). */
export interface LinkSummary {
  number?: string | null;
  title: string;
  requester?: PersonOption | null;
  company?: Reference | null;
  amount?: Amount | null;
  currency: string;
  requiredDate?: string | null;
  stageName?: string | null;
  lines?: { description: string; quantity: Amount; unit?: string | null; lineKind?: string | null }[];
  /** Total number of lines; the summary shows the first few. */
  lineCount?: number | null;
  /** Counted over all lines, so a service line beyond the first few is still flagged. */
  serviceLineCount?: number | null;
  currencyMinorUnits?: number | null;
  expiresAt?: string | null;
}

export interface LinkResult {
  outcome?: string | null;
  number?: string | null;
}

export interface Absence {
  id: number;
  user?: PersonOption | null;
  delegate: PersonOption;
  startsAt: string;
  endsAt: string;
  reason?: string | null;
  adminOverride?: boolean;
  createdBy?: PersonOption | null;
  createdAt?: string | null;
  active?: boolean;
}

export interface AbsenceInput {
  delegateUserId: number;
  startsAt: string;
  endsAt: string;
  reason: string | null;
}

export interface RuleStage {
  stageNo: number;
  name?: string | null;
  approverType: ApproverType;
  approverUserId?: number | null;
  policy: StagePolicy;
  slaHours: number;
  escalationRole?: string | null;
  /** Optional lower amount from which this stage applies (company currency). */
  minAmount?: Amount | null;
}

export type RuleStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED';

export interface ApprovalRule {
  id: number;
  ruleKey: number;
  versionNo: number;
  status: RuleStatus;
  company: Reference;
  name: string;
  categoryId?: number | null;
  purchaseType?: string | null;
  /** Inclusive lower bound, in company currency. */
  minAmount: Amount;
  /** Exclusive upper bound; null = no upper limit (S2DR-05). */
  maxAmount?: Amount | null;
  createdAt?: string | null;
  activatedAt?: string | null;
  /** True once a request used this version: it can no longer change (S2DR-05). */
  used?: boolean;
  stages: RuleStage[];
}

export interface ApprovalRuleInput {
  companyId: number;
  name: string;
  categoryId: number | null;
  purchaseType: string | null;
  minAmount: string;
  maxAmount: string | null;
  stages: {
    name: string | null;
    approverType: ApproverType;
    approverUserId: number | null;
    policy: StagePolicy;
    slaHours: number;
    escalationRole: string | null;
    minAmount: string | null;
  }[];
}

/** `GET /admin/companies`. */
export interface AdminCompany {
  id: number;
  code: string;
  name: string;
  currency?: string | null;
}

/** `GET /admin/users`. */
export interface AdminUser {
  id: number;
  displayName: string;
  email?: string | null;
  companyId?: number | null;
  roles?: string[];
}

export interface ExchangeRate {
  id: number;
  baseCurrency: string;
  quoteCurrency: string;
  rate: Amount;
  rateDate: string;
  source?: string | null;
  createdAt?: string | null;
}

export interface ExchangeRateInput {
  baseCurrency: string;
  quoteCurrency: string;
  rate: string;
  rateDate: string;
  source: string | null;
}

/** One in-app notification; title and body are already localised by the server for the recipient. */
export interface InAppNotification {
  id: number;
  kind: string;
  requestId?: number | null;
  title: string;
  body?: string | null;
  link?: string | null;
  createdAt: string;
  readAt?: string | null;
}

export interface NotificationPage {
  items: InAppNotification[];
  page: number;
  size: number;
  totalItems: number;
  totalPages?: number;
  unreadCount?: number;
}
