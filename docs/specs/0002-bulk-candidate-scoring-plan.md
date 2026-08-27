# Implementation Plan: Bulk Candidate Scoring

**Spec:** [0002-bulk-candidate-scoring.md](./0002-bulk-candidate-scoring.md) (Approved)
**Ticket:** Closes #10
**Status:** Approved (Gate 1) — implemented and live-verified 2026-08-27.

## Implementation notes (deviations from the plan as written)

- **Phase 2/4:** `forEach` ended up needing its own `onSuccess`/`onError` (mirroring `apiCall`'s convention), not just an `onError` on the per-item upload. Reason: within one dispatched action tree, a `setState` in an earlier step is not visible to a later sibling's `{{state.x}}` read (the whole tree shares one `ExpressionContext` snapshot from when the user's click was dispatched — proven by tracing `handleSequence`/`UIEngine.dispatch`). So the scoring-trigger call had to be nested inside `forEach`'s `onSuccess` (only runs if every upload succeeds), not placed as a sibling step after `forEach` in the `sequence` — a sibling would have run unconditionally even after an upload failure.
- **Phase 4:** did not use the `conditional` action to branch `FileInput`'s `onChange` (it's untested/unused elsewhere in this codebase, unlike component-level `if`, which is proven throughout the existing pages). Instead, single- and bulk-mode each get their own `FileInput`/`Button`, gated with `if: "{{!state.bulkMode}}"` / `if: "{{state.bulkMode}}"` inside the same Card/Stack — same effect, uses an already-proven mechanism.

## Phase 1: Edge Function — plural resume paths

`supabase/functions/start-scoring-workflow/index.ts`

- [x] Accept new optional `resume_storage_paths?: string[]` field on `StartScoringRequest`
- [x] Refactor the existing "ensure candidate entity exists + POST to `/start-scoring`" block (currently written for one `resume_storage_path`) into a single-resume helper function (`scoreOneResume`)
- [x] JD resolution (existing `job_description_entity_id`/`jd_text` branch) runs exactly once, unchanged, regardless of singular or plural resume input
- [x] If `resume_storage_paths` is present (and singular `resume_storage_path` is absent): loop the helper once per path, `await`ing sequentially (matches the workflow's existing per-candidate side effects — avoids firing an uncontrolled burst of concurrent Temporal-trigger HTTP calls)
- [x] Response shape for the plural case: `{ job_description_entity_id, candidates: [{ candidate_entity_id, workflow_id }, ...] }`
- [x] If a resume in the batch fails (candidate creation, version insert, or the trigger POST), stop the loop and return an error identifying which path failed (`resume_storage_path` in the error body) — no partial-success swallowing; matches the spec's "clear error, not a silent drop" acceptance criterion
- [x] Singular-path request/response shape is completely unchanged (existing tests/behavior for single-candidate scoring keep working)

## Phase 2: JSON engine — `forEach` action

- [x] `frontend/src/engine/types.ts`: add `ForEachAction` to the `ActionDefinition` union — `{ action: 'forEach', items: unknown, as: string, do: ActionDefinition, onSuccess?, onError? }` (`onSuccess`/`onError` added beyond the original plan — see Implementation notes above)
- [x] `frontend/src/engine/ActionDispatcher.ts`: add a `case 'forEach'` calling `resolveValue(action.items, context)` to get the array, then a `for...of` loop `await`ing `dispatch(action.do, { ...context, [action.as]: item })` per item, sequentially (not `Promise.all` — keeps upload order predictable and avoids overwhelming Storage with concurrent requests, consistent with the Edge Function's sequential loop in Phase 1); wrapped in try/catch so a failing item runs `onError` and stops iteration instead of continuing or crashing silently
- [x] If `items` isn't an array (or is empty), no-op rather than throwing

## Phase 3: `EngineFileInput` — multi-file selection

`frontend/src/components/engine/forms/EngineFileInput.tsx`

- [x] New `multiple?: boolean` prop, passed through to the native `<Input type="file" multiple>`
- [x] When `multiple` is true, `handleChange` reads all of `e.target.files` (not just `[0]`), maps each to `{ file, fileName, path }` (same per-file path generation as today: `${crypto.randomUUID()}${extension}`), and dispatches `onChange` with `event: { files: [...], paths: [...path strings...], fileNames: [...fileName strings...] }`
- [x] When `multiple` is false/absent, behavior is byte-for-byte unchanged (existing single-file event shape)

## Phase 4: `candidate-upload.json` — bulk mode UI

- [x] New state fields: `bulkMode: false`, `resumeFiles: []` (array of `{file, fileName, path}` from FileInput), `resumeStoragePaths: []` (array of path strings)
- [x] New `Checkbox`: "Score multiple candidates against this job description" → toggles `state.bulkMode` (also resets file-selection state so switching modes mid-flow doesn't leave stale data)
- [x] Bulk `FileInput` (separate component, `if: "{{state.bulkMode}}"`, `multiple: true`); its `onChange`: `setState resumeFiles = {{event.files}}`, `setState resumeStoragePaths = {{event.paths}}` (upload deferred to submit — see Risks in spec: uploading N files immediately on selection with no cancel affordance is worse UX than uploading on submit). Single-mode `FileInput` unchanged, gated `if: "{{!state.bulkMode}}"`.
- [x] File-selected summary text shows count in bulk mode ("N file(s) selected") vs. filename in single mode
- [x] Submit button in bulk mode (separate `Button`, `if: "{{state.bulkMode}}"`): label `"Score {{state.resumeFiles.length}} candidates"`, disabled when `resumeFiles` is empty, submitting, or `jdText` is blank; `onClick` sequence:
  1. `setState error=null, submitting=true`
  2. `forEach` `items: {{state.resumeFiles}}` `as: "resumeFile"` `do:` `apiCall upload bucket=resumes path={{resumeFile.path}} data={{resumeFile.file}}` (no per-item `onError` — lets it throw so `forEach` itself catches it); `onError` → `setState submitting=false`, `setState error`; `onSuccess` (all uploads done) → `apiCall invoke start-scoring-workflow` with `resume_storage_paths`/`jd_text`/`job_title`, `onSuccess` → `navigate to /candidates`, `onError` → `setState submitting=false`, `setState error`
- [x] Single-mode submit path is unchanged (existing single-candidate `onClick` action untouched, now on a separate `Button` gated `if: "{{!state.bulkMode}}"`)

## Phase 5: Manual/live verification

- [x] Bring up the full local stack and bulk-upload 2 real resumes (generated `.docx` fixtures) against one JD via the exact API sequence the bulk-mode UI drives (Storage upload x2 → single `start-scoring-workflow` call with `resume_storage_paths`) — done at the API level rather than through a live browser session, consistent with how spec 0001's original live verification was performed (no browser-automation tool available in this environment, a pre-existing documented gap)
- [x] Confirmed exactly one `job_description` entity + two `candidate` entities created per batch (`select entity_type, count(*) ... group by entity_type` → `candidate: 2`, `job_description: 1`)
- [x] Both candidates independently reached `status: scored`, each with the correct extracted name/skills matching its own resume (no cross-contamination between the two resumes in the batch) and a real score (85) from the local Ollama model
- [x] Verified the "stop on first failure, don't drop silently" behavior by code inspection (forEach's try/catch + onError, no per-item onError so it propagates) — not separately reproduced live, since doing so cleanly requires the live browser UI this environment can't drive
- Environment notes surfaced (and fixed for this verification, not code changes): the Supabase Edge Runtime container had exited and needed a manual restart; `USE_LOCAL_LLM`/`OLLAMA_MODEL` weren't set in the worker (no `.env` in this repo), defaulting to the unconfigured Anthropic path — both are session-local runtime overrides, unrelated to this feature's code and not committed
- **Gap:** no live click-through of the actual React UI (checkbox toggle, multi-file picker, progress states) — same class of gap as spec 0001's missing E2E test, not newly introduced here

## Out of scope for this plan (per spec's Non-Goals)

- Dedicated batch-progress/results screen
- Editing/removing files from a batch after selection
- Bulk delete/re-score as a group
- Parallelizing workflow triggers
- Server-enforced max batch size
- New Deno/frontend unit test harnesses (neither exists in this repo today per spec 0001's precedent) — flagged as a pre-existing gap, not created here

## Dependencies between phases

Phase 1 (Edge Function) and Phases 2-3 (engine primitives) are independent and can be built in either order. Phase 4 (the JSON page) depends on all three. Phase 5 depends on everything.
