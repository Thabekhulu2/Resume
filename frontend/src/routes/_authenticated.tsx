/**
 * Recruitment Team layout route — gates every nested route behind an
 * authenticated recruiter session. See docs/specs/0008.
 */

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { getSessionRole } from '@/lib/auth';

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async ({ preload }) => {
    const { session, role } = await getSessionRole();
    // `defaultPreload: 'intent'` (main.tsx) runs this on link hover, ahead of
    // a real click — don't let a hover-triggered check redirect the page out
    // from under the user; only enforce on actual navigations.
    if (preload) return;
    if (!session) {
      throw redirect({ to: '/login' });
    }
    if (role !== 'recruiter') {
      throw redirect({ to: '/candidate/login' });
    }
  },
  component: () => <Outlet />,
});
