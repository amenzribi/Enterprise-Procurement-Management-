export interface AwardView {
  id: number;
  requestId: number;
  quotationId: number;
  supplierId: number;
  supplierName: string;
  currency: string;
  merchandiseAmount: number;
  freightAmount: number;
  dutyAmount: number;
  otherAmount: number;
  landedAmount: number;
  reason: string;
  awardedAt: string;
  lines: AwardLineView[];
}

export interface AwardLineView {
  id: number;
  lineNo: number;
  description: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  lineAmount: number;
}

export interface PurchaseOrderView {
  id: number;
  awardId: number;
  requestId: number;
  number: string | null;
  status: 'DRAFT' | 'ISSUED' | 'CONFIRMED' | 'CANCELLED';
  supplierId: number;
  supplierName: string;
  currency: string;
  merchandiseAmount: number;
  freightAmount: number;
  dutyAmount: number;
  otherAmount: number;
  totalAmount: number;
  paymentTerms: string | null;
  incoterm: string | null;
  requiredDate: string | null;
  deliveryAddress: string | null;
  createdAt: string;
  issuedAt: string | null;
  confirmedAt: string | null;
  confirmationNote: string | null;
  version: number;
  lines: PoLineView[];
  milestones: MilestoneView[];
}

export interface PoLineView {
  id: number;
  lineNo: number;
  description: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  lineAmount: number;
}

export interface MilestoneView {
  id: number;
  label: string;
  dueDate: string | null;
  amount: number;
  currency: string;
  status: string;
}
