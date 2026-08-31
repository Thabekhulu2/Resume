# Implementation Plan: Bulk Scoring Partial-Failure Handling

**Spec:** [0004-bulk-scoring-partial-failure-handling.md](./0004-bulk-scoring-partial-failure-handling.md) (Approved)
**Ticket:** Closes #12
**Status:** Approved (Gate 1) — implemented and live-verified 2026-08-31.

## Implementation notes (deviations from the plan as written)

- **Phase 1:** `resultsKey` writes to state are for post-completion *rendering* only. The freshly-computed `results`/`successfulPaths` are also passed straight into `onComplete`'s dispatched context via `event.results`/`event.successfulPaths` (not read back from `state`), because a `setState` call is not guaranteed visible to a `{{state.x}}` read later in the *same* dispatch chain — the exact async-state-snapshot issue already documented in spec 0002's plan for `forEach`'s `onSuccess`/`onError`. `candidate-upload.json`'s `onComplete` conditional and the trigger call's `resume_storage_paths` read `{{event.successfulPaths}}`, not `{{state.uploadResultsPaths}}`.
- **Phase 3:** Confirmed via live testing that `each` + `if` combined on the same component (used for the per-file upload-failure alert list) works correctly, despite being the first use of that combination in this codebase — `ComponentRenderer` evaluates `if` once with the *outer* (pre-item) context, where the item variable is `undefined`; for the negated condition used here (`{{!result.success}}`), that evaluates to `true` and lets the `each` loop proceed, which then re-evaluates `if` correctly per item via the item-bound context. Also confirmed this is the first live use of the previously-unused `conditional` action (twice, in this same flow) — both branches verified live (see Phase 4).

## Phase 1: `forEach` engine action — continue past per-item failure

`frontend/src/engine/types.ts`

- [x] `ForEachAction`: remove `onError`; rename `onSuccess` → `onComplete` (fires once, after every item has been attempted, regardless of per-item outcome); add optional `resultsKey?: string`
- [x] Update the interface's doc comment — it currently says "Stops at the first item whose action throws"; that's no longer true

`frontend/src/engine/ActionDispatcher.ts` (`handleForEach`)

