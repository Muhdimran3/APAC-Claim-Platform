import { ClaimSummary, Me } from '../../core/api.models';
import { daysSince, isOpen } from '../../core/format';

export interface AttentionItem {
  claim: ClaimSummary;
  reason: string;
}

export const AGED_DAYS = 14;

/**
 * Which claims in the current list need someone's attention, and why. Presentation only:
 * the actions actually allowed always come from the claim's availableActions.
 */
export function needsAttention(claims: ClaimSummary[], me: Me, now: Date = new Date()): AttentionItem[] {
  const items: AttentionItem[] = [];
  for (const c of claims) {
    const reason = reasonFor(c, me, now);
    if (reason) items.push({ claim: c, reason });
  }
  return items.sort((a, b) => a.claim.submittedAt.localeCompare(b.claim.submittedAt));
}

function reasonFor(c: ClaimSummary, me: Me, now: Date): string | null {
  if (me.role === 'CLAIMANT') {
    return c.status === 'AWAITING_INFO' ? 'We need more information from you' : null;
  }
  if (!isOpen(c.status)) return null;
  if (c.status === 'SUBMITTED' && !c.assignedOfficer) {
    return me.role === 'MANAGER' ? 'Needs an officer' : 'Unassigned: pick it up';
  }
  const mine = c.assignedOfficer?.id === me.id;
  if (me.role === 'OFFICER' && mine) {
    switch (c.status) {
      case 'ASSIGNED':
        return 'Start the assessment';
      case 'UNDER_ASSESSMENT':
        return 'Assess and decide';
      case 'APPROVED':
        return 'Approved: ready to settle';
      default:
        return null; // AWAITING_INFO: waiting on the claimant
    }
  }
  if (me.role === 'MANAGER' && daysSince(c.submittedAt, now) > AGED_DAYS) {
    return `Open more than ${AGED_DAYS} days`;
  }
  return null;
}
