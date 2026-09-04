# Implementation Plan: Candidate Job Application Flow

**Spec:** [0010-candidate-job-application.md](./0010-candidate-job-application.md)
**Ticket:** Closes #20
**Status:** Implemented, Phase 4 verified end-to-end and adversarially against the local stack — 2026-09-04. Awaiting Gate 2 (commit) approval.

## Implementation notes (deviations from the plan as written)

- **Infra gotcha (not a code deviation, but worth recording):** the local Supabase CLI bakes its list of Edge Functions into `supabase_edge_runtime_resume`'s env (`SUPABASE_INTERNAL_FUNCTIONS_CONFIG`) at container-creation time. Adding a brand-new function directory (`get-my-resume-url`) isn't picked up by editing files (unlike the frontend's live-reload) or by `docker restart` / re-running `supabase start` while the container already exists — it required a full `supabase stop` + `supabase start` cycle (data preserved via the CLI's own backup/restore, confirmed no data loss) to regenerate that manifest and recreate the container. Re-applied `scripts/supabase-env.sh`'s `docker update --restart unless-stopped` afterward per its own documented reason (the edge runtime is known to exit and not self-heal otherwise).

## Phase 1: Migration — `applicant_id`, candidate RLS, storage policy

New migration `supabase/migrations/<timestamp>_candidate_application_access.sql`:

- [x] `alter table entities add column applicant_id uuid references auth.users(id)`
- [x] Candidate RLS (all `for select to authenticated`, additive to ticket #18's recruiter `for all` policies — Postgres RLS policies are OR'd, so recruiters keep full access and these only add a narrow read path for candidates):
  - `entities`: `exists (select 1 from candidates where id = auth.uid())` AND (`entity_type = 'job_description'` with a current version where `data->>'status' = 'open'`) OR (`applicant_id = auth.uid()`)
  - `entity_versions`: mirrors the above via `exists (select 1 from entities e where e.id = entity_versions.entity_id and (...same conditions...))`
- [x] Storage (`storage.objects`, bucket `resumes`): drop `"resumes anon insert"` / `"resumes anon select"` (from spec 0001's migration); add `"resumes authenticated insert"` (`to authenticated, with check bucket_id = 'resumes'` — both recruiters and candidates upload their own new file, no cross-user write risk since paths are random UUIDs) and `"resumes recruiters select"` (`to authenticated using bucket_id = 'resumes' and exists (select 1 from recruiters where id = auth.uid())`). No candidate select policy — see Phase 2's signed-URL function.
- [x] Apply via `supabase migration up --local`

## Phase 2: Edge Functions

- [x] `supabase/functions/start-scoring-workflow/index.ts`: accept optional `applicant_id` in the request body; when `scoreOneResume` creates a new candidate entity, set `applicant_id` on the `entities` insert (if provided) and add `applied_to_job_id: jobDescriptionEntityId` to the `entity_versions.data` payload alongside the existing `resume_file_path`/`status` fields
- [x] New `supabase/functions/get-my-resume-url/index.ts`: reads the caller's JWT from the request's `Authorization` header, resolves the calling user via an anon-key client's `auth.getUser()`, looks up their candidate entity (`entities` where `applicant_id = <caller id>` and `entity_type = 'candidate'`, optionally filtered by a `job_id` param if a candidate could someday have multiple applications — for v1 just the one, per the one-application-per-job non-goal on the recruiter/candidate relationship, though a candidate could in principle apply to multiple *different* jobs, so filter by `applied_to_job_id = job_id`), reads `resume_file_path` from its current version, returns `storage.from('resumes').createSignedUrl(path, 300)` via a `service_role` client. 404s if no matching application; 401 if the JWT doesn't resolve to a user.

## Phase 3: Frontend

- [x] `frontend/src/routes/_candidateAuth.tsx` — layout `beforeLoad`: no session → `/candidate/login`; role !== `'candidate'` → `/login`. Candidate counterpart to `_authenticated.tsx` from #18.
- [x] `frontend/src/pages/candidate-jobs.json` — lists open job postings (same `dataSources` shape as `jobs.json`'s open-tab query, no closed toggle needed here); per-posting: Apply button, or "Applied" + "View my resume" if the candidate already has an application entity with matching `applied_to_job_id`
- [x] `frontend/src/routes/_candidateAuth/apply/index.tsx` — routes `candidate-jobs.json` in, protected by the new layout
- [x] Apply flow (in `candidate-jobs.json`): `FileInput` (PDF/DOCX) → `apiCall operation: upload` to `resumes` bucket → `apiCall operation: invoke` on `start-scoring-workflow` with `{ resume_storage_path, job_description_entity_id: job.id, applicant_id: <candidate auth uid> }` → on success, refetch and show confirmation (no score, no reasoning)
- [x] "View my resume" calls `get-my-resume-url` (`operation: invoke`) with the job id, opens the returned signed URL
- [x] `frontend/src/routes/candidate/login.tsx` and `candidate/signup.tsx` (#18): change post-auth `navigate({ to: '/candidate/login' })` → `navigate({ to: '/apply' })`, now that there's somewhere to land

## Phase 4: Verification (e2e + adversarial, per repo testing standard)

Test candidate accounts use email + password `Password01` (per user instruction), created via real self-service signup at `/candidate/signup` (not a provisioning script — candidates are self-service by design from #18).

**e2e (happy path):**
- [x] Candidate signs up / logs in, lands on `/apply`
- [x] Sees the open job posting(s) created via #19's Jobs page
- [x] Applies with a resume — sees a submitted confirmation, no score/reasoning anywhere in the UI
- [x] Posting now shows "Applied" with a working "View my resume" link
- [x] Recruiter sees the new application in Candidate History exactly like a recruiter-uploaded resume, and can score/view it normally

**Adversarial:**
- [x] Same candidate tries to apply to the same posting again → blocked (Apply button already replaced by "Applied")
- [x] A second candidate account cannot see or fetch the first candidate's resume via `get-my-resume-url` (wrong caller → 404, not another candidate's file)
- [x] Direct REST call to `entities` with a candidate's token, no filters → only their own `applicant_id` rows and open job postings come back, nothing else (no other candidates, no closed postings, no recruiter-only entities)
- [x] Direct REST call to storage `resumes` bucket listing/reading with a candidate's token → denied (no candidate select policy)
- [x] Unauthenticated hit on `/apply` → redirected to `/candidate/login`
- [x] Recruiter session hitting `/apply` → redirected to `/login` (wrong role for this layout)
- [x] `get-my-resume-url` called with no/garbage auth token → 401, not a crash

## Out of scope for this plan (per spec's Non-Goals)

- Candidate-facing scorecard/score visibility
- Multiple applications per candidate per job
- Editing/withdrawing an application
- A separate "my applications" list page
- Any change to spec 0001's recruiter paste-JD flow

## Dependencies between phases

Phase 1 must land before Phase 2 (Edge Functions read/write the new column and rely on the RLS being in place for correctness, even though they run as service_role). Phase 3 depends on Phase 2 (frontend calls both Edge Functions). Phase 4 runs last, against the real local stack.
