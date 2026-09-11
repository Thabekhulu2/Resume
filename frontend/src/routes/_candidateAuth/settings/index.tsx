/**
 * Candidate Settings Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateSettingsPage from '@/pages/candidate-settings.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/settings/')({
  component: SettingsPage,
});

function SettingsPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateSettingsPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
