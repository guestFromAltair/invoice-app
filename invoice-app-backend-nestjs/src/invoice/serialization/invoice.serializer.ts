import Decimal from 'decimal.js';
import { InvoiceStatus } from '@prisma/client';
import { InvoiceResponse, LineItemResponse } from '../types/invoice-response.type';

const MONEY_DP = 4;
const QUANTITY_DP = 2;
const RATE_DP = 4;

export interface LineItemEntity {
  id: string;
  description: string;
  quantity: Decimal;
  unitPrice: Decimal;
  discountPct: Decimal;
  lineTotal: Decimal;
  position: number;
}

export interface InvoiceEntity {
  id: string;
  invoiceNumber: string;
  clientId: string;
  createdById: string;
  status: InvoiceStatus;
  issueDate: Date;
  dueDate: Date;
  subtotal: Decimal;
  taxRate: Decimal;
  taxAmount: Decimal;
  discountAmount: Decimal;
  total: Decimal;
  notes: string | null;
  version: number;
  createdAt: Date;
  updatedAt: Date;
  lineItems: LineItemEntity[];
}

function toDateOnly(value: Date): string {
  return value.toISOString().slice(0, 10);
}

function serializeLineItem(item: LineItemEntity): LineItemResponse {
  return {
    id: item.id,
    description: item.description,
    quantity: item.quantity.toFixed(QUANTITY_DP),
    unitPrice: item.unitPrice.toFixed(MONEY_DP),
    discountPct: item.discountPct.toFixed(RATE_DP),
    lineTotal: item.lineTotal.toFixed(MONEY_DP),
    position: item.position,
  };
}

export function serializeInvoice(invoice: InvoiceEntity): InvoiceResponse {
  return {
    id: invoice.id,
    invoiceNumber: invoice.invoiceNumber,
    clientId: invoice.clientId,
    createdById: invoice.createdById,
    status: invoice.status,
    issueDate: toDateOnly(invoice.issueDate),
    dueDate: toDateOnly(invoice.dueDate),
    subtotal: invoice.subtotal.toFixed(MONEY_DP),
    taxRate: invoice.taxRate.toFixed(RATE_DP),
    taxAmount: invoice.taxAmount.toFixed(MONEY_DP),
    discountAmount: invoice.discountAmount.toFixed(MONEY_DP),
    total: invoice.total.toFixed(MONEY_DP),
    notes: invoice.notes,
    version: invoice.version,
    createdAt: invoice.createdAt.toISOString(),
    updatedAt: invoice.updatedAt.toISOString(),
    lineItems: invoice.lineItems.map(serializeLineItem),
  };
}
