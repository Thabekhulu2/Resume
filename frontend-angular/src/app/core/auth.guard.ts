import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const recruiterGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.role() === 'RECRUITER') {
    return true;
  }
  return router.parseUrl('/login');
};

export const candidateGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.role() === 'CANDIDATE') {
    return true;
  }
  return router.parseUrl('/login');
};
