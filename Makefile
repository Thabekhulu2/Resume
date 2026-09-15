export DOCKER_BUILDKIT=0

COMPOSE_BASE=docker-compose.yml
COMPOSE_DEV=docker-compose.dev.yml
USE_DEV?=1

ifeq ($(USE_DEV),1)
COMPOSE_FILES=$(COMPOSE_BASE) $(COMPOSE_DEV)
else
COMPOSE_FILES=$(COMPOSE_BASE)
endif

COMPOSE_CMD=docker compose $(foreach file,$(COMPOSE_FILES),-f $(file))

.PHONY: up down reset logs logs-temporal logs-backend logs-frontend supabase-status

# `up` starts the Supabase CLI stack (used solely as the local Postgres
# provider -- Auth/Storage/Studio/Edge Functions are not used by this stack),
# applying migrations and seed, THEN brings up Temporal + backend +
# frontend-angular via docker compose. Live-reload (docker-compose.dev.yml)
# is on by default; pass USE_DEV=0 for a frozen built-image run instead.
up:
	supabase start
	@eval "$$(./scripts/supabase-env.sh)"; $(COMPOSE_CMD) up -d
	@echo ""
	@echo "Stack up. Frontend http://localhost:54200 | Backend API http://localhost:58081 | Temporal UI http://localhost:58080"

down:
	$(COMPOSE_CMD) down
	supabase stop

# Full wipe: tear down compose volumes AND the Supabase stack (incl. its DB),
# then recreate everything from scratch (migrations + seed re-applied).
reset:
	$(COMPOSE_CMD) down -v
	-supabase stop --no-backup
	$(MAKE) up

logs:
	$(COMPOSE_CMD) logs -f

logs-temporal:
	$(COMPOSE_CMD) logs -f temporal

logs-backend:
	$(COMPOSE_CMD) logs -f backend

logs-frontend:
	$(COMPOSE_CMD) logs -f frontend-angular

# Supabase is CLI-managed, not a compose service -- use this for its status/keys.
supabase-status:
	supabase status
