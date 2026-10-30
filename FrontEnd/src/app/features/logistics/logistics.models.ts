export interface ShipmentView {
  id: number;
  requestId: number;
  purchaseOrderId: number;
  number: string;
  kind: 'LOCAL' | 'INTERNATIONAL';
  status: string;
  carrierName: string | null;
  trackingReference: string | null;
  originPlace: string | null;
  destinationPlace: string | null;
  createdAt: string;
  version: number;
  lines: { id: number; lineNo: number; description: string; quantity: number; unit: string }[];
  milestones: MilestoneView[];
  documents: DocumentView[];
}

export interface MilestoneView {
  id: number;
  code: string;
  sortOrder: number;
  plannedDate: string | null;
  actualDate: string | null;
  note: string | null;
  late: boolean;
}

export interface DocumentView {
  id: number;
  kind: string;
  referenceNumber: string | null;
  note: string | null;
  fileName: string | null;
  uploadedAt: string;
}
