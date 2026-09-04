-- Fixes "infinite recursion detected in policy for relation entities"
-- (42P17) from 20260903140000's candidate policies: the entities policy's
-- USING subqueries entity_versions, and entity_versions' policy subqueries
-- entities back -- a genuine cycle that Postgres's RLS planner detects
-- regardless of which policy would ultimately grant access. The standard
-- fix is a SECURITY DEFINER helper: it runs as the function owner (the
-- table owner, which bypasses RLS on tables it queries by default, no
-- FORCE ROW LEVEL SECURITY is set anywhere in this schema), so the cross-
-- table check happens once, outside RLS evaluation, instead of re-entering
-- each table's policy.

drop policy if exists "candidates read open jobs and own applications" on entities;
drop policy if exists "candidates read entity_versions for own apps and open jobs" on entity_versions;
drop view if exists candidate_open_jobs;

create or replace function _is_open_job_description(p_entity_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1
    from entities e
    join entity_versions ev on ev.entity_id = e.id and ev.is_current
    where e.id = p_entity_id
      and e.entity_type = 'job_description'
      and ev.data ->> 'status' = 'open'
  );
$$;

create or replace function _is_own_application_entity(p_entity_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from entities e where e.id = p_entity_id and e.applicant_id = auth.uid()
  );
$$;

create or replace function _has_applied_to_job(p_job_entity_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1
    from entities ae
    join entity_versions aev on aev.entity_id = ae.id and aev.is_current
    where ae.applicant_id = auth.uid()
      and aev.data ->> 'applied_to_job_id' = p_job_entity_id::text
  );
$$;

create policy "candidates read open jobs and own applications" on entities
  for select to authenticated
  using (
    exists (select 1 from candidates where id = auth.uid())
    and (
      applicant_id = auth.uid()
      or (entity_type = 'job_description' and _is_open_job_description(id))
    )
  );

create policy "candidates read entity_versions for own apps and open jobs" on entity_versions
  for select to authenticated
  using (
    exists (select 1 from candidates where id = auth.uid())
    and (
      _is_own_application_entity(entity_id)
      or (is_current and data ->> 'status' = 'open' and _is_open_job_description(entity_id))
    )
  );

create view candidate_open_jobs
  with (security_invoker = true)
  as
select
  e.id,
  ev.data ->> 'title' as title,
  ev.data ->> 'location' as location,
  ev.data ->> 'jd_text' as jd_text,
  _has_applied_to_job(e.id) as already_applied
from entities e
join entity_versions ev on ev.entity_id = e.id and ev.is_current
where e.entity_type = 'job_description'
  and ev.data ->> 'status' = 'open';

grant select on candidate_open_jobs to authenticated;
