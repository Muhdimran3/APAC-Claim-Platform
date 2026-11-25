import { formatMoney, formatMoneyCompact, isOpen } from './format';

describe('money formatting', () => {
  it('always shows the ISO code, never an ambiguous symbol', () => {
    expect(formatMoney(2400, 'SGD')).toBe('SGD 2,400.00');
    expect(formatMoney(2400, 'AUD')).not.toContain('$');
  });

  it('uses each currency’s own minor units', () => {
    expect(formatMoney(1500000, 'IDR')).toMatch(/^IDR 1,500,000/);
  });

  it('shows a dash for a missing amount', () => {
    expect(formatMoney(undefined, 'SGD')).toBe('—');
  });

  it('abbreviates large dashboard figures but keeps the currency', () => {
    expect(formatMoneyCompact(1_250_000, 'HKD')).toBe('HKD 1.25M');
  });
});

describe('isOpen', () => {
  it('treats only settled and rejected as closed', () => {
    expect(isOpen('APPROVED')).toBe(true);
    expect(isOpen('SETTLED')).toBe(false);
    expect(isOpen('REJECTED')).toBe(false);
  });
});
