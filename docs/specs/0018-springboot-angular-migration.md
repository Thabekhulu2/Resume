# Feature Specification: Spring Boot + Angular Migration

## Overview
Replace the current tech stack — a Python Temporal worker plus Supabase Edge Functions on the backend, and a JSON-engine-driven React/TanStack frontend — with a Spring Boot backend (Java, Temporal Java SDK) and an Angular frontend. The existing Supabase Postgres schema is kept as-is; Supabase Auth, Storage, and Edge Functions are retired in favor of Spring Boot equivalents.

## Metadata
- **Feature Name**: Spring Boot + Angular Migration
- **Status**: Approved
- **Priority**: P1 - High
- **Target Release**: TBD
- **Owner**: Ndumiso Mpanza
- **Stakeholders**: Ndumiso Mpanza
- **Epic/Initiative**: Tech stack re-platform
- **Created**: 2026-09-14
- **Last Updated**: 2026-09-14
- **Ticket**: Closes #29

## Problem Statement

### User Problem / Business Problem
The project currently uses a JSON-driven React UI engine (custom-built for this repo) and a Python Temporal worker. The team wants to standardize on Spring Boot and Angular instead, moving off the custom engine and Python orchestration.

### Current State
- **Backend**: `temporal/` — Python Temporal worker (`temporalio` SDK). Activities call Supabase (`supabase-py`) for persistence, Anthropic/Ollama for LLM scoring, `pypdf`/`python-docx` for resume text extraction. An `aiohttp` HTTP trigger server exposes `POST /start-scoring`. Business logic (job posting, applications, recruiter decisions, interviews, notifications) is otherwise implemented as Supabase Edge Functions (`supabase/functions/`: `start-scoring-workflow`, `get-my-resume-url`) plus direct PostgREST table access from the frontend.
- **Frontend**: `frontend/` — Vite + React 18 + TanStack Router/Query, with a custom JSON-driven UI engine (`src/engine/`) that renders pages from JSON page definitions (`src/pages/*.json`) and dispatches declarative actions (`apiCall`, `forEach`, `setState`, etc.) against Supabase directly (PostgREST + Storage + Auth + Edge Functions).
- **Data/Auth/Storage**: Supabase Postgres (13 migrations; generic `entities`/`entity_versions`/`relationships_v2`/`entity_facts` SCD2 model plus recruiter/candidate auth and decisions/interviews/notifications tables), Supabase Auth (recruiter + candidate roles), Supabase Storage (`resumes` bucket).
- **Feature inventory to re-platform** (from current routes/pages):
  - Recruiter: unified login, Dashboard (Kanban by fit-score band), Jobs (list/create), Applications (per-job, filter, trigger scoring, bulk upload), Candidate History/detail/scorecard, Interviews, decisions/notifications.
  - Candidate: sign-up, login, Dashboard, browse Jobs, Apply wizard (resume upload), My Applications, Saved Jobs, Job Alerts, Messages, Notifications, Profile, Settings.

### Desired State
- Backend: Spring Boot application exposing a REST API, using Spring Security for recruiter/candidate auth (replacing Supabase Auth), Spring's own file storage abstraction (replacing Supabase Storage) backed by the same Postgres instance for metadata, and the Temporal Java SDK for the resume-scoring workflow (replacing the Python worker and Edge Functions).
- Frontend: Angular application re-implementing the same page/route inventory above as conventional Angular components/services/routing (no JSON-engine equivalent required — the JSON-engine's job is to be replaced by ordinary Angular code).
- Data: same Supabase-hosted Postgres database and schema (`entities`/`entity_versions`/etc.) — Spring Boot connects to it directly (e.g. via JDBC/JPA), bypassing PostgREST/Supabase client libraries.

## Goals and Non-Goals

### Goals
- Re-implement every current recruiter and candidate capability (see feature inventory) on Spring Boot + Angular + Temporal Java SDK, with no functional regressions.
- Keep the existing Postgres schema and data (`entities`/`entity_versions`/`relationships_v2`/`entity_facts`, auth/decisions/interviews/notifications tables) intact and reused as-is.
- Replace Supabase Auth with Spring Security-based auth for both recruiter and candidate roles.
- Replace Supabase Storage (`resumes` bucket) with a Spring Boot-managed file storage mechanism.
- Replace the two Supabase Edge Functions (`start-scoring-workflow`, `get-my-resume-url`) and the Python Temporal worker with Spring Boot REST endpoints + a Java Temporal worker.

### Non-Goals
- No new user-facing features beyond what already exists — this is a re-platform, not a feature expansion.
- No changes to the Postgres schema/data model beyond what's strictly required to drop Supabase-specific mechanisms (e.g. RLS policies, Supabase Auth's `auth.users` linkage) — schema changes needed for that will be called out explicitly during planning, not assumed here.
- Not migrating away from Anthropic/Ollama as the scoring LLM provider.
- Not changing hosting/deployment platform choices beyond what's needed to run Spring Boot + Angular locally via the existing `docker-compose.yml`/Makefile pattern.

