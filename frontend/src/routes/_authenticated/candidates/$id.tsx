/**
 * Candidate Scorecard Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateScorecardPage from '@/pages/candidate-scorecard.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/_authenticated/candidates/$id')({
  component: CandidateScorecardPage,
});

function CandidateScorecardPage() {
  const { id } = Route.useParams();
  return (
    <UIEngine
      page={candidateScorecardPage as PageDefinition}
      params={{ id }}
    />
  );
}
