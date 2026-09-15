# Resume Candidate Profile Stack

Spring Boot (Java) backend + Angular frontend, with Temporal (Java SDK) for the resume-scoring workflow and Postgres (provided by the Supabase CLI) for storage.

## Prerequisites
- Docker Desktop with Compose v2
- `make` (comes with macOS/Linux; install via Xcode CLT on macOS)
- **Supabase CLI** — required; `make up` runs `supabase start` to launch a local Postgres instance (that's its only role here — Auth/Storage/Studio/Edge Functions are not used)
- Node 18+ (optional for running the Angular frontend outside Docker)

## Quick Start
1) Copy environment defaults  
   `cp .env.example .env`
2) Start everything  
   `make up`  
   (live-reload mounts for backend/frontend-angular are on by default; pass `USE_DEV=0 make up` for a frozen built-image run instead)  
   This runs `supabase start` (Postgres, with migrations and seed applied), then brings up Temporal, the Spring Boot backend, and the Angular frontend.
3) Open services  
   - Frontend: http://localhost:54200  
   - Backend API: http://localhost:58081/api  
   - Temporal UI: http://localhost:58080  
   - Temporal gRPC: localhost:57234

Common commands:
- `make down` — stop containers and the Supabase stack
- `make reset` — tear down volumes + Supabase, then recreate (re-applies migrations and seed)
- `make logs` — stream all service logs
- `make logs-temporal` / `make logs-backend` / `make logs-frontend` — targeted logs
- `make supabase-status` — show Supabase-CLI Postgres connection details

## What's Included
- Local Postgres via the Supabase CLI (`supabase start`), with migrations and seed applied
- Docker Compose stack with Temporal server, UI, Spring Boot backend (owns auth, data access, file storage, and hosts the Temporal Java worker), and the Angular frontend dev server
- Development overrides in `docker-compose.dev.yml` for live-reloading backend and frontend-angular code
- Makefile wrappers for the usual lifecycle commands
- `.env.example` capturing required variables for the backend, frontend, and Temporal

## Auth
Login is required (`POST /api/auth/recruiter/login`, `POST /api/auth/candidate/login`) — see `docs/specs/0018-springboot-angular-migration.md`. Candidates self-register at `POST /api/auth/candidate/signup`. Recruiter accounts are not self-service and there is currently no admin endpoint or script to provision them (the previous `scripts/create-recruiter.sh` targeted Supabase Auth and no longer applies). For local dev, use the seeded account (`recruiter@resume.local` / `password123`, from `supabase/migrations/20260914090000_decouple_auth_from_supabase.sql`) or insert a row into `recruiters` directly with a bcrypt hash matching Spring Security's `PasswordEncoder`.

## Notes
- Supabase CLI runs Postgres only; the backend reaches it at `host.docker.internal:55322` from inside the container, `localhost:55322` from the host.
- Accounts that existed before the Spring Boot auth migration (`supabase/migrations/20260914090000_decouple_auth_from_supabase.sql`) had their passwords in Supabase Auth (GoTrue), which are unrecoverable — those users must sign up again.
