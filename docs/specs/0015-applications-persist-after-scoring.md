# Applications: Persist Candidates in Per-Job List After Scoring Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-11
**Last Updated:** 2026-09-11
**Ticket:** Closes #26

## Overview

On the recruiter Applications page, opening a job's applicant tab (`/applications/job/:jobId`, spec 0012) should show every candidate who applied to that job posting, before and after they've been scored. Today, scoring a candidate silently drops them out of that list because the view backing it derives "which job did this candidate apply to" from a mutable JSON field that scoring overwrites. This spec fixes the underlying data derivation so the job link survives scoring.

## Problem Statement

### Current State

`application-job.json` queries `recruiter_job_applications` filtered by `job_id = params.jobId` (spec 0012). That view computes `job_id` as `(cv.data ->> 'applied_to_job_id')::uuid`, read off the candidate's **current** `entity_versions` row.

`ScoreResumeFitWorkflow` (`temporal/src/workflows/score_resume_fit/workflow.py:39-53`) scores an applicant by building a brand-new `candidate_data` dict (`name`, `resume_file_path`, `resume_text`, `extracted`, `status`) and passing it to `update_entity_scd2`, which inserts it as the **entire** `data` payload of the new current version (`temporal/src/activities/supabase_core.py:44-61`) — a full replace, not a merge. `applied_to_job_id` is not one of the fields it sets, so it's silently dropped.

Result: as soon as a candidate is scored, `recruiter_job_applications.job_id` for that row becomes `NULL`, and the `job_id = params.jobId` filter no longer matches — the candidate disappears from that job's applicant tab, even though their `status` correctly shows `"scored"` and the "Scored" badge exists in the UI for exactly this case (`application-job.json:77-80`).

Separately, the same scoring workflow already writes a durable, non-mutable link every time it runs: `create_relationship(job_description_entity_id, candidate_entity_id, "candidate_scored_against_job", {})` (`workflow.py:64-68`), inserted into `relationships_v2` (parent = job, child = candidate). Candidate History already reads this exact relationship to show "Scored against: <job title>" (spec 0013) — it just isn't consulted by the Applications view.

### Desired State

Opening a job's applicant tab shows all candidates who applied to that job, and scoring a candidate never removes them from that list.

## Goals

- A candidate remains listed under the job they applied to after being scored (first score or re-score), with their status/score reflected inline as today.
- No change to what's shown for not-yet-scored applicants (already correct today).
- No change to the scoring workflow's data model beyond what's strictly needed — prefer fixing the read side over threading extra fields through every future SCD2 update.

## Non-Goals

- Any change to Candidate History or its existing `candidate_scored_against_job` display (spec 0013) — unaffected.
- Any change to the scoring workflow's `candidate_data` shape, `update_entity_scd2`'s full-replace semantics, or the SCD2 pattern in general — a wider audit of "does every update to a candidate preserve unrelated fields" is out of scope here.
- Bulk actions, application withdrawal, or any other change to the Applications page beyond persisting rows after scoring.

## User Stories

### As a recruiter, I want to see every applicant to a job, including ones I've already scored, when I open that job's tab

**Acceptance Criteria:**
- [ ] A candidate who applied to job X and has not been scored appears under job X's applicant tab (unchanged behavior)
- [ ] A candidate who applied to job X and has since been scored still appears under job X's applicant tab, showing the "Scored" badge and score
- [ ] Re-scoring an applicant (the existing "Score" button, re-triggerable per spec 0012) does not remove them from the list at any point (scoring → scored)
- [ ] A recruiter-uploaded candidate (no `applicant_id`, scored via "Score a Candidate") still never appears on this page, matching existing behavior — this fix only changes how the applied-to job is derived, not who qualifies as an applicant

## Technical Design

### Root cause

`recruiter_job_applications.job_id` is sourced solely from `cv.data ->> 'applied_to_job_id'`, which scoring's full-replace `update_entity_scd2` call clears.

### Fix

Update the `recruiter_job_applications` view to fall back to the `candidate_scored_against_job` relationship (already written by the scoring workflow, already relied on by spec 0013) when the mutable `applied_to_job_id` field is absent:

```sql
-- supabase/migrations/<timestamp>_recruiter_job_applications_job_id_fallback.sql
create or replace view recruiter_job_applications
  with (security_invoker = true)
  as
select
  c.id as candidate_id,
  cv.data ->> 'name' as candidate_name,
  cv.data ->> 'resume_file_path' as resume_file_path,
  cv.data ->> 'status' as status,
  coalesce(
    (cv.data ->> 'applied_to_job_id')::uuid,
    scored_rel.parent_id
  ) as job_id,
  j_ev.data ->> 'title' as job_title,
  (select value from entity_facts ef
     where ef.entity_id = c.id
     order by ef.created_at desc limit 1) as score,
  c.created_at as applied_at
from entities c
join entity_versions cv on cv.entity_id = c.id and cv.is_current
left join relationships_v2 scored_rel
  on scored_rel.child_id = c.id
  and scored_rel.relationship_type = 'candidate_scored_against_job'
left join entities j
  on j.id = coalesce((cv.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id)
left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
where c.entity_type = 'candidate'
  and c.applicant_id is not null;

grant select on recruiter_job_applications to authenticated;
```

- `coalesce(applied_to_job_id, scored_rel.parent_id)` keeps today's behavior for unscored applicants (relationship doesn't exist yet) and recovers the job link for scored ones (relationship exists, mutable field is gone).
- If a candidate has been scored against more than one job (re-scored against a different job than they applied to), `relationships_v2` could have multiple rows; this is an existing edge case shared with spec 0013's display and not introduced by this fix — out of scope to resolve here. In the common case (score/re-score against the same job they applied to) this is a non-issue.
- No changes to `frontend/src/pages/application-job.json` or `applications.json` — they already filter/display via `job_id` and `status`, which now stay populated.
- No changes to the Temporal workflow, `update_entity_scd2`, or `create_relationship` — this is a read-side (view) fix only, so it can't regress by a future engineer forgetting to carry a field forward in some other update path.

### Security Considerations

- No new RLS surface: the view keeps `security_invoker = true`; the added join is against `relationships_v2`, which recruiters already have unrestricted select on (spec 0008), same as the tables already joined.

## Testing Strategy

- `supabase db reset` to apply the migration cleanly.
- Manual E2E: apply as a candidate to an open job, confirm it appears under that job's tab; score it via the recruiter "Score" button; confirm it's still listed under the same job tab with the "Scored" badge and score after the workflow completes; re-score and confirm it remains listed throughout (scoring → scored).
- Adversarial: a candidate who applied but whose scoring failed (`status = 'failed'`) must also remain listed (their `applied_to_job_id` was never touched, so this already works — verify no regression).

## Open Questions

- [ ] None — this is a targeted view fix reusing an existing, already-written relationship.
