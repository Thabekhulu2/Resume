import { Routes } from '@angular/router';
import { Login } from './auth/login/login';
import { Signup } from './auth/signup/signup';
import { recruiterGuard, candidateGuard } from './core/auth.guard';
import { RecruiterShell } from './recruiter/shell/recruiter-shell';
import { Dashboard } from './recruiter/dashboard/dashboard';
import { Jobs } from './recruiter/jobs/jobs';
import { Applications } from './recruiter/applications/applications';
import { CandidateHistory } from './recruiter/candidate-history/candidate-history';
import { CandidateRange } from './recruiter/candidate-range/candidate-range';
import { CandidateScorecard } from './recruiter/candidate-scorecard/candidate-scorecard';
import { Interviews } from './recruiter/interviews/interviews';
import { CandidateShell } from './candidate/shell/candidate-shell';
import { CandidateDashboard } from './candidate/dashboard/candidate-dashboard';
import { CandidateJobs } from './candidate/jobs/candidate-jobs';
import { CandidateApplications } from './candidate/applications/candidate-applications';
import { CandidateNotifications } from './candidate/notifications/candidate-notifications';
import { CandidateProfile } from './candidate/profile/candidate-profile';
import { CandidateSettings } from './candidate/settings/candidate-settings';
import { CandidateSavedJobs } from './candidate/saved-jobs/candidate-saved-jobs';
import { CandidateJobAlerts } from './candidate/job-alerts/candidate-job-alerts';
import { CandidateMessages } from './candidate/messages/candidate-messages';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'candidate/signup', component: Signup },
  {
    path: '',
    component: RecruiterShell,
    canActivate: [recruiterGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'jobs', component: Jobs },
      { path: 'jobs/:jobId/applications', component: Applications },
      { path: 'candidates', component: CandidateHistory },
      { path: 'candidates/range/:min/:max', component: CandidateRange },
      { path: 'candidates/:id', component: CandidateScorecard },
      { path: 'interviews', component: Interviews },
    ],
  },
  {
    path: 'candidate',
    component: CandidateShell,
    canActivate: [candidateGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'jobs' },
      { path: 'dashboard', component: CandidateDashboard },
      { path: 'jobs', component: CandidateJobs },
      { path: 'applications', component: CandidateApplications },
      { path: 'notifications', component: CandidateNotifications },
      { path: 'profile', component: CandidateProfile },
      { path: 'settings', component: CandidateSettings },
      { path: 'saved-jobs', component: CandidateSavedJobs },
      { path: 'job-alerts', component: CandidateJobAlerts },
      { path: 'messages', component: CandidateMessages },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
