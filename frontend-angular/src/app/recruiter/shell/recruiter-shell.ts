import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-recruiter-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './recruiter-shell.html',
})
export class RecruiterShell {
  readonly auth = inject(AuthService);

  logout(): void {
    this.auth.logout();
  }
}
