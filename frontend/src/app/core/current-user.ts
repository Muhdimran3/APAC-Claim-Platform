import { Injectable, signal } from '@angular/core';

/**
 * Demo identities: the usernames seeded by the backend's V4 migration. The UI only stores
 * which username is selected. Role and display name always come from GET /api/me.
 */
export const DEMO_USERS = [
  { username: 'alice', hint: 'Claimant' },
  { username: 'ben', hint: 'Claimant' },
  { username: 'olivia', hint: 'Officer' },
  { username: 'oscar', hint: 'Officer' },
  { username: 'maya', hint: 'Manager' },
  { username: 'mallory', hint: 'Unknown user (401)' },
] as const;

const STORAGE_KEY = 'claims-workbench.x-user';

/**
 * The username sent as X-User. Has no dependencies on purpose: the HTTP interceptor reads
 * it, so it must not depend on HttpClient itself (that would be a DI cycle).
 */
@Injectable({ providedIn: 'root' })
export class CurrentUser {
  readonly username = signal<string>(readStored() ?? 'alice');

  select(username: string): void {
    this.username.set(username);
    try {
      localStorage.setItem(STORAGE_KEY, username);
    } catch {
      // storage unavailable (private mode): selection just isn't remembered
    }
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
