/**
 * Jobs Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import jobsPage from '@/pages/jobs.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/_authenticated/jobs/')({
  component: JobsPage,
});

function JobsPage() {
  return <UIEngine page={jobsPage as PageDefinition} />;
}
