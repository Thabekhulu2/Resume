import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { JobService } from '../../core/job.service';
import { JobSummary } from '../../core/models';

@Component({
  selector: 'app-jobs',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './jobs.html',
})
export class Jobs implements OnInit {
  private readonly jobService = inject(JobService);

  jobs = signal<JobSummary[]>([]);
  statusFilter = signal<'open' | 'closed' | 'all'>('open');
  loading = signal(true);
  showCreateForm = signal(false);

  title = '';
  jdText = '';
  location = '';
  creating = signal(false);

  ngOnInit(): void {
    this.load();
  }

  setFilter(status: 'open' | 'closed' | 'all'): void {
    this.statusFilter.set(status);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.jobService.list(this.statusFilter()).subscribe((jobs) => {
      this.jobs.set(jobs);
      this.loading.set(false);
    });
  }

  createJob(): void {
    this.creating.set(true);
    this.jobService.create(this.title, this.jdText, this.location).subscribe(() => {
      this.creating.set(false);
      this.showCreateForm.set(false);
      this.title = '';
      this.jdText = '';
      this.location = '';
      this.load();
    });
  }

  toggleStatus(job: JobSummary): void {
    const nextStatus = job.status === 'open' ? 'closed' : 'open';
    this.jobService.updateStatus(job.id, nextStatus).subscribe(() => this.load());
  }
}
