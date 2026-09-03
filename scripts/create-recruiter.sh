#!/usr/bin/env bash
# Provisions a Recruitment Team account against the local Supabase stack.
# Recruiter signup is intentionally not self-service (spec 0008) -- this is
# the supported way to create one for local dev/testing.
#
# Usage: ./scripts/create-recruiter.sh <email> <password> <full name>
set -euo pipefail

email="${1:?usage: create-recruiter.sh <email> <password> <full name>}"
password="${2:?usage: create-recruiter.sh <email> <password> <full name>}"
full_name="${3:?usage: create-recruiter.sh <email> <password> <full name>}"

status="$(supabase status -o env)"
get() { printf '%s\n' "$status" | sed -n "s/^$1=\"\\(.*\\)\"\$/\\1/p"; }

api_url="$(get API_URL)"
service_key="$(get SERVICE_ROLE_KEY)"

if [ -z "$api_url" ] || [ -z "$service_key" ]; then
  echo "create-recruiter.sh: could not read API_URL/SERVICE_ROLE_KEY from 'supabase status' -- is Supabase running?" >&2
  exit 1
fi

user_id="$(curl -sf "$api_url/auth/v1/admin/users" \
  -H "apikey: $service_key" \
  -H "Authorization: Bearer $service_key" \
  -H "Content-Type: application/json" \
  -d "$(printf '{"email":"%s","password":"%s","email_confirm":true}' "$email" "$password")" \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)"

if [ -z "$user_id" ]; then
  echo "create-recruiter.sh: failed to create auth user (see response above)" >&2
  exit 1
fi

curl -sf "$api_url/rest/v1/recruiters" \
  -H "apikey: $service_key" \
  -H "Authorization: Bearer $service_key" \
  -H "Content-Type: application/json" \
  -d "$(printf '{"id":"%s","full_name":"%s"}' "$user_id" "$full_name")" \
  > /dev/null

echo "Recruiter account created: $email (id: $user_id)"
