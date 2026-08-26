/**
 * Candidate Upload Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateUploadPage from '@/pages/candidate-upload.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/candidates/upload')({
  component: CandidateUploadPage,
});

function CandidateUploadPage() {
  return <UIEngine page={candidateUploadPage as PageDefinition} />;
}
