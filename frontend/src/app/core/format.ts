import { ClaimStatus } from './api.models';

/**
 * "SGD 2,400.00". Always shows the ISO code, never a symbol ($ is ambiguous across SGD, HKD
 * and AUD), and never converts between currencies.
 */
export function formatMoney(amount: number | null | undefined, currency: string): string {
  if (amount === null || amount === undefined) return '—';
  try {
    return new Intl.NumberFormat('en', {
      style: 'currency',
      currency,
      currencyDisplay: 'code',
    })
      .format(amount)
      .replace(/ /g, ' ');
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

/** Short form for big numbers on dashboards: "SGD 1.25M", "IDR 3.4B". */
export function formatMoneyCompact(amount: number, currency: string): string {
  const n = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 2 }).format(amount);
  return `${currency} ${n}`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso.length === 10 ? iso + 'T00:00:00' : iso);
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function daysSince(iso: string, now: Date = new Date()): number {
  return Math.floor((now.getTime() - new Date(iso).getTime()) / 86_400_000);
}

export const STATUS_LABEL: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  ASSIGNED: 'Assigned',
  UNDER_ASSESSMENT: 'Under assessment',
  AWAITING_INFO: 'Awaiting info',
  APPROVED: 'Approved',
  SETTLED: 'Settled',
  REJECTED: 'Rejected',
};

export function isOpen(status: ClaimStatus): boolean {
  return status !== 'SETTLED' && status !== 'REJECTED';
}
