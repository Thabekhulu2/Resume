# Recruiter Applications Page Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-04
**Last Updated:** 2026-09-04
**Ticket:** Closes #23

## Overview

Give the Recruitment Team a dedicated page listing candidates who applied through the candidate-facing job application flow (spec 0010) — separate from Candidate History, which mixes in candidates the recruiter uploaded directly. From this page a recruiter can filter applicants by job posting, trigger (or re-trigger) scoring for an applicant against the job they applied to, and jump to the existing scorecard to see the result.

## Goals

- New sidebar item "Applications" for recruiters, landing on a page listing only candidates who applied via the self-service flow (i.e. have an `applicant_id`) — recruiter-uploaded candidates (Candidate History) are unaffected and unchanged.
- Each row shows: candidate name (or resume filename if not yet extracted), which job they applied to, current status (scoring/scored/failed), and score if available.
- The job filter shows the exact same set of job postings that exist in the Jobs menu (every posting created there appears here too) as a Kanban-style grid of clickable tabs, 3 per row, wrapping into additional rows below as more postings exist (plain CSS grid — not a click-through pager).
- An Open / Closed / All jobs toggle (mirrors the Jobs page's own toggle) narrows which job tabs are shown; "All jobs" restores today's default (every posting, any status) and also clears any specific-job selection.
- A "Score" action per row triggers the existing scoring workflow for that specific candidate + resume + job — works whether this is the first score (application scoring failed or is still pending) or a re-score.
- A "View" link per row goes to the existing candidate scorecard page (`/candidates/:id`) to see score + reasoning once available.

## Non-Goals

- Any change to the automatic scoring that already happens at the moment a candidate applies (spec 0010) — this page is an additional recruiter-side view/control, not a replacement.
- Any change to Candidate History (still shows everyone, recruiter-uploaded and applicant-sourced, unchanged).
- Bulk actions (bulk re-score, bulk delete) on this page — out of scope for v1.
- Editing/withdrawing an application, or any candidate-facing change.

## User Stories

### As a recruiter, I want to see only candidates who applied through the job posting flow, not ones I uploaded myself

**Acceptance Criteria:**
- [ ] A new "Applications" sidebar link is visible to recruiters only (candidates never see recruiter nav, per spec 0008's `RoleAwareSidebar`)
- [ ] The list shows only candidates with an `applicant_id` set; a candidate uploaded via "Score a Candidate" never appears here
- [ ] Each row shows the job title applied to, candidate name/resume filename, status, and score (if scored)

### As a recruiter, I want to filter applicants by which job they applied to

**Acceptance Criteria:**
- [ ] A row of job-title buttons (plus "All") filters the list to that job's applicants
- [ ] The job list includes postings regardless of open/closed status (a recruiter reviewing applicants doesn't care whether the posting is still open)

### As a recruiter, I want to score (or re-score) an applicant against the job they applied to

**Acceptance Criteria:**
- [ ] A "Score" button per row re-invokes scoring for that exact candidate/resume/job — no need to re-enter or re-upload anything
- [ ] Clicking "Score" updates the row's status to "scoring" and, once complete, "scored" (or "failed" with a reason, consistent with existing status handling)
- [ ] A "View" link opens the existing scorecard page for that candidate

## Technical Design

### Architecture

```
New view: recruiter_job_applications (Postgres, security_invoker)
  joins entities/entity_versions (candidate rows with applicant_id set)
  + the job they applied to (entities/entity_versions, entity_versions.data->>applied_to_job_id)
  + their score (entity_facts, scalar subquery)
  -> RLS inherited from existing recruiter/candidate policies (security_invoker),
     no new policy needed: recruiters already have full access to the underlying
     tables (spec 0008); a candidate querying this view would only ever see their
     own row, same as they already can via `entities` directly

Frontend: frontend/src/pages/applications.json (new)
  dataSources.jobs      -> entities (entity_type=job_description, any status) — powers filter buttons
  dataSources.applications -> recruiter_job_applications, optionally filtered by job_id
  "Score" button -> apiCall invoke start-scoring-workflow
                     { candidate_entity_id, resume_storage_path, job_description_entity_id }
  "View" link -> /candidates/{{application.candidate_id}} (existing route, unchanged)

frontend/src/routes/_authenticated/applications/index.tsx (new)
frontend/src/routes/__root.tsx — add sidebar link
```

### Database Schema

New migration, new view only (no new tables/columns):

```sql
create view recruiter_job_applications
  with (security_invoker = true)
  as
select
  c.id as candidate_id,
  cv.data ->> 'name' as candidate_name,
  cv.data ->> 'resume_file_path' as resume_file_path,
  cv.data ->> 'status' as status,
  (cv.data ->> 'applied_to_job_id')::uuid as job_id,
  j_ev.data ->> 'title' as job_title,
  (select value from entity_facts ef
     where ef.entity_id = c.id
     order by ef.created_at desc limit 1) as score,
  c.created_at as applied_at
from entities c
join entity_versions cv on cv.entity_id = c.id and cv.is_current
left join entities j on j.id = (cv.data ->> 'applied_to_job_id')::uuid
left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
where c.entity_type = 'candidate'
  and c.applicant_id is not null;

grant select on recruiter_job_applications to authenticated;
```

### Engine change (small, reusable)

`FilterDefinition` gains an optional `if` (expression string). `buildSupabaseQuery` skips a filter whose `if` evaluates falsy, instead of always applying every filter in the array. Used twice: the `job_id` filter on `applications` is skipped when no specific job is selected, and the `entity_versions.data->>status` filter on `jobs` is skipped when `jobStatusFilter === 'all'` — both avoid needing an "OR" expression, which the JSON engine's expression evaluator cannot safely combine with a comparison in one `{{}}` (see project history).

**Design iteration:** an initial version used click-through Prev/Next pagination (3 job tabs per page) with two new engine primitives (`chunkSize`/`chunkIndex` on `each`-loops, a `paginate` action) to work around the expression language having no arithmetic. After a follow-up request, this was replaced with a plain wrapping 3-column `Grid` (rows of 3 form naturally via CSS grid, no JS pagination needed) — simpler and no engine changes required for that part. The pagination-specific engine additions were fully reverted since nothing else needed them.

Each job tab is rendered as a `Button` (not `Card` — `EngineCard` doesn't forward `onClick` at all, confirmed by reading its implementation) styled with card-like padding/borders, since `Button` already has proven `onClick` + custom-`className` support used throughout this codebase.

### Security Considerations

- The view relies entirely on existing RLS (security_invoker) — no new policy surface. Recruiters already have unrestricted select on `entities`/`entity_versions`/`entity_facts` (spec 0008); a candidate's own RLS (spec 0010) still applies if they were to query this view, limiting them to their own row with `score` coming back null (candidates have no `entity_facts` select policy — matches the existing "candidate never sees their score" rule).
- The "Score" action calls `start-scoring-workflow` with an explicit `candidate_entity_id`, so it updates the existing application entity in place (via the Edge Function's existing reuse-if-given-an-id path) rather than creating a duplicate candidate entity.

## Open Questions

- [ ] None — scope is additive and reuses existing scoring/RLS/scorecard infrastructure.
