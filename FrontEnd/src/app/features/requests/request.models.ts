import { Reference } from '../../core/auth/auth.models';

/**
 * Wire types of the purchase-request API (docs/product/sprint-1.md, "API contract"). Amounts may arrive as JSON
 * numbers or strings (BigDecimal); both are accepted. Optional fields tolerate servers that omit them.
 */

export const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
export type Priority = (typeof PRIORITIES)[number];

export const PURCHASE_TYPES = ['LOCAL', 'INTERNATIONAL'] as const;
export type PurchaseType = (typeof PURCHASE_TYPES)[number];

export type RequestAction = 'EDIT' | 'SUBMIT' | 'DISCARD';

export type Amount = number | string;

export interface NamedOption {
  id: number;
  code?: string;
  name?: string;
  nameEn?: string;
  nameFr?: string;
}

export interface CurrencyOption {
  code: string;
  minorUnits: number;
  name?: string;
  nameEn?: string;
  nameFr?: string;
}

export interface UnitOption {
  code: string;
  name?: string;
  nameEn?: string;
  nameFr?: string;
}

export interface PersonOption {
  id: number;
  displayName: string;
  departmentId?: number | null;
}

export interface OrgOption {
  id: number;
  code?: string;
  name: string;
  siteId?: number | null;
}

/** A company the user may create requests for, with everything the form needs for it. */
export interface CompanyOption {
  id: number;
  code: string;
  name: string;
  currency: string;
  timeZone?: string;
  canEnterOnBehalf: boolean;
  departments: OrgOption[];
  sites: OrgOption[];
  costCenters: OrgOption[];
  requesters: PersonOption[];
  chargedCompany?: Reference | null;
  buyingCompany?: Reference | null;
  /** True when the buying company is a proposal purchasing may change (S1DR-07). */
  buyingCompanyProposed?: boolean;
}

/** `GET /requests/form-options`. `companies` is empty when the user cannot create requests. */
export interface FormOptions {
  currencies: CurrencyOption[];
  units: UnitOption[];
  categories: NamedOption[];
  priorities?: string[];
  purchaseTypes?: string[];
  maxLines?: number;
  defaults?: {
    requestingCompanyId?: number | null;
    departmentId?: number | null;
    siteId?: number | null;
    currency?: string | null;
    requesterId?: number | null;
  };
  companies: CompanyOption[];
}

export interface RequestLine {
  id?: number;
  lineNo?: number;
  description: string;
  itemReference?: string | null;
  quantity: Amount;
  unit: string;
  estimatedUnitPrice: Amount;
  lineTotal?: Amount | null;
  requiredDate?: string | null;
  deliveryLocation?: string | null;
  status?: string;
}

export interface RequestTransition {
  fromStatus?: string | null;
  toStatus: string;
  stage?: string | null;
  owner?: PersonOption | null;
  actor?: PersonOption | null;
  occurredAt: string;
  comment?: string | null;
}

/** `GET /requests/{id}`. */
export interface PurchaseRequest {
  id: number;
  number?: string | null;
  status: string;
  stage?: string | null;
  title: string;
  category?: NamedOption | null;
  priority: string;
  purchaseType: string;
  origin?: string | null;
  currency: string;
  /** Minor units of the request currency (TND 3, EUR 2, JPY 0). */
  currencyMinorUnits?: number | null;
  requestingCompany: Reference;
  chargedCompany?: Reference | null;
  buyingCompany?: Reference | null;
  buyingCompanyProposed?: boolean;
  department?: Reference | null;
  site?: Reference | null;
  costCenter?: Reference | null;
  beneficiarySite?: Reference | null;
  project?: string | null;
  justification?: string | null;
  urgencyJustification?: string | null;
  budgetLineRef?: string | null;
  equipmentRef?: string | null;
  maintenanceOrderRef?: string | null;
  requiredDate?: string | null;
  deliveryLocation?: string | null;
  beneficiary?: string | null;
  requester: PersonOption;
  enteredBy: PersonOption;
  updatedBy?: PersonOption | null;
  currentOwner?: PersonOption | null;
  ownerSince?: string | null;
  totalAmount?: Amount | null;
  lines: RequestLine[];
  transitions?: RequestTransition[];
  version: number;
  createdAt?: string;
  updatedAt?: string;
  submittedAt?: string | null;
  /** Actions the server allows the current user (S1DR-18). The UI offers exactly these. */
  actions: RequestAction[];
}

/**
 * Body of `POST /requests` and `PUT /requests/{id}`. Server-derived fields (number, status, entered-by, charged and
 * buying company, origin, totals) are never sent: the server rejects them with `not-editable`.
 */
export interface RequestPayload {
  title: string;
  categoryId: number | null;
  priority: string | null;
  purchaseType: string | null;
  currency: string | null;
  requestingCompanyId: number | null;
  /** Omitted when the user requests for themselves. */
  requesterId?: number;
  departmentId: number | null;
  siteId: number | null;
  costCenterId: number | null;
  project: string | null;
  justification: string | null;
  urgencyJustification: string | null;
  budgetLineRef: string | null;
  equipmentRef: string | null;
  maintenanceOrderRef: string | null;
  requiredDate: string | null;
  deliveryLocation: string | null;
  beneficiary: string | null;
  beneficiarySiteId: number | null;
  /** Existing lines carry their `id` so the server updates them; new lines have none (S1CR-07). */
  lines: {
    id?: number;
    description: string;
    itemReference: string | null;
    quantity: number | null;
    unit: string;
    estimatedUnitPrice: number | null;
    requiredDate: string | null;
    deliveryLocation: string | null;
  }[];
  version?: number;
}

export interface RequestListItem {
  id: number;
  number?: string | null;
  title: string;
  status: string;
  stage?: string | null;
  totalAmount?: Amount | null;
  currency: string;
  requiredDate?: string | null;
  updatedAt?: string | null;
  submittedAt?: string | null;
  requester?: PersonOption | null;
  requestingCompany?: Reference | null;
  actions?: RequestAction[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages?: number;
}

/** "My requests" or every request the user may see (department or group visibility). */
export type RequestScope = 'mine' | 'visible';

export const SORTABLE_COLUMNS = ['number', 'title', 'status', 'totalAmount', 'requiredDate', 'updatedAt'] as const;
export type SortColumn = (typeof SORTABLE_COLUMNS)[number];
export type SortDirection = 'asc' | 'desc';

export interface RequestQuery {
  scope: RequestScope;
  status: string | null;
  q: string | null;
  sort: SortColumn;
  direction: SortDirection;
  page: number;
  size: number;
}

/** Display name of an option in the current language. */
export function optionLabel(option: { name?: string; nameEn?: string; nameFr?: string; code?: string } | null | undefined, language: string): string {
  if (!option) {
    return '';
  }
  const localised = language === 'fr' ? option.nameFr : option.nameEn;
  return localised || option.name || option.nameEn || option.code || '';
}
