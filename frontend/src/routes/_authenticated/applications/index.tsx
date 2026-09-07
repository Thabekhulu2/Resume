/**
 * Applications Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import applicationsPage from '@/pages/applications.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/_authenticated/applications/')({
  component: ApplicationsPage,
});

function ApplicationsPage() {
  return <UIEngine page={applicationsPage as PageDefinition} />;
}
