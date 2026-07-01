import { InvoiceStatus } from '@prisma/client';

export interface LineItemResponse {
  id: string;
  description: string;
  quantity: string;
  unitPrice: string;
  discountPct: string;
  lineTotal: string;
  position: number;
}

export interface InvoiceResponse {
  id: string;
  invoiceNumber: string;
  clientId: string;
  createdById: string;
  status: InvoiceStatus;
  issueDate: string;
  dueDate: string;
  subtotal: string;
  taxRate: string;
  taxAmount: string;
  discountAmount: string;
  total: string;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  lineItems: LineItemResponse[];
}
