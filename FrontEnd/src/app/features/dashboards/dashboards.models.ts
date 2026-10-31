export interface StatusCount {
  userStatus: string;
  count: number;
}

export interface CurrencyTotal {
  currency: string;
  amount: string | number;
  requestCount: number;
}

export interface CompanyCount {
  companyCode: string;
  companyName: string;
  count: number;
}

export interface PipelineView {
  byUserStatus: StatusCount[];
  openAmountsByCurrency: CurrencyTotal[];
  byRequestingCompany: CompanyCount[];
  groupView: boolean;
}

export interface ManagementRow {
  id: number;
  number: string | null;
  title: string;
  userStatus: string | null;
  status: string;
  ownerName: string;
  ownerSince: string | null;
  requiredDate: string | null;
  totalAmount: string | number | null;
  currency: string | null;
  late: boolean;
}

export interface ManagementPage {
  items: ManagementRow[];
  page: number;
  size: number;
  totalElements: number;
}
