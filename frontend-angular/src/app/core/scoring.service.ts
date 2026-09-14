import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ScoringStartResponse } from './models';

export interface StartScoringRequest {
  resumeStoragePath?: string;
  resumeStoragePaths?: string[];
  jobDescriptionEntityId?: string;
  jdText?: string;
  jobTitle?: string;
  candidateEntityId?: string;
  applicantId?: string;
  createdBy?: string;
}

@Injectable({ providedIn: 'root' })
export class ScoringService {
  private readonly api = inject(ApiService);

  start(request: StartScoringRequest) {
    return this.api.post<ScoringStartResponse>('/scoring/start', request);
  }
}
