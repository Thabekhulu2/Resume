#!/usr/bin/env bash
# Emits `export VAR=value` lines mapping the keys printed by `supabase status`
# to the variable names this stack's docker-compose expects.
#
# Local Supabase signing keys are generated PER INSTANCE (newer CLIs use
# asymmetric ES256 keys with an instance-specific key id), so they cannot be
# committed to .env.example -- `make up` sources them live via this script and
# exports them into the environment docker compose reads.
set -euo pipefail

status="$(supabase status -o env)"
get() { printf '%s\n' "$status" | sed -n "s/^$1=\"\\(.*\\)\"\$/\\1/p"; }

anon="$(get ANON_KEY)"
service="$(get SERVICE_ROLE_KEY)"

if [ -z "$anon" ] || [ -z "$service" ]; then
  echo "supabase-env.sh: could not read keys from 'supabase status' -- is Supabase running?" >&2
  exit 1
fi

printf 'export SUPABASE_ANON_KEY=%s\n' "$anon"
printf 'export SUPABASE_SERVICE_ROLE_KEY=%s\n' "$service"
printf 'export VITE_SUPABASE_ANON_KEY=%s\n' "$anon"

# supabase start recreates supabase_edge_runtime_<project_id> without a restart
# policy, and it's known to exit shortly after startup -- keep it auto-restarting
# so a dead edge runtime doesn't silently break scoring. Output goes to stderr so
# it doesn't get swallowed by callers that eval this script's stdout.
docker update --restart unless-stopped "supabase_edge_runtime_resume" >&2 || true
