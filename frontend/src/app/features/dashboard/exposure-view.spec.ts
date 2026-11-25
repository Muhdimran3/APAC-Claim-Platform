import { ExposureDashboard } from '../../core/api.models';
import { barsFor, totalFor } from './exposure-view';

const dashboard: ExposureDashboard = {
  generatedAt: '2026-11-25T00:00:00Z',
  byCurrency: [
    { currency: 'IDR', openClaims: 1, unreservedClaims: 0, outstandingReserve: 50_000_000 },
    { currency: 'SGD', openClaims: 3, unreservedClaims: 1, outstandingReserve: 3000 },
  ],
  breakdown: [
    { currency: 'IDR', market: 'ID', product: 'PROPERTY', openClaims: 1, unreservedClaims: 0, outstandingReserve: 50_000_000 },
    { currency: 'SGD', market: 'SG', product: 'MOTOR', openClaims: 2, unreservedClaims: 1, outstandingReserve: 1000 },
    { currency: 'SGD', market: 'SG', product: 'PROPERTY', openClaims: 1, unreservedClaims: 0, outstandingReserve: 2000 },
  ],
};

describe('exposure view', () => {
  it('only includes lines of the selected currency', () => {
    expect(barsFor(dashboard, 'SGD').map((b) => b.label)).toEqual(['SG · PROPERTY', 'SG · MOTOR']);
  });

  it('scales bars within the currency, so IDR never dwarfs SGD', () => {
    expect(barsFor(dashboard, 'SGD').map((b) => b.pct)).toEqual([100, 50]);
  });

  it('returns the server total for a currency without summing across currencies', () => {
    expect(totalFor(dashboard, 'SGD')?.outstandingReserve).toBe(3000);
    expect(totalFor(dashboard, 'EUR')).toBeNull();
  });

  it('handles a currency whose open claims have no reserve yet', () => {
    const zero: ExposureDashboard = { ...dashboard, breakdown: [{ ...dashboard.breakdown[1], outstandingReserve: 0 }] };
    expect(barsFor(zero, 'SGD')[0].pct).toBe(0);
  });
});
