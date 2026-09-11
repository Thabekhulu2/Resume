/**
 * Candidate Saved Jobs Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateSavedJobsPage from '@/pages/candidate-saved-jobs.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/saved-jobs/')({
  component: SavedJobsPage,
});

function SavedJobsPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateSavedJobsPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
