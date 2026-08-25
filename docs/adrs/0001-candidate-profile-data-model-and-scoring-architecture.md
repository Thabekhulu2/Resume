# ADR-0001: Candidate profile data model and extraction/scoring architecture

**Status:** Accepted
**Date:** 2026-08-25
**Deciders:** Ndumiso Mpanza
**Technical Story:** N/A yet — no ticket opened for this feature; this ADR precedes ticket/spec creation.

## Context

We're building the Resume project's core feature: a user uploads a resume + a job description, the system extracts skills/experience from the resume, scores the candidate's fit against the JD with reasoning, persists the result, and the UI shows a scorecard next to the resume.

This repo was scaffolded from the 10x-stack-template, which ships:
- A generic entity graph (`entities` / `entity_versions` / `relationships_v2` / `entity_facts`) intended for exactly this kind of "model an arbitrary business object + its history + its metrics" problem (see `DATABASE.md`, `Generalisable_schema.md`, `Guide_for_agents_using_supabase_template.md`).
- A Temporal worker (`temporal/src/`) with example workflow/activity scaffolding, plus a stub `supabase_core` activity for talking to Supabase.
- Supabase Edge Functions (`supabase/functions/`) as an alternative place to run server-side logic, currently empty (`_shared/.gitkeep` only).

Two decisions need to be made before any spec/implementation work starts:
1. **Data model** — model candidates/JDs/scorecards on the template's generic entity schema, or add dedicated tables for this domain.
2. **Extraction/scoring architecture** — where the "upload -> extract -> score -> persist" logic runs, and which LLM backs the extraction/scoring step.

This is a fresh, independent implementation — not required to match any other project's prior choices on this template.

## Decision

**Data model:** Reuse the template's generic entity model rather than adding bespoke tables.
- `candidate` and `job_description` are `entities.entity_type` values.
- Raw resume text/JD text plus the LLM's structured extraction (skills, experience, etc.) are stored as JSON in `entity_versions.data` (one version per upload/re-extraction, giving us history for free via the existing SCD2 trigger).
- The candidate-to-job relationship is modeled via `relationships_v2` (`relationship_type = 'candidate_applied_to_job'`, `parent_id` = job entity, `child_id` = candidate entity, or similar — exact direction to be settled in the spec).
- The fit score is stored as a numeric `entity_facts` row (`fact_type = 'jd_fit_score'`), with the LLM's reasoning/rationale text captured in `entity_facts.metadata` (JSONB) rather than a new column, per the template's "prefer JSONB in metadata over new columns" convention.

**Extraction/scoring architecture:** Orchestrate the extract-and-score step as a Temporal workflow with a dedicated activity that calls an LLM.
- A workflow (e.g. `score_resume_fit_workflow`) coordinates: parse upload -> call LLM extraction+scoring activity -> persist entity/version/fact rows via Supabase -> (optionally) notify.
- The LLM call lives in its own activity so it can be retried/timed-out independently of the persistence step, using Temporal's built-in retry/observability (Temporal UI is already part of the stack) rather than hand-rolled retry logic in an Edge Function.
- **Production LLM:** Anthropic Claude, called with a real `ANTHROPIC_API_KEY`.
- **Local/dev testing:** a local Ollama model may be substituted for the Anthropic call to avoid requiring a paid API key for plumbing verification, clearly marked as a temporary local-only patch and never committed as the real implementation (this mirrors a workflow preference already established on a prior project, not an architectural dependency on it).

## Consequences

### Positive
- No new migrations needed to start the feature — extraction/scoring logic is app-layer, not schema-layer, so we avoid schema churn while the shape of "what we extract" is still likely to change.
- Consistent with the template's documented conventions (`DATABASE.md`, `Guide_for_agents_using_supabase_template.md`), so future contributors/agents working from `CLAUDE.md`/`AGENTS.md` don't hit a surprise deviation.
- History comes for free: every re-upload or re-extraction is a new `entity_versions` row; every re-score is a new/updated `entity_facts` row — useful for showing "score changed after resume update" later without extra design work.
- Temporal gives retry/backoff/observability on the LLM call step without extra code, and keeps the LLM call isolated from the DB-write step (a failure in one doesn't require re-running the other from scratch).

### Negative
- Generic JSONB modeling pushes validation into the application layer (per Guide §10.2) — we must define and enforce the expected shape of extracted data ourselves; the database won't catch a malformed extraction.
- Two runtimes involved (Temporal worker + Supabase) for a single logical operation adds more moving parts to debug locally than a single Edge Function would.
- Querying/filtering candidates by extracted skills means querying into JSONB (`entity_versions.data`) rather than indexed columns, which may need targeted indexes later if search performance matters.

### Neutral
- RLS policies for the entity tables still need to be defined per Guide §10.4 regardless of this decision — tracked as follow-up spec/ticket work, not decided here.
- Exact `relationship_type` / `fact_type` key names are left to the spec, not fixed by this ADR.

## Options Considered

### Option 1: Generic entity schema + Temporal workflow + Anthropic Claude (chosen)
- **Pros:** Matches template conventions; minimal schema churn; retries/observability via Temporal; reasoning captured alongside the score without new columns.
- **Cons:** More app-layer validation burden; two runtimes to run locally.

### Option 2: Dedicated `candidates` / `job_descriptions` / `scorecards` tables + Supabase Edge Function calling the LLM directly
- **Pros:** Simpler local dev loop (one runtime); typed columns are easier to query/index; a single synchronous HTTP call fits a "user uploads, waits for a scorecard" flow well.
- **Cons:** Diverges from the template's documented "prefer the generic entity model" convention; new migrations needed up front; no built-in retry/observability for the LLM call — would have to be hand-rolled.

### Option 3: Dedicated tables + Temporal workflow (hybrid)
- **Pros:** Typed, indexable schema with Temporal's retry/observability.
- **Cons:** Combines both cons above (schema churn AND two runtimes) without a corresponding combined benefit; harder to justify over Option 1 or 2 individually.

## Related Decisions

None yet — first ADR in this repo.

## Notes

- This ADR intentionally precedes the feature ticket/spec (per `CLAUDE.md`'s required workflow, the ticket + `SPEC_TEMPLATE.md`-based spec are still to come); it exists to settle the architecture questions that the spec would otherwise have to re-litigate.
- Exact `entity_type`, `relationship_type`, and `fact_type` key names, plus the extracted-data JSON shape, are deliberately left open here and belong in the spec.
