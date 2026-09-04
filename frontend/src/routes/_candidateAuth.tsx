/**
 * Candidate layout route — gates every nested route behind an
 * authenticated candidate session. See docs/specs/0010.
 */

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { getSessionRole } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth')({
  beforeLoad: async ({ preload }) => {
    const { session, role } = await getSessionRole();
    // `defaultPreload: 'intent'` (main.tsx) runs this on link hover, ahead of
    // a real click — don't let a hover-triggered check redirect the page out
    // from under the user; only enforce on actual navigations.
    if (preload) return;
    if (!session) {
      throw redirect({ to: '/candidate/login' });
    }
    if (role !== 'candidate') {
      throw redirect({ to: '/login' });
    }
  },
  component: () => <Outlet />,
});
