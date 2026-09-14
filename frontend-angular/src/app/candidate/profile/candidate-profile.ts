import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-candidate-profile',
  standalone: true,
  templateUrl: './candidate-profile.html',
})
export class CandidateProfile {
  readonly auth = inject(AuthService);
}
