import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { Problem, problemHint } from '../core/problem';

/** Renders an RFC 7807 problem: status + title, detail, a hint, and any 400 field errors. */
@Component({
  selector: 'app-problem-alert',
  imports: [KeyValuePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (problem(); as p) {
      <div class="problem" role="alert" [attr.data-status]="p.status">
        <div class="spread">
          <strong class="num">{{ p.status > 0 ? p.status + ' ' : '' }}{{ p.title }}</strong>
          @if (dismissible()) {
            <button type="button" class="link" (click)="dismissed.emit()">Dismiss</button>
          }
        </div>
        <div>{{ p.detail }}</div>
        @if (hint()) {
          <div class="hint">{{ hint() }}</div>
        }
        @if (hasFieldErrors()) {
          <ul>
            @for (e of p.errors | keyvalue; track e.key) {
              <li><code>{{ e.key }}</code> {{ e.value }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: `
    .problem { border-radius: 16px; padding: 12px 14px; background: #fff4f3; border: 1px solid #f6c9c5; color: #5c1a14; display: grid; gap: 4px; font-size: 14px; }
    .problem[data-status='409'], .problem[data-status='422'] { background: #fff8e6; border-color: #f3dc9b; color: #5a4300; }
    .problem[data-status='401'] { background: var(--accent-soft); border-color: #b9c8ff; color: #142c7a; }
    .hint { font-size: 12px; opacity: 0.8; }
    ul { margin: 4px 0 0; padding-left: 18px; }
    code { font-family: var(--mono); font-size: 12px; }
    .link { border: 0; background: none; color: inherit; opacity: 0.7; cursor: pointer; font: inherit; font-size: 12px; }
  `,
})
export class ProblemAlert {
  readonly problem = input<Problem | null>(null);
  readonly dismissible = input(false);
  readonly dismissed = output<void>();

  protected readonly hint = computed(() => {
    const p = this.problem();
    return p ? problemHint(p.status) : '';
  });
  protected readonly hasFieldErrors = computed(() => Object.keys(this.problem()?.errors ?? {}).length > 0);
}
