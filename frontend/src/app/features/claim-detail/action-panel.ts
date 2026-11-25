import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ClaimDetail, Me, Officer } from '../../core/api.models';
import { ClaimsApi } from '../../core/claims-api';
import { isOpen } from '../../core/format';
import { Problem, toProblem } from '../../core/problem';
import { MoneyPipe } from '../../shared/pipes';
import { ProblemAlert } from '../../shared/problem-alert';

export type PanelKey =
  | 'assign' | 'reassign' | 'start' | 'requestInfo' | 'respond' | 'reserve' | 'decide' | 'settle' | 'note';

export interface ActionButton {
  key: PanelKey;
  label: string;
  /** true = runs straight away (no inputs needed) */
  instant: boolean;
}

/**
 * The buttons a caller may press on a claim right now.
 * Status-changing actions come only from the server's availableActions (ownership and
 * assignment already applied). Commands that don't change status (reserve, note,
 * reassign) aren't in that list, so they're derived from the documented rules:
 * a manager on any claim, an officer only on their own. The server enforces them anyway.
 */
export function buttonsFor(claim: ClaimDetail, me: Me): ActionButton[] {
  const a = new Set(claim.availableActions);
  const out: ActionButton[] = [];
  if (a.has('ASSIGN')) {
    out.push(me.role === 'OFFICER'
      ? { key: 'assign', label: 'Pick up claim', instant: true }
      : { key: 'assign', label: 'Assign', instant: false });
  }
  if (a.has('START_ASSESSMENT')) out.push({ key: 'start', label: 'Start assessment', instant: true });
  if (a.has('PROVIDE_INFO')) out.push({ key: 'respond', label: 'Respond', instant: false });
  if (a.has('REQUEST_INFO')) out.push({ key: 'requestInfo', label: 'Request info', instant: false });

  const staffCanAct = me.role === 'MANAGER' || (me.role === 'OFFICER' && claim.assignedOfficer?.id === me.id);
  if (staffCanAct && isOpen(claim.status)) out.push({ key: 'reserve', label: 'Set reserve', instant: false });
  if (a.has('APPROVE') || a.has('REJECT')) out.push({ key: 'decide', label: 'Decide', instant: false });
  if (a.has('SETTLE')) out.push({ key: 'settle', label: 'Settle', instant: false });
  if (me.role === 'MANAGER' && claim.assignedOfficer && claim.status !== 'SUBMITTED' && isOpen(claim.status)) {
    out.push({ key: 'reassign', label: 'Reassign', instant: false });
  }
  if (staffCanAct) out.push({ key: 'note', label: 'Add note', instant: false });
  return out;
}

@Component({
  selector: 'app-action-panel',
  imports: [FormsModule, ProblemAlert, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './action-panel.html',
  styleUrl: './action-panel.css',
})
export class ActionPanel {
  private readonly api = inject(ClaimsApi);

  readonly claim = input.required<ClaimDetail>();
  readonly me = input.required<Me>();
  readonly officers = input<Officer[]>([]);
  readonly updated = output<ClaimDetail>();

  protected readonly buttons = computed(() => buttonsFor(this.claim(), this.me()));
  protected readonly active = signal<PanelKey | null>(null);
  protected readonly busy = signal(false);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly flash = signal<string | null>(null);

  // inline form fields
  protected readonly officerId = signal('');
  protected readonly text = signal('');
  protected readonly amount = signal<number | null>(null);
  protected readonly reason = signal('');
  protected readonly outcome = signal<'APPROVED' | 'REJECTED'>('APPROVED');

  protected readonly canApprove = computed(() => this.claim().availableActions.includes('APPROVE'));
  protected readonly canReject = computed(() => this.claim().availableActions.includes('REJECT'));
  protected readonly openRequest = computed(() => this.claim().infoRequests.find((r) => r.open) ?? null);
  protected readonly assignableOfficers = computed(() =>
    this.officers().filter((o) => o.id !== this.claim().assignedOfficer?.id),
  );

  protected press(b: ActionButton): void {
    this.problem.set(null);
    this.flash.set(null);
    if (b.instant) {
      this.execute(b.key);
      return;
    }
    if (this.active() === b.key) {
      this.active.set(null);
      return;
    }
    this.resetFields(b.key);
    this.active.set(b.key);
  }

  protected execute(key: PanelKey): void {
    const c = this.claim();
    const commands: Record<PanelKey, () => Observable<ClaimDetail>> = {
      assign: () => this.api.assign(c.id, this.me().role === 'OFFICER' ? undefined : this.officerId()),
      reassign: () => this.api.assign(c.id, this.officerId()),
      start: () => this.api.startAssessment(c.id),
      requestInfo: () => this.api.requestInfo(c.id, this.text()),
      respond: () => this.api.respond(c.id, this.openRequest()?.id ?? '', this.text()),
      reserve: () => this.api.setReserve(c.id, Number(this.amount()), this.reason()),
      decide: () => this.api.decide(c.id, {
        outcome: this.outcome(),
        amount: this.outcome() === 'APPROVED' ? Number(this.amount()) : undefined,
        reason: this.reason(),
      }),
      settle: () => this.api.settle(c.id, Number(this.amount())),
      note: () => this.api.addNote(c.id, this.text()),
    };
    this.busy.set(true);
    this.problem.set(null);
    commands[key]().subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.active.set(null);
        this.flash.set(`Done. Claim is now at version ${updated.version}.`);
        this.updated.emit(updated);
      },
      error: (e) => {
        this.busy.set(false);
        this.problem.set(toProblem(e));
      },
    });
  }

  private resetFields(key: PanelKey): void {
    const c = this.claim();
    this.text.set('');
    this.reason.set('');
    this.officerId.set(this.assignableOfficers()[0]?.id ?? '');
    this.outcome.set(this.canApprove() ? 'APPROVED' : 'REJECTED');
    this.amount.set(
      key === 'settle' ? (c.decision?.approvedAmount ?? null)
        : key === 'reserve' ? (c.reserveAmount ?? null)
          : key === 'decide' ? (c.reserveAmount || c.claimedAmount)
            : null,
    );
  }
}
