# Default Dev Stack to Live-Reload Specification

**Status:** Draft
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-01
**Last Updated:** 2026-09-01
**Ticket:** Closes #16

## Overview

`make up` (i.e. `docker compose -f docker-compose.yml up`, `USE_DEV` unset/`0`) builds the frontend from a static image snapshot of `frontend/` at build time rather than mounting source live. Editing frontend code after `make up` shows no changes in the browser, with no indication in logs or UI that the running container is frozen rather than live. Only `USE_DEV=1 make up` (which layers `docker-compose.dev.yml`, bind-mounting `frontend/` and `temporal/src/`) reflects live edits. This spec makes live-reload the default local-dev path.

## Goals

- A plain `make up` gives live-reload for `frontend/` and `temporal/src/` by default — no `USE_DEV=1` needed
- The frozen-image (non-dev) mode remains available for verification/CI-like local checks, opt-in rather than opt-out
- README Quick Start documents the new default and how to opt into the frozen-image mode

## Non-Goals

- Changing what `docker-compose.dev.yml` mounts or how live-reload works internally — only which mode is the default
- Renaming Makefile targets or changing `make down` / `make reset` behavior
- Any change to production/deployed build behavior (`deploy/`, CI) — this is local dev only

## User Stories

### As the developer, I want `make up` to give me a live-reload frontend/worker by default, so that editing source code always shows up without extra flags or a manual rebuild

**Acceptance Criteria:**
- [ ] `make up` (no `USE_DEV` set) starts the stack with `docker-compose.dev.yml` layered in
- [ ] Editing a file under `frontend/src/` while the stack from `make up` is running shows the change in the browser without a manual `docker compose up --build`
- [ ] An explicit opt-out (e.g. `USE_DEV=0 make up`) still gives the frozen-image build for verification purposes
- [ ] README Quick Start reflects the new default and documents the opt-out

## Technical Design

### Architecture

No architectural change — flips the Makefile's default value for `USE_DEV` and updates docs to match. No application or compose-file changes beyond the default.

### Changes

- **`Makefile`**: `USE_DEV?=0` → `USE_DEV?=1`
- **`README.md`**: Quick Start's `make up` step updated to note live-reload is the default; document `USE_DEV=0 make up` as the opt-out for a frozen-image run

### Security Considerations

None — dev-only local tooling default, no secrets or auth surface touched.

### Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Someone relying on the old frozen-image default (e.g. to sanity-check a production-like build locally) gets live-reload unexpectedly | Low | Low | Document `USE_DEV=0 make up` opt-out clearly in README |

## Testing Strategy

- Manual: run `make up` with no `USE_DEV` set, confirm `docker compose ps` shows dev-overlay behavior (bind mounts present via `docker inspect`), edit a `frontend/src` file, confirm it's reflected in the browser without a rebuild
- Manual: run `USE_DEV=0 make up`, confirm the frozen-image behavior still works (no live mount)
- Adversarial: confirm `make down` / `make reset` still work correctly against the new default-dev stack

## Open Questions

- [ ] None

## Approval

- [ ] Ndumiso Mpanza
