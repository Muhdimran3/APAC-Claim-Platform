import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaimDetail, Officer } from '../../core/api.models';
import { ClaimsApi } from '../../core/claims-api';
import { Problem, toProblem } from '../../core/problem';
import { Session } from '../../core/session';
import { DayPipe, MoneyPipe, WhenPipe } from '../../shared/pipes';
import { ProblemAlert } from '../../shared/problem-alert';
import { StatusPill } from '../../shared/status-pill';
import { ActionPanel } from './action-panel';
import { TimelineCard } from './timeline-card';

/**
 * Claim detail, read directly from GET /api/claims/{id}, so it's always current. Staff-only
 * fields (reserve, reserve history, notes) are simply absent from the claimant's JSON;
 * the template renders them only when present. A claim belonging to another claimant comes
 * back as 404 and is shown as such.
 */
@Component({
  selector: 'app-claim-detail',
  imports: [RouterLink, StatusPill, ProblemAlert, ActionPanel, TimelineCard, MoneyPipe, DayPipe, WhenPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.css',
})
export class ClaimDetailPage {
  private readonly api = inject(ClaimsApi);
  protected readonly session = inject(Session);

  /** Route param, bound via withComponentInputBinding. */
  readonly id = input.required<string>();

  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly officers = signal<Officer[]>([]);
  protected readonly answered = computed(() => (this.claim()?.infoRequests ?? []).filter((r) => !r.open));

  ngOnInit(): void {
    this.api.claim(this.id()).subscribe({
      next: (c) => this.claim.set(c),
      error: (e) => this.problem.set(toProblem(e)),
    });
    if (this.session.isManager()) {
      this.api.officers().subscribe({ next: (o) => this.officers.set(o) });
    }
  }

  protected onUpdated(c: ClaimDetail): void {
    this.claim.set(c);
  }
}
