import { describe, it, expect } from 'vitest';

import { calcLineTotal } from '@/utils/calculations.ts';

describe('calcLineTotal', () => {
    it('should calculate line total with no discount', () => {
        expect(calcLineTotal(10, 150, 0)).toBe(1500);
    });

    it('should apply discount correctly', () => {
        expect(calcLineTotal(8, 175, 0.10)).toBe(1260);
    });

    it('should return 0 for zero quantity', () => {
        expect(calcLineTotal(0, 150, 0)).toBe(0);
    });

    it('should return 0 for zero price', () => {
        expect(calcLineTotal(10, 0, 0)).toBe(0);
    });

    it('should handle 100% discount', () => {
        expect(calcLineTotal(10, 150, 1.0)).toBe(0);
    });
});