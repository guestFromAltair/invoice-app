import Decimal from 'decimal.js';
import { computeInvoiceTotals, computeLineTotal } from './invoice-calculator';

describe('invoice-calculator', () => {
  describe('computeLineTotal', () => {
    it('multiplies quantity by unit price', () => {
      const result = computeLineTotal({ quantity: 2, unitPrice: 100 });
      expect(result.toFixed(4)).toBe('200.0000');
    });

    it('applies a per-line percentage discount', () => {
      const result = computeLineTotal({
        quantity: 1,
        unitPrice: 100,
        discountPct: '0.10',
      });
      expect(result.toFixed(4)).toBe('90.0000');
    });

    it('is immune to floating-point drift (3 × 0.1 === 0.3 exactly)', () => {
      const result = computeLineTotal({ quantity: 3, unitPrice: '0.1' });
      expect(result.toFixed(4)).toBe('0.3000');
    });

    it('rounds HALF_UP at the 4th decimal place', () => {
      const result = computeLineTotal({ quantity: 1, unitPrice: '0.12345' });
      expect(result.toFixed(4)).toBe('0.1235');
    });
  });

  describe('computeInvoiceTotals', () => {
    it('sums line totals into the subtotal', () => {
      const totals = computeInvoiceTotals({
        lineItems: [
          { quantity: 2, unitPrice: 100 },
          { quantity: 1, unitPrice: 50 },
        ],
      });
      expect(totals.subtotal.toFixed(4)).toBe('250.0000');
      expect(totals.total.toFixed(4)).toBe('250.0000');
    });

    it('applies tax to the subtotal', () => {
      const totals = computeInvoiceTotals({
        lineItems: [{ quantity: 1, unitPrice: 200 }],
        taxRate: '0.20',
      });
      expect(totals.taxAmount.toFixed(4)).toBe('40.0000');
      expect(totals.total.toFixed(4)).toBe('240.0000');
    });

    it('applies an invoice-level discount before tax', () => {
      const totals = computeInvoiceTotals({
        lineItems: [{ quantity: 1, unitPrice: 200 }],
        discountAmount: 50,
        taxRate: '0.20',
      });
      expect(totals.taxAmount.toFixed(4)).toBe('30.0000');
      expect(totals.total.toFixed(4)).toBe('180.0000');
    });

    it('never produces a negative base when the discount exceeds the subtotal', () => {
      const totals = computeInvoiceTotals({
        lineItems: [{ quantity: 1, unitPrice: 100 }],
        discountAmount: 150,
        taxRate: '0.20',
      });
      expect(totals.taxAmount.toFixed(4)).toBe('0.0000');
      expect(totals.total.toFixed(4)).toBe('0.0000');
    });

    it('combines per-line discounts, subtotal, and tax correctly', () => {
      const totals = computeInvoiceTotals({
        lineItems: [
          { quantity: 2, unitPrice: 100, discountPct: '0.10' },
          { quantity: 3, unitPrice: 50 },
        ],
        taxRate: '0.0825',
      });
      expect(totals.subtotal.toFixed(4)).toBe('330.0000');
      expect(totals.taxAmount.toFixed(4)).toBe('27.2250');
      expect(totals.total.toFixed(4)).toBe('357.2250');
    });

    it('returns Decimal instances (not numbers)', () => {
      const totals = computeInvoiceTotals({
        lineItems: [{ quantity: 1, unitPrice: 10 }],
      });
      expect(totals.subtotal).toBeInstanceOf(Decimal);
      expect(totals.total).toBeInstanceOf(Decimal);
    });
  });
});
