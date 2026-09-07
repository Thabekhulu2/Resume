# Implementation Plan: Recruiter Applications Page

**Spec:** [0012-recruiter-applications-page.md](./0012-recruiter-applications-page.md)
**Ticket:** Closes #23
**Status:** Approved (Gate 1)

## Phase 1: Migration — `recruiter_job_applications` view

New migration `supabase/migrations/20260904120000_recruiter_job_applications_view.sql`:

- [x] `create view recruiter_job_applications with (security_invoker = true) as ...` per spec's SQL — joins the candidate's current version, the job they applied to (via `entity_versions.data->>applied_to_job_id`), and a scalar-subquery latest `entity_facts.value` for score. `where entity_type = 'candidate' and applicant_id is not null`.
- [x] `grant select on recruiter_job_applications to authenticated`
- [x] Apply via `supabase migration up --local`

## Phase 2: Engine change — conditional filters

- [x] `frontend/src/engine/types.ts`: add optional `if?: string` to `FilterDefinition`
- [x] `frontend/src/data/queryBuilder.ts`: in `buildSupabaseQuery`'s filter loop, `if (filter.if && !evaluateExpression(filter.if, context)) continue;` before calling `applyFilter` — skip filters whose `if` is falsy instead of always applying every entry
- [x] ~~`chunkSize`/`chunkIndex` on `ComponentDefinition` + `paginate` action~~ — added for an initial Prev/Next-paginated design, then fully reverted after a follow-up request replaced it with a plain wrapping grid (Phase 3). No trace left in `types.ts`/`ComponentRenderer.tsx`/`ActionDispatcher.ts` — confirmed via grep.

## Phase 3: Frontend page

