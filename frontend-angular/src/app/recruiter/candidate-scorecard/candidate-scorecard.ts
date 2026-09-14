import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CandidateService } from '../../core/candidate.service';
import { DecisionService } from '../../core/decision.service';
import { CandidateScorecard as CandidateScorecardModel } from '../../core/models';

@Component({
  selector: 'app-candidate-scorecard',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './candidate-scorecard.html',
})
export class CandidateScorecard implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly candidateService = inject(CandidateService);
  private readonly decisionService = inject(DecisionService);

  candidateId = '';
  candidate = signal<CandidateScorecardModel | null>(null);
  loading = signal(true);
  actionInProgress = signal(false);
  showInterviewModal = signal(false);

  scheduledAt = '';
  notes = '';

  ngOnInit(): void {
    this.candidateId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.candidateService.get(this.candidateId).subscribe((candidate) => {
      this.candidate.set(candidate);
      this.loading.set(false);
    });
  }

  shortlist(): void {
    this.actionInProgress.set(true);
    this.decisionService.record(this.candidateId, 'shortlisted').subscribe(() => {
      this.actionInProgress.set(false);
      this.load();
    });
  }

  reject(): void {
    this.actionInProgress.set(true);
    this.decisionService.record(this.candidateId, 'rejected').subscribe(() => {
      this.actionInProgress.set(false);
      this.load();
    });
  }

  scheduleInterview(): void {
    if (!this.scheduledAt) {
      return;
    }
    this.actionInProgress.set(true);
    const isoScheduledAt = new Date(this.scheduledAt).toISOString();
    this.decisionService.record(this.candidateId, 'interview_scheduled', isoScheduledAt, this.notes).subscribe(() => {
      this.actionInProgress.set(false);
      this.showInterviewModal.set(false);
      this.scheduledAt = '';
      this.notes = '';
      this.load();
    });
  }
}
