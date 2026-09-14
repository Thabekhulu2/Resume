-- Decouple recruiter/candidate auth from Supabase Auth (spec docs/specs/0018,
-- ticket #29, Phase 1). Spring Boot now owns credentials directly via Spring
-- Security instead of delegating to GoTrue/auth.users.

-- 1. recruiters/candidates become app-owned identity tables: drop the
--    auth.users FK, add their own credential columns.
alter table recruiters drop constraint if exists recruiters_id_fkey;
alter table candidates drop constraint if exists candidates_id_fkey;

alter table recruiters
  add column if not exists email text,
  add column if not exists password_hash text;
alter table candidates
  add column if not exists email text,
  add column if not exists password_hash text;

-- Backfill for any pre-existing local rows before tightening constraints.
-- Known limitation (dev/local data only): these accounts' real passwords
-- lived in GoTrue and are unrecoverable -- they must be re-created against
-- the new Spring Boot auth after this migration.
update recruiters set email = coalesce(email, id::text || '@migrated.invalid') where email is null;
update candidates set email = coalesce(email, id::text || '@migrated.invalid') where email is null;
update recruiters set password_hash = coalesce(password_hash, '') where password_hash is null;
update candidates set password_hash = coalesce(password_hash, '') where password_hash is null;

alter table recruiters
  alter column email set not null,
  alter column password_hash set not null;
alter table candidates
  alter column email set not null,
  alter column password_hash set not null;

alter table recruiters add constraint recruiters_email_key unique (email);
alter table candidates add constraint candidates_email_key unique (email);

-- 2. Candidate signup used to be provisioned by a trigger on auth.users;
--    Spring Boot's candidate signup endpoint now inserts into `candidates`
--    directly.
drop trigger if exists on_auth_user_created_candidate on auth.users;
drop function if exists handle_candidate_signup();

-- 3. Self-row RLS policies keyed off auth.uid() are dead now that identity
--    isn't Supabase-Auth-based; Spring Boot enforces per-user access in code.
drop policy if exists "recruiters select own row" on recruiters;
drop policy if exists "candidates select own row" on candidates;

-- 4. entities.applicant_id / notifications.recipient_id previously stored the
--    Supabase-Auth user id, which was always == candidates.id (candidates.id
--    itself was that same auth.users id, per the old FK). Now that
--    candidates.id is independently generated, existing values still line up
--    1:1 with candidates.id, but new candidate rows created via Spring Boot
--    have no auth.users row at all -- point these FKs at candidates(id)
--    instead so future inserts don't fail against a table that no longer
--    authoritatively holds candidate identity.
alter table entities drop constraint if exists entities_applicant_id_fkey;
alter table entities add constraint entities_applicant_id_fkey
  foreign key (applicant_id) references candidates(id);

alter table notifications drop constraint if exists notifications_recipient_id_fkey;
alter table notifications add constraint notifications_recipient_id_fkey
  foreign key (recipient_id) references candidates(id) on delete cascade;

-- 5. Local dev seed: recruiter accounts were previously provisioned manually
--    via Supabase Studio's Auth UI, which no longer exists in this flow.
--    Seed one known local recruiter account (password: "password123") so
--    Phase 1 can be verified end-to-end. Recruiter self-signup is out of
--    scope (matches the original "provisioned manually" design).
insert into recruiters (id, full_name, email, password_hash)
values (
  gen_random_uuid(),
  'Dev Recruiter',
  'recruiter@resume.local',
  '$2a$10$9Jk4ccuuPF0vao183AlX3ess9oyAj4/qCiNE.x4GmVu3JYV9XNe3u'
)
on conflict (email) do nothing;
