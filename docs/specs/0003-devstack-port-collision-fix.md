# Dev Stack Port/Name Collision Fix Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-08-31
**Last Updated:** 2026-08-31
**Ticket:** Closes #11

## Overview

Resume and the sibling repo NduMan are both scaffolded from the same `10x-stack-template` and their `docker-compose.yml` files still share the same compose project name, container names, and several host ports. Running `docker compose up` for both stacks at once on this machine collides. This fix makes Resume's stack unique so it can run alongside NduMan's without manual workarounds.

## Goals

- Resume's `docker-compose.yml` (and `docker-compose.dev.yml`) use a compose project name and container names that cannot collide with NduMan's
- Resume's compose-managed host ports (temporal-db, temporal, temporal-ui, frontend, temporal-worker HTTP trigger) do not overlap NduMan's
- `.env.example`, `scripts/supabase-env.sh`, and `README.md`/setup docs are updated to match any changed ports
- `make up` / `make down` continue to work unchanged (no Makefile target renames)

## Non-Goals

- Changing NduMan's `docker-compose.yml` (separate repo, out of scope)
- Changing the Supabase CLI port block (55xxx) — already deconflicted, not part of this ticket
- A general-purpose port-allocation mechanism (env-driven port overrides, `.env`-configurable compose ports) — fixed reassigned values are sufficient for a two-project collision
- Making the two stacks runnable on a shared Docker network / service discovery between them — they remain fully independent stacks

## User Stories

### As the developer, I want to run Resume's full local stack while NduMan's stack is also running, so that I can work on both projects without stopping one to start the other

**Acceptance Criteria:**
- [ ] `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build` succeeds in Resume while NduMan's equivalent stack is already up
- [ ] `docker ps` shows distinct container names for both stacks, no name-conflict errors
- [ ] No host port bind errors on startup
- [ ] Resume's frontend, Temporal UI, and scoring flow (upload → score → scorecard) all work normally against the renamed/reported ports
- [ ] `docs/` / `README.md` reflect the new ports so a fresh clone doesn't hit stale instructions

## Technical Design

### Architecture

No architectural change — this is a renaming/remapping exercise across compose config and the env files that reference those ports. No application code changes.

### Changes

- **`docker-compose.yml`**: `name:` → `resume-stack`; each `container_name` prefixed uniquely (`resume-temporal-db`, `resume-temporal`, `resume-temporal-ui`, `resume-temporal-worker`, `resume-frontend`); reassign host ports for `temporal-db` (5433), `temporal` (7234), `temporal-ui` (8080), `frontend` (3000), `temporal-worker` (8001) to a block that doesn't overlap NduMan's current ports. In-container ports and container-name-based DNS (`temporal:7233`, `temporal-db`, etc.) are also updated to match new container names, since Temporal/worker/UI reference each other by container name over the compose network.
- **`docker-compose.dev.yml`**: has its own `name: 10x-stack-template` (must match the base file's `name:` for the two files to be one compose project) and volume-mounts `frontend`/`temporal-worker` by service name (unaffected by container renames) — only the `name:` line needs updating to match.
- **`.env.example`**: `SCORING_TRIGGER_URL` currently hard-codes `http://host.docker.internal:8001` — update to the new worker port if changed.
- **`README.md`** and any setup doc listing the ports for local dev.

### Port/Name Assignment

Reassign to a Resume-specific block, analogous to the 55xxx Supabase remap already done:

| Service | Old container name | New container name | Old port | New port |
|---|---|---|---|---|
| temporal-db | `temporal-db` | `resume-temporal-db` | 5433 | 55433 |
| temporal | `temporal` | `resume-temporal` | 7234 | 57234 |
| temporal-ui | `temporal-ui` | `resume-temporal-ui` | 8080 | 58080 |
| temporal-worker | `temporal-worker` | `resume-temporal-worker` | 8001 | 58001 |
| frontend | `frontend` | `resume-frontend` | 3000 | 53000 |

Compose project `name:` → `resume-stack`.

(Exact port numbers open for adjustment in review — goal is "obviously non-overlapping with NduMan's defaults," not a specific scheme.)

### Security Considerations

None — dev-only local networking change, no secrets or auth surface touched.

### Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Missed a hard-coded reference to an old port/container name somewhere (docs, scripts, CI) | Medium — stale docs or a broken script | Medium | Grep the repo for each old port number and container name before closing the ticket |
| Frontend port change breaks muscle-memory `localhost:3000` bookmark | Low | High | Call out new URL clearly in README and PR description |

## Testing Strategy

- Manual: `supabase start`, then bring up compose stack, confirm all containers healthy under new names, confirm frontend loads and a resume upload/score/scorecard round-trip works end to end on the new ports
- Manual: with NduMan's stack also running, repeat the above and confirm no port/name conflicts and no cross-talk between the two stacks

## Open Questions

- [ ] None — port scheme above is a proposal; confirmed during plan review.

## Approval

- [ ] Ndumiso Mpanza
