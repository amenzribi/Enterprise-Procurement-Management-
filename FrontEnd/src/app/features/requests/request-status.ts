import { StatusTone } from '../../shared/ui/status-badge';

/**
 * The shared, ordered status enumeration for request headers and lines (sprint-1.md, S1DR-08). Labels live in the
 * i18n files under `requests.status.*`. Only DRAFT -> CREATED is reachable in Sprint 1.
 */
export const REQUEST_STATUSES = [
  'DRAFT',
  'CREATED',
  'IN_APPROVAL',
  'REVISION_REQUESTED',
  'REJECTED',
  'APPROVED',
  'IN_PURCHASING_REVIEW',
  'ASSIGNED_TO_BUYER',
  'SOURCING',
  'QUOTES_RECEIVED',
  'TECHNICAL_VALIDATION',
  'COMMERCIAL_VALIDATION',
  'MANAGEMENT_VALIDATION',
  'AWARDED',
  'ORDERED',
  'CONTRACT_SIGNED',
  'ORDER_CONFIRMED',
  'IN_PRODUCTION',
  'READY',
  'SHIPPED',
  'AT_PORT',
  'IN_CUSTOMS',
  'IN_WAREHOUSE',
  'DELIVERED',
  'PARTIALLY_RECEIVED',
  'NON_CONFORMING',
  'ACCEPTED',
  'INVOICED',
  'PAYMENT_PENDING',
  'PAID',
  'CLOSED',
  'CANCELLED',
] as const;
export type RequestStatus = (typeof REQUEST_STATUSES)[number];

/**
 * Lifecycle stages of spec §5 ("Étape"), as returned by the API (`stage`). The mapping below is only a fallback
 * when a response omits the stage.
 */
export const REQUEST_STAGES = [
  'REQUEST',
  'APPROVAL',
  'PURCHASING',
  'SOURCING',
  'EVALUATION',
  'COMMITMENT',
  'SHIPMENT',
  'RECEIPT',
  'FINANCE',
  'CLOSURE',
] as const;
export type RequestStage = (typeof REQUEST_STAGES)[number];

const STAGE_OF: Record<RequestStatus, RequestStage> = {
  DRAFT: 'REQUEST',
  CREATED: 'REQUEST',
  IN_APPROVAL: 'APPROVAL',
  REVISION_REQUESTED: 'APPROVAL',
  REJECTED: 'APPROVAL',
  APPROVED: 'APPROVAL',
  IN_PURCHASING_REVIEW: 'PURCHASING',
  ASSIGNED_TO_BUYER: 'PURCHASING',
  SOURCING: 'SOURCING',
  QUOTES_RECEIVED: 'SOURCING',
  TECHNICAL_VALIDATION: 'EVALUATION',
  COMMERCIAL_VALIDATION: 'EVALUATION',
  MANAGEMENT_VALIDATION: 'EVALUATION',
  AWARDED: 'EVALUATION',
  ORDERED: 'COMMITMENT',
  CONTRACT_SIGNED: 'COMMITMENT',
  ORDER_CONFIRMED: 'COMMITMENT',
  IN_PRODUCTION: 'SHIPMENT',
  READY: 'SHIPMENT',
  SHIPPED: 'SHIPMENT',
  AT_PORT: 'SHIPMENT',
  IN_CUSTOMS: 'SHIPMENT',
  IN_WAREHOUSE: 'SHIPMENT',
  DELIVERED: 'RECEIPT',
  PARTIALLY_RECEIVED: 'RECEIPT',
  NON_CONFORMING: 'RECEIPT',
  ACCEPTED: 'RECEIPT',
  INVOICED: 'FINANCE',
  PAYMENT_PENDING: 'FINANCE',
  PAID: 'FINANCE',
  CLOSED: 'CLOSURE',
  CANCELLED: 'CLOSURE',
};

export function isRequestStatus(value: unknown): value is RequestStatus {
  return typeof value === 'string' && (REQUEST_STATUSES as readonly string[]).includes(value);
}

export function stageOf(status: string): RequestStage | null {
  return isRequestStatus(status) ? STAGE_OF[status] : null;
}

/** Colour reinforces, never replaces, the status text. */
export function statusTone(status: string): StatusTone {
  switch (status) {
    case 'DRAFT':
      return 'neutral';
    case 'REJECTED':
    case 'NON_CONFORMING':
    case 'CANCELLED':
      return 'danger';
    case 'REVISION_REQUESTED':
    case 'PARTIALLY_RECEIVED':
    case 'PAYMENT_PENDING':
      return 'warning';
    case 'ACCEPTED':
    case 'PAID':
    case 'CLOSED':
      return 'success';
    default:
      return 'info';
  }
}

