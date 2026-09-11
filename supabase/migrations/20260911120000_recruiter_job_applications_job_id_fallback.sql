-- Fix recruiter_job_applications so scored candidates don't drop out of their
-- job's applicant list (spec docs/specs/0015, ticket #26).
--
-- job_id was derived solely from entity_versions.data->>'applied_to_job_id',
-- which the scoring workflow clears: ScoreResumeFitWorkflow's update_entity_scd2
-- call full-replaces the candidate's current version data (name, resume_file_path,
-- resume_text, extracted, status only), dropping applied_to_job_id. Once that
-- happens, job_id resolved to NULL and the candidate no longer matched the
-- application-job.json page's job_id filter.
--
-- Fix: fall back to the candidate_scored_against_job relationship the same
-- workflow already writes to relationships_v2 (parent = job, child = candidate)
-- -- already relied on by Candidate History (spec 0013) for the same link.
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
left join lateral (
  select r.parent_id
  from relationships_v2 r
  where r.child_id = c.id
    and r.relationship_type = 'candidate_scored_against_job'
    and r.is_current
  order by r.created_at desc
  limit 1
) scored_rel on true
left join entities j
  on j.id = coalesce((cv.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id)
left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
where c.entity_type = 'candidate'
  and c.applicant_id is not null;

grant select on recruiter_job_applications to authenticated;
