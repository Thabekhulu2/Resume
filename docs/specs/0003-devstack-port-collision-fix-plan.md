# Implementation Plan: Dev Stack Port/Name Collision Fix

**Spec:** [0003-devstack-port-collision-fix.md](./0003-devstack-port-collision-fix.md) (Approved)
**Ticket:** Closes #11
**Status:** Approved (Gate 1) — implemented and live-verified 2026-08-31.

## Implementation notes (deviations from the plan as written)

- **Phase 1:** Frontend host port ended up as **53900**, not 53000. Windows currently holds TCP `52978-53380` as a dynamic port-exclusion range (Hyper-V/WSL NAT reservation, visible via `netsh interface ipv4 show excludedportrange protocol=tcp`), which made Docker's bind to `53000` fail with "forbidden by its access permissions." All other new ports (55433, 57234, 58080, 58001) landed outside any excluded range and bound on the first try. `53900` was picked as a value clear of every currently-excluded range; this class of exclusion is dynamic and can shift after a reboot, so a future collision on this specific port, while unlikely, isn't structurally impossible — no code depends on the literal value, so it's a one-line fix if it recurs.
- **Phase 1:** Internal container-to-container hostnames (`temporal:7233`, `temporal-db`) were left unchanged rather than renamed to `resume-temporal:7233`/etc. Docker Compose registers a network alias for both the `container_name` and the (unchanged) service key, so `temporal`/`temporal-db` still resolve correctly post-rename — changing them was unnecessary and added rename-typo risk for no behavioral benefit. Verified live: the worker successfully reached `temporal:7233` and the DB.

## Phase 1: `docker-compose.yml`

- [x] `name: 10x-stack-template` → `name: resume-stack`
- [x] Rename `container_name`s: `temporal-db`→`resume-temporal-db`, `temporal`→`resume-temporal`, `temporal-ui`→`resume-temporal-ui`, `temporal-worker`→`resume-temporal-worker`, `frontend`→`resume-frontend`
- [x] Cross-container references reviewed — no changes needed (see Implementation notes above); confirmed working live
- [x] Reassign host ports: `temporal-db` 5433→55433, `temporal` 7234→57234, `temporal-ui` 8080→58080, `temporal-worker` 8001→58001, `frontend` 3000→**53900** (see Implementation notes); container-internal ports unchanged
- [x] Header comment re-read — no stale references to the renamed services' old ports

## Phase 2: `docker-compose.dev.yml`

- [x] `name: 10x-stack-template` → `name: resume-stack`
- [x] Service keys and volume mounts confirmed unaffected

## Phase 3: `.env.example`

- [x] `SCORING_TRIGGER_URL=http://host.docker.internal:8001` → `http://host.docker.internal:58001`
- [x] Scanned remaining lines — no other stale port references found

## Phase 4: Docs

- [x] `README.md` — Frontend/Temporal UI/Temporal gRPC URLs updated to 53900/58080/57234
- [x] Grepped `Guide_for_agents_using_supabase_template.md` and `docs/_templates/*` for the old port numbers — only unrelated hits found (generic boilerplate templates, and `charts/app/README.md`'s Helm chart in-cluster service port, which is a separate production/k8s config untouched by local dev compose) — no changes needed there

## Phase 5: Verification — live, both stacks running simultaneously

- [x] Started Docker Desktop, `supabase start`, then `eval "$(./scripts/supabase-env.sh)"` + `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build` — all 5 containers came up under their `resume-*` names once the port-53000 exclusion was worked around
- [x] `docker ps` showed `resume-temporal-db/temporal/temporal-ui/temporal-worker/frontend` running **alongside** NduMan's still-running unprefixed `temporal-db/temporal/temporal-ui/temporal-worker/frontend` and `supabase_*_project-template` containers — zero name or port collisions
- [x] Frontend responded `200` at `localhost:53900`; Temporal UI `200` at `localhost:58080`
- [x] Full end-to-end smoke test via direct API calls (Storage upload of a generated `.docx` fixture → `start-scoring-workflow` Edge Function → HTTP trigger → `ScoreResumeFitWorkflow` → local Ollama `qwen2.5:7b` scoring): candidate reached `status: scored` with correctly extracted name/skills/experience and a persisted `entity_facts` row (`jd_fit_score = 95.0`)
- [x] Confirmed network isolation directly: `docker network ls` shows `resume-stack_default` and `10x-stack-template_default` as separate bridge networks; `docker inspect` confirms `resume-temporal` and NduMan's `temporal` sit on their respective networks only
- [x] Confirmed NduMan unaffected throughout: its frontend (`localhost:3000`) and Temporal UI (`localhost:8080`) both still returned `200` after Resume's stack was brought up and after it was torn down
- Test data (Storage objects, `entities` rows) cleaned up after verification; Resume's compose stack and `supabase stop`ped afterward; NduMan's stack was never touched (was already running before this session, left running as-is per instruction)
- Environment notes surfaced (not code changes, not committed): local Edge Functions need `supabase/functions/.env` for the `SCORING_TRIGGER_URL` secret (gitignored via the existing blanket `.env` rule, not previously documented) — created for this test, removed afterward; hit the same "stale placeholder Supabase key baked into a container recreated without re-sourcing keys in the same shell call" gotcha as prior sessions ([[project-nduman-setup]]) when recreating the worker with local-LLM env overrides — fixed by re-running `eval` + `docker compose up` in one call

## Out of scope for this plan (per spec's Non-Goals)

- Changing NduMan's `docker-compose.yml`
- Changing the Supabase CLI port block (55xxx)
- Env-driven/configurable port overrides
- Shared networking or service discovery between the two stacks

## Dependencies between phases

Phase 1 must land before Phases 2-4 (they reference the same project name / port values). Phase 5 depends on all prior phases.