- [x] `frontend/src/pages/applications.json` (new, then revised twice — once for Kanban tabs, once to replace click-through pagination with a wrapping grid and add the status toggle):
  - `dataSources.jobs`: `entities` where `entity_type = 'job_description'`, select `id, entity_versions(data, is_current)`, with a conditional status filter `{field: "entity_versions.data->>status", op: "eq", value: "{{state.jobStatusFilter}}", if: "{{state.jobStatusFilter !== 'all'}}"}` — skipped entirely when `jobStatusFilter` is `'all'` (default), so every posting shows, exactly matching the Jobs menu's full set
  - `dataSources.applications`: `recruiter_job_applications`, `select: "*"`, one conditional filter `{field: "job_id", op: "eq", value: "{{state.jobFilter}}", if: "{{state.jobFilter}}"}`, ordered by `applied_at desc`
  - Status toggle: Open / Closed / All jobs buttons (mirrors `jobs.json`'s own toggle), each a `sequence` action setting `jobStatusFilter` AND clearing `jobFilter` (so switching status never leaves a stale single-job selection filtering the list underneath)
  - Job tabs: a plain 3-column `Grid` (`columns: 3`) of job tabs (rendered as `Button`, not `Card` — `EngineCard` doesn't forward `onClick`), each showing job title + open/closed badge, highlighted when selected via `state.jobFilter === job.id`. CSS grid wraps naturally into additional rows of 3 as more jobs are visible — no pagination/JS math needed.
  - Each application row: candidate name (fallback to resume filename), job title, status badge (scoring/scored/failed, same three-state pattern as `candidate-history.json`), score if present, "Score" button, "View" link
  - "Score" button: `apiCall invoke start-scoring-workflow` with `{candidate_entity_id: application.candidate_id, resume_storage_path: application.resume_file_path, job_description_entity_id: application.job_id}`, `onSuccess: refetch applications`
  - "View" link: `to: "/candidates/{{application.candidate_id}}"` (existing route, unchanged)
- [x] `frontend/src/routes/_authenticated/applications/index.tsx` (new) — same pattern as `_authenticated/jobs/index.tsx`
- [x] `frontend/src/routes/__root.tsx`: add an "Applications" sidebar link (new icon import, e.g. `Inbox` from `lucide-react`) between "Jobs" and "Score a Candidate", active-state check on `location.pathname === '/applications'`

## Phase 4: Verification (e2e + adversarial, per repo testing standard)

**No browser-automation tool is available in this environment** (same gap as tickets #2, #21, #22) — verified below via curl/psql against the exact queries and API calls the page's JSON config issues, plus `tsc --noEmit` (no new errors) and confirming the router plugin registered the route. NOT verified: actually clicking the buttons in a browser. That gap should be closed with a manual click-through.

- [x] `jobs` and `applications` dataSource queries return exactly the shapes the JSON references (`job.entity_versions[0].data.title`, `application.candidate_name/status/job_title/score/...`) — confirmed via curl against the live PostgREST API
- [x] Job filter: querying the view with `job_id=eq.<id>` (the exact filter the page applies when a job button is clicked) returns only that job's applicant — confirmed
- [x] "Score" button's exact `apiCall` payload (`candidate_entity_id`, `resume_storage_path`, `job_description_entity_id`) re-triggers scoring for an existing applicant without creating a duplicate candidate entity, and the view reflects the status change — confirmed (candidate `6f0b7b81-...` re-scored via curl, `candidate_entity_id` unchanged, status flipped to `scoring`)
- [x] RLS inheritance: a candidate's own token against `recruiter_job_applications` with no filter returns only their own row, with `score: null` (no `entity_facts` access) — confirmed
- [x] `tsc --noEmit`: no new errors introduced (same two pre-existing baseline errors, confirmed line-for-line against the original baseline capture) after both the pagination revert and the grid/status-toggle rebuild; router plugin registered `/applications` in `routeTree.gen.ts`; frontend hot-reloaded every change cleanly (one transient esbuild error during an earlier intermediate save, resolved by the next save)
- [x] `grep` confirms zero remaining references to `chunkSize`/`chunkIndex`/`PaginateAction`/`handlePaginate`/the `'paginate'` action case anywhere in the engine — the revert is complete, no dead code left behind
- [x] Status-filter counts confirmed against the real local data: 40 total job postings, 3 `status=open`, 5 `status=closed` (the remaining 32 are older QA jobs with no status set — correctly still included under "All jobs", correctly excluded from both "Open" and "Closed")
- [x] Real browser click-through (Playwright, headless Chromium, logged in as `recruiter@resume.local`): sidebar link renders and highlights correctly; the job-tab grid renders 3 per row wrapping into additional rows (not paginated); "All jobs" shows all 40 postings; Score/View buttons work — View navigates to the real scorecard (`/candidates/:id`), which shows "Scoring in progress" after re-triggering; a job with zero applicants (Bestmed Medical Scheme) shows "No applications for this filter."; switching Open/Closed/All after selecting a specific job tab clears that selection (row count reverts to all 4 applications, highlight border removed) — confirms the sequence action's second `setState` step.
  - **Bug found and fixed during this pass**: `applications.json`'s `jobs` dataSource selected `entity_versions(data, is_current)` (no `!inner`), unlike the established pattern in `jobs.json` (`entity_versions!inner(...)`). PostgREST only prunes the *embedded* `entity_versions` array when filtering on it without `!inner` — the top-level `entities` row set is untouched. So clicking "Open" or "Closed" still returned all 40 job entities, but the ~35 non-matching ones came back with an empty `entity_versions` array, rendering as blank/skeleton grid cells reading `job.entity_versions[0].data.title` on `undefined`. Fixed by adding `!inner` to match `jobs.json`'s select. Re-verified: "Closed" now shows exactly 5 cells, "Open" exactly 3, "All jobs" all 40 (including title-less legacy QA jobs, unchanged) — no blank cells in any state.
  - Not verified: direct nav to `/applications` while logged out or as a candidate (relies on `_authenticated`'s existing guard, unchanged by this ticket, not re-tested here).

**Known pre-existing gap surfaced during verification (not caused by this ticket):** the two applicants used for the "Score" re-trigger test have fake/invalid resume content from earlier QA sessions, so `pypdf` fails to parse them and the workflow's activity retries indefinitely rather than settling into `failed` — this is a resume-parsing/retry-policy issue in the existing scoring pipeline, unrelated to the Applications page itself. A real PDF resume was not manufactured to force a genuine `scored` outcome here, since a status transition to `scoring` already proves the button/API wiring works; getting all the way to `scored` only demonstrates the pre-existing scoring pipeline, already covered by other tickets' tests.

## Out of scope

- Bulk actions, application withdrawal, editing (spec Non-Goals)
- Any change to Candidate History or the auto-score-on-apply behavior

## Dependencies between phases

Phase 1 must land before Phase 3 (page queries the view). Phase 2 must land before Phase 3 (page's job filter relies on the conditional-filter engine change). Phase 4 runs last, against the real local stack.
