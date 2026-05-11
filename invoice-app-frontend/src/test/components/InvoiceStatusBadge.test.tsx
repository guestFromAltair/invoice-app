import { describe, it, expect } from 'vitest';
import type { InvoiceStatus } from '@/types';
import { statusVariant, statusLabel } from '@/utils/invoiceStatus';

describe('invoice status mappings', () => {
    const statuses: InvoiceStatus[] = [
        'DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED'
    ];

    it('should map a variant for every possible status', () => {
        statuses.forEach(status => {
            expect(statusVariant[status]).toBeDefined();
        });
    });

    it('should map a label for every possible status', () => {
        statuses.forEach(status => {
            expect(statusLabel[status]).toBeDefined();
        });
    });

    it('should map OVERDUE to the destructive variant', () => {
        expect(statusVariant['OVERDUE']).toBe('destructive');
    });

    it('should map PAID to the outline variant', () => {
        expect(statusVariant['PAID']).toBe('outline');
    });
});