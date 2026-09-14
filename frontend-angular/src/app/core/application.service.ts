import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ApplicationRow } from './models';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly api = inject(ApiService);

  byJob(jobId: string) {
    return this.api.get<ApplicationRow[]>(`/jobs/${jobId}/applications`);
  }

  mine() {
    return this.api.get<ApplicationRow[]>('/applications/mine');
  }

  myResumeForJob(jobId: string) {
    return this.api.get<{ resumeStoragePath: string }>(`/applications/mine/resume?jobId=${jobId}`);
  }
}
