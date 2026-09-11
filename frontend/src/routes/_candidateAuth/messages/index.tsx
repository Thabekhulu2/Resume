/**
 * Candidate Messages Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import candidateMessagesPage from '@/pages/candidate-messages.json';
import type { PageDefinition } from '@/engine/types';
import { useAuth } from '@/lib/auth';

export const Route = createFileRoute('/_candidateAuth/messages/')({
  component: MessagesPage,
});

function MessagesPage() {
  const { user } = useAuth();
  return (
    <UIEngine
      page={candidateMessagesPage as PageDefinition}
      params={{ candidateId: user?.id ?? '' }}
    />
  );
}
