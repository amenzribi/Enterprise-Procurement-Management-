import { Page } from '../requests/request.models';

export const QUEUE_SORTS = ['requiredDate', 'amount', 'submittedAt'] as const;
export type QueueSort = (typeof QUEUE_SORTS)[number];

export const PURCHASE_MODES = ['DIRECT', 'CENTRALIZED', 'ON_BEHALF'] as const;
export type PurchaseMode = (typeof PURCHASE_MODES)[number];

export interface QueueQuery {
  q: string | null;
  status: string | null;
  priority: string | null;
  purchaseMode: string | null;
  buyingCompanyId: number | null;
  categoryId: number | null;
  countryCode: string | null;
  buyerUserId: number | null;
  requesterUserId: number | null;
  requiredFrom: string | null;
  requiredTo: string | null;
  amountMin: string | null;
  amountMax: string | null;
  complete: boolean | null;
  sort: QueueSort;
  direction: 'asc' | 'desc';
  page: number;
  size: number;
}

export interface QueueRef {
  id: number;
  code?: string;
  name: string;
}

export interface QueueFilterOptions {
  companies: QueueRef[];
  categories: QueueRef[];
  buyers: QueueRef[];
  requesters: QueueRef[];
  countries: string[];
}

export interface QueueItem {
  requestId: number;
  version: number;
  number: string | null;
  title: string;
  status: string;
  userStatus: string;
  requester: QueueRef | null;
  buyingCompany: QueueRef | null;
  requestingCompany: QueueRef | null;
  amount: number | string | null;
  currency: string;
  category?: string | null;
  priority?: string | null;
  purchaseType?: string | null;
  purchaseMode?: string | null;
  buyingCompanyProposed?: boolean;
  assignedBuyer?: QueueRef | null;
  requiredDate?: string | null;
  submittedAt?: string | null;
  completenessGaps?: string[];
}

export type QueuePage = Page<QueueItem>;

export interface BuyerOption {
  id: number;
  displayName: string;
  email?: string;
  openAssignments: number;
  suggested: boolean;
}

export interface Assignment {
  id: number;
  buyerUserId: number;
  buyerName: string;
  assignedByUserId: number;
  assignedByName: string;
  status: string;
  reason?: string | null;
  assignedAt: string;
  decidedAt?: string | null;
}

export interface PurchasingAction {
  version: number;
  buyerUserId?: number | null;
  purchaseMode?: string | null;
  reason?: string | null;
  question?: string | null;
}
