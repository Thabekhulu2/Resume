# Implementation Plan: Recruiter Decisions, Scheduled Interviews & Candidate Notifications

**Spec:** [0017-recruiter-decisions-interviews-notifications.md](./0017-recruiter-decisions-interviews-notifications.md)
**Ticket:** Closes #28
**Status:** Approved (Gate 1)

## Phase 1: Migration — tables, RLS, RPC, view

New migration `supabase/migrations/<timestamp>_recruiter_decisions_interviews_notifications.sql`:

- [x] `application_decisions`, `interviews`, `notifications` tables exactly per spec's SQL
- [x] RLS enabled on all three; recruiter-full-access policies on `application_decisions`/`interviews` (mirrors the existing `recruiters full access to X` pattern); `notifications` gets a recruiter-insert-only policy plus candidate select/update-own policies (no candidate insert policy)
- [x] `record_recruiter_decision(...)` function, no `security definer`, matching `create_entity_with_version_fn`'s convention
- [x] `recruiter_scheduled_interviews` view, `security_invoker = true`, granted to `authenticated`
- [x] Applied via `supabase migration up --db-url "postgresql://postgres:postgres@127.0.0.1:55322/postgres"` — `--local` didn't resolve since this environment's stack runs on non-default ports

## Phase 2: Engine — date/time input

- [x] `frontend/src/components/engine/forms/EngineInput.tsx`: extend the `type` prop union with `'date' | 'datetime-local'` — passes straight through to the underlying native input, no other logic change

## Phase 3: Scorecard redesign

- [x] `frontend/src/pages/candidate-scorecard.json` (revised):
  - New `decision` data source: `application_decisions` filtered by `candidate_id = params.id`, ordered `created_at desc`, `limit: 1`
  - 5-stage pipeline stepper (Applied/Screening/Assessment/Interview/Decision) — each stage's "done" state computed via chained single-comparison ternaries (no `===`/`||` mixed in one `{{}}`, per the ticket #27 lesson): stage 2 done if `status` is truthy; stage 3 done if `status === 'scored'` or `'failed'`; stage 4 done if latest `decision` is `'shortlisted'` or `'interview_scheduled'`; stage 5 done if latest `decision === 'rejected'`
  - Application status Badge showing the latest `decision` value (or hidden if none)
  - Fit score / "Why this score" / matched-skills / Experience sections restyled from the existing `facts`/`extracted` data — no new fields
  - Shortlist button: `apiCall rpc record_recruiter_decision` with `{p_candidate_id: params.id, p_decision: 'shortlisted'}`, `onSuccess`: refetch `decision`
  - Reject button: same shape, `p_decision: 'rejected'`
  - Schedule Interview button: opens a new modal (date/time `Input type="datetime-local"` + notes `Textarea`) whose submit calls the same RPC with `p_decision: 'interview_scheduled'`, `p_scheduled_at`, `p_notes`, then refetches `decision`
  - "← Back to Candidate History" link replaces the mockup's X/Close (per spec Non-Goals — no modal conversion)

## Phase 4: Scheduled Interviews page

- [x] `frontend/src/pages/recruiter-interviews.json` (new) — lists `recruiter_scheduled_interviews`, ordered `scheduled_at asc`; candidate name, job title, date/time, status
- [x] `frontend/src/routes/_authenticated/interviews/index.tsx` (new), matching the existing `_authenticated/jobs/index.tsx` pattern
- [x] `frontend/src/routes/__root.tsx`: recruiter `Sidebar` gains a "Scheduled Interviews" link (new icon import, e.g. `Calendar` from `lucide-react`), active-state check on `location.pathname === '/interviews'`

## Phase 5: Real candidate notifications

- [x] `frontend/src/pages/candidate-notifications.json` (revised): replace the `static` data source with a real one — `notifications` table, filtered implicitly by RLS (`recipient_id = auth.uid()`, no explicit filter needed since RLS already scopes it), ordered `created_at desc`
- [x] No route change needed — `_candidateAuth/notifications/index.tsx` (from spec 0016) already mounts this page unchanged

## Phase 6: Verification (e2e + adversarial, per repo testing standard)

