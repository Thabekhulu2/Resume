import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { ApplicationService } from '../../core/application.service';
import { JobService } from '../../core/job.service';
import { ResumeService } from '../../core/resume.service';
import { ScoringService } from '../../core/scoring.service';
import { OpenJob } from '../../core/models';

@Component({
  selector: 'app-candidate-jobs',
  standalone: true,
  imports: [],
  templateUrl: './candidate-jobs.html',
})
export class CandidateJobs implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly jobService = inject(JobService);
  private readonly applicationService = inject(ApplicationService);
  private readonly resumeService = inject(ResumeService);
  private readonly scoringService = inject(ScoringService);

  jobs = signal<OpenJob[]>([]);
  loading = signal(true);

  applyingJob = signal<OpenJob | null>(null);
  selectedFile: File | null = null;
  applying = signal(false);
  applyError = signal<string | null>(null);

  viewingResumeForJob = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.jobService.listOpen().subscribe((jobs) => {
      this.jobs.set(jobs);
      this.loading.set(false);
    });
  }

  openApply(job: OpenJob): void {
    this.applyingJob.set(job);
    this.selectedFile = null;
    this.applyError.set(null);
  }

  closeApply(): void {
    this.applyingJob.set(null);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  submitApplication(): void {
    const job = this.applyingJob();
    const file = this.selectedFile;
    const candidateId = this.auth.currentUser()?.id;
    if (!job || !file || !candidateId) {
      return;
    }

    this.applying.set(true);
    this.applyError.set(null);
    this.resumeService.upload(file).subscribe({
      next: (uploaded) => {
        this.scoringService
          .start({
            resumeStoragePath: uploaded.id,
            jobDescriptionEntityId: job.id,
            applicantId: candidateId,
            createdBy: candidateId,
          })
          .subscribe({
            next: () => {
              this.applying.set(false);
              this.applyingJob.set(null);
              this.load();
            },
            error: () => {
              this.applying.set(false);
              this.applyError.set('Failed to submit your application. Please try again.');
            },
          });
      },
      error: () => {
        this.applying.set(false);
        this.applyError.set('Failed to upload your resume. Please try again.');
      },
    });
  }

  viewMyResume(job: OpenJob): void {
    this.viewingResumeForJob.set(job.id);
    this.applicationService.myResumeForJob(job.id).subscribe({
      next: (resp) => {
        this.resumeService.downloadBlob(resp.resumeStoragePath).subscribe({
          next: (blob) => {
            this.viewingResumeForJob.set(null);
            const url = URL.createObjectURL(blob);
            window.open(url, '_blank');
          },
          error: () => this.viewingResumeForJob.set(null),
        });
      },
      error: () => this.viewingResumeForJob.set(null),
    });
  }
}
