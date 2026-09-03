/**
 * Recruitment Team layout route — gates every nested route behind an
 * authenticated recruiter session. See docs/specs/0008.
 */

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { getSessionRole } from '@/lib/auth';

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async () => {
    const { session, role } = await getSessionRole();
    if (!session) {
      throw redirect({ to: '/login' });
    }
    if (role !== 'recruiter') {
      throw redirect({ to: '/candidate/login' });
    }
  },
  component: () => <Outlet />,
});
