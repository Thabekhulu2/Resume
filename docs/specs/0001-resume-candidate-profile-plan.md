# Implementation Plan: Resume → Structured Candidate Profile

**Spec:** [0001-resume-candidate-profile.md](./0001-resume-candidate-profile.md) (Approved)
**ADR:** [ADR-0001](../adrs/0001-candidate-profile-data-model-and-scoring-architecture.md) (Accepted)
**Ticket:** Closes #2
**Status:** Approved (Gate 1) — implementation may proceed, per `CLAUDE.md`'s required workflow.

## Plan-level decision not settled by the spec

The spec left "how the frontend triggers the workflow" as an open question (Edge Function vs. direct Temporal client). Concretely, there is no Temporal client SDK for Supabase Edge Functions' Deno runtime, so a plain Edge Function can't start a workflow via gRPC directly. This plan proposes:

- Add a small HTTP trigger endpoint inside the existing Temporal worker process (new module, e.g. `temporal/src/http_trigger.py`, using a lightweight server such as `aiohttp`) that accepts a start-workflow request and calls the Temporal client's `start_workflow`.
- The Supabase Edge Function (`supabase/functions/start-scoring-workflow`) does the Supabase-side work (create/update candidate + job entities, upload bookkeeping) and then makes a plain HTTP POST to that worker endpoint.

This is a plan-level addition beyond what the spec fixed — flagging it explicitly for approval rather than assuming it.

## Phase 0: Prerequisites

- [ ] Confirm local Supabase stack is up and migrations apply cleanly (ticket #1 scope — verify, don't redo)
- [ ] Add `ANTHROPIC_API_KEY` to `.env.example` (documented as required for production; local dev may substitute Ollama per ADR-0001, following the same "TEMPORARY LOCAL TEST PATCH — DO NOT COMMIT" convention used on the sibling NduMan project)
- [ ] Add new Python dependencies to `temporal/pyproject.toml`: `anthropic` (LLM client), `pypdf` (PDF text extraction), `python-docx` (DOCX text extraction), `aiohttp` (HTTP trigger endpoint), `supabase` (Python client, to replace the current mock persistence activities with real Supabase calls)

## Phase 1: Data model setup (no schema changes, per ADR-0001)

- [ ] New migration `supabase/migrations/<timestamp>_jd_fit_score_fact_type.sql`: idempotent seed of a `fact_types` row (`key = 'jd_fit_score'`, `label = 'JD Fit Score'`, `unit = 'percent'`), `on conflict do nothing`, per `DATABASE.md`/Guide §10.1 ordering (fact types before facts)
- [ ] New migration or `seed.sql` addition: create the `resumes` Supabase Storage bucket (private) — `insert into storage.buckets (id, name, public) values ('resumes', 'resumes', false)`
- [ ] Run `supabase db reset --config supabase/config.toml` to verify the migration/seed apply cleanly

## Phase 2: Temporal worker — real Supabase persistence

The existing `temporal/src/activities/supabase_core.py` activities are stubs that return mock data — they don't write anywhere yet. This phase makes them real before building the new feature on top of them.

- [ ] Wire a real Supabase client (service-role key, from worker env/settings) into `temporal/src/activities/supabase_core.py`
- [ ] Implement `create_entity`, `update_entity_scd2`, `get_entity`, `create_relationship` against the real `entities`/`entity_versions`/`relationships_v2` tables (replacing the `[STUB]`-logged mock returns)
- [ ] Leave `append_event`/`notifications.py` as-is unless the workflow needs them (not required by the spec)

## Phase 3: Resume text extraction activity

- [ ] New activity `extract_resume_text(storage_path: str) -> str` in a new `temporal/src/activities/resume_parsing.py`: downloads the file from the `resumes` Storage bucket, extracts plain text via `pypdf` (PDF) or `python-docx` (DOCX), raises a clear error for unsupported formats (per spec's risk mitigation — no silent degradation)

## Phase 4: Extraction + scoring activity

- [ ] New activity `extract_and_score(resume_text: str, jd_text: str) -> ScoreResult` in `temporal/src/activities/scoring.py`: calls Anthropic Claude (or local Ollama when `USE_LOCAL_LLM` env flag is set, per the temporary local-test-patch convention) with a prompt that returns structured JSON: `{ skills: [...], experience: [...], score: number, reasoning: string }`
- [ ] Validate the LLM's JSON response against the expected shape before returning (per Guide §10.2 and the spec's top risk); raise on malformed output rather than persisting garbage

## Phase 5: Workflow

- [ ] New workflow `ScoreResumeFitWorkflow` in `temporal/src/workflows/score_resume_fit/workflow.py`, following the existing `ApprovalWorkflow` pattern:
  1. `extract_resume_text` activity
  2. `extract_and_score` activity
  3. `create_entity`/`update_entity_scd2` — persist candidate `entity_versions.data` (resume text + extracted skills/experience)
  4. `create_relationship` — `relationship_type = 'candidate_scored_against_job'`, parent=job entity, child=candidate entity (per spec's assumed direction)
  5. New activity `upsert_entity_fact` (extends `supabase_core.py`) — writes `entity_facts` row (`fact_type='jd_fit_score'`, numeric `value`, `metadata.reasoning`)
- [ ] Register the workflow + all new activities in `temporal/src/worker.py`

## Phase 6: Trigger endpoint + Edge Function

- [ ] `temporal/src/http_trigger.py`: minimal HTTP server (co-located with the worker process) exposing `POST /start-scoring` — starts `ScoreResumeFitWorkflow`, returns the Temporal workflow ID
- [ ] `supabase/functions/start-scoring-workflow/index.ts`: accepts `{ candidate_entity_id | resume_upload, job_description_entity_id | jd_text }`, ensures the candidate/job entities + resume upload exist in Supabase, then POSTs to the worker's `/start-scoring` endpoint

## Phase 7: Frontend

- [ ] New page JSON `frontend/src/pages/candidate-upload.json`: resume file input + JD text input, submit calls the `start-scoring-workflow` Edge Function (following the existing `apiCall`/JSON-engine action pattern used in `entity-detail.json`)
- [ ] New page JSON `frontend/src/pages/candidate-scorecard.json` (or extend `entity-detail.json` for `entityType = candidate`): shows resume + polls/refetches the candidate's `entity_facts` for `jd_fit_score`, rendering score + reasoning; distinct "scoring in progress" vs. "failed" vs. "scored" states per the spec's UX requirement
- [ ] Wire new routes under `frontend/src/routes/` following the existing `entities/$entityType/...` pattern

## Phase 8: Tests

- [ ] Unit tests (`temporal/tests/`): `resume_parsing` (PDF/DOCX fixtures), `scoring` (mocked LLM response, including a malformed-JSON case), persistence activities (mocked Supabase client)
- [ ] Integration test: `ScoreResumeFitWorkflow` end-to-end against a local worker with `USE_LOCAL_LLM` set (no paid API key required)
- [ ] E2E test: upload → scorecard renders score + reasoning, using the local LLM substitute

## Out of scope for this plan (per spec's Non-Goals / ADR's Neutral consequences)

- RLS policies on the entity tables
- Candidate search/filtering by extracted skills
- Bulk upload, multi-JD UX, re-editing an extraction/score

## Dependencies between phases

Phases 0–1 must land before 2–5 (persistence needs real client + seeded fact type). Phase 6 depends on 5 (workflow must exist to trigger). Phase 7 depends on 6 (frontend needs the Edge Function). Phase 8 runs alongside 2–7, not strictly after.
