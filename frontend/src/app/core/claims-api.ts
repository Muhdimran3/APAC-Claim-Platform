import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ClaimDetail,
  ClaimStatus,
  ClaimSummary,
  DecideBody,
  ExposureDashboard,
  Me,
  Officer,
  Policy,
  SubmitClaimBody,
  TimelineEntry,
  WorkloadDashboard,
} from './api.models';

export type AssigneeFilter = 'me' | 'unassigned' | 'all';

/** Thin typed client over the REST API. Relative URLs go through the dev proxy to :8080. */
@Injectable({ providedIn: 'root' })
export class ClaimsApi {
  private readonly http = inject(HttpClient);

  me(): Observable<Me> {
    return this.http.get<Me>('/api/me');
  }

  officers(): Observable<Officer[]> {
    return this.http.get<Officer[]>('/api/users/officers');
  }

  policies(): Observable<Policy[]> {
    return this.http.get<Policy[]>('/api/policies');
  }

  listClaims(filter: { status?: ClaimStatus | null; assignee?: AssigneeFilter | null }): Observable<ClaimSummary[]> {
    let params = new HttpParams();
    if (filter.status) params = params.set('status', filter.status);
    if (filter.assignee) params = params.set('assignee', filter.assignee);
    return this.http.get<ClaimSummary[]>('/api/claims', { params });
  }

  claim(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`/api/claims/${encodeURIComponent(id)}`);
  }

  timeline(id: string): Observable<TimelineEntry[]> {
    return this.http.get<TimelineEntry[]>(`/api/claims/${encodeURIComponent(id)}/timeline`);
  }

  workload(): Observable<WorkloadDashboard> {
    return this.http.get<WorkloadDashboard>('/api/dashboard/workload');
  }

  exposure(): Observable<ExposureDashboard> {
    return this.http.get<ExposureDashboard>('/api/dashboard/exposure');
  }

  // ---- commands: each returns the updated claim (including the new availableActions) ----

  submit(body: SubmitClaimBody): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>('/api/claims', body);
  }

  /** officerId omitted = officer picks the claim up themselves. */
  assign(id: string, officerId?: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.url(id, 'assign'), officerId ? { officerId } : {});
  }

  startAssessment(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.url(id, 'start-assessment'), {});
  }

  requestInfo(id: string, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.url(id, 'info-requests'), { question });
  }

  respond(id: string, requestId: string, response: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(
      this.url(id, `info-requests/${encodeURIComponent(requestId)}/response`),
      { response },
    );
  }

  setReserve(id: string, amount: number, reason: string): Observable<ClaimDetail> {
    return this.http.put<ClaimDetail>(this.url(id, 'reserve'), { amount, reason });
  }

  decide(id: string, body: DecideBody): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.url(id, 'decision'), body);
  }

  settle(id: string, amount: number): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.url(id, 'settlement'), { amount });
  }

  addNote(id: string, body: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.url(id, 'notes'), { body });
  }

  private url(id: string, path: string): string {
    return `/api/claims/${encodeURIComponent(id)}/${path}`;
  }
}
