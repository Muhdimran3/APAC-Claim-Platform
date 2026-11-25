import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../core/api.models';
import { STATUS_LABEL } from '../core/format';

/** Colour by who has the ball: lime = waiting on the claimant, cobalt = being assessed, dark = decided. */
const STATUS_STYLE: Record<ClaimStatus, { bg: string; fg: string }> = {
  SUBMITTED: { bg: '#eef0f4', fg: '#3a3f4b' },
  ASSIGNED: { bg: '#dce4ff', fg: '#1e45c9' },
  UNDER_ASSESSMENT: { bg: '#2356f6', fg: '#ffffff' },
  AWAITING_INFO: { bg: '#d8f24b', fg: '#111318' },
  APPROVED: { bg: '#111318', fg: '#ffffff' },
  SETTLED: { bg: '#e4e5e9', fg: '#6b6f7a' },
  REJECTED: { bg: '#fde2e1', fg: '#b42318' },
};

@Component({
  selector: 'app-status-pill',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="pill" [style.background]="style().bg" [style.color]="style().fg">{{ label() }}</span>`,
})
export class StatusPill {
  readonly status = input.required<ClaimStatus>();
  protected readonly style = computed(() => STATUS_STYLE[this.status()]);
  protected readonly label = computed(() => STATUS_LABEL[this.status()]);
}
