# Resume → Structured Candidate Profile Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-08-25
**Last Updated:** 2026-08-25
**Ticket:** Closes #2
**ADR:** [ADR-0001](../adrs/0001-candidate-profile-data-model-and-scoring-architecture.md) (Accepted) — data model and extraction/scoring architecture are settled there; this spec fills in the concrete shapes and flow.

## Overview

A user uploads a resume and a job description (JD). A Temporal workflow extracts structured skills/experience from the resume, scores the candidate's fit against the JD with reasoning, and persists the result. The UI shows a scorecard next to the resume.

## Goals

- Upload a resume + a JD for a candidate/job pairing
- Extract structured data (skills, experience) from the resume via LLM
- Score fit against the JD, with a numeric score and a text rationale
- Persist candidate, job description, extraction, and score so they can be revisited later
- Display a scorecard (score + reasoning) next to the resume in the UI

## Non-Goals

- Resume/JD parsing accuracy tuning or prompt-engineering benchmarks (functional correctness is enough for v1; quality tuning is a follow-up)
- Bulk/batch upload of multiple resumes at once
- Candidate search/filtering by extracted skills (JSONB querying is possible later; not built now)
- Authentication/multi-tenant access control and RLS policy definitions (per ADR-0001, tracked separately)
- Editing/correcting an extraction or score after the fact

## User Stories

### As a recruiter, I want to upload a resume and a JD so that I get a fit score with reasoning

**Acceptance Criteria:**
- [ ] I can upload a resume file and paste/upload JD text in the UI
- [ ] After submission, I can see the workflow is running (not a silent hang)
- [ ] Once complete, I see a scorecard (numeric score + written reasoning) next to the resume
- [ ] If extraction or scoring fails, I see an error state, not a stuck spinner

### As a recruiter, I want to see a previously scored candidate again so that I don't have to re-run the workflow

**Acceptance Criteria:**
- [ ] Navigating to a candidate's page shows their resume, extracted data, and latest scorecard without re-running the LLM
- [ ] If a candidate has been scored against more than one job, I can tell which JD a given scorecard is for

## Technical Design

### Architecture

```
Frontend (upload form)
  -> Supabase Storage (resume file) + Supabase insert (job_description entity + version)
  -> Temporal workflow: score_resume_fit_workflow
       activity: extract_and_score(resume_text, jd_text) -> LLM call (Anthropic Claude;
                 local Ollama substitute permitted for local plumbing tests only)
       activity: persist_result(...) -> Supabase (entities/entity_versions/relationships_v2/entity_facts)
  -> Frontend polls/subscribes for the candidate entity's latest entity_facts row
  -> Scorecard UI renders score + reasoning next to the resume
```

### Data Model

Per ADR-0001, no new tables — uses the existing generic entity schema.

- `entities.entity_type = 'candidate'` — one row per uploaded resume
- `entities.entity_type = 'job_description'` — one row per JD
- `entity_versions.data` (candidate):
  ```json
  {
    "resume_file_path": "storage path in Supabase Storage",
    "resume_text": "extracted plain text used as LLM input",
    "extracted": {
      "skills": ["..."],
      "experience": [
        { "title": "...", "company": "...", "duration": "...", "summary": "..." }
      ]
    }
  }
  ```
- `entity_versions.data` (job_description):
  ```json
  { "title": "...", "jd_text": "..." }
  ```
- `relationships_v2`: `relationship_type = 'candidate_scored_against_job'`, `parent_id` = job_description entity id, `child_id` = candidate entity id. (Direction chosen so a job can list all candidates scored against it via one relationship type; open to revisiting — see Open Questions.)
- `entity_facts`: `fact_type = 'jd_fit_score'`, `value` = numeric score (0–100), `dimension_type`/`dimension_id` = null (no dimension table needed — score is inherently numeric), `metadata = { "reasoning": "...", "observed_at": "..." }`.
- `fact_types` seed row: `key = 'jd_fit_score'`, `label = 'JD Fit Score'`, `unit = 'percent'`.

### API Design

