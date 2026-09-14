import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApplicationService } from '../../core/application.service';
import { ApplicationRow } from '../../core/models';

@Component({
  selector: 'app-candidate-applications',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './candidate-applications.html',
})
export class CandidateApplications implements OnInit {
  private readonly applicationService = inject(ApplicationService);

  applications = signal<ApplicationRow[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.applicationService.mine().subscribe((rows) => {
      this.applications.set(rows);
      this.loading.set(false);
    });
  }
}
