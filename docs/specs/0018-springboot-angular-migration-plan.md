# Implementation Plan: Spring Boot + Angular Migration

**Spec:** [0018-springboot-angular-migration.md](./0018-springboot-angular-migration.md)
**Ticket:** Closes #29
**Status:** Approved (Gate 1)

## Resolving the spec's open questions

- **Module layout**: single Spring Boot Maven/Gradle module (`backend/`) containing the REST API, Spring Security config, and an embedded Temporal worker (started via a `@Component`/`CommandLineRunner`, same "worker lives in the same process as the trigger" shape the Python side used). No separate worker module — simplest option, matches current deployment shape (one `temporal-worker` container).
- **Credential storage**: `recruiters`/`candidates` currently FK to `auth.users(id)` and hold no password — GoTrue owns credentials entirely. Since Supabase Auth is being dropped, this plan adds a migration (`supabase/migrations/<ts>_decouple_auth_from_supabase.sql`) that:
  - Drops the `references auth.users(id)` FK on both tables (keeps `id uuid primary key`, now app-generated).
  - Adds `email text unique not null` and `password_hash text not null` to both tables.
  - Drops the `handle_candidate_signup` trigger/function and the RLS policies that reference `auth.uid()` (dead once Spring Boot connects as its own DB role and enforces auth at the API layer instead of RLS).
  - **Known limitation, called out explicitly**: existing local dev accounts' passwords live in GoTrue and are not recoverable — they must be recreated (re-signed-up) against the new Spring Boot auth once it's live. This is acceptable for local/dev data; flag to the user before cutover in case any account matters.
  - Spring Security uses a JDBC-backed `UserDetailsService` (via `recruiters`/`candidates`, `BCryptPasswordEncoder` for `password_hash`) plus JWT issuance for stateless API auth (Angular sends `Authorization: Bearer`).
- **Supabase CLI's role**: kept solely as the local Postgres provider (`supabase start` for the DB container). Studio/Auth/Storage/Realtime/Edge Functions are not started or are ignored once the new stack is up; `docker-compose.yml` stops depending on them.

## Phase 0: Scaffolding

