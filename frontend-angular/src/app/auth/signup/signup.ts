import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './signup.html',
})
export class Signup {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  fullName = '';
  email = '';
  password = '';
  error = signal<string | null>(null);
  submitting = signal(false);

  submit(): void {
    this.error.set(null);
    this.submitting.set(true);
    this.auth.signupCandidate(this.email, this.password, this.fullName).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl('/candidate/jobs');
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err.status === 409 ? 'An account with this email already exists.' : 'Sign up failed.');
      },
    });
  }
}
