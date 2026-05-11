export const calcLineTotal = (qty: number, price: number, discount: number): number => {
    if (!qty || !price) return 0;
    return qty * price * (1 - (discount ?? 0));
};