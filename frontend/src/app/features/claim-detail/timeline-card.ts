import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TimelineEntry } from '../../core/api.models';
import { ClaimsApi } from '../../core/claims-api';
import { Problem, toProblem } from '../../core/problem';
import { WhenPipe } from '../../shared/pipes';
import { ProblemAlert } from '../../shared/problem-alert';

/**
 * The claim timeline from the Kafka-fed projection. It's eventually consistent, so it's
 * loaded once and then refreshed on demand. No polling and no simulated lag. For staff,
 * comparing the event count with the claim's version shows honestly whether the
 * projection has caught up (one event per version, starting at 0).
 */
@Component({
  selector: 'app-timeline-card',
  imports: [ProblemAlert, WhenPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card soft">
      <div class="spread">
        <div>
          <h2 class="card-title">Timeline</h2>
          <div class="mono muted">timeline-projection · claim-events</div>
        </div>
        <button type="button" class="btn small ghost" (click)="load()" [disabled]="loading()">
          {{ loading() ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>
      <div class="note">Built from Kafka events, so it can trail the claim by a moment. The claim details on this page are always current.</div>
      @if (staffView() && entries(); as list) {
        @if (list.length < claimVersion() + 1) {
          <span class="pill lime">Catching up: {{ list.length }} of {{ claimVersion() + 1 }} events projected</span>
        } @else {
          <span class="pill">Up to date with claim v{{ claimVersion() }}</span>
        }
      }
      <app-problem-alert [problem]="problem()" />
      @if (entries(); as list) {
        @for (e of reversed(); track e.eventId) {
          <article class="entry">
            <span class="dot" [class.internal]="e.eventType === 'ReserveChanged'" aria-hidden="true"></span>
            <div class="body">
              <div class="spread"><strong class="small">{{ e.summary }}</strong><span class="small muted num">{{ e.occurredAt | when }}</span></div>
              <div class="mono muted">{{ e.eventType }}{{ e.actor ? ' · ' + e.actor : '' }}</div>
            </div>
          </article>
        } @empty {
          <div class="empty">No events projected yet. Try Refresh in a moment.</div>
        }
      } @else if (!problem()) {
        <div class="skeleton">Loading timeline…</div>
      }
    </section>
  `,
  styles: `
    .entry { background: #fff; border-radius: 16px; padding: 10px 12px; display: flex; gap: 10px; align-items: flex-start; }
    .dot { width: 12px; height: 12px; border-radius: 50%; background: var(--lime); margin-top: 4px; flex: none; }
    .dot.internal { background: var(--ink); }
    .body { flex: 1; min-width: 0; display: grid; gap: 2px; }
  `,
})
export class TimelineCard {
  private readonly api = inject(ClaimsApi);

  readonly claimId = input.required<string>();
  readonly claimVersion = input.required<number>();
  readonly staffView = input(false);

  protected readonly entries = signal<TimelineEntry[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly reversed = computed(() => [...(this.entries() ?? [])].reverse());

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.problem.set(null);
    this.api.timeline(this.claimId()).subscribe({
      next: (list) => {
        this.entries.set(list);
        this.loading.set(false);
      },
      error: (e) => {
        this.problem.set(toProblem(e));
        this.loading.set(false);
      },
    });
  }
}
