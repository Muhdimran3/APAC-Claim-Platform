import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ExposureDashboard, WorkloadDashboard } from '../../core/api.models';
import { ClaimsApi } from '../../core/claims-api';
import { Problem, toProblem } from '../../core/problem';
import { Session } from '../../core/session';
import { DayPipe, MoneyPipe, WhenPipe } from '../../shared/pipes';
import { ProblemAlert } from '../../shared/problem-alert';
import { barsFor, totalFor } from './exposure-view';

/**
 * Workload (staff) and liability exposure (managers). Both come from Kafka-fed projections,
 * so they're eventually consistent: shown with their generatedAt time and a manual Refresh.
 * Exposure is only requested for managers. For anyone else it would be a 403, which the
 * API enforces either way.
 */
@Component({
  selector: 'app-dashboard',
  imports: [ProblemAlert, MoneyPipe, DayPipe, WhenPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly api = inject(ClaimsApi);
  protected readonly session = inject(Session);

  protected readonly workload = signal<WorkloadDashboard | null>(null);
  protected readonly workloadProblem = signal<Problem | null>(null);
  protected readonly exposure = signal<ExposureDashboard | null>(null);
  protected readonly exposureProblem = signal<Problem | null>(null);
  protected readonly currency = signal<string | null>(null);

  protected readonly currencies = computed(() => this.exposure()?.byCurrency ?? []);
  protected readonly selectedTotal = computed(() => {
    const d = this.exposure();
    const c = this.currency();
    return d && c ? totalFor(d, c) : null;
  });
  protected readonly bars = computed(() => {
    const d = this.exposure();
    const c = this.currency();
    return d && c ? barsFor(d, c) : [];
  });

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.workloadProblem.set(null);
    this.api.workload().subscribe({
      next: (w) => this.workload.set(w),
      error: (e) => this.workloadProblem.set(toProblem(e)),
    });
    if (this.session.isManager()) {
      this.exposureProblem.set(null);
      this.api.exposure().subscribe({
        next: (d) => {
          this.exposure.set(d);
          const keep = d.byCurrency.some((t) => t.currency === this.currency());
          if (!keep) this.currency.set(d.byCurrency[0]?.currency ?? null);
        },
        error: (e) => this.exposureProblem.set(toProblem(e)),
      });
    }
  }
}
