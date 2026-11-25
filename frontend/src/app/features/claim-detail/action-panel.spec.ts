import { ClaimDetail, Me } from '../../core/api.models';
import { buttonsFor } from './action-panel';

const alice: Me = { id: 'u-alice', username: 'alice', displayName: 'Alice', role: 'CLAIMANT' };
const olivia: Me = { id: 'u-olivia', username: 'olivia', displayName: 'Olivia', role: 'OFFICER' };
const oscar: Me = { id: 'u-oscar', username: 'oscar', displayName: 'Oscar', role: 'OFFICER' };
const maya: Me = { id: 'u-maya', username: 'maya', displayName: 'Maya', role: 'MANAGER' };

function claim(over: Partial<ClaimDetail>): ClaimDetail {
  return {
    id: 'c1', claimNumber: 'CLM-001000', status: 'UNDER_ASSESSMENT',
    policy: { id: 'p1', policyNumber: 'SG-MOT-0001', market: 'SG', product: 'MOTOR' },
    currency: 'SGD', claimedAmount: 1000, incidentDate: '2026-11-01', description: 'x',
    submittedAt: '2026-11-02T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z',
    claimant: { id: alice.id, displayName: 'Alice' }, assignedOfficer: { id: olivia.id, displayName: 'Olivia' },
    version: 2, infoRequests: [], availableActions: [],
    ...over,
  };
}

const keys = (c: ClaimDetail, me: Me) => buttonsFor(c, me).map((b) => b.key);

describe('buttonsFor', () => {
  it('shows a claimant only what the server allows (respond while awaiting info)', () => {
    expect(keys(claim({ status: 'AWAITING_INFO', availableActions: ['PROVIDE_INFO'] }), alice)).toEqual(['respond']);
    expect(keys(claim({ availableActions: [] }), alice)).toEqual([]);
  });

  it('gives the assigned officer lifecycle actions plus reserve and note', () => {
    const c = claim({ availableActions: ['REQUEST_INFO', 'APPROVE', 'REJECT'] });
    expect(keys(c, olivia)).toEqual(['requestInfo', 'reserve', 'decide', 'note']);
  });

  it('gives an officer who is not assigned nothing to do', () => {
    // the server returns no availableActions for them; reserve/note must not appear either
    expect(keys(claim({ availableActions: [] }), oscar)).toEqual([]);
  });

  it('lets a manager act on any claim and reassign it', () => {
    const c = claim({ availableActions: ['REQUEST_INFO', 'APPROVE', 'REJECT'] });
    expect(keys(c, maya)).toEqual(['requestInfo', 'reserve', 'decide', 'reassign', 'note']);
  });

  it('labels an officer pick-up as a one-click action and a manager assign as a form', () => {
    const submitted = claim({ status: 'SUBMITTED', assignedOfficer: undefined, availableActions: ['ASSIGN'] });
    expect(buttonsFor(submitted, oscar)[0]).toEqual({ key: 'assign', label: 'Pick up claim', instant: true });
    expect(buttonsFor(submitted, maya)[0]).toEqual({ key: 'assign', label: 'Assign', instant: false });
  });

  it('offers no reserve change on a closed claim', () => {
    expect(keys(claim({ status: 'SETTLED', availableActions: [] }), olivia)).toEqual(['note']);
  });
});
