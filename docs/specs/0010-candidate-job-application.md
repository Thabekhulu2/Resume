# Candidate Job Application Flow Specification

**Status:** Draft
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-03
**Last Updated:** 2026-09-03
**Ticket:** Closes #20

## Overview

Let an authenticated candidate browse open job postings (from #19) and apply to one by uploading a resume, which runs the existing scoring pipeline (spec 0001) against that posting's JD. This is the third and final ticket of the multi-user Jobs/Applications feature.

## Goals

- Candidate-facing `/apply` page: lists open job postings
- Apply: upload a resume against a specific posting → triggers `ScoreResumeFitWorkflow` with that posting's `jd_text`
- One application per candidate per job; the Apply button is replaced by an "Applied" state once submitted
- Candidate sees only a submitted confirmation — no score or reasoning (Recruitment-Team-only, unchanged from spec 0001)
- Candidates can read open job postings and their own application records; nothing else (no other candidates' data, no recruiter-only data)

## Non-Goals

- Candidate-facing score/scorecard visibility
- Multiple applications per candidate per job
- Editing or withdrawing an application
- A "my applications" list page (the `/apply` list itself shows applied-state inline — no separate page needed for v1)
- Any change to the existing Recruitment Team "paste a JD, upload a resume, score" flow (spec 0001) — coexists unchanged

## User Stories

### As a candidate, I want to see open jobs so I know what to apply for

**Acceptance Criteria:**
- [ ] `/apply` lists job postings with `status = 'open'` (title, location, description)
- [ ] Empty state if there are no open postings

### As a candidate, I want to apply to a job by uploading my resume

**Acceptance Criteria:**
- [ ] "Apply" on a posting opens a resume upload (PDF/DOCX, same constraint as spec 0001)
- [ ] On submit, I see a confirmation that my application was received — no score, no reasoning
- [ ] The posting I applied to now shows "Applied" instead of the Apply button, on this and future visits, with a link to view my own submitted resume
- [ ] I cannot apply to the same posting twice
- [ ] I cannot view or fetch any other candidate's resume, by any means the UI exposes

### As a recruiter, I want candidate applications to show up exactly like today's scored candidates

**Acceptance Criteria:**
- [ ] A candidate's application appears in Candidate History / Dashboard the same way a recruiter-uploaded resume does (same entity/scoring pipeline, no parallel data model)

## Technical Design

### Architecture

```
Frontend (candidate, /apply)
  -> Supabase Storage upload (resumes bucket, now authenticated-only)
  -> supabase.functions.invoke('start-scoring-workflow', {
       resume_storage_path, job_description_entity_id: <posting id>, applicant_id: <candidate auth uid>
     })
     (existing Edge Function, unchanged control flow -- already supports
     applying to an existing job_description_entity_id; only new work is
     accepting/storing applicant_id)
  -> Edge Function (service_role, bypasses RLS) creates the candidate entity
     + version (as it does today), now also storing `applicant_id` and
     `applied_to_job_id` so the candidate can later query their own
     application without recruiter-level access
  -> Same ScoreResumeFitWorkflow / scorecard pipeline as spec 0001 -- recruiters
     see it in Candidate History exactly like any other scored candidate
```

No changes to the Temporal workflow, activities, or scoring logic — this ticket only adds a second, narrower entry point into the same pipeline.

### Data Model

No new tables. Two additions to existing ones:

- `entities` gains a nullable column `applicant_id uuid references auth.users(id)`, set only when a candidate entity is created via self-service application (recruiter-driven uploads leave it `null`)
- Candidate `entity_versions.data` gains `applied_to_job_id` (the `job_description` entity id applied to), alongside the existing `resume_file_path`/`status` fields from spec 0001 — used to answer "have I already applied to this job" without needing `relationships_v2` access

### RLS (candidates — all read-only; writes go through the Edge Function's service_role, which bypasses RLS)

- `entities`: select job_description rows with a current version where `status = 'open'`
- `entities`: select rows where `applicant_id = auth.uid()` (their own applications)
- `entity_versions`: select current versions of open job postings
- `entity_versions`: select current versions of their own application entities
- `relationships_v2`, `entity_facts` (scores): **no candidate policy** — unchanged from ticket #18, so scores stay fully invisible to candidates, satisfying the "no score visibility" decision at the data layer, not just the UI

### Storage

`resumes` bucket write policy (from spec 0001, currently `anon, authenticated` insert) tightened to `authenticated` only. For reads, a candidate must only ever be able to fetch their **own** resume (resolved open question — they'll see it as part of their own application state):
- Recruiters keep broad read access (`exists (select 1 from recruiters where id = auth.uid())`) — they need to review any candidate's resume, unchanged from today
- Candidates get **no** direct `storage.objects` read policy at all (default-deny, same pattern as everything else candidates can't touch). Instead, a new Edge Function `get-my-resume-url` verifies the caller, looks up their own candidate entity via `entities.applicant_id = auth.uid()`, reads that entity's `resume_file_path`, and returns a short-lived signed URL (`createSignedUrl`, via `service_role`) for that exact file. A candidate physically cannot obtain a URL for anyone else's resume — there's no path-guessing or folder-prefix convention to get right, ownership is resolved server-side from data we already have

This avoids adding a folder-prefix-per-user convention to the shared `EngineFileInput` upload-path generator (`toStoragePath()`), which any JSON page can use and isn't currently auth-aware — scoping storage via ownership lookup instead of path convention keeps that shared component untouched.

### UI/UX Design

- New page `frontend/src/pages/candidate-jobs.json` (JSON-engine, same pattern as `jobs.json`), route `frontend/src/routes/_candidateAuth/apply/index.tsx`
- New layout route `frontend/src/routes/_candidateAuth.tsx` — the candidate-side counterpart to `_authenticated.tsx` from #18 (deferred there because there was nothing to gate yet; this ticket is what needs it)
- `candidate/login.tsx` and `candidate/signup.tsx` (#18) updated to navigate to `/apply` on success instead of back to the login page (previously a no-op landing since nothing existed yet)
- Per-posting "Applied" state driven by whether a current-version application entity with matching `applied_to_job_id` exists for the logged-in candidate

## Open Questions

- [x] Should resume file reads in Storage be scoped so a candidate can only read their own upload? — **Resolved 2026-09-03: yes, via the `get-my-resume-url` signed-URL Edge Function described above (not raw storage RLS path-prefix matching).**
