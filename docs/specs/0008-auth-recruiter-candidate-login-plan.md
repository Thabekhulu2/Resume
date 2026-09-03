# Implementation Plan: Auth — Recruitment Team & Candidate Login

**Spec:** [0008-auth-recruiter-candidate-login.md](./0008-auth-recruiter-candidate-login.md)
**Ticket:** Closes #18
**Status:** Implemented and verified 2026-09-03.

Open question resolved: immediate cutover — login required right away, no transition window.

## Implementation notes (deviations from the plan as written)

- Existing recruiter route files (`index.tsx`, `candidates/*`, `entities/*`) were moved under a new `_authenticated/` pathless layout directory (with a matching `routes/_authenticated.tsx` layout route carrying the `beforeLoad` guard) instead of adding a `beforeLoad` guard to each file individually. One guard, defined once, covers all current and future nested recruiter routes — safer than relying on every new route file remembering to add it. URLs are unchanged (`_authenticated` is pathless); only file locations and internal route ids moved.
- Did not scaffold an empty `_authenticated-candidate` layout route for "candidate routes beyond login/signup" — there are no such routes yet (that's ticket 3's scope), so there was nothing to gate. Deferred to ticket 3, which will add its own layout guard alongside its first protected candidate route.
- Recruiter provisioning uses the GoTrue admin REST API (`POST /auth/v1/admin/users` + insert into `recruiters`) via `scripts/create-recruiter.sh`, rather than a `supabase auth admin` CLI subcommand — the installed CLI version (2.115.0) doesn't have one for local projects.
- E2E verification used Playwright (installed ad hoc into the session scratchpad, chromium already cached locally) driving the real running frontend, since no browser-automation CLI was preinstalled in this environment.

## Phase 1: Migration — profile tables + RLS

New migration `supabase/migrations/20260903120000_recruiter_candidate_auth.sql`:

- [x] `create table recruiters (...)` — applied via `supabase migration up --local`
- [x] `create table candidates (...)` — applied
- [x] Enable RLS on both; policy: authenticated user may `select` only their own row (`auth.uid() = id`)
- [x] Trigger function on `auth.users` insert provisions a `candidates` row when `raw_user_meta_data->>'signup_role' = 'candidate'`; verified against a real signup (`candidates` row created with correct `full_name`)
- [x] RLS enabled on `entities`, `entity_versions`, `relationships_v2`, `fact_types`, `entity_facts`, `time_series_points`; policy requires `exists (select 1 from recruiters where id = auth.uid())`. Verified: `entities` has 71 rows; anon key and a candidate's access token each get `200 []` (RLS-filtered to zero rows); the recruiter account reads/writes normally through the app.

## Phase 2: Frontend auth infrastructure

- [x] `frontend/src/lib/auth.tsx` — `AuthProvider`/`useAuth` context wrapping `onAuthStateChange` + `getSession()`, exposing `{ user, role, loading, signIn, signUp, signOut }`; plus `getSessionRole()` for use in route `beforeLoad` (outside the React tree)
- [x] Wrapped `RouterProvider` with `AuthProvider` in `main.tsx`

## Phase 3: Routes & guards

- [x] `frontend/src/routes/login.tsx` — Recruitment Team login, navigates to `/` on success
- [x] `frontend/src/routes/candidate/login.tsx` — candidate login
- [x] `frontend/src/routes/candidate/signup.tsx` — candidate signup, calls `signUp` with `signup_role: 'candidate'`
- [x] `frontend/src/routes/_authenticated.tsx` layout `beforeLoad` guard: no session → `/login`; role !== `'recruiter'` → `/candidate/login`. Applies to all nested routes (`/`, `/candidates`, `/candidates/upload`, `/candidates/$id`, `/candidates/range/$min/$max`, `/entities/$entityType`, `/entities/$entityType/$id`)
- [x] Candidate-route guard: not built (see Implementation notes — nothing to gate yet)
- [x] Header (`__root.tsx`): shows user email + Logout when authenticated; hidden entirely on `/login`, `/candidate/login`, `/candidate/signup`

## Phase 4: Provisioning & local dev setup

- [x] `scripts/create-recruiter.sh` + a README "Auth" section documenting it
- [x] Created a working local recruiter account (`recruiter@resume.local`) and used it for Phase 5

## Phase 5: Verification (e2e + adversarial, per repo testing standard)

Ran via a real Chromium browser (Playwright) against the live local stack (`http://localhost:53900`), plus direct REST calls against the local Supabase API for the RLS checks. 15/15 browser assertions passed on the final run (one initial failure was a test-timing issue — reading the header before the async role lookup resolved — not a product bug; fixed by waiting for the header to update, then re-ran clean).

**e2e (happy path) — all passed:**
- [x] Recruiter logs in at `/login`, lands on `/`, header shows email + Logout
- [x] Candidate signs up at `/candidate/signup` — real signup verified end-to-end; `candidates` row created with correct `full_name` via the trigger
- [x] Candidate logs back in at `/candidate/login`
- [x] Recruiter logs out → redirected to `/login`; subsequent `GET /` redirects back to `/login` (session actually cleared, not just UI state)

**Adversarial — all passed:**
- [x] Wrong password (recruiter) → inline "Invalid login credentials", no crash, stays on `/login`
- [x] Unauthenticated direct hits on `/`, `/candidates`, `/candidates/upload` → all redirect to `/login`
- [x] Logged-in candidate hits `/` → redirected to `/candidate/login`, never sees recruiter data
- [x] Logged-in recruiter hits `/candidate/login` → renders the page, no redirect loop
- [x] Anon key direct REST call to `entities` → `200 []` despite 71 real rows (RLS confirmed closing the previously-open gap)
- [x] Candidate access token direct REST call to `entities` → `200 []` (no candidate policy exists — correctly denied)
- [x] Duplicate signup email → inline "User already registered", no crash
- [x] Empty login fields → client-side validation blocks submit with inline message, no request sent

## Out of scope for this plan (per spec's Non-Goals)

- Jobs page, job posting CRUD, candidate application flow (tickets 2–3)
- Password reset / email verification UI
- SSO / MFA
- Any candidate-facing page beyond login/signup

## Dependencies between phases

Phase 1 must land before Phases 2–3 (frontend needs the tables + trigger to exist). Phase 4's recruiter account must exist before Phase 5 verification can exercise the recruiter login path. Phase 5 runs last, against the real local stack.
