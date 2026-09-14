import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CandidateService } from '../../core/candidate.service';
import { CandidateSummary } from '../../core/models';

@Component({
  selector: 'app-candidate-range',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './candidate-range.html',
})
export class CandidateRange implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly candidateService = inject(CandidateService);

  min = 0;
  max = 100;
  candidates = signal<CandidateSummary[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.min = Number(this.route.snapshot.paramMap.get('min'));
    this.max = Number(this.route.snapshot.paramMap.get('max'));
    this.candidateService.range(this.min, this.max).subscribe((candidates) => {
      this.candidates.set(candidates);
      this.loading.set(false);
    });
  }
}
