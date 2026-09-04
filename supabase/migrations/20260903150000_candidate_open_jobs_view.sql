-- Candidate job application flow (spec docs/specs/0010, ticket #20).
-- The JSON-engine's expression language can't correlate two separate query
-- results (e.g. "is this job's id in my list of applications"), so this
-- view precomputes already_applied per job server-side -- the candidate
-- page just reads a plain boolean, no client-side cross-referencing.
-- security_invoker means it runs as the calling user, so it only ever
-- returns what ticket #18/#20's RLS already lets that candidate see.

create view candidate_open_jobs
  with (security_invoker = true)
  as
select
  e.id,
  ev.data ->> 'title' as title,
  ev.data ->> 'location' as location,
  ev.data ->> 'jd_text' as jd_text,
  exists (
    select 1
    from entities ae
    join entity_versions aev on aev.entity_id = ae.id and aev.is_current
    where ae.applicant_id = auth.uid()
      and aev.data ->> 'applied_to_job_id' = e.id::text
  ) as already_applied
from entities e
join entity_versions ev on ev.entity_id = e.id and ev.is_current
where e.entity_type = 'job_description'
  and ev.data ->> 'status' = 'open';

grant select on candidate_open_jobs to authenticated;
