import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { CandidateScorecard, CandidateSummary, ScoreBandCounts } from './models';

@Injectable({ providedIn: 'root' })
export class CandidateService {
  private readonly api = inject(ApiService);

  list() {
    return this.api.get<CandidateSummary[]>('/candidates');
  }

  get(id: string) {
    return this.api.get<CandidateScorecard>(`/candidates/${id}`);
  }

  delete(id: string) {
    return this.api.delete<void>(`/candidates/${id}`);
  }

  deleteMany(ids: string[]) {
    return this.api.delete<void>('/candidates', { ids });
  }

  range(min: number, max: number) {
    return this.api.get<CandidateSummary[]>(`/candidates/range?min=${min}&max=${max}`);
  }

  scoreBands() {
    return this.api.get<ScoreBandCounts>('/dashboard/score-bands');
  }
}
