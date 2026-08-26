-- Resume -> candidate profile feature (spec: docs/specs/0001-resume-candidate-profile.md)
-- Resolves the spec's open question on Storage bucket access policy: v1 has no
-- auth/multi-tenancy (per spec's Non-Goals), so the anon-key upload flow used by
-- the candidate-upload page needs an explicit policy or every upload 403s under
-- Storage's default-deny RLS.

drop policy if exists "resumes anon insert" on storage.objects;
create policy "resumes anon insert"
  on storage.objects for insert
  to anon, authenticated
  with check (bucket_id = 'resumes');

drop policy if exists "resumes anon select" on storage.objects;
create policy "resumes anon select"
  on storage.objects for select
  to anon, authenticated
  using (bucket_id = 'resumes');
