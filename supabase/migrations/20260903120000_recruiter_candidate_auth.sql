-- Auth: Recruitment Team & Candidate login (spec docs/specs/0008, ticket #18).
-- Adds separate recruiter/candidate profile tables keyed to auth.users, a
-- trigger that provisions a `candidates` row on self-service signup, and
-- locks down the entity-schema tables (previously wide open to `anon` per
-- 20260826081900's Non-Goals note) to authenticated recruiters only.

create table if not exists recruiters (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  created_at timestamptz not null default now()
);

create table if not exists candidates (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  created_at timestamptz not null default now()
);

alter table recruiters enable row level security;
alter table candidates enable row level security;

create policy "recruiters select own row" on recruiters
  for select to authenticated
  using (auth.uid() = id);

create policy "candidates select own row" on candidates
  for select to authenticated
  using (auth.uid() = id);

grant select on recruiters, candidates to authenticated;

-- Provisions a `candidates` row when a user self-registers via the candidate
-- signup form (frontend passes signup_role/full_name as auth signUp metadata).
-- Recruitment Team accounts are provisioned manually and do NOT set
-- signup_role='candidate', so they're intentionally skipped here.
create or replace function handle_candidate_signup()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.raw_user_meta_data ->> 'signup_role' = 'candidate' then
    insert into candidates (id, full_name)
    values (new.id, coalesce(new.raw_user_meta_data ->> 'full_name', ''));
  end if;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_candidate on auth.users;

create trigger on_auth_user_created_candidate
  after insert on auth.users
  for each row
  execute function handle_candidate_signup();

-- Lock down the entity-schema tables to authenticated Recruitment Team
-- members. Candidate access is out of scope here (ticket 3 defines it) --
-- no policy for candidates means they're correctly denied for now.
-- service_role (Edge Functions, Temporal worker) bypasses RLS entirely.
alter table entities enable row level security;
alter table entity_versions enable row level security;
alter table relationships_v2 enable row level security;
alter table fact_types enable row level security;
alter table entity_facts enable row level security;
alter table time_series_points enable row level security;

create policy "recruiters full access to entities" on entities
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to entity_versions" on entity_versions
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to relationships_v2" on relationships_v2
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to fact_types" on fact_types
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to entity_facts" on entity_facts
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to time_series_points" on time_series_points
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));