- [x] Migration applied cleanly (`supabase migration up --db-url ...`, the running local instance uses non-default ports so `--local`/`--config` didn't resolve it — used `--db-url` directly instead)
- [x] `tsc --noEmit` diffed against the previously-captured baseline: only one line-number shift (from the new sidebar link), zero new error categories
- [x] All 3 new/changed page JSON files parse as valid JSON; re-swept all new/changed pages for the `===`/`||`-mixing mistake from ticket #27 — none found, only the already-proven-safe `!x || x.length === 0` idiom
- [x] Called `record_recruiter_decision` live via PostgREST `rpc/` for all 3 decisions against real self-applied candidates: `shortlisted` → correct `application_decisions` row + notification ("You've been shortlisted" / job title interpolated correctly); `interview_scheduled` with a real timestamp + notes → correct `interviews` row (`job_id` correctly derived from the candidate's `applied_to_job_id`) + notification with the date formatted as "20 Sep 2026 14:30"
- [x] Adversarial: called the RPC with `rejected` against a real recruiter-uploaded candidate (`applicant_id is null`) — decision recorded successfully (HTTP 200), notification count unchanged (confirmed via count query) — the `v_applicant_id is not null` guard works, no crash
- [x] RLS verified via anon-key PostgREST calls: `select` on `interviews` → `[]`; `select` on `notifications` → `[]`; direct `insert` on `notifications` → HTTP 401 `"new row violates row-level security policy"` — a candidate cannot fabricate their own notification, confirming the "no candidate insert policy" design
- [x] `recruiter_scheduled_interviews` view queried live (service-role key) — returns the scheduled interview with correct `job_title` (candidate_name was null for that particular test candidate since their resume name hadn't been extracted yet — expected, matches the underlying `entity_versions.data.name` being genuinely empty, not a view bug)
- [x] Dev server logs (`docker logs resume-frontend`) show clean HMR/reload for every touched file (`EngineInput.tsx`, `candidates/$id.tsx`, `__root.tsx`, `_candidateAuth/notifications/index.tsx`, plus the new `/interviews` route) — no build/transform errors
- [x] **Manual browser pass** (headless Playwright/Chromium, installed for this session since no `chromium-cli`/project run-skill existed; recommend `/run-skill-generator` to capture this as a reusable project skill): logged in as both a real recruiter (`recruiter@resume.local`) and a real candidate account (password reset via the local GoTrue admin API for both, dev-only local stack), then:
  - Candidate History → real scorecard for "Sipho Dlamini" (a real scored candidate, 55% fit, real matched skills/experience/reasoning) renders exactly per the mockup: stepper, status, Shortlist/Schedule Interview/Reject/Close
  - Clicked **Shortlist** → status badge flipped to "Shortlisted" live, no reload
  - Clicked **Schedule Interview** → modal opened with a real `datetime-local` input + notes textarea, filled and submitted → modal closed, status flipped to "Interview Scheduled", stepper's stage 4 "Interview" badge went active — all live
  - Navigated directly to a second candidate, clicked **Reject** → status flipped to "Rejected"
  - **Scheduled Interviews** page shows both interviews created during this session (correct candidate name, job title, date/time, notes)
  - Candidate portal: all 8 sidebar pages loaded with zero console errors and zero 4xx/5xx network responses across the entire session
  - **Notifications page shows a real "You've been shortlisted" notification** (from an earlier RPC test against this same candidate) with a working "Mark read" button — confirms the static-to-real rewiring works end-to-end, not just at the API layer
  - Apply wizard: opened a job's detail modal → Apply Now → stepped through all 3 wizard stages (Review → Questions, where the new native-radio "Do you have experience with SQL?" rendered and was clickable → Submit, showing the real FileInput with the Submit button correctly disabled pending a file)
  - One caught-and-fixed issue was in the **test script itself, not the app**: the first run raced ahead of the async sign-in redirect and mis-clicked a dynamic list item; fixed by waiting for the URL to actually leave `/login` before proceeding, and by navigating directly to known candidate IDs instead of clicking a live-loading list
- [x] Rapid-succession decisions exercised for real (not just double-click timing): the same candidate received a `shortlisted` decision (from an earlier direct RPC test) followed by `interview_scheduled` (from this browser pass) — the status badge correctly showed only the latest (`Interview Scheduled`), confirming the latest-wins design holds under real sequential writes

## Out of scope

- Hire/final-offer action, candidate-side interview view, editing/cancelling interviews, email/push notifications (spec Non-Goals)

## Dependencies between phases

Phase 1 must land before Phases 3-5 (all query/write against the new schema). Phase 2 must land before Phase 3's Schedule Interview modal. Phases 3, 4, and 5 are independent of each other and can be built in any order. Phase 6 runs last.