No new HTTP API surface beyond Supabase's existing auto-generated REST/PostgREST endpoints for the entity tables, plus:
- A way to trigger the Temporal workflow from the frontend. Options: a thin Supabase Edge Function that starts the workflow (keeps the frontend from talking to Temporal's gRPC endpoint directly), or a direct Temporal client call from the frontend build. **Assumption for this spec: a small Edge Function (`supabase/functions/start-scoring-workflow`) that starts the Temporal workflow and returns its workflow ID**, since the frontend has no existing Temporal client wiring and the template already uses Edge Functions as the frontend-facing surface.
- Frontend reads workflow completion via polling `entity_facts` for the candidate (simplest, no new infra) rather than a websocket/Temporal-UI-style live subscription.

### UI/UX Design

- New page: upload form (resume file input + JD text input) — new JSON page definition under `frontend/src/pages/`, following the existing JSON-driven UI engine pattern (`entity-list.json`, `entity-detail.json` are the existing examples to follow).
- New page: candidate detail — resume (or a link/preview to the stored file) alongside a scorecard component (score + reasoning), reusing `entity-detail.json`'s pattern for entity types where practical.
- Loading/error states: while the workflow runs, show a "scoring in progress" state; on failure, show an error state distinct from "not yet scored."

## Implementation Plan

Deferred. Per `CLAUDE.md`'s required workflow, the implementation plan is written as a separate step after this spec is approved, and requires explicit approval (Gate 1) before any code is written.

## Testing Strategy

- **Unit tests:** extraction/scoring activity's parsing and payload-shaping logic (mock the LLM call); persistence activity's Supabase writes
- **Integration tests:** `supabase db reset` still applies cleanly if a `fact_types` seed row is added; Temporal workflow runs end-to-end against a local worker with a mocked/local LLM
- **E2E tests:** upload -> wait for scorecard -> verify score + reasoning render, using the local Ollama substitute so the E2E suite doesn't require a paid API key
- **Performance tests:** none planned for v1 (out of scope — no defined load target yet)

## Rollout Plan

Local-first; no staged rollout, feature flags, or production deploy plan yet. Production use requires `ANTHROPIC_API_KEY` to be set (never the local Ollama patch) per ADR-0001.

## Metrics & Success Criteria

- A recruiter can go from "resume + JD in hand" to "score + reasoning on screen" without touching the database or Temporal UI directly
- Workflow failures surface as a visible error state, not a silent hang

## Dependencies

- Supabase Storage bucket for resume files (does not exist yet — needs to be created as part of implementation)
- A resume text-extraction step (PDF/DOCX -> plain text) before the LLM call — library choice not yet decided (see Open Questions)
- `ANTHROPIC_API_KEY` for production LLM calls (per ADR-0001)
- Existing Temporal worker scaffold (`temporal/src/workflows/`, `temporal/src/activities/`) and Supabase entity schema (already in repo)

## Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| LLM extraction returns malformed/unexpected JSON shape | High | Medium | Validate LLM output against expected schema in the activity before persisting (per Guide §10.2); fail the workflow visibly rather than persisting garbage |
| Resume file format (PDF/DOCX) parsing fails or loses formatting-dependent info | Medium | Medium | Constrain v1 to a small set of supported formats; surface a clear "unsupported format" error rather than silently degrading |
| Local Ollama substitute produces lower-quality scoring than Claude, masking real issues during dev | Low | Medium | Keep the substitute clearly marked as temporary/local-only (per ADR-0001); revert to Anthropic before any merge/deploy, consistent with prior project practice |
| Temporal + Supabase + Edge Function adds more local moving parts to debug than a single Edge Function would (ADR-0001's accepted negative) | Medium | Medium | Accepted trade-off per ADR-0001; mitigate via clear logging (one-line rule per `CLAUDE.md`) at each activity boundary |

## Open Questions

- [ ] Resume text extraction library/approach for PDF/DOCX -> plain text (Python, runs in the Temporal worker) — Owner: Ndumiso Mpanza
- [ ] Exact direction/semantics of `relationships_v2` for candidate<->job (current assumption: parent=job, child=candidate) — Owner: Ndumiso Mpanza
- [ ] How the frontend triggers the workflow — Edge Function vs. direct Temporal client (current assumption: Edge Function) — Owner: Ndumiso Mpanza
- [ ] Supabase Storage bucket name/access policy for resume files — Owner: Ndumiso Mpanza
- [ ] Should a candidate be re-scorable against a *different* JD (many-to-many), or is v1 one candidate : one JD? (current assumption from the relationship model: many-to-many is naturally supported, but UI/UX for v1 isn't designed for it) — Owner: Ndumiso Mpanza

## References

- [ADR-0001: Candidate profile data model and extraction/scoring architecture](../adrs/0001-candidate-profile-data-model-and-scoring-architecture.md)
- [DATABASE.md](../../DATABASE.md)
- [Guide_for_agents_using_supabase_template.md](../../Guide_for_agents_using_supabase_template.md)
- Ticket: https://github.com/Thabekhulu2/Resume/issues/2
