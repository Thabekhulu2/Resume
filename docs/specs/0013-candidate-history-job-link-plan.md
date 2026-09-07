# Implementation Plan: Show Scored-Against Job on Candidate History

**Spec:** [0013-candidate-history-job-link.md](./0013-candidate-history-job-link.md)
**Ticket:** Closes #24
**Status:** Approved (Gate 1)

## Phase 1: Frontend — `candidate-history.json`

- [x] Extend `dataSources.candidates.select` to add the nested embed (confirmed working against the live local API):
  `relationships_v2!relationships_v2_child_id_fkey(is_current, job:entities!relationships_v2_parent_id_fkey(entity_versions(data, is_current)))`
- [x] Add one `Text` node to each candidate's `Card` (under the existing "Uploaded {{candidate.created_at}}" line), `if: "{{candidate.relationships_v2[0].job.entity_versions[0].data.title}}"`, rendering `Scored against {{candidate.relationships_v2[0].job.entity_versions[0].data.title}}`
- [x] No changes to `frontend/src/routes/_authenticated/candidate-history` route, no engine changes, no migration

## Phase 2: Verification (e2e + adversarial, per repo testing standard)

- [x] Confirmed the extended select still returns all 43 candidate rows (no accidental inner-join narrowing) via a live PostgREST query with a recruiter token
- [x] Candidates with a `candidate_scored_against_job` relationship (23 of 43) resolve a real job title — spot-checked `dabe2f17-...` (Sipho Dlamini) -> "Graduate Internships"
- [x] Candidates with no relationship (20 of 43, e.g. `2f289ad4-...`) return an empty `relationships_v2` array -> the `if` expression (lodash `get`-based path evaluation, safe on missing nested paths) resolves falsy, no extra line, no `undefined`/blank artifact
- [x] `tsc --noEmit`: same pre-existing baseline errors only (none in `candidate-history.json`/touched files; JSON-only change, engine's expression/type-check surface untouched)
- [ ] Real browser click-through — **not performed**, no browser-automation tool available in this environment (same known gap as tickets #2/#12/#21/#22). Verified instead via the exact PostgREST query + expression-evaluator semantics the page uses, per Phase 2's own findings above.

## Out of scope

- Backfilling relationships for candidates that never got one (spec Non-Goals)
- Any change to Applications page / `recruiter_job_applications` view
- Ordering when a candidate has more than one current relationship (spec's flagged limitation — accepted as-is)