### Success Metrics
- Every route/page in the current feature inventory has a working Angular equivalent backed by the new Spring Boot API.
- `make up` (or equivalent) brings up Postgres (via Supabase CLI, kept for local Postgres only) + Spring Boot + Angular, with no Python/Node Temporal worker or Edge Functions running.
- A full resume-upload-to-score flow works end-to-end through the new stack, verified live (not just unit tests), matching current behavior.

## Requirements

### Functional Requirements

#### Must Have (P0)
- Spring Security auth for recruiter and candidate roles, matching current role-based access behavior (see `docs/specs/0008-auth-recruiter-candidate-login.md`, `0011-unify-login.md`).
- REST endpoints covering: jobs (list/create), applications (list/filter by job, trigger scoring, bulk upload), candidates (history/detail/scorecard), recruiter decisions, interviews, notifications, candidate self-service (profile, settings, saved jobs, job alerts, messages, my-applications).
- File upload/storage for resumes, replacing the Supabase `resumes` Storage bucket.
- Temporal Java SDK workflow reproducing `ScoreResumeFitWorkflow` (extract resume text → LLM scoring → persist candidate entity/version/relationship/fact), including the existing partial-failure handling for bulk scoring (see `docs/specs/0004-bulk-scoring-partial-failure-handling.md`).
- Angular app covering every route listed in Current State, using conventional Angular routing/components/services (no JSON-driven page engine).

#### Should Have (P1)
- Reuse of the Adapt IT design tokens/branding already applied to the React frontend (colors, fonts) in the new Angular app.

#### Nice to Have (P2)
- Automated migration/parity checklist mapping each old route to its new Angular equivalent, to track cutover completeness.

### Non-Functional Requirements
- **Security**: Auth/session handling must not regress current role-separation guarantees (recruiter vs. candidate data access).
- **Compatibility**: Must connect to the same Postgres schema without requiring a data migration/backfill.
- **Reliability**: Temporal-based scoring must retain durability/retry behavior at least equivalent to the current Python workflow.

### Constraints
- Big-bang rollout: new `backend/` (Spring Boot) and a new Angular frontend directory are built alongside the existing `temporal/`/`frontend/`, cut over once feature-complete, then the old stack is removed.
- Local Postgres continues to run via the Supabase CLI (`supabase start`) purely as a Postgres provider — Supabase Auth/Storage/Edge Functions/PostgREST are not used by the new stack.

## Technical Design

### Architecture
```
┌───────────┐     ┌────────────────────┐     ┌──────────────┐
│  Angular  │────▶│  Spring Boot REST  │────▶│   Postgres   │
│ frontend  │     │      API           │     │ (Supabase-   │
└───────────┘     │  + Spring Security │     │  hosted, CLI)│
                   │  + file storage    │     └──────────────┘
                   └─────────┬──────────┘
                             │
                             ▼
                   ┌────────────────────┐
                   │ Temporal Java SDK  │
                   │  worker (scoring)  │
                   └─────────┬──────────┘
                             │
                             ▼
                   ┌────────────────────┐
                   │ Anthropic / Ollama │
                   └────────────────────┘
```

### Data Model
No schema changes assumed at spec time. The implementation plan must audit which existing mechanisms are Supabase-specific and therefore need a Spring Boot-side equivalent or a small schema adjustment:
- Supabase Auth's `auth.users` table (recruiter/candidate accounts currently link to it) — Spring Security needs its own credential storage or a compatible substitute.
- RLS policies (e.g. `resumes` Storage bucket policy, any table-level RLS) — irrelevant once PostgREST/Supabase client access is dropped, but must confirm nothing else depends on them.
- Table grants added for `anon`/`authenticated`/`service_role` (see prior grant-fix migration) — Spring Boot will likely connect as a dedicated DB role instead.

### Security Considerations
- Recruiter/candidate credential storage and password handling must be re-designed under Spring Security (was previously fully delegated to Supabase Auth) — needs explicit design in the implementation plan, not assumed.
- File upload validation (type/size limits on resumes) must be enforced server-side in Spring Boot, since Supabase Storage RLS is being dropped.

## Implementation Plan
Not written yet — per repo workflow, the implementation plan is written after this spec is approved (Gate 1 approval required before any code is written).

## Testing Strategy
To be detailed in the implementation plan. At minimum, per repo testing guidelines: real end-to-end testing of the full upload-to-score flow against a live local stack, plus adversarial testing (bad file types, oversized uploads, invalid auth, a failing resume in a bulk batch).

## Open Questions
- [ ] Exact Spring Boot module layout (single module vs. multi-module for API/worker) — to be resolved in the implementation plan.
- [ ] How recruiter/candidate credentials migrate off `auth.users` (new table + Spring Security `UserDetailsService`, vs. another approach) — to be resolved in the implementation plan.
- [ ] Whether the Supabase CLI stack is kept solely for local Postgres, or replaced with a plain Postgres Docker image, once Auth/Storage/Edge Functions/Studio are no longer used.

## References
- Prior specs for existing features being re-platformed: `docs/specs/0001` through `0017`.
- Ticket: #29
