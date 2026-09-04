/**
 * Candidate Apply Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateJobsPage from '@/pages/candidate-jobs.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/apply/')({
  component: ApplyPage,
});

function ApplyPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateJobsPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
