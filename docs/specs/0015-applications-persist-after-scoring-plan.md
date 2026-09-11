# Implementation Plan: Applications Persist After Scoring

**Spec:** [0015-applications-persist-after-scoring.md](./0015-applications-persist-after-scoring.md)
**Ticket:** Closes #26
**Status:** Approved (Gate 1)

## Phase 1: Migration — fix `recruiter_job_applications.job_id` derivation

New migration `supabase/migrations/20260911120000_recruiter_job_applications_job_id_fallback.sql`:

- [x] `create or replace view recruiter_job_applications with (security_invoker = true) as ...` — adds a `left join lateral (select r.parent_id from relationships_v2 r where r.child_id = c.id and r.relationship_type = 'candidate_scored_against_job' and r.is_current order by r.created_at desc limit 1) scored_rel on true`, and computes `job_id` / the `entities j` join via `coalesce((cv.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id)`. Everything else (candidate_name, resume_file_path, status, score subquery, applied_at, the `where` clause) unchanged from the existing view.
  - Deviation from spec's plain `left join`: used a `left join lateral ... limit 1` instead, so a candidate with multiple current `candidate_scored_against_job` relationships (re-scored against different jobs) still yields exactly one view row (most recent relationship wins) instead of fanning out — closes the risk called out in this plan's original adversarial check before it could surface as a bug.
- [x] `grant select on recruiter_job_applications to authenticated` (unchanged, re-stated since `create or replace view` doesn't drop grants but keep it explicit per existing migration's own convention)
- [x] Applied via `supabase migration up --local` — applied cleanly, no errors

## Phase 2: Verification (e2e + adversarial, per repo testing standard)

No frontend/workflow code changes in this ticket, so verification is against the live Postgres view (via `docker exec ... psql`, `psql` not installed on this host) and PostgREST (the real path `application-job.json` queries), against real pre-existing local data rather than freshly manufactured scenarios.

- [x] Baseline repro via forensic evidence instead of a live before/after (migration was already applied when verification started; reset-and-replay would have cost the existing local dataset for no added confidence): for candidate `27b8272d-...` (Kagiso Phiri, applied to "Graduate in Training Programme", status `scored` from an earlier session), confirmed directly against `entity_versions` that their current version's `data->>'applied_to_job_id'` is empty/absent, and confirmed `relationships_v2` holds `candidate_scored_against_job` linking them to job `85cb3766-...`. This is the exact mechanism the spec describes — proves the old view (no fallback) would have resolved `job_id = NULL` for this real candidate, dropping them from their job's tab.
- [x] Post-fix: same candidate now resolves `job_id = 85cb3766-...` / `job_title = "Graduate in Training Programme"` in `recruiter_job_applications` — confirmed via `docker exec supabase_db_resume psql`.
- [x] Confirmed via PostgREST (service-role key, the real REST path the frontend page uses): `GET /rest/v1/recruiter_job_applications?job_id=eq.85cb3766-...` returns **both** a still-`scoring` applicant and Kagiso Phiri (`scored`) for that job — the literal query `application-job.json` issues, proving the fix end-to-end through the API layer, not just raw SQL.
- [x] No fan-out: `select candidate_id, count(*) group by candidate_id having count(*) > 1` returns zero rows across all 7 current applicants (mix of `scored`/`scoring`) — the `lateral ... limit 1` guard works as intended.
- [x] Unscored/still-`scoring` applicants unaffected: all 4 `scoring`-status applicants in the live data resolve `job_id`/`job_title` via `applied_to_job_id` exactly as before (no relationship row needed/used for these) — confirms the fallback is additive, doesn't change the already-working path.
- [x] RLS unchanged: anon (unauthenticated) key against the view returns `[]` (200, empty), not an error and not other tenants' rows — same shape as before this change; the added `relationships_v2` join doesn't widen access since recruiters already have unrestricted select on it (spec 0008) and `security_invoker` is preserved.
- [ ] Not verified: a `failed`-status applicant — none exists in current local data to check against; the code path is untouched (`applied_to_job_id` was never cleared for failures) so no regression is expected, but this wasn't empirically confirmed.
- [ ] Not verified: a live re-score cycle (`scored` → `scoring` → `scored` again) end-to-end, and the "different job than applied to" adversarial case — no candidate in local data has been re-scored against a second job. Structurally covered by the `lateral limit 1 order by created_at desc` guard (always yields the most recent relationship, never fans out), but not exercised against a live re-score.
- [ ] Not verified: real browser click-through (no browser-automation tool available in this environment, consistent with prior tickets in this repo — e.g. spec 0012's plan). The PostgREST-level check above exercises the exact query the page issues; clicking through the actual rendered tab in a browser was not additionally performed.

## Out of scope

- Any change to `frontend/src/pages/applications.json`, `application-job.json`, the scoring workflow, or `update_entity_scd2` (spec Non-Goals).
- Resolving which job "wins" when a candidate has multiple `candidate_scored_against_job` relationships against different jobs (spec's noted edge case, pre-existing, shared with spec 0013).

## Dependencies between phases

Phase 1 must land before Phase 2's post-fix checks (the baseline-repro checks in Phase 2 run first, against the unmigrated view, to confirm the bug; the rest run after).
