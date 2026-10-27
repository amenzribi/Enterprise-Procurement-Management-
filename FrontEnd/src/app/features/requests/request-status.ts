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
