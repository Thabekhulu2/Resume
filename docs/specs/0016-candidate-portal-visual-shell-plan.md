# Implementation Plan: Candidate Portal Visual Shell

**Spec:** [0016-candidate-portal-visual-shell.md](./0016-candidate-portal-visual-shell.md)
**Ticket:** Closes #27
**Status:** Approved (Gate 1)

## Phase 0: Spike — de-risk the two open items before building 6 pages on top of them

- [x] Confirmed `static` data sources render end-to-end by code inspection rather than a throwaway scratch page: `useDataSources.ts:116-122` returns `Promise.resolve(source.data)` directly as the query result with `enabled: true, staleTime: Infinity` — identical shape to the working `supabase`/`api` branches that already feed `{{data.x}}` expressions and `each` loops elsewhere. All 6 static-only pages built in Phase 3 exercise this path for real; no separate scratch page was needed given how directly this mirrors the already-proven branches.
- [x] Sidebar approach: extended `RoleAwareSidebar` in `frontend/src/routes/__root.tsx` — `return role === 'candidate' ? <CandidateSidebar /> : <Sidebar />;` — as planned. Smaller diff, recruiter `Sidebar`/branch untouched.

## Phase 1: Engine — add `Radio` component

- [x] `frontend/src/components/engine/forms/EngineRadio.tsx` (new) — mirrors `EngineCheckbox.tsx`'s structure; uses a plain native `<input type="radio">` (no shadcn radio-group primitive existed in this repo) styled to match the existing form-control look
- [x] Registered in `frontend/src/registry/index.ts` and `frontend/src/components/engine/index.ts` alongside the other form components
- [x] No component-type enum exists to update — `ComponentDefinition.type` is a plain `string` resolved dynamically via the registry (`types.ts:49`), so registering `Radio` in the registry is sufficient; confirmed by reading `types.ts` before assuming an enum existed
- **Known, pre-existing-pattern type looseness**: `tsc --noEmit` flags `Radio` in the registry (required `name`/`value` props vs. the registry's loose `EngineComponentProps` type) — but this is the exact same category of error already present for `EngineLink` (`to` required) and `EngineIcon` (`name` required) before this ticket. Confirmed via a baseline diff (`git stash`/`tsc`/`stash pop`): zero *new* error categories introduced, only this one expected addition plus line-number shifts in unrelated pre-existing errors.

## Phase 2: Candidate sidebar shell

- [x] `frontend/src/routes/__root.tsx`: new `CandidateSidebar` + `CandidateNavLink` components with the prototype's nav items — Dashboard, Apply for Jobs, My Applications, Saved Jobs, Notifications (static badge "3"), Messages (static badge "2"), then "My account": My Profile, Job Alerts, Settings — same active-link highlighting pattern as the existing `Sidebar`
- [x] **Deviation from plan**: dropped the prototype's sidebar "Logout" nav item — the app already has a working Logout button in the shared `Header` (used by both roles); duplicating it in the sidebar would be redundant, not a missing feature
- [x] Recruiter `Sidebar` component and its branch untouched (confirmed via `tsc` baseline diff and by re-reading the function after edits)

## Phase 3: Routes + pages

Each new route follows the exact pattern of the existing `_candidateAuth/apply/index.tsx` (`createFileRoute` + `UIEngine` + page JSON + `params: { candidateId: user?.id }`).

- [x] `frontend/src/pages/candidate-dashboard.json` + `.../dashboard/index.tsx` — stat tiles, "Recommended for you", "Recent activity", `static` data
- [x] `frontend/src/pages/candidate-jobs.json` (existing, revised) — job-card grid using the real `candidate_open_jobs` data source (unchanged query, verified live via curl: `id`/`title`/`location`/`jd_text`/`already_applied` all present) and already-applied branch, unchanged; new `viewJobModal` shows the real `jd_text`; `applyModal` extended into a 3-step wizard (`state.wizardStep` 1/2/3, `if`-gated content blocks) — step 1 static review, step 2 Textarea + new Radio (not persisted), step 3 reuses the existing FileInput-upload-then-invoke submission verbatim, unchanged
- [x] `frontend/src/pages/candidate-applications.json` + **`.../my-applications/index.tsx`** — route path changed from the plan's assumed `/applications` to **`/my-applications`**: `/applications` is already the recruiter Applications page's real URL (`_authenticated/applications/index.tsx` resolves to that exact path); reusing it would have collided. Queries `recruiter_job_applications` with no filter (RLS scopes to the candidate's own rows); status pill + a 3-badge progress indicator (Submitted/Reviewed/Decision) mapped from this system's real `scoring`/`scored`/`failed` values, not the prototype's recruiter-stage vocabulary
- [x] `frontend/src/pages/candidate-saved-jobs.json` + `.../saved-jobs/index.tsx` — `static`
- [x] `frontend/src/pages/candidate-notifications.json` + `.../notifications/index.tsx` — `static`
- [x] `frontend/src/pages/candidate-messages.json` + `.../messages/index.tsx` — `static` thread list + Textarea/Button giving an inline "Message sent" Alert, not persisted
- [x] `frontend/src/pages/candidate-profile.json` + `.../profile/index.tsx` — **deviation from plan**: built fully `static`, not "real name/résumé where available" as originally planned. Discovered during implementation (`start-scoring-workflow`'s candidate-jobs.json call path never passes `candidate_entity_id`) that this data model creates a **new** `entities` row per application, not one canonical entity per candidate — so there is no single reliable "my profile" record to query yet. Querying by `applicant_id` with `.single()` would error for any candidate with more than one application. Flagging as a finding for a future ticket rather than shipping a fragile/incorrect query.
- [x] `frontend/src/pages/candidate-job-alerts.json` + `.../job-alerts/index.tsx` — `static`, Input/Select/Checkbox/Radio all exercised; "Create Alert" gives an Alert acknowledgement, not persisted
- [x] `frontend/src/pages/candidate-settings.json` + `.../settings/index.tsx` — `static` toggles

**Bug caught and fixed during implementation**: an early draft of the My Applications badge-variant expression combined `===` with `||` in one `{{}}` (`application.status === 'scoring' || application.status === 'scored' || ...`). Reading `ExpressionEvaluator.ts` directly confirmed this breaks — the comparison-regex match runs before the `||`-split, so it mis-parses combined expressions exactly like the memory note describing this engine's expression-parser limits warns. Fixed by chaining single-comparison ternaries (`a === 'x' ? v : a === 'y' ? v : 'default'`) instead, which the parser handles correctly (traced through `evaluatePath`'s ternary branch). Swept all new/changed pages for the same mistake afterward — none found elsewhere; the one pre-existing `!x || x.length === 0` idiom already used in `candidate-jobs.json`/reused in the new pages is a different, already-proven-safe shape (single trailing comparison, not multiple `===` each OR'd).

## Phase 4: Verification (e2e + adversarial, per repo testing standard)

- [x] `tsc --noEmit`, compared against a captured pre-change baseline (via `git stash`/`tsc`/`stash pop`): zero new error categories beyond the expected `Radio` registry-looseness entry (see Phase 1) — confirmed line-by-line via `diff`
- [x] All 8 new/changed page JSON files parse as valid JSON (`node -e "JSON.parse(...)"` over every `candidate-*.json`) — catches structural typos a visual check alone would miss
- [x] Dev server logs (`docker logs resume-frontend`) show only clean HMR/reload cycles for every file touched — no build/transform errors surfaced at any point
- [x] `candidate_open_jobs` queried live via PostgREST (service-role key, the real path the page uses) returns `id`/`title`/`location`/`jd_text`/`already_applied` with real content (confirmed two real open jobs' full `jd_text`) — the job cards and "View details" modal have real data to render, not just a schema assumption
- [x] Confirmed no new migrations were introduced this ticket (`git status --short supabase/migrations/` shows only the unrelated #26 migration) — frontend-only, per spec Non-Goals
- [x] RLS unchanged: anon (unauthenticated) key against `recruiter_job_applications` still returns `[]` — the My Applications page's data source doesn't loosen access
- [x] Route collision caught and fixed before it could ship: My Applications was re-pathed to `/my-applications` (see Phase 3) after confirming `/applications` already resolves to the recruiter Applications page
- [x] Expression-parser bug caught and fixed via direct source-reading of `ExpressionEvaluator.ts`, not by trial-and-error in a browser (see Phase 3 note) — the safest verification available for engine behavior without a browser tool
- [ ] **Not verified — no browser-automation tool available in this environment** (same gap as prior tickets, e.g. spec 0012's plan): an actual click-through as a logged-in candidate — sidebar navigation between all 8 pages, opening the job-details modal, stepping through the 3-step wizard's Back/Continue buttons, submitting a real résumé through the final step and confirming `start-scoring-workflow` still fires and the already-applied branch updates, viewing My Applications rendering real rows, and toggling the static Saved Jobs/Notifications/Messages/Job Alerts/Settings interactions. This is the largest remaining gap — recommend a manual pass in the browser before considering this ticket fully done.
- [ ] Not verified: emptied-array empty-state rendering for Saved Jobs/Notifications (code path exists via the same `if: "{{!data.x || data.x.length === 0}}"` idiom already proven elsewhere, but not exercised against an actually-emptied static array)
- [ ] Not verified: recruiter-side regression click-through (Jobs/Applications/Candidate History/Score a Candidate pages) — the diff to recruiter-facing code is limited to one `return` line in `RoleAwareSidebar`, which a recruiter session never touches (role check short-circuits to the unchanged `<Sidebar/>` branch), so risk is low, but no live recruiter-session click-through was performed

## Out of scope

- Persisting any new interaction (spec Non-Goals) — Saved Jobs, Notifications, Messages, Job Alerts, Settings toggles, and the wizard's application-questions step are visual/UI-only this ticket.
- Any new migration, table, or edge function.
- Pixel-identical CSS port of the prototype's inline styles (spec's stated assumption).

## Dependencies between phases

Phase 0 must land before Phase 3 (both the static-data-source spike and the sidebar-approach decision gate every subsequent page). Phase 1 (Radio) must land before the apply-wizard part of Phase 3. Phase 2 must land before Phase 3 (pages assume the sidebar/nav exists to link to). Phase 4 runs last, against the real local stack.
