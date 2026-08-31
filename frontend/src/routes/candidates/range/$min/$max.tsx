/**
 * Candidates by Fit-Score Range Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateRangePage from '@/pages/candidate-range.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/candidates/range/$min/$max')({
  component: CandidateRangePage,
});

function CandidateRangePage() {
  const { min, max } = Route.useParams();
  return (
    <UIEngine
      page={candidateRangePage as PageDefinition}
      params={{ min, max }}
    />
  );
}
