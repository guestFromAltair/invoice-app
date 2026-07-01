import { InvoiceStatus } from '@prisma/client';

/**
 * Invoice API Response Contract
 *
 * ARCHITECT'S NOTE — WHY MONEY IS A STRING:
 *
 * Every monetary and rate field is a STRING ("1234.5600"), not a number. JSON
 * numbers are IEEE-754 doubles, so a large total or a high-precision rate would
 * silently lose precision crossing the wire. Serializing to a fixed-precision
 * string preserves the exact value the database holds. The React client parses
 * and formats these for display; the backend never emits a lossy number.
 *
 * Dates are emitted as strings too: issueDate/dueDate as `YYYY-MM-DD` (they are
 * DATE columns) and createdAt/updatedAt as full ISO-8601 timestamps.
 */
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
