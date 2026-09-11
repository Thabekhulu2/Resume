/**
 * Candidate My Applications Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateApplicationsPage from '@/pages/candidate-applications.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/my-applications/')({
  component: MyApplicationsPage,
});

function MyApplicationsPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateApplicationsPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
