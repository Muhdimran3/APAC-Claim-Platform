import { Routes } from '@angular/router';

// Lazy-loaded pages. There are no client-side role guards: the API enforces roles and the
// page renders its 403/404 problem, so the UI can't drift from the server's rules.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'claims' },
  { path: 'claims', loadComponent: () => import('./features/claims-list/claims-list').then((m) => m.ClaimsList) },
  { path: 'claims/new', loadComponent: () => import('./features/submit-claim/submit-claim').then((m) => m.SubmitClaim) },
  { path: 'claims/:id', loadComponent: () => import('./features/claim-detail/claim-detail').then((m) => m.ClaimDetailPage) },
  { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard) },
  { path: '**', redirectTo: 'claims' },
];
