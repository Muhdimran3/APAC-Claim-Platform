import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CLAIM_STATUSES, ClaimStatus, ClaimSummary } from '../../core/api.models';
import { AssigneeFilter, ClaimsApi } from '../../core/claims-api';
import { STATUS_LABEL } from '../../core/format';
import { Problem, toProblem } from '../../core/problem';
import { Session } from '../../core/session';
import { DayPipe, MoneyPipe } from '../../shared/pipes';
import { ProblemAlert } from '../../shared/problem-alert';
import { StatusPill } from '../../shared/status-pill';
import { needsAttention } from './attention';

@Component({
  selector: 'app-claims-list',
  imports: [RouterLink, StatusPill, ProblemAlert, MoneyPipe, DayPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './claims-list.html',
  styleUrl: './claims-list.css',
})
export class ClaimsList {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  protected readonly session = inject(Session);

  protected readonly statuses = CLAIM_STATUSES;
  protected readonly statusLabel = STATUS_LABEL;
  protected readonly today = new Date();

  protected readonly status = signal<ClaimStatus | null>(null);
  /** Officers start on their own claims, managers on everything (matches the API defaults). */
  protected readonly assignee = signal<AssigneeFilter>(this.session.role() === 'OFFICER' ? 'me' : 'all');

  protected readonly claims = signal<ClaimSummary[] | null>(null);
  protected readonly problem = signal<Problem | null>(null);

  protected readonly attention = computed(() => {
    const me = this.session.me();
    const list = this.claims();
    return me && list ? needsAttention(list, me, this.today).slice(0, 6) : [];
  });

  protected readonly stats = computed(() => {
    const list = this.claims() ?? [];
    if (this.session.isClaimant()) {
      return [
        { label: 'My claims', value: list.length },
        { label: 'Need your input', value: list.filter((c) => c.status === 'AWAITING_INFO').length },
        { label: 'Closed', value: list.filter((c) => c.status === 'SETTLED' || c.status === 'REJECTED').length },
      ];
    }
    return [
      { label: 'In this view', value: list.length },
      { label: 'Awaiting info', value: list.filter((c) => c.status === 'AWAITING_INFO').length },
      { label: 'Unassigned', value: list.filter((c) => !c.assignedOfficer).length },
    ];
  });

  constructor() {
    this.load();
  }

  protected setStatus(s: ClaimStatus | null): void {
    this.status.set(s);
    this.load();
  }

  protected setAssignee(a: AssigneeFilter): void {
    this.assignee.set(a);
    this.load();
  }

  protected open(c: ClaimSummary): void {
    this.router.navigate(['/claims', c.id]);
  }

  private load(): void {
    this.problem.set(null);
    this.claims.set(null);
    const assignee = this.session.isStaff() ? this.assignee() : null;
    this.api.listClaims({ status: this.status(), assignee }).subscribe({
      next: (list) => this.claims.set(list),
      error: (e) => this.problem.set(toProblem(e)),
    });
  }
}
