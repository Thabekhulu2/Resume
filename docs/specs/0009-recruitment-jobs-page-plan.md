# Implementation Plan: Recruitment Team Jobs Page

**Spec:** [0009-recruitment-jobs-page.md](./0009-recruitment-jobs-page.md)
**Ticket:** Closes #19
**Status:** Implemented and verified 2026-09-03 (one known gap — see Implementation notes).

## Implementation notes (deviations from the plan as written)

- **Known gap, not fixed:** whitespace-only titles (e.g. `"   "`) are not blocked client-side. The JSON-engine's expression language (`ExpressionEvaluator.ts`) supports path lookups, ternaries, and comparisons, but no string methods (`.trim()`), so the `disabled` check (`!state.newJobTitle`) treats whitespace as truthy. Fixing this properly means extending the shared expression evaluator — a cross-page change bigger than this ticket, not done without separate sign-off. Impact is low: no security/data-integrity issue (RLS still applies), worst case is a job with a blank-looking title that can only be closed, not edited (editing is a stated non-goal). Flagged to the user rather than silently passing verification.
- The two-inserts-in-sequence approach originally implied by "insert then insert" wasn't used — confirmed `apiCall`'s `insert` operation doesn't return inserted rows for chaining (no `.select()`), which is exactly why the plan already called for the atomic `create_entity_with_version` RPC instead. No deviation here, just confirming the plan's reasoning held.

## Phase 1: Migration — `create_entity_with_version` RPC

New migration `supabase/migrations/20260903130000_create_entity_with_version_fn.sql`:

- [x] `create function create_entity_with_version(p_entity_type text, p_data jsonb) returns uuid` — inserts one row into `entities`, then one row into `entity_versions` (`version_number = 1`, `is_current = true`), returns the entity id. Plain `security invoker` — relies on the caller already passing ticket 1's `recruiters` RLS check on both tables.
- [x] `grant execute on function create_entity_with_version(text, jsonb) to authenticated`
- [x] Applied via `supabase migration up --local`; smoke-tested directly via SQL, then confirmed end-to-end that `entity-list.json`'s previously-broken "New Entity" button now works

## Phase 2: Jobs page (JSON-engine)

`frontend/src/pages/jobs.json`:

- [x] `dataSources.jobs`: filters on `entity_type`, `entity_versions.is_current`, and `entity_versions.data->>status` (PostgREST embedded-resource jsonb path filter — confirmed working against the real API)
- [x] Two toggle buttons ("Open" / "Closed") switching `state.statusFilter`
- [x] List: title, location (if present); per-row "Close"/"Reopen" button
- [x] Close/Reopen: `apiCall` `update` on `entity_versions`, matched on `entity_id` + `is_current`, rebuilding the full `data` object (title/jd_text/location preserved, status flipped) since the engine has no partial-jsonb-merge/spread support
- [x] Empty state text
- [x] "New Job" modal: Title (`name: "title"`, required), Location (`name: "location"`, optional), Description (`name: "description"`, required)
- [x] Modal save via `create_entity_with_version` RPC; `onSuccess` closes modal, clears form, resets to the Open tab, refetches
- [x] Client-side validation: Create button disabled when title or description is empty — **partial**, see Implementation notes (whitespace-only not caught)

## Phase 3: Route & nav

- [x] `frontend/src/routes/_authenticated/jobs/index.tsx`
- [x] `__root.tsx` Sidebar: "Jobs" link (Briefcase icon) between Dashboard and Score a Candidate

## Phase 4: Verification (e2e + adversarial, per repo testing standard)

Ran via real Chromium (Playwright) against the live local stack, logged in as the recruiter account from #18. 12/13 assertions passed; the 1 failure is the documented known gap above, not a crash or security issue.

**e2e (happy path) — all passed:**
- [x] Recruiter sees "Jobs" in the sidebar, navigates to `/jobs`
- [x] Create a job posting via the modal — appears in the open list immediately, no reload
- [x] Close it — disappears from the open list; "Closed" tab shows it
- [x] Reopen it — reappears in the open list
- [x] Previously-broken `entity-list.json` "New Entity" button now works (RPC fix confirmed against `/entities/job_description`)

**Adversarial:**
- [x] Empty title + empty description → Create button disabled, no request sent
- [ ] Whitespace-only title → **not blocked** (known gap, documented above)
- [x] Two jobs created back-to-back → both appear independently, no data bleed
- [x] Unauthenticated direct hit on `/jobs` → redirected to `/login`
- [x] Candidate-role access to Jobs data → covered by ticket 1's existing RLS tests (no candidate policy on `entities`/`entity_versions`, unchanged by this ticket) — not re-tested standalone since nothing about this ticket's migration touches those policies

## Out of scope for this plan (per spec's Non-Goals)

- Editing title/description/location after creation
- Candidate-facing anything (ticket 3)
- Approval workflows / multi-recruiter collaboration / edit audit trail

## Dependencies between phases

Phase 1 must land before Phase 2 (the create-job flow calls the new RPC). Phase 3 depends on Phase 2 existing (nothing to route to otherwise). Phase 4 runs last, against the real local stack.
