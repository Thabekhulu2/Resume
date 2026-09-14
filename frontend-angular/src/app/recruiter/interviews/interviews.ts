import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecisionService } from '../../core/decision.service';
import { InterviewRow } from '../../core/models';

@Component({
  selector: 'app-interviews',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './interviews.html',
})
export class Interviews implements OnInit {
  private readonly decisionService = inject(DecisionService);

  interviews = signal<InterviewRow[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.decisionService.listInterviews().subscribe((interviews) => {
      this.interviews.set(interviews);
      this.loading.set(false);
    });
  }
}
