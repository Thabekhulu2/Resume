import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CandidateService } from '../../core/candidate.service';
import { ScoreBandCounts } from '../../core/models';

@Component({
  selector: 'app-recruiter-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly candidateService = inject(CandidateService);

  bands = signal<ScoreBandCounts | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    this.candidateService.scoreBands().subscribe((bands) => {
      this.bands.set(bands);
      this.loading.set(false);
    });
  }
}
