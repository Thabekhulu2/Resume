# Implementation Plan: Default Dev Stack to Live-Reload

**Spec:** [0007-default-dev-stack-live-reload.md](./0007-default-dev-stack-live-reload.md)
**Ticket:** Closes #16
**Status:** Approved (Gate 1) — implemented and live-verified 2026-09-01.

## Implementation notes (deviations from the plan as written)

- `make` itself is not installed on this machine (Windows/Git Bash — matches the known gotcha for this repo family). Verification below exercises the exact command sequences the Makefile's `up` target now runs by default (`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`) rather than literally invoking `make up`/`make down`/`make reset`. The Makefile logic itself (`ifeq ($(USE_DEV),1)`) is unchanged from the prior working version — only the default value flipped — so this is not expected to behave differently under real `make`.
- Did not test `make down`/`make reset` end-to-end (no `make` binary available); the compose/`supabase stop` commands they wrap were already exercised in this and prior sessions and are unaffected by this change (it only touches which files are layered on `up`).

## Phase 1: `Makefile`

- [x] `USE_DEV?=0` → `USE_DEV?=1`
- [x] Header comment above `up` updated to state live-reload is on by default and `USE_DEV=0` is the opt-out

## Phase 2: `README.md`

- [x] Quick Start's `make up` step updated: live-reload on by default, `USE_DEV=0 make up` documented as the frozen-image opt-out
- [x] Scanned rest of `README.md` — no other `USE_DEV` mentions found

## Phase 3: Verification

- [x] Confirmed default (dev) path: `docker inspect resume-frontend` showed `frontend/` bind-mounted live into `/app` (the config `make up` now produces by default)
- [x] Frontend already reflects live source with this mount active (verified earlier this session — edited `frontend/src` picked up without rebuild)
- [x] Opt-out path: ran the `USE_DEV=0` equivalent (`docker compose -f docker-compose.yml up -d --build frontend`, no dev overlay) — `docker inspect` showed zero mounts (frozen image), frontend still served `200`
- [x] Restored default: ran the `USE_DEV=1`-equivalent command again — live mount confirmed back in place
- [ ] `make down` / `make reset` — not run (see Implementation notes; no `make` binary on this machine)
- [ ] Adversarial Ctrl-C-mid-up case — not run (same reason; the compose commands underneath are unchanged by this fix, so behavior on interrupt is not expected to differ from before)

## Out of scope for this plan (per spec's Non-Goals)

- Changing `docker-compose.dev.yml`'s mounts or mechanics
- Renaming Makefile targets
- Any change to `deploy/`/CI build behavior

## Dependencies between phases

Phase 1 must land before Phase 3 (verification exercises the new default). Phase 2 is independent of Phase 1 but should land alongside it in the same change.
