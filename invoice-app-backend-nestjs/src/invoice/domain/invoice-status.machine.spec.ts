import { InvoiceStatus } from '@prisma/client';
import { canTransition, getAllowedTransitions } from './invoice-status.machine';

describe('invoice-status.machine', () => {
  describe('canTransition - legal moves', () => {
    it.each([
      [InvoiceStatus.DRAFT, InvoiceStatus.SENT],
      [InvoiceStatus.DRAFT, InvoiceStatus.CANCELLED],
      [InvoiceStatus.SENT, InvoiceStatus.PAID],
      [InvoiceStatus.SENT, InvoiceStatus.OVERDUE],
      [InvoiceStatus.SENT, InvoiceStatus.CANCELLED],
      [InvoiceStatus.OVERDUE, InvoiceStatus.PAID],
      [InvoiceStatus.OVERDUE, InvoiceStatus.CANCELLED],
    ])('allows %s -> %s', (from, to) => {
      expect(canTransition(from, to)).toBe(true);
    });
  });

  describe('canTransition - illegal moves', () => {
    it.each([
      [InvoiceStatus.DRAFT, InvoiceStatus.PAID],
      [InvoiceStatus.DRAFT, InvoiceStatus.OVERDUE],
      [InvoiceStatus.SENT, InvoiceStatus.DRAFT],
      [InvoiceStatus.OVERDUE, InvoiceStatus.SENT],
      [InvoiceStatus.PAID, InvoiceStatus.DRAFT],
      [InvoiceStatus.PAID, InvoiceStatus.SENT],
      [InvoiceStatus.CANCELLED, InvoiceStatus.SENT],
    ])('rejects %s -> %s', (from, to) => {
      expect(canTransition(from, to)).toBe(false);
    });
  });

  it('treats PAID as terminal', () => {
    expect(getAllowedTransitions(InvoiceStatus.PAID)).toHaveLength(0);
  });

  it('treats CANCELLED as terminal', () => {
    expect(getAllowedTransitions(InvoiceStatus.CANCELLED)).toHaveLength(0);
  });

  it('rejects a self-transition', () => {
    expect(canTransition(InvoiceStatus.DRAFT, InvoiceStatus.DRAFT)).toBe(false);
  });

  it('exposes the exact allowed set for DRAFT', () => {
    expect(getAllowedTransitions(InvoiceStatus.DRAFT)).toEqual([InvoiceStatus.SENT, InvoiceStatus.CANCELLED]);
  });
});
