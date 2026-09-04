/**
 * Candidate layout route — gates every nested route behind an
 * authenticated candidate session. See docs/specs/0010.
 */

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { getSessionRole } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth')({
  beforeLoad: async () => {
    const { session, role } = await getSessionRole();
    if (!session) {
      throw redirect({ to: '/candidate/login' });
    }
    if (role !== 'candidate') {
      throw redirect({ to: '/login' });
    }
  },
  component: () => <Outlet />,
});
