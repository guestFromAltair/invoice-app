import Decimal from 'decimal.js';

const MONEY_DP = 4;
const ROUNDING = Decimal.ROUND_HALF_UP;

export interface LineItemInput {
  quantity: Decimal.Value;
  unitPrice: Decimal.Value;
  discountPct?: Decimal.Value;
}

export interface InvoiceTotalsInput {
  lineItems: LineItemInput[];
  taxRate?: Decimal.Value;
  discountAmount?: Decimal.Value;
}

export interface InvoiceTotals {
  lineTotals: Decimal[];
  subtotal: Decimal;
  discountAmount: Decimal;
  taxRate: Decimal;
  taxAmount: Decimal;
  total: Decimal;
}

function round4(value: Decimal): Decimal {
  return value.toDecimalPlaces(MONEY_DP, ROUNDING);
}

export function computeLineTotal(item: LineItemInput): Decimal {
  const quantity = new Decimal(item.quantity);
  const unitPrice = new Decimal(item.unitPrice);
  const discountPct = new Decimal(item.discountPct ?? 0);

  const gross = quantity.times(unitPrice);
  const net = gross.times(new Decimal(1).minus(discountPct));

  return round4(net);
}

export function computeInvoiceTotals(input: InvoiceTotalsInput): InvoiceTotals {
  const taxRate = new Decimal(input.taxRate ?? 0);
  const discountAmount = round4(new Decimal(input.discountAmount ?? 0));

  const lineTotals = input.lineItems.map(computeLineTotal);

  const subtotal = round4(lineTotals.reduce((acc, lineTotal) => acc.plus(lineTotal), new Decimal(0)));

  const discountedBase = Decimal.max(subtotal.minus(discountAmount), 0);

  const taxAmount = round4(discountedBase.times(taxRate));
  const total = round4(discountedBase.plus(taxAmount));

  return { lineTotals, subtotal, discountAmount, taxRate, taxAmount, total };
}
