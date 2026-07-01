import Decimal from 'decimal.js';
import { InvoiceStatus } from '@prisma/client';
import { InvoiceEntity, serializeInvoice } from './invoice.serializer';

describe('serializeInvoice', () => {
  const baseInvoice = (): InvoiceEntity => ({
    id: 'invoice-uuid-1',
    invoiceNumber: 'INV-2026-0001',
    clientId: 'client-uuid-1',
    createdById: 'user-uuid-1',
    status: InvoiceStatus.DRAFT,
    issueDate: new Date('2026-06-30T00:00:00.000Z'),
    dueDate: new Date('2026-07-30T00:00:00.000Z'),
    subtotal: new Decimal('330'),
    taxRate: new Decimal('0.0825'),
    taxAmount: new Decimal('27.225'),
    discountAmount: new Decimal('0'),
    total: new Decimal('357.225'),
    notes: null,
    version: 0,
    createdAt: new Date('2026-06-30T12:34:56.000Z'),
    updatedAt: new Date('2026-06-30T12:34:56.000Z'),
    lineItems: [
      {
        id: 'line-uuid-1',
        description: 'Consulting',
        quantity: new Decimal('2'),
        unitPrice: new Decimal('100'),
        discountPct: new Decimal('0.10'),
        lineTotal: new Decimal('180'),
        position: 0,
      },
    ],
  });

  it('serializes every monetary field to a fixed-precision string', () => {
    const result = serializeInvoice(baseInvoice());

    expect(result.subtotal).toBe('330.0000');
    expect(result.taxRate).toBe('0.0825');
    expect(result.taxAmount).toBe('27.2250');
    expect(result.discountAmount).toBe('0.0000');
    expect(result.total).toBe('357.2250');
  });

  it('serializes line-item decimals at their schema scales', () => {
    const result = serializeInvoice(baseInvoice());
    const [line] = result.lineItems;

    expect(line.quantity).toBe('2.00');
    expect(line.unitPrice).toBe('100.0000');
    expect(line.discountPct).toBe('0.1000');
    expect(line.lineTotal).toBe('180.0000');
  });

  it('emits DATE columns as YYYY-MM-DD and timestamps as ISO-8601', () => {
    const result = serializeInvoice(baseInvoice());

    expect(result.issueDate).toBe('2026-06-30');
    expect(result.dueDate).toBe('2026-07-30');
    expect(result.createdAt).toBe('2026-06-30T12:34:56.000Z');
  });

  it('never emits a raw number for money (guards against float precision loss)', () => {
    const result = serializeInvoice(baseInvoice());
    expect(typeof result.total).toBe('string');
    expect(typeof result.subtotal).toBe('string');
  });
});
