-- Recruiter Applications page (spec docs/specs/0012, ticket #23).
-- Flat, join-friendly view of candidates who applied via the self-service
-- job application flow (spec 0010) -- distinct from recruiter-uploaded
-- candidates, which have no applicant_id and never appear here.
--
-- security_invoker means RLS on the underlying entities/entity_versions/
-- entity_facts tables still applies to whoever queries this view: recruiters
-- already have unrestricted select (spec 0008), so they see every row; a
-- candidate querying it would only ever see their own row (their entities
-- RLS policy), with score coming back null since candidates have no
-- entity_facts select policy -- matching the existing "candidate never sees
-- their score" rule. No new RLS policy needed.
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
