export interface ReceiptDossier {
  receipts: GoodsReceipt[];
  invoice: SupplierInvoice | null;
  closure: { ready: boolean; missing: string[]; closed: boolean; rating: number | null };
  serviceAccepted: boolean;
  shortfallAccepted: boolean;
  purchaseOrder: PurchaseOrderSummary | null;
}

export interface PurchaseOrderSummary {
  currency: string;
  total: number;
  lines: PurchaseOrderLine[];
}

export interface PurchaseOrderLine {
  id: number;
  description: string;
  quantity: number;
  unit: string;
  received: number;
  receivable: number;
  kind: 'GOODS' | 'SERVICE';
  shipmentLines: ShipmentLineReceipt[];
}

/** A delivered shipment line of a goods line: receipts are recorded against one of these. */
export interface ShipmentLineReceipt {
  id: number;
  shipmentId: number;
  shipmentNumber: string;
  quantity: number;
  received: number;
  receivable: number;
}

export interface ReceiptLineDraft {
  received: string;
  accepted: string;
  rejected: string;
  condition: string;
  /** Once the user edits Accepted, it no longer follows Received. */
  acceptedEdited?: boolean;
}

export interface GoodsReceipt {
  id: number;
  number: string;
  status: string;
  note: string | null;
  version: number;
  receivedByUserId?: number;
  lines: {
    id: number;
    description: string;
    quantityReceived: number;
    quantityAccepted: number;
    quantityRejected: number;
    quantityDamaged: number;
    condition: string;
    nonConformity?: 'OPEN' | 'RESOLVED' | null;
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
