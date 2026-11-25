// Types mirroring the backend contract (see GET /v3/api-docs). The backend owns the contract.

export type Role = 'CLAIMANT' | 'OFFICER' | 'MANAGER';

export const CLAIM_STATUSES = [
  'SUBMITTED',
  'ASSIGNED',
  'UNDER_ASSESSMENT',
  'AWAITING_INFO',
  'APPROVED',
  'SETTLED',
  'REJECTED',
] as const;
export type ClaimStatus = (typeof CLAIM_STATUSES)[number];

export type ClaimAction =
  | 'ASSIGN'
  | 'START_ASSESSMENT'
  | 'REQUEST_INFO'
  | 'PROVIDE_INFO'
  | 'APPROVE'
  | 'REJECT'
  | 'SETTLE';

export interface Me {
  id: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface Officer {
  id: string;
  username: string;
  displayName: string;
}

export interface Policy {
  id: string;
  policyNumber: string;
  market: string;
  product: string;
  currency: string;
  sumInsured: number;
  effectiveFrom: string;
  effectiveTo: string;
}

export interface UserRef {
  id: string;
  displayName: string | null;
}

export interface PolicyRef {
  id: string;
  policyNumber: string;
  market: string;
  product: string;
}

export interface ClaimSummary {
  id: string;
  claimNumber: string;
  status: ClaimStatus;
  policy: PolicyRef;
  currency: string;
  claimedAmount: number;
  incidentDate: string;
  submittedAt: string;
  updatedAt: string;
  claimant: UserRef;
  assignedOfficer: UserRef | null;
}

export interface InfoRequest {
  id: string;
  question: string;
  requestedBy: UserRef;
  requestedAt: string;
  response?: string;
  respondedAt?: string;
  open: boolean;
}

export interface DecisionView {
  outcome: 'APPROVED' | 'REJECTED';
  approvedAmount?: number;
  reason: string;
  decidedBy: UserRef;
  decidedAt: string;
}

export interface ReserveChange {
  previousAmount: number;
  newAmount: number;
  reason: string;
  changedBy: UserRef;
  changedAt: string;
}

export interface Note {
  id: string;
  body: string;
  author: UserRef;
  createdAt: string;
}

/** Fields marked staff-only are omitted from the JSON entirely for claimants. */
export interface ClaimDetail {
  id: string;
  claimNumber: string;
  status: ClaimStatus;
  policy: PolicyRef;
  currency: string;
  claimedAmount: number;
  incidentDate: string;
  description: string;
  submittedAt: string;
  updatedAt: string;
  closedAt?: string;
  claimant: UserRef;
  assignedOfficer?: UserRef;
  settledAmount?: number;
  version: number;
  infoRequests: InfoRequest[];
  decision?: DecisionView;
  availableActions: ClaimAction[];
  /** staff only */
  reserveAmount?: number;
  /** staff only */
  reserveHistory?: ReserveChange[];
  /** staff only */
  notes?: Note[];
}

export interface TimelineEntry {
  eventId: string;
  eventType: string;
  occurredAt: string;
  summary: string;
  actor: string | null;
}

export interface OfficerWorkload {
  officerId: string;
  displayName: string;
  open: number;
  openByStatus: Record<'ASSIGNED' | 'UNDER_ASSESSMENT' | 'AWAITING_INFO' | 'APPROVED', number>;
  aged: number;
  oldestOpenSubmittedAt: string | null;
  closedInWindow: number;
  avgDaysToClose: number | null;
}

export interface WorkloadDashboard {
  generatedAt: string;
  agedAfterDays: number;
  performanceWindowDays: number;
  unassigned: { open: number; aged: number; oldestSubmittedAt: string | null };
  officers: OfficerWorkload[];
}

export interface CurrencyTotal {
  currency: string;
  openClaims: number;
  unreservedClaims: number;
  outstandingReserve: number;
}

export interface ExposureLine extends CurrencyTotal {
  market: string;
  product: string;
}

export interface ExposureDashboard {
  generatedAt: string;
  byCurrency: CurrencyTotal[];
  breakdown: ExposureLine[];
}

// Command bodies. None of them carries a role or user: identity comes only from X-User.
export interface SubmitClaimBody {
  policyId: string;
  incidentDate: string;
  description: string;
  claimedAmount: number;
}

export interface DecideBody {
  outcome: 'APPROVED' | 'REJECTED';
  amount?: number;
  reason: string;
}
