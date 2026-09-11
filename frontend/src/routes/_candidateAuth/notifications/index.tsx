/**
 * Candidate Notifications Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateNotificationsPage from '@/pages/candidate-notifications.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/notifications/')({
  component: NotificationsPage,
});

function NotificationsPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateNotificationsPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
