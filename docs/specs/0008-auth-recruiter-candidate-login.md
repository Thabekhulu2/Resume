# Auth: Recruitment Team & Candidate Login Specification

**Status:** Draft
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-03
**Last Updated:** 2026-09-03
**Ticket:** Closes #18

## Overview

Introduce Supabase Auth-backed login for two distinct user types — **Recruitment Team** (internal staff) and **Candidates** (external job applicants) — with separate profile tables and role-scoped access. This is ticket 1 of 3 for the multi-user Jobs/Applications feature (2: Jobs page for Recruitment Team, 3: Candidate application flow), and is a prerequisite for both.

## Goals

- Recruitment Team members log in with email/password to reach the existing scoring UI (Dashboard, Score a Candidate, Candidate History) — currently open to anyone
- Candidates self-register (email/password) and log in to a new candidate-facing area (built in ticket 3)
- Two separate profile tables (`recruiters`, `candidates`), each linked 1:1 to a `auth.users` row — not a shared role column
- Route guards: unauthenticated users hitting a protected route are redirected to the correct login page; a candidate hitting Recruitment Team routes (or vice versa) is denied
- RLS policies scope database access by the caller's profile table membership
- Logout, and session persistence across page reloads

## Non-Goals

- Jobs page / job posting CRUD (ticket 2)
- Candidate application flow (ticket 3)
- Password reset / email verification UI beyond Supabase Auth's built-in flows
- SSO, MFA, or self-service Recruitment Team signup (accounts provisioned manually via Supabase Studio/SQL for now)

## User Stories

### As a Recruitment Team member, I want to log in so that only staff can access candidate data

**Acceptance Criteria:**
- [ ] Visiting any existing route (`/`, `/candidates/upload`, `/candidates`, `/candidates/:id`) while logged out redirects to `/login`
- [ ] Valid email/password logs in and lands on the Dashboard
- [ ] Invalid credentials show an inline error, not a crash
- [ ] A logged-in recruiter sees their email and a Logout control in the header; logout clears the session and redirects to `/login`

### As a candidate, I want to create an account so that I can apply to jobs (ticket 3)

**Acceptance Criteria:**
- [ ] `/candidate/signup` lets a new candidate register with name, email, password
- [ ] `/candidate/login` lets a returning candidate log in
- [ ] A candidate session cannot reach Recruitment Team routes (redirected to `/candidate/login` or shown a 403 page)
- [ ] A recruiter session cannot reach candidate routes

## Technical Design

### Architecture

```
Frontend
  -> Supabase Auth (signInWithPassword / signUp / signOut)
  -> on auth state change, look up caller's row in `recruiters` or `candidates`
       to determine role and gate routing
  -> TanStack Router route guards (beforeLoad) redirect based on session + role
  -> Postgres RLS policies enforce the same boundary at the data layer
       (defense in depth — UI guard is not the security boundary)
```

### Data Model

Two new tables, both keyed by `auth.users.id` (not the generic `entities` schema — these are login-identity records, distinct from the existing `entities.entity_type = 'candidate'` rows, which represent a resume/profile *submitted for scoring* and may not correspond 1:1 to a candidate login — a candidate account can end up with zero or many such entity rows, one per job application, wired up in ticket 3).

```sql
create table recruiters (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  created_at timestamptz not null default now()
);

create table candidates (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  created_at timestamptz not null default now()
);
```

- Row existence in `recruiters` vs `candidates` (mutually exclusive) is the role signal — no enum/flag needed
- Recruitment Team signup is not self-service: a `recruiters` row is inserted manually (Supabase Studio or a seed script) after creating the `auth.users` entry via `supabase auth admin` — documented in the migration/README, not built as a UI
- Candidate self-signup: `auth.signUp()` followed by an insert into `candidates` (via a Postgres function/trigger on `auth.users` insert, scoped to signups originating from the candidate signup form, so a Recruitment Team-provisioned user doesn't accidentally get a candidate row too)

### Security Considerations

- RLS enabled on both new tables: a user may only `select` their own row
- Existing tables (`entities`, `entity_versions`, `relationships_v2`, `entity_facts`) currently have no RLS restricting them to authenticated Recruitment Team members — this ticket adds a policy requiring `auth.uid()` to exist in `recruiters` for read/write access, closing the gap called out as a non-goal in spec 0001
- Candidate access to `entities`/etc. is out of scope here (ticket 3 defines exactly what a candidate may read/write — e.g. only their own application rows)

## Open Questions

- [x] Should an existing (currently-open) deployment require an immediate hard cutover to login-required, or is a short transition window needed? — **Resolved 2026-09-03: immediate cutover, login required right away.**
