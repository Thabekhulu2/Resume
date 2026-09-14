import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApplicationService } from '../../core/application.service';
import { JobService } from '../../core/job.service';
import { ScoringService } from '../../core/scoring.service';
import { ApplicationRow } from '../../core/models';

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './applications.html',
})
export class Applications implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly applicationService = inject(ApplicationService);
  private readonly jobService = inject(JobService);
  private readonly scoringService = inject(ScoringService);

  jobId = '';
  jobTitle = signal<string>('');
  applications = signal<ApplicationRow[]>([]);
  loading = signal(true);
  rescoring = signal<string | null>(null);

  ngOnInit(): void {
    this.jobId = this.route.snapshot.paramMap.get('jobId')!;
    this.jobService.get(this.jobId).subscribe((job) => this.jobTitle.set((job['title'] as string) ?? ''));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.applicationService.byJob(this.jobId).subscribe((rows) => {
      this.applications.set(rows);
      this.loading.set(false);
    });
  }

  rescore(row: ApplicationRow): void {
    this.rescoring.set(row.candidateId);
    this.scoringService
      .start({
        candidateEntityId: row.candidateId,
        resumeStoragePath: row.resumeFilePath,
        jobDescriptionEntityId: this.jobId,
      })
      .subscribe(() => {
        this.rescoring.set(null);
        this.load();
      });
  }
}
