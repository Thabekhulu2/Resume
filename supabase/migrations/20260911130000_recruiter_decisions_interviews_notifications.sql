-- Recruiter decisions, scheduled interviews, and candidate notifications
-- (spec docs/specs/0017, ticket #28).
--
-- Plain domain tables, not the entities/entity_versions SCD2 pattern -- these
-- are append-only/simple-update records with no need for version history,
-- mirroring the existing time_series_points precedent.

create table application_decisions (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references entities(id) on delete cascade,
  decision text not null check (decision in ('shortlisted','interview_scheduled','rejected')),
  notes text,
  created_by uuid references recruiters(id),
  created_at timestamptz not null default now()
);
create index idx_application_decisions_candidate on application_decisions(candidate_id, created_at desc);

create table interviews (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references entities(id) on delete cascade,
  job_id uuid references entities(id),
  scheduled_at timestamptz not null,
  status text not null default 'scheduled' check (status in ('scheduled','completed','cancelled')),
  notes text,
  created_by uuid references recruiters(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- recipient_id points at auth.users (the actual logged-in person), not an
-- `entities` row -- one candidate can have multiple entities (one per
-- application, see spec 0016's Profile-page finding), but notifications
-- belong to the person, not any single application.
create table notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  body text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);
create index idx_notifications_recipient on notifications(recipient_id, created_at desc);

alter table application_decisions enable row level security;
alter table interviews enable row level security;
alter table notifications enable row level security;

create policy "recruiters full access to application_decisions" on application_decisions
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to interviews" on interviews
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

-- Candidates see/update (mark read) only their own row; no candidate insert
-- policy at all -- a candidate can never write their own notification.
create policy "recruiters insert notifications" on notifications
  for insert to authenticated
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "candidates select own notifications" on notifications
  for select to authenticated
  using (auth.uid() = recipient_id);

create policy "candidates update own notifications" on notifications
  for update to authenticated
  using (auth.uid() = recipient_id)
  with check (auth.uid() = recipient_id);

grant select, insert on application_decisions to authenticated;
grant select, insert, update on interviews to authenticated;
grant select, insert, update on notifications to authenticated;

-- Runs as the caller (no security definer) -- recruiters already have full
-- access to application_decisions/interviews via the policies above, and the
-- "recruiters insert notifications" policy covers the notification insert.
-- A candidate calling this directly would have every insert rejected by RLS.
create or replace function record_recruiter_decision(
  p_candidate_id uuid,
  p_decision text,
  p_scheduled_at timestamptz default null,
  p_notes text default null
) returns uuid
language plpgsql
as $$
declare
  v_decision_id uuid;
  v_applicant_id uuid;
  v_job_title text;
  v_title text;
  v_body text;
begin
  insert into application_decisions (candidate_id, decision, notes, created_by)
  values (p_candidate_id, p_decision, p_notes, auth.uid())
  returning id into v_decision_id;

  if p_decision = 'interview_scheduled' then
    insert into interviews (candidate_id, job_id, scheduled_at, notes, created_by)
    select p_candidate_id, (cv.data ->> 'applied_to_job_id')::uuid, p_scheduled_at, p_notes, auth.uid()
    from entity_versions cv
    where cv.entity_id = p_candidate_id and cv.is_current;
  end if;

  select c.applicant_id into v_applicant_id from entities c where c.id = p_candidate_id;

  select coalesce(j_ev.data ->> 'title', 'the role') into v_job_title
  from entity_versions cv
  left join entities j on j.id = (cv.data ->> 'applied_to_job_id')::uuid
  left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
  where cv.entity_id = p_candidate_id and cv.is_current;

  if v_applicant_id is not null then
    v_title := case p_decision
      when 'shortlisted' then 'You''ve been shortlisted'
      when 'interview_scheduled' then 'Interview scheduled'
      when 'rejected' then 'Application update'
    end;
    v_body := case p_decision
      when 'shortlisted' then 'You have been shortlisted for ' || v_job_title || '.'
      when 'interview_scheduled' then 'An interview has been scheduled for ' || v_job_title || ' on ' || to_char(p_scheduled_at, 'DD Mon YYYY HH24:MI') || '.'
      when 'rejected' then 'Thank you for applying for ' || v_job_title || '. We will not be proceeding with your application at this time.'
    end;
    insert into notifications (recipient_id, title, body) values (v_applicant_id, v_title, v_body);
  end if;

  return v_decision_id;
end;
$$;

grant execute on function record_recruiter_decision(uuid, text, timestamptz, text) to authenticated;

create view recruiter_scheduled_interviews
  with (security_invoker = true)
  as
select
  i.id,
  i.candidate_id,
  cv.data ->> 'name' as candidate_name,
  i.job_id,
  j_ev.data ->> 'title' as job_title,
  i.scheduled_at,
  i.status,
  i.notes
from interviews i
join entity_versions cv on cv.entity_id = i.candidate_id and cv.is_current
left join entities j on j.id = i.job_id
left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current;

grant select on recruiter_scheduled_interviews to authenticated;