- [x] Loop every item in a `try/catch` **per item** (not one `try` around the whole loop) so one item's throw doesn't stop the next item from running
- [x] Accumulate `{ item, success: boolean, error?: string }` per item (`error` = `error instanceof Error ? error.message : String(error)`, matching the existing catch block's convention)
- [x] If `action.resultsKey` is set: write the full results array to `context.state[action.resultsKey]`, and write the successful items' `.path` values to `context.state[`${action.resultsKey}Paths`]` (see Implementation notes re: also passing these via `event` for the same-chain read)
- [x] After the loop finishes (always — no early return path anymore): if `action.onComplete`, dispatch it

## Phase 2: `start-scoring-workflow` Edge Function — continue past per-resume failure

`supabase/functions/start-scoring-workflow/index.ts`

- [x] Replace the single `try { for (...) { ... } } catch { return 500 }` around the plural-path loop with a per-resume `try/catch` inside the loop; on failure, push `{ resume_storage_path, error: message }` to a `failures` array instead of returning early
- [x] Plural-case response becomes `{ job_description_entity_id, candidates: [...succeeded], failures: [...] }`, status 200 (unless the JD resolution itself fails, or the request payload is invalid — those request-level failures are unchanged: 400/404/500 before the loop even starts)
- [x] Singular-path (`resume_storage_path`, non-array) behavior is untouched: still throws/returns 500 on failure, exactly as today — only the plural branch's loop changes
- [x] `results.length === 0` (all failed) in the plural case is not itself an error — it's a normal 200 with an empty `candidates` array and a full `failures` array

## Phase 3: `candidate-upload.json` — bulk-mode wiring

- [x] New state fields: `uploadResults: []`, `uploadResultsPaths: []`, `triggerResult: null` (the now-unused `resumeStoragePaths` state field from spec 0002 was removed — dead after this change)
- [x] Bulk-mode `forEach` split into two steps as planned — upload-only `do`, `resultsKey: "uploadResults"`, `onComplete` branches via `conditional` on whether any upload succeeded
- [x] Error display: per-file upload failures via `each`/`if` over `state.uploadResults`; per-file trigger failures via `each` over `state.triggerResult.failures`
- [x] Single-mode submit path completely unchanged

## Phase 4: Testing — live e2e + adversarial via a real browser against the real stack

Backend logic (Phase 2) was verified two ways: a direct `curl` reproduction against the real running Edge Function (bypassing the browser), and through the live UI flows below. Frontend logic (Phase 1 `forEach`, Phase 3 wiring) has no existing unit-test harness in this repo (confirmed, same pre-existing gap as specs 0001/0002) and can't be verified by API calls alone, so a real Chromium browser (Playwright, installed ephemerally outside the repo — not added as a project dependency) was driven against the actually-running stack (`resume-stack` containers + local Supabase + local Ollama) for every scenario below. Test data (Storage objects, `entities` rows) was deleted afterward; the stack was torn down; NduMan's separate stack was not touched.

- [x] **E2E, happy path**: bulk-uploaded 2 valid `.docx` resumes through the real browser UI (checkbox → multi-file picker → JD text → submit) — both uploaded, one `job_description` + 2 `candidate` entities created, navigated to `/candidates`
- [x] **Adversarial — one bad upload**: used Playwright network interception to force the *first* file's real Storage upload request to fail (500) while letting the second through unmodified — confirmed (a) the second file still uploaded and got scored, (b) the batch still navigated to `/candidates` (one success is enough per spec), (c) exactly 1 candidate + 1 JD entity were created (not 2) — the failed file correctly never reached the trigger call
- [x] **Adversarial — one bad scoring-trigger**: intercepted the real `start-scoring-workflow` request and corrupted one resume's path to an empty string before letting it continue to the real Edge Function — confirmed via direct `curl` reproduction of the same payload that the response is `{ candidates: [...1 succeeded], failures: [{ resume_storage_path: "", error: "failed to start scoring for : missing required field(s): resume_storage_path" }] }`, and via the browser test that the batch still navigated to `/candidates` because `candidates.length > 0`
- [x] **Adversarial — all fail**: forced every Storage upload to fail — confirmed the page did **not** navigate away, `start-scoring-workflow` was **never called** (network log asserted), both file names and the real "simulated storage failure" error text appeared in the per-file alerts, and zero `candidate`/`job_description` entities were created in the DB
- [x] **Adversarial — single-file bulk mode**: 1-file batch (spec 0002's original bulk edge case) still uploads, triggers, and navigates correctly — no regression
- [x] Deviation from the plan's suggested repro techniques: rather than stopping the Temporal worker mid-batch (timing-sensitive, hard to control precisely) or hand-crafting an unsupported-file-format resume (that failure is async, inside the Temporal workflow, and doesn't surface in the synchronous `start-scoring-workflow` response at all — same as pre-existing single-resume behavior, out of this ticket's scope), per-item failures were forced deterministically via Playwright's request interception (mutating one request's payload / failing one specific network call) — a standard technique for adversarial network-failure testing, and it exercises the exact same real server code paths
- [x] Not tested: `conditional`'s `else` branch on the *outer* "some succeeded, most failed" ratio isn't meaningfully different from "one failed" — not a separate case per the spec's acceptance criteria (only "at least one succeeded" vs. "none succeeded" are distinguished)

## Out of scope for this plan (per spec's Non-Goals)

- Retry-just-the-failed-items UX
- A dedicated batch-results screen
- Changes to single-resume (non-bulk) error handling
- Friendlier/translated error message wording

## Dependencies between phases

Phase 1 (engine) and Phase 2 (Edge Function) are independent and can be built in either order. Phase 3 depends on both. Phase 4 depends on all three.
