import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CandidateService } from '../../core/candidate.service';
import { CandidateSummary } from '../../core/models';

@Component({
  selector: 'app-candidate-history',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './candidate-history.html',
})
export class CandidateHistory implements OnInit {
  private readonly candidateService = inject(CandidateService);

  candidates = signal<CandidateSummary[]>([]);
  loading = signal(true);
  selected = signal<Set<string>>(new Set());

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.candidateService.list().subscribe((candidates) => {
      this.candidates.set(candidates);
      this.loading.set(false);
    });
  }

  toggleSelected(id: string): void {
    const next = new Set(this.selected());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selected.set(next);
  }

  deleteOne(id: string): void {
    this.candidateService.delete(id).subscribe(() => this.load());
  }

  deleteSelected(): void {
    const ids = Array.from(this.selected());
    if (ids.length === 0) {
      return;
    }
    this.candidateService.deleteMany(ids).subscribe(() => {
      this.selected.set(new Set());
      this.load();
    });
  }
}
