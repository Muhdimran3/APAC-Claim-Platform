import { Injectable, computed, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { Me } from './api.models';
import { ClaimsApi } from './claims-api';
import { CurrentUser } from './current-user';
import { Problem, toProblem } from './problem';

type MeState = { loading: true } | { loading: false; me: Me } | { loading: false; problem: Problem };

/**
 * Who the backend says the selected user is. Reloads whenever the selection changes, and
 * switchMap drops a slow response for the previous user. The role used for showing or
 * hiding UI comes only from here, and the backend still enforces every rule.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(ClaimsApi);
  readonly currentUser = inject(CurrentUser);

  private readonly state = signal<MeState>({ loading: true });

  readonly me = computed(() => {
    const s = this.state();
    return 'me' in s ? s.me : null;
  });
  readonly problem = computed(() => {
    const s = this.state();
    return 'problem' in s ? s.problem : null;
  });
  readonly loading = computed(() => this.state().loading);
  readonly role = computed(() => this.me()?.role ?? null);
  readonly isClaimant = computed(() => this.role() === 'CLAIMANT');
  readonly isStaff = computed(() => this.role() === 'OFFICER' || this.role() === 'MANAGER');
  readonly isManager = computed(() => this.role() === 'MANAGER');

  constructor() {
    toObservable(this.currentUser.username)
      .pipe(
        switchMap(() => {
          this.state.set({ loading: true });
          return this.api.me().pipe(
            map((me): MeState => ({ loading: false, me })),
            catchError((e) => of<MeState>({ loading: false, problem: toProblem(e) })),
          );
        }),
      )
      .subscribe((s) => this.state.set(s));
  }
}
