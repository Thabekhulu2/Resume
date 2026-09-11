/**
 * Candidate Profile Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateProfilePage from '@/pages/candidate-profile.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/profile/')({
  component: ProfilePage,
});

function ProfilePage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateProfilePage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
