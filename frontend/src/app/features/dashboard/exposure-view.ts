import { CurrencyTotal, ExposureDashboard, ExposureLine } from '../../core/api.models';

export interface ExposureBar {
  label: string;
  amount: number;
  openClaims: number;
  unreservedClaims: number;
  /** width relative to the largest line of the SAME currency, 0–100 */
  pct: number;
}

/**
 * Breakdown for one currency only. Bars are scaled within that currency, because comparing
 * SGD and IDR bars would imply a conversion the system deliberately doesn't do.
 */
export function barsFor(dashboard: ExposureDashboard, currency: string): ExposureBar[] {
  const lines: ExposureLine[] = dashboard.breakdown
    .filter((l) => l.currency === currency)
    .sort((a, b) => b.outstandingReserve - a.outstandingReserve);
  const max = Math.max(0, ...lines.map((l) => l.outstandingReserve));
  return lines.map((l) => ({
    label: `${l.market} · ${l.product}`,
    amount: l.outstandingReserve,
    openClaims: l.openClaims,
    unreservedClaims: l.unreservedClaims,
    pct: max > 0 ? Math.round((l.outstandingReserve / max) * 100) : 0,
  }));
}

export function totalFor(dashboard: ExposureDashboard, currency: string): CurrencyTotal | null {
  return dashboard.byCurrency.find((t) => t.currency === currency) ?? null;
}
