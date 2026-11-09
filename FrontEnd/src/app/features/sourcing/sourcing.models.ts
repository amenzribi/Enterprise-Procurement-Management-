export interface RfqLine {
  id: number;
  lineNo: number;
  description: string;
  quantity: number | string;
  unit: string;
}

export interface RfqSupplier {
  id: number;
  supplierId: number;
  code: string;
  name: string;
}

export interface QuoteLine {
  rfqLineId: number;
  lineNo: number;
  quantity: number | string;
  unitPrice: number | string | null;
  lineAmount: number | string | null;
  priceMissing: boolean;
}

export interface Quote {
  id: number;
  rfqSupplierId: number;
  supplierId: number;
  supplierName: string;
  status: string;
  currency: string;
  merchandiseAmount: number | string;
  freightAmount: number | string;
  dutyAmount: number | string;
  otherAmount: number | string;
  validUntil?: string | null;
  lines: QuoteLine[];
}

export interface RfqDetail {
  id: number;
  requestId: number;
  kind: string;
  status: string;
  title: string;
  comparisonCurrency: string;
  version: number;
  lines: RfqLine[];
  suppliers: RfqSupplier[];
  quotes: Quote[];
}

export interface ComparisonRow {
  quotationId: number;
  supplierId: number;
  supplierName: string;
  currency: string;
  merchandise: number | string;
  freight: number | string;
  duty: number | string;
  other: number | string;
  landed: number | string;
  landedInComparisonCurrency: number | string;
  rate: number | string;
  rateDate: string | null;
  missingFields: string[];
}

export interface ComparisonView {
  comparisonCurrency: string;
  rows: ComparisonRow[];
}

export interface PublicSummary {
  title: string;
  kind: string;
  supplierName: string;
  lines: RfqLine[];
  alreadySubmitted: boolean;
}

export interface CreateRfqBody {
  kind: 'QUICK' | 'STANDARD';
  supplierIds: number[];
}

export interface QuoteBody {
  rfqSupplierId: number;
  currency: string;
  freightAmount?: number;
  dutyAmount?: number;
  otherAmount?: number;
  notes?: string | null;
  lines: { rfqLineId: number; quantity: number; unitPrice: number | null }[];
}
