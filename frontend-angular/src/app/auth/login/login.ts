import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Role } from '../../core/models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  role = signal<Role>('RECRUITER');
  email = '';
  password = '';
  error = signal<string | null>(null);
  submitting = signal(false);

  setRole(role: Role): void {
    this.role.set(role);
    this.error.set(null);
  }

  submit(): void {
    this.error.set(null);
    this.submitting.set(true);
    const login$ = this.role() === 'RECRUITER'
      ? this.auth.loginRecruiter(this.email, this.password)
      : this.auth.loginCandidate(this.email, this.password);

    login$.subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.role() === 'RECRUITER' ? '/dashboard' : '/candidate/jobs');
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Invalid email or password.');
      },
    });
  }
}
