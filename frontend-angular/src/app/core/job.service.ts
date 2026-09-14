import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { JobSummary, OpenJob } from './models';

@Injectable({ providedIn: 'root' })
export class JobService {
  private readonly api = inject(ApiService);

  list(status: 'open' | 'closed' | 'all' = 'all') {
    return this.api.get<JobSummary[]>(`/jobs?status=${status}`);
  }

  get(id: string) {
    return this.api.get<Record<string, unknown>>(`/jobs/${id}`);
  }

  create(title: string, jdText: string, location: string) {
    return this.api.post<{ id: string }>('/jobs', { title, jdText, location });
  }

  updateStatus(id: string, status: 'open' | 'closed') {
    return this.api.patch<{ id: string }>(`/jobs/${id}/status`, { status });
  }

  listOpen() {
    return this.api.get<OpenJob[]>('/jobs/open');
  }
}
