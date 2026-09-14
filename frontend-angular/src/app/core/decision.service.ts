import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { InterviewRow } from './models';

@Injectable({ providedIn: 'root' })
export class DecisionService {
  private readonly api = inject(ApiService);

  record(candidateId: string, decision: 'shortlisted' | 'rejected' | 'interview_scheduled', scheduledAt?: string, notes?: string) {
    return this.api.post<{ id: string }>(`/candidates/${candidateId}/decisions`, { decision, scheduledAt, notes });
  }

  listInterviews() {
    return this.api.get<InterviewRow[]>('/interviews');
  }
}
