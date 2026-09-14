export type Role = 'RECRUITER' | 'CANDIDATE';

export interface AuthResponse {
  token: string;
  id: string;
  role: Role;
  fullName: string;
  email: string;
}

export interface JobSummary {
  id: string;
  title: string;
  location: string;
  status: string;
  jdText: string;
  createdAt: string;
}

export interface OpenJob {
  id: string;
  title: string;
  location: string;
  jdText: string;
  alreadyApplied: boolean;
}

export interface CandidateSummary {
  id: string;
  name: string;
  resumeFilePath: string;
  status: string;
  score: number | null;
  jobTitle: string | null;
  createdAt: string;
}

export interface ExperienceEntry {
  title?: string;
  company?: string;
  duration?: string;
  summary?: string;
}

export interface CandidateScorecard {
  id: string;
  name: string;
  status: string;
  error: string | null;
  skills: string[];
  experience: ExperienceEntry[];
  score: number | null;
  reasoning: string | null;
  jobId: string | null;
  jobTitle: string | null;
  latestDecision: string | null;
}

export interface ApplicationRow {
  candidateId: string;
  candidateName: string;
  resumeFilePath: string;
  status: string;
  jobId: string | null;
  jobTitle: string | null;
  score: number | null;
  appliedAt: string;
}

export interface InterviewRow {
  id: string;
  candidateId: string;
  candidateName: string;
  jobId: string | null;
  jobTitle: string | null;
  scheduledAt: string;
  status: string;
  notes: string | null;
}

export interface NotificationRow {
  id: string;
  recipientId: string;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

export interface ScoreBandCounts {
  weak: number;
  potential: number;
  strong: number;
}

export interface ScoringStartResponse {
  candidate_entity_id?: string;
  job_description_entity_id?: string;
  workflow_id?: string;
  candidates?: { candidate_entity_id: string; workflow_id: string }[];
  failures?: { resume_storage_path: string; error: string }[];
}
