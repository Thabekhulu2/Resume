# Bulk Scoring Partial-Failure Handling Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-08-31
**Last Updated:** 2026-08-31
**Ticket:** Closes #12

## Overview

In bulk-scoring mode (spec 0002), one resume failing to upload or failing to start scoring currently aborts the whole batch with a generic "Failed to upload one of the resumes. Please try again." banner, even when other resumes in the batch were fine. This reverses that behavior: the batch keeps going past a per-resume failure, and the user is told exactly which file failed and why.

## Goals

- A failure on one resume in a bulk batch (upload failure or scoring-trigger failure) does not stop the other resumes in the batch from being uploaded and scored
- The user sees, per failed resume, the file name and the actual underlying error message — not a generic "please try again"
- Successfully-processed resumes in the same batch still land on Candidate History as scoring/scored, exactly as today
- Exactly one `job_description` entity is still created per batch (unchanged from spec 0002), regardless of which/how many resumes fail

## Non-Goals

- Retrying just the failed items from the same page (re-select and resubmit is enough for v1, same as spec 0002's existing stance)
- A dedicated batch-results screen — the existing page's error area grows to list per-file failures; Candidate History remains the place to see what got created
- Changing single-resume (non-bulk) upload/scoring error handling — unchanged

## User Stories

### As a recruiter, when I bulk-score resumes and one of them is bad, I want the rest to still get scored, and I want to know which resume failed and why

**Acceptance Criteria:**
- [ ] If resume B fails to upload while A and C upload fine, A and C are still triggered for scoring
- [ ] If resume B fails to start scoring (Edge Function/Temporal trigger) while A and C succeed, A and C still proceed
- [ ] The error area lists every failed file by name with its specific error message (e.g., "resume2.pdf: Failed to upload — network error", "resume3.docx: Failed to start scoring — job_description_entity_id not found")
- [ ] If at least one resume in the batch succeeded, I land on Candidate History same as today
- [ ] If every resume in the batch failed, I stay on the upload page with the full list of per-file errors (no navigation to a Candidate History with nothing new in it)

## Technical Design

### Architecture

No new services or schema. Two existing pieces change:

1. **`forEach` engine action** (`frontend/src/engine/types.ts`, `ActionDispatcher.ts`): change from "abort the whole loop on the first item's error" to "run every item regardless of earlier failures, and surface a per-item results list once done." This is an internal contract change; `forEach` has exactly one call site in the codebase (bulk-mode upload, added for spec 0002), so it's a safe, non-breaking-in-practice change.
2. **`start-scoring-workflow` Edge Function**'s plural-path loop: currently stops and returns 500 at the first resume that fails to create a candidate/trigger scoring. Changes to attempt every resume regardless of earlier failures, and return both the successes and the failures in one 200 response.

Upload and scoring-trigger remain two separate steps (as today — uploads happen client-side in the `forEach` loop; the single batched `start-scoring-workflow` call still resolves/creates the `job_description` entity exactly once). This preserves spec 0002's "exactly one job_description entity per batch" property without needing a new endpoint or a bootstrap-then-loop dance.

### `forEach` action changes

- Drop the "stop iteration on first error" behavior and the `onError` field. Every item runs via `try/catch` regardless of prior items' outcome.
- Rename `onSuccess` → `onComplete` (fires exactly once, after every item has been attempted, regardless of whether any item failed) — the old name would be misleading now that it no longer implies "no failures occurred."
- New optional `resultsKey: string` field: when present, after the loop finishes, `context.state[resultsKey]` is set (via the same state-setting path `setState` already uses) to an array of `{ item, success, error? }`, one entry per input item, in order. `error` is `error.message` when the item's action threw, extracted the same way `handleForEach`'s current catch block already does.
- This computation (building the results array, and deriving which items succeeded) happens in TypeScript inside `handleForEach`, not via a JSON `{{}}` expression — consistent with the existing precedent that the expression evaluator has no array map/filter capability (`EngineFileInput`'s multi-file event shaping, spec 0002, cites the same limitation).

### `candidate-upload.json` bulk-mode submit flow

- `forEach` over `state.resumeFiles`, `do`: upload only (`apiCall upload`, as today), `resultsKey: "uploadResults"`, `onComplete`:
  1. Compute (in the JSON, via a plain `setState` reading the TS-populated `uploadResults`) which paths succeeded: since filtering still isn't available in the expression layer, `handleForEach` additionally writes a second, derived array under `` `${resultsKey}Paths` `` (i.e. `uploadResultsPaths`) containing just the successfully-uploaded items' `.path` values, in TS, at the same time it writes `resultsKey`. (Same rationale as above — this is shaping, not filtering-in-JSON.)
  2. If `state.uploadResultsPaths.length` is 0 (every upload failed): skip the scoring-trigger call entirely, `setState submitting=false`, and let the error Alert render straight from `state.uploadResults`.
  3. Otherwise, call `start-scoring-workflow` once with `resume_storage_paths: "{{state.uploadResultsPaths}}"` (unchanged shape from spec 0002, just now possibly a subset of the originally-selected files).
     - `onSuccess`: `setState triggerResult = {{event.data}}` (holds `{ job_description_entity_id, candidates: [...], failures: [...] }` — see Edge Function changes below), then `conditional` on `{{event.data.candidates.length}}` — `then`: `navigate to /candidates`; `else`: `setState submitting=false` (stay on page, show errors). This is the first live use of the existing-but-previously-unused `conditional` action; it fits here because the branch depends on runtime API response data, not static layout, so the `if`-per-component pattern used elsewhere in this codebase doesn't apply.
     - `onError` (network-level failure of the trigger call itself, distinct from per-resume failures inside a 200 response): unchanged pattern — `setState submitting=false`, `setState error`.
- Error display: the existing single `Alert` (`if: "{{state.error}}"`) is joined by a second block that renders per-file failures via `each`/`as` (the existing list-rendering pattern used on Candidate History/entity pages) over `state.uploadResults` (upload failures) and `state.triggerResult.failures` (scoring-trigger failures), each row `if: "{{!item.success}}"` / always-shown-since-only-failures-array respectively, showing `{{item.item.fileName}}: {{item.error}}` / `{{item.resume_storage_path}}: {{item.error}}`.

### `start-scoring-workflow` Edge Function changes

- The plural-path loop (`for (const resumeStoragePath of resumeStoragePaths) { ... }`) changes from "throw and return 500 on first failure" to "catch each resume's error individually, keep going."
- Response shape for the plural case becomes `{ job_description_entity_id, candidates: [...succeeded], failures: [{ resume_storage_path, error }...] }` — always 200 (a partial or total per-resume failure is not a request-level failure; the request-level 400/404/500 cases — bad payload, missing JD — are unchanged).
- Singular-path (non-bulk) behavior is completely unchanged: a single resume's failure still returns a 500 with the error, exactly as today.

### Security Considerations

None beyond what spec 0002 already covers — no new inputs, no new auth surface.

### Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Changing `forEach`'s contract breaks something else using it | Low | Very low | Confirmed via grep: bulk-mode upload is the only call site in the codebase today |
| First live use of `conditional` action surfaces a latent bug in an untested code path | Medium | Medium | Manually verify both branches (some-succeeded / all-failed) during live testing, not just one |
| Error messages from Postgres/Storage/Temporal are technical and not recruiter-friendly | Low | Medium | Out of scope for this ticket — show the real message rather than inventing a friendlier one; wording pass can be a follow-up if it proves confusing in practice |

## Testing Strategy

- Manual: bulk-upload a batch where one file is a valid resume and one is an unsupported/corrupt file — confirm the valid one reaches `status: scored` and the corrupt one's specific error appears in the UI, and that only one `job_description` entity was created for the batch
- Manual: force a scoring-trigger failure for one resume in an otherwise-valid batch (e.g., stop the Temporal worker mid-batch) — confirm the others still complete and the failed one's reason is shown
- Manual: an all-fail batch — confirm the page does not navigate away and every failure reason is visible

## Open Questions

- None — approach confirmed against the existing engine's actual capabilities (expression evaluator, `each`/`if` rendering, `conditional` action) before writing this spec.

## Approval

- [ ] Ndumiso Mpanza
