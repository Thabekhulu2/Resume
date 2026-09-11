/**
 * Recruiter Scheduled Interviews Route
 */

import { createFileRoute } from '@tanstack/react-router';
import { UIEngine } from '@/engine';
import recruiterInterviewsPage from '@/pages/recruiter-interviews.json';
import type { PageDefinition } from '@/engine/types';

export const Route = createFileRoute('/_authenticated/interviews/')({
  component: InterviewsPage,
});

function InterviewsPage() {
  return <UIEngine page={recruiterInterviewsPage as PageDefinition} params={{}} />;
}
