# Bulk Candidate Scoring Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-08-27
**Last Updated:** 2026-08-27
**Ticket:** Closes #10

## Overview

Recruiters currently upload and score one resume at a time. This feature lets a recruiter select multiple resume files in one action and score all of them against a single job description, reusing the existing per-resume extraction/scoring pipeline (spec 0001) rather than introducing a new one.

## Goals

- Select multiple resume files (PDF/DOCX) in one upload action
- Enter one job description that all selected resumes are scored against
- Trigger scoring for every selected resume from a single "Score candidates" action
- See a clear error if any individual resume fails to upload or fails to start scoring — not a silent drop
- Land on Candidate History afterward, where each uploaded resume appears as its own candidate row (status progresses "Scoring..." -> "Scored", same as today)

## Non-Goals

- A dedicated batch-progress/results screen — recruiter uses the existing Candidate History page + its "Refresh" button, same as single-candidate scoring
- Editing or removing individual files from a batch after selection (re-select to start over)
- Bulk delete/re-score of a batch as a group (already covered independently by ticket #6's bulk delete)
- Deduplication of identical resumes uploaded twice in one batch
- Parallelizing/tuning scoring throughput — resumes are triggered sequentially in v1; revisit if batches prove slow in practice
- A server-enforced max batch size — soft client-side guidance only, if any

## User Stories

### As a recruiter, I want to upload several resumes and score them all against one job description in a single action, so that I don't have to repeat the upload/JD flow per candidate

**Acceptance Criteria:**
- [ ] I can switch the existing "Score a Candidate" page into a bulk mode
- [ ] I can select multiple resume files in one file picker
- [ ] I enter the job description once, for the whole batch
- [ ] Submitting uploads all selected resumes and starts scoring for each against the same job description
- [ ] I land on Candidate History, where each resume appears as its own candidate, initially "Scoring..."
- [ ] If a resume fails to upload or fails to start scoring, I see a clear error rather than a stuck spinner or a silently dropped file

## Technical Design

### Architecture

Reuses the existing single-candidate pipeline end to end (Storage upload → `start-scoring-workflow` Edge Function → `ScoreResumeFitWorkflow`) — no new backend pipeline. Two additions:

1. **Frontend:** select N files, upload each to Storage (sequential loop), then make **one** bulk-trigger call for the whole batch.
2. **`start-scoring-workflow` Edge Function:** accepts a new plural `resume_storage_paths: string[]` alongside the existing singular `resume_storage_path`. When given the plural form, it resolves/creates the `job_description` entity **once**, then loops the existing per-candidate creation + workflow-trigger logic over each path. The singular-path (existing single-upload) code path is unchanged.

### Data Model

No schema changes. Each resume in a batch becomes its own independent `candidate` entity scored against the same `job_description` entity — identical to calling the single-candidate flow N times. A "batch" is not itself a persisted concept; nothing new to migrate.

### API / Engine Changes

- **Edge Function** (`supabase/functions/start-scoring-workflow/index.ts`): new optional `resume_storage_paths: string[]`. JD resolution logic (existing) runs once regardless of singular/plural input. Candidate-creation + HTTP-trigger-POST logic (existing, currently written for one resume) is reused via a loop shared by both the singular and plural paths, to avoid duplicating that logic. Response for the plural case: `{ job_description_entity_id, candidates: [{ candidate_entity_id, workflow_id }, ...] }`.
- **JSON engine — new `forEach` action**: `{ action: 'forEach', items: <expr>, as: <string>, do: <ActionDefinition> }`, dispatched sequentially (await each item), mirroring the existing render-time `each`/`as` looping pattern already used throughout the engine (e.g. `Card`'s `each`). Scope: a small, generically useful primitive, not bulk-scoring-specific — same bar as the `toggleArrayItem`/`sequence` actions added in tickets #4/#6.
- **`EngineFileInput`**: new `multiple` prop. When set, emits `event.files` (array of `{file, fileName, path}`) plus `event.paths`/`event.fileNames` (plain string arrays), computed in the component itself — the JSON expression evaluator has no array-mapping capability (per prior session's documented limits), so this shaping must happen in TypeScript, not a `{{}}` expression.
- **`candidate-upload.json`**: add a bulk-mode toggle (`Checkbox`); in bulk mode, `FileInput` gets `multiple`, and the submit action becomes: `forEach`-upload every selected file to Storage, then one `apiCall invoke` to `start-scoring-workflow` with `resume_storage_paths`, then navigate to `/candidates` (Candidate History) instead of a single candidate's scorecard.

### UX

Single-candidate mode is the default and is unchanged. A checkbox ("Score multiple candidates against this job description") switches the page into bulk mode: multi-file picker, same job-description fields, submit button labeled with the selected count (e.g. "Score 4 candidates"). Uploading/submitting states reuse the existing `uploading`/`submitting` state pattern. A failure (upload or trigger) surfaces via the existing `Alert`-based error state, consistent with the single-candidate flow's established error handling.

## Testing Strategy

- **Unit:** Edge Function's new plural-path branch (JD resolved exactly once; N candidates/workflows created) — no existing Deno test harness in this repo (per spec 0001, Deno was only syntax-checked, not unit-tested); same gap applies here unless a Deno test setup is added as part of this work.
- **Unit:** new `forEach` case in `ActionDispatcher.ts` (frontend test suite, if/when one exists for the engine — currently the engine has no unit tests either; flagged as a gap, not blocking).
- **Manual/live verification:** bulk-upload 2-3 real resumes against one JD through the local stack (Supabase + Temporal + Ollama), confirm each appears as a distinct candidate in Candidate History and each eventually scores.
- **E2E:** none added — the E2E gap is pre-existing (spec 0001) and tracked separately, not expanded here.

## Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Sequential per-resume workflow triggering is slow for large batches | Medium | Medium | Accepted for v1 (see Non-Goals); revisit if real usage shows large batches |
| A failure partway through a batch (e.g. resume 3 of 5) leaves a partial batch with no automatic retry | Medium | Medium | Surface which resume failed in the error message; since each candidate is independent, the recruiter can re-select just the failed file(s) and resubmit — no batch-tracking state needed |
| New Edge Function branch duplicates candidate-creation/trigger logic between singular and plural paths | Low | Medium | Share one loop body between both paths (singular = loop of one) rather than writing it twice |

## Open Questions

- [ ] Should there be a soft client-side warning above some batch size (e.g. >20 files), given sequential triggering? Leaning yes (simple text hint, no hard block) — will confirm during implementation. — Owner: Ndumiso Mpanza

## References

- [Spec 0001: Resume → Structured Candidate Profile](0001-resume-candidate-profile.md)
- [ADR-0001: Candidate profile data model and extraction/scoring architecture](../adrs/0001-candidate-profile-data-model-and-scoring-architecture.md)
- Ticket: https://github.com/Thabekhulu2/Resume/issues/10
