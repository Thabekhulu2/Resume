/**
 * Candidate Dashboard Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateDashboardPage from '@/pages/candidate-dashboard.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/dashboard/')({
  component: DashboardPage,
});

function DashboardPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateDashboardPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