- [x] `backend/` — new Spring Boot 3.3.5 / Java 21 Maven project: Spring Web, Spring Security (placeholder permissive `SecurityFilterChain`, real rules land in Phase 1), Spring Data JPA, PostgreSQL driver, Temporal Java SDK (`temporal-sdk` 1.25.1), `jjwt` 0.12.6 (JWT), Apache PDFBox 3.0.3 + Apache POI 5.3.0 (PDF/DOCX extraction). No extra HTTP client dependency added for Anthropic — Spring's built-in `RestClient`/`java.net.http` covers it when Phase 4 needs it. A `GET /api/health` controller added purely to have something to smoke-test.
- [x] `frontend-angular/` — new Angular 22 (standalone components) project via `ng new --routing`, default welcome template stripped down to `<router-outlet />`, `provideHttpClient()` wired in `app.config.ts`, `environments/environment.ts`/`environment.prod.ts` (`apiUrl`), and a thin `core/api.service.ts` wrapping `HttpClient` (get/post/put/delete) for later pages to build on.
- [x] `docker-compose.yml`: added `backend` (builds `./backend`, port 58081, connects to the Supabase-CLI Postgres on 55322 via `host.docker.internal`, same Anthropic/Ollama/Temporal env vars as `temporal-worker`) and `frontend-angular` (builds `./frontend-angular`, port 54200) services, alongside the existing `temporal-worker`/`frontend`. `docker-compose.dev.yml`: bind-mounts + live-reload command overrides for both new services, matching the existing dev-override pattern.
- [x] `backend/src/main/resources/application.yml`: `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` default to the local Supabase-CLI Postgres (`127.0.0.1:55322`, `postgres`/`postgres`, per `supabase/config.toml`'s `[db] port = 55322`), plus `TEMPORAL_*`, `ANTHROPIC_*`, `USE_LOCAL_LLM`/`OLLAMA_*`, `RESUMES_STORAGE_DIR`, and a dev-default `JWT_SECRET` — same env var shape as `temporal/src/config.py` where applicable.
- [x] `backend/Dockerfile` (Maven+Temurin 21 image, `mvn spring-boot:run`) and `frontend-angular/Dockerfile` (`node:20-alpine`, `ng serve --host 0.0.0.0`), matching the dev-only-container style of the existing `temporal/Dockerfile`/`frontend/Dockerfile`.
- [x] `.gitignore`: added `backend/target/` (Angular's `dist/`/`node_modules/` were already covered by existing root patterns; `frontend-angular/` also ships its own CLI-generated `.gitignore`).

**Verification performed** (proportional to scaffolding-only scope — no business logic exists yet to functionally test):
- `mvn -DskipTests compile` and `mvn -DskipTests package` — both succeed, `backend-0.0.1-SNAPSHOT.jar` produced.
- `ng build` — succeeds, bundle produced.
- `ng test --watch=false` (Vitest, Angular 22's default runner) — 1/1 passing.
- `docker compose -f docker-compose.yml -f docker-compose.dev.yml config` — merges and parses cleanly, exit 0.
- **Not done, and flagged rather than faked**: an actual `docker compose up backend` boot-and-hit-`/api/health` smoke test. The Docker daemon isn't running in this sandbox (`docker ps` fails to reach `npipe:////./pipe/dockerDesktopLinuxEngine`). This should be done manually (`make up`, then `curl http://localhost:58081/api/health`) before relying on the backend container, and will happen naturally once Phase 1+ needs a live DB connection to verify against anyway.

## Phase 1: Auth decoupling migration + Spring Security

- [ ] Migration per "Resolving the spec's open questions" above.
- [ ] Spring Security: `UserDetailsService` backed by `recruiters`/`candidates`, BCrypt password hashing, JWT issuance/validation, role claim (`RECRUITER`/`CANDIDATE`) driving endpoint authorization (mirrors the current recruiter-vs-candidate RLS split, now enforced in `@PreAuthorize`/security filter chain instead of Postgres RLS).
- [ ] Endpoints: `POST /api/auth/recruiter/login`, `POST /api/auth/candidate/login`, `POST /api/auth/candidate/signup` (matches spec 0008/0011/0014 behavior — unified login page routes by role, candidate self-signup creates a `candidates` row directly instead of via trigger).

## Phase 2: Core data access layer

- [ ] JPA entities mapping the existing generic SCD2 model as-is: `entities`, `entity_versions`, `relationships_v2`, `fact_types`, `entity_facts` (no schema change to these — only the auth tables change).
- [ ] A thin repository/service layer replicating what `supabase_core.py` did (`create_entity`, `update_entity_scd2`, `get_entity`, `create_relationship`, `upsert_entity_fact`), now as plain JPA/`@Transactional` service methods instead of PostgREST calls.
- [ ] Since Spring Boot connects directly as a DB role (not through PostgREST), the anon/authenticated grants and remaining RLS policies from earlier migrations become dead weight for this new role — verify the new DB role Spring Boot uses has direct table grants (may reuse `service_role`'s underlying grants, or a new dedicated role — decide during implementation based on how local Postgres roles are set up).

## Phase 3: File storage (replacing the `resumes` Storage bucket)

- [ ] Local filesystem-backed storage service in Spring Boot (mirrors `Guide_for_agents_using_supabase_template.md`'s dev-only conventions — a `resumes/` volume mounted into the backend container), exposing upload (`multipart/form-data`) and authenticated download endpoints.
- [ ] Replaces both the frontend's direct Storage `upload()` call and the `get-my-resume-url` Edge Function (candidate's own-resume-download link) with `POST /api/resumes` and `GET /api/resumes/{id}` (auth-scoped: candidates can only fetch their own).

## Phase 4: Temporal Java SDK worker (scoring workflow)

- [ ] `ScoreResumeFitWorkflow` reimplemented with the Temporal Java SDK: activities for `extractResumeText` (PDF/DOCX text extraction from the Phase 3 storage service), `extractAndScore` (Anthropic/Ollama call, same strict response-shape validation the Python side had), and the entity/version/relationship/fact persistence activities from Phase 2.
- [ ] Reproduce the existing behavior this repo already hardened: bulk multi-resume scoring stops on first failure with a clear error identifying the failing resume (spec 0002/0004), `status: scoring/scored/failed` lifecycle on the candidate record, root-cause unwrapping for activity failures (the Python side's `exc.__cause__` walk — Java's `ActivityFailure`/`ApplicationFailure` have an equivalent cause chain to unwrap).
- [ ] `POST /api/scoring/start` (single or bulk `resumeStoragePaths`) replaces `start-scoring-workflow`'s Edge Function role, starting the workflow via the Temporal Java client.

## Phase 5: REST API — jobs, applications, candidates, decisions/interviews/notifications

- [ ] Jobs: list/create (spec 0009).
- [ ] Applications: list/filter by job, bulk upload + trigger scoring (spec 0010/0012/0015).
- [ ] Candidates: history list, detail/scorecard, job-link display (spec 0013).
- [ ] Recruiter decisions/interviews/notifications: `record_recruiter_decision` equivalent as a transactional service method (was a Postgres function — reimplement its exact guard logic, e.g. the `applicant_id is not null` check from spec 0017, in Java rather than SQL), `recruiter_scheduled_interviews` equivalent as a JPA query/projection, notification creation on decision events.
- [ ] Candidate self-service: profile, settings, saved jobs, job alerts, messages, my-applications, notifications (read/mark-read) — endpoints matching the candidate portal shell's data needs (spec 0016).

## Phase 6: Angular frontend

- [ ] Auth: unified login page (role-routed), candidate signup, route guards (Angular `CanActivate`) replacing `_authenticated`/`_candidateAuth` route-group guards.
- [ ] Recruiter app: Dashboard (Kanban by fit-score band, spec 0006), Jobs, Applications (per-job + bulk upload), Candidate History/detail/scorecard (with decision actions + interview scheduling modal), Scheduled Interviews.
- [ ] Candidate app: Dashboard, Jobs browse + Apply wizard, My Applications, Saved Jobs, Job Alerts, Messages, Notifications, Profile, Settings.
- [ ] Carry over the existing Adapt IT design tokens (colors/fonts from spec/ticket #8) into Angular's global styles.
- [ ] No JSON-page-engine equivalent — each page is a normal Angular component calling the Phase 1/3/4/5 REST endpoints directly.

## Phase 7: Cutover

- [ ] `docker-compose.yml`/`docker-compose.dev.yml`: remove `temporal-worker`/`frontend` services (Python + React), keep only the Postgres-providing pieces of the Supabase CLI stack, add `backend`/`frontend-angular` as the running services.
- [ ] Remove `temporal/`, `frontend/`, and the two Supabase Edge Functions (`supabase/functions/start-scoring-workflow`, `get-my-resume-url`) once the new stack has full parity.
- [ ] Update root `README.md`/`Guide_for_agents_using_supabase_template.md`/`Makefile` references to the old stack.

## Phase 8: Verification (e2e + adversarial, per repo testing standard)

- [ ] Real login flow for both roles against the new Spring Security auth (including a freshly-recreated account, per the credential-migration limitation above).
- [ ] Full resume-upload-to-score flow through the new stack (upload → Temporal Java workflow → LLM → persisted candidate + fact + relationship), verified by reading Postgres directly, matching current behavior.
- [ ] Bulk upload with one deliberately-bad resume (adversarial, mirrors spec 0004) — confirms the stop-on-first-failure + clear error behavior survived the port.
- [ ] Recruiter decision/interview/notification flow live (shortlist/reject/schedule → notification appears, matching spec 0017's verified behavior).
- [ ] Candidate portal: browse jobs, apply, view own applications/notifications — full click-through in a real browser.
- [ ] Adversarial auth checks: candidate token cannot hit recruiter-only endpoints and vice versa; expired/invalid JWT rejected; oversized/wrong-filetype resume upload rejected server-side (no client-side-only validation).

## Out of scope

Per spec Non-Goals: no new features beyond current parity, no LLM provider change, no schema changes beyond the auth-decoupling migration above.

## Dependencies between phases

Phase 0 must land first. Phase 1 (auth) blocks Phase 5's recruiter/candidate-scoped endpoints and all of Phase 6. Phase 2 (data layer) blocks Phases 4 and 5. Phase 3 (storage) blocks Phase 4 (resume text extraction needs storage to read from). Phase 4 blocks the scoring parts of Phase 5. Phase 6 depends on Phases 1, 3, 4, 5 all being far enough along to have real endpoints to call — in practice built incrementally per-page against whichever endpoints already exist, rather than strictly after Phase 5 finishes entirely. Phase 7 (cutover) only happens once Phase 8 verification passes. Given the size of this migration, each phase will be committed and Gate-2-approved separately rather than as one giant commit at the end.
