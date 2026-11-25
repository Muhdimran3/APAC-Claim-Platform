import { ClaimStatus, ClaimSummary, Me } from '../../core/api.models';
import { needsAttention } from './attention';

const now = new Date('2026-11-25T00:00:00Z');
const olivia: Me = { id: 'o1', username: 'olivia', displayName: 'Olivia', role: 'OFFICER' };
const maya: Me = { id: 'm1', username: 'maya', displayName: 'Maya', role: 'MANAGER' };
const alice: Me = { id: 'a1', username: 'alice', displayName: 'Alice', role: 'CLAIMANT' };

function summary(id: string, status: ClaimStatus, officerId: string | null, submittedAt = '2026-11-20T00:00:00Z'): ClaimSummary {
  return {
    id, claimNumber: id, status, currency: 'SGD', claimedAmount: 1, incidentDate: '2026-11-01',
    submittedAt, updatedAt: submittedAt,
    policy: { id: 'p', policyNumber: 'P', market: 'SG', product: 'MOTOR' },
    claimant: { id: 'a1', displayName: 'Alice' },
    assignedOfficer: officerId ? { id: officerId, displayName: 'x' } : null,
  };
}

describe('needsAttention', () => {
  it('claimant: only claims waiting on them', () => {
    const items = needsAttention([summary('A', 'AWAITING_INFO', 'o1'), summary('B', 'UNDER_ASSESSMENT', 'o1')], alice, now);
    expect(items.map((i) => i.claim.id)).toEqual(['A']);
  });

  it('officer: unassigned queue and own claims needing action, not claims waiting on the claimant', () => {
    const items = needsAttention([
      summary('Q', 'SUBMITTED', null),
      summary('MINE', 'UNDER_ASSESSMENT', 'o1'),
      summary('WAIT', 'AWAITING_INFO', 'o1'),
      summary('OTHER', 'UNDER_ASSESSMENT', 'o2'),
    ], olivia, now);
    expect(items.map((i) => i.claim.id).sort()).toEqual(['MINE', 'Q']);
  });

  it('manager: unassigned and aged claims, oldest first', () => {
    const items = needsAttention([
      summary('NEW', 'UNDER_ASSESSMENT', 'o1', '2026-11-24T00:00:00Z'),
      summary('OLD', 'UNDER_ASSESSMENT', 'o1', '2026-11-01T00:00:00Z'),
      summary('Q', 'SUBMITTED', null, '2026-11-10T00:00:00Z'),
    ], maya, now);
    expect(items.map((i) => i.claim.id)).toEqual(['OLD', 'Q']);
  });

  it('never flags closed claims', () => {
    expect(needsAttention([summary('S', 'SETTLED', 'o1', '2026-01-01T00:00:00Z')], maya, now)).toEqual([]);
  });
});