/** i18n key of a status label; unknown codes fall back to the missing-translation handler (shows the code). */
export function statusKey(status: string): string {
  return `requests.status.${status}`;
}

/**
 * The twelve statuses users see (Solution Guide v3.0 §8). The server computes `userStatus`; the mapping below is the
 * fallback for responses without it.
 */
export const USER_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'RETURNED',
  'REJECTED',
  'APPROVED',
  'ASSIGNED',
  'QUOTING',
  'AWARDED',
  'ORDERED',
  'IN_TRANSIT',
  'RECEIVED',
  'CLOSED',
  'CANCELLED',
] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

const USER_STATUS_OF: Record<RequestStatus, UserStatus> = {
  DRAFT: 'DRAFT',
  CREATED: 'SUBMITTED',
  IN_APPROVAL: 'SUBMITTED',
  REVISION_REQUESTED: 'RETURNED',
  REJECTED: 'REJECTED',
  APPROVED: 'APPROVED',
  IN_PURCHASING_REVIEW: 'APPROVED',
  ASSIGNED_TO_BUYER: 'ASSIGNED',
  SOURCING: 'QUOTING',
  QUOTES_RECEIVED: 'QUOTING',
  TECHNICAL_VALIDATION: 'QUOTING',
  COMMERCIAL_VALIDATION: 'QUOTING',
  MANAGEMENT_VALIDATION: 'QUOTING',
  AWARDED: 'AWARDED',
  ORDERED: 'ORDERED',
  CONTRACT_SIGNED: 'ORDERED',
  ORDER_CONFIRMED: 'ORDERED',
  IN_PRODUCTION: 'ORDERED',
  READY: 'ORDERED',
  SHIPPED: 'IN_TRANSIT',
  AT_PORT: 'IN_TRANSIT',
  IN_CUSTOMS: 'IN_TRANSIT',
  IN_WAREHOUSE: 'IN_TRANSIT',
  DELIVERED: 'RECEIVED',
  PARTIALLY_RECEIVED: 'RECEIVED',
  NON_CONFORMING: 'RECEIVED',
  ACCEPTED: 'RECEIVED',
  INVOICED: 'RECEIVED',
  PAYMENT_PENDING: 'RECEIVED',
  PAID: 'RECEIVED',
  CLOSED: 'CLOSED',
  CANCELLED: 'CANCELLED',
};

export function isUserStatus(value: unknown): value is UserStatus {
  return typeof value === 'string' && (USER_STATUSES as readonly string[]).includes(value);
}

/** The user-facing status of a request: the server's `userStatus`, else derived from the internal status. */
export function userStatusOf(request: { status: string; userStatus?: string | null }): UserStatus | null {
  if (isUserStatus(request.userStatus)) {
    return request.userStatus;
  }
  return isRequestStatus(request.status) ? USER_STATUS_OF[request.status] : null;
}

export function userStatusKey(status: string): string {
  return `requests.userStatus.${status}`;
}

export function userStatusTone(status: UserStatus | null): StatusTone {
  switch (status) {
    case 'DRAFT':
      return 'neutral';
    case 'REJECTED':
    case 'CANCELLED':
      return 'danger';
    case 'RETURNED':
      return 'warning';
    case 'APPROVED':
    case 'RECEIVED':
    case 'CLOSED':
      return 'success';
    default:
      return 'info';
  }
}

/** The main line of the tracker (S2-AC-11). RETURNED and REJECTED are branches shown on the "Submitted" step. */
export const TRACKER_STEPS: readonly UserStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'ASSIGNED',
  'QUOTING',
  'AWARDED',
  'ORDERED',
  'IN_TRANSIT',
  'RECEIVED',
  'CLOSED',
];

/** Position of a user status on the tracker line; the branches sit on the "Submitted" step. */
export function trackerIndex(status: UserStatus | null): number {
  if (status === 'RETURNED' || status === 'REJECTED' || status === 'CANCELLED') {
    return TRACKER_STEPS.indexOf('SUBMITTED');
  }
  return status ? TRACKER_STEPS.indexOf(status) : 0;
}

/**
 * Internal statuses behind each user-facing status, for the list filter: the API filters on internal statuses
 * (`status=A,B`). Exported for tests.
 */
export function internalStatusesOf(userStatus: string): RequestStatus[] {
  return REQUEST_STATUSES.filter((status) => USER_STATUS_OF[status] === userStatus);
}
