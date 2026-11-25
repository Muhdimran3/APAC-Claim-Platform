import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DEMO_USERS } from './core/current-user';
import { Session } from './core/session';
import { ProblemAlert } from './shared/problem-alert';

interface NavTab {
  label: string;
  path: string;
  exact: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ProblemAlert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly session = inject(Session);
  protected readonly users = DEMO_USERS;
  protected readonly username = this.session.currentUser.username;

  /** Tabs follow the role the backend resolved, not the button that was clicked. */
  protected readonly tabs = computed<NavTab[]>(() => {
    switch (this.session.role()) {
      case 'CLAIMANT':
        return [
          { label: 'My claims', path: '/claims', exact: true },
          { label: 'Report an incident', path: '/claims/new', exact: true },
        ];
      case 'OFFICER':
        return [
          { label: 'Claims', path: '/claims', exact: true },
          { label: 'My workload', path: '/dashboard', exact: true },
        ];
      case 'MANAGER':
        return [
          { label: 'Claims', path: '/claims', exact: true },
          { label: 'Workload & exposure', path: '/dashboard', exact: true },
        ];
      default:
        return [];
    }
  });

  protected select(username: string): void {
    this.session.currentUser.select(username);
  }
}
