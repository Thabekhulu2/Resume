/**
 * Candidate Job Alerts Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateJobAlertsPage from '@/pages/candidate-job-alerts.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/job-alerts/')({
  component: JobAlertsPage,
});

function JobAlertsPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateJobAlertsPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
