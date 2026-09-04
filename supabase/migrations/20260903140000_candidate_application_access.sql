-- Candidate job application flow (spec docs/specs/0010, ticket #20).
-- Adds applicant_id so a self-service candidate application can be traced
-- back to the auth user who submitted it, narrow read-only RLS so
-- candidates can browse open job postings and see their own application
-- (never anyone else's, never a score), and tightens the resumes storage
-- bucket now that both recruiters and candidates are authenticated.

alter table entities add column if not exists applicant_id uuid references auth.users(id);

-- Candidates: read job_description postings that are currently open, and
-- their own application entities (any entity_type they were the applicant
-- for -- in practice always 'candidate'). Additive to #18's recruiter
-- `for all` policies (RLS policies are OR'd), so recruiters are unaffected.
create policy "candidates read open jobs and own applications" on entities
  for select to authenticated
  using (
    exists (select 1 from candidates where id = auth.uid())
    and (
      applicant_id = auth.uid()
      or (
        entity_type = 'job_description'
        and exists (
          select 1 from entity_versions ev
          where ev.entity_id = entities.id
            and ev.is_current
            and ev.data ->> 'status' = 'open'
        )
      )
    )
  );

create policy "candidates read entity_versions for own apps and open jobs" on entity_versions
  for select to authenticated
  using (
    exists (select 1 from candidates where id = auth.uid())
    and (
      exists (select 1 from entities e where e.id = entity_versions.entity_id and e.applicant_id = auth.uid())
      or (
        is_current
        and data ->> 'status' = 'open'
        and exists (select 1 from entities e where e.id = entity_versions.entity_id and e.entity_type = 'job_description')
      )
    )
  );

-- Storage: both recruiters and candidates are authenticated post-#18, so
-- anonymous upload/read of resume PII is no longer needed. Candidates get
-- no direct read policy at all -- their own-resume access goes through the
-- get-my-resume-url Edge Function (ownership resolved server-side via
-- applicant_id, not a storage path convention).
drop policy if exists "resumes anon insert" on storage.objects;
drop policy if exists "resumes anon select" on storage.objects;

create policy "resumes authenticated insert"
  on storage.objects for insert
  to authenticated
  with check (bucket_id = 'resumes');

create policy "resumes recruiters select"
  on storage.objects for select
  to authenticated
  using (bucket_id = 'resumes' and exists (select 1 from recruiters where id = auth.uid()));
