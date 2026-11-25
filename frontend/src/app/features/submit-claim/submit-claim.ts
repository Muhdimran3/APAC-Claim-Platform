import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Policy } from '../../core/api.models';
import { ClaimsApi } from '../../core/claims-api';
import { Problem, toProblem } from '../../core/problem';
import { Session } from '../../core/session';
import { DayPipe, MoneyPipe } from '../../shared/pipes';
import { ProblemAlert } from '../../shared/problem-alert';

@Component({
  selector: 'app-submit-claim',
  imports: [ReactiveFormsModule, ProblemAlert, MoneyPipe, DayPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './submit-claim.html',
  styleUrl: './submit-claim.css',
})
export class SubmitClaim {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  protected readonly session = inject(Session);

  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly policies = signal<Policy[] | null>(null);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly submitting = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    policyId: ['', Validators.required],
    incidentDate: ['', Validators.required],
    description: ['', [Validators.required, Validators.maxLength(4000)]],
    claimedAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
  });

  private readonly policyId = toSignal(this.form.controls.policyId.valueChanges, { initialValue: '' });
  protected readonly selected = computed(() => this.policies()?.find((p) => p.id === this.policyId()) ?? null);

  constructor() {
    this.api.policies().subscribe({
      next: (list) => {
        this.policies.set(list);
        if (list.length === 1) this.form.controls.policyId.setValue(list[0].id);
      },
      error: (e) => this.problem.set(toProblem(e)),
    });
  }

  /** A 400 error the server reported for this field, shown under the input. */
  protected serverError(field: string): string | null {
    return this.problem()?.errors[field] ?? null;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.problem.set(null);
    this.api
      .submit({
        policyId: v.policyId,
        incidentDate: v.incidentDate,
        description: v.description,
        claimedAmount: Number(v.claimedAmount),
      })
      .subscribe({
        next: (claim) => this.router.navigate(['/claims', claim.id]),
        error: (e) => {
          this.problem.set(toProblem(e));
          this.submitting.set(false);
        },
      });
  }
}
