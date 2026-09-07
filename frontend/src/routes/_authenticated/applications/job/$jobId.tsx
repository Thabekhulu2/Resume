/**
 * Applications for a Single Job Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import applicationJobPage from '@/pages/application-job.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/_authenticated/applications/job/$jobId')({
  component: ApplicationJobPage,
});

function ApplicationJobPage() {
  const { jobId } = Route.useParams();
  return (
    <UIEngine
      page={applicationJobPage as PageDefinition}
      params={{ jobId }}
    />
  );
}
