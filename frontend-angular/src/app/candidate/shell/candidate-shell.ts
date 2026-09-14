import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-candidate-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './candidate-shell.html',
})
export class CandidateShell {
  readonly auth = inject(AuthService);

  logout(): void {
    this.auth.logout();
  }
}
