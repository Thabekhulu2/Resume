import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ApiService } from './api.service';
import { AuthResponse, Role } from './models';

const STORAGE_KEY = 'resume.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly authState = signal<AuthResponse | null>(this.readStoredAuth());

  readonly currentUser = computed(() => this.authState());
  readonly isAuthenticated = computed(() => this.authState() !== null);
  readonly role = computed<Role | null>(() => this.authState()?.role ?? null);

  private readStoredAuth(): AuthResponse | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthResponse;
    } catch {
      return null;
    }
  }

  get token(): string | null {
    return this.authState()?.token ?? null;
  }

  loginRecruiter(email: string, password: string): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/recruiter/login', { email, password }).pipe(
      tap((response) => this.setAuth(response)),
    );
  }

  loginCandidate(email: string, password: string): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/candidate/login', { email, password }).pipe(
      tap((response) => this.setAuth(response)),
    );
  }

  signupCandidate(email: string, password: string, fullName: string): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/candidate/signup', { email, password, fullName }).pipe(
      tap((response) => this.setAuth(response)),
    );
  }

  private setAuth(response: AuthResponse): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(response));
    this.authState.set(response);
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.authState.set(null);
    this.router.navigateByUrl('/login');
  }
}
