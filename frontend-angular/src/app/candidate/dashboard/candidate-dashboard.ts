import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-candidate-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './candidate-dashboard.html',
})
export class CandidateDashboard {
  readonly auth = inject(AuthService);
}
