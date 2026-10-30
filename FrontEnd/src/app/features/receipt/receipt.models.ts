export interface ReceiptDossier {
  receipts: GoodsReceipt[];
  invoice: SupplierInvoice | null;
  closure: { ready: boolean; missing: string[]; closed: boolean; rating: number | null };
  serviceAccepted: boolean;
}

export interface GoodsReceipt {
  id: number;
  number: string;
  status: string;
  note: string | null;
  version: number;
  lines: {
    id: number;
    description: string;
    quantityReceived: number;
    quantityAccepted: number;
    quantityRejected: number;
    quantityDamaged: number;
    condition: string;
  }[];
  evidence: { id: number; kind: string; referenceNumber: string | null }[];
}

export interface SupplierInvoice {
  id: number;
  invoiceNumber: string;
  amount: number;
  currency: string;
  documentReference: string | null;
  matchAttested: boolean;
  paymentStatus: string;
}
