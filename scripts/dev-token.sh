#!/usr/bin/env bash
# Mint a Supabase access token (ES256 JWT) for a dev user against the LOCAL stack.
#
#   TOKEN=$(scripts/dev-token.sh)
#   curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
#
# Env overrides: SUPABASE_URL, SUPABASE_ANON_KEY, DEV_EMAIL, DEV_PASSWORD.
# The apikey below is the local stack's shared publishable default (not a real secret).
set -euo pipefail

SUPABASE_URL="${SUPABASE_URL:-http://127.0.0.1:54321}"
APIKEY="${SUPABASE_ANON_KEY:-sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH}"
EMAIL="${DEV_EMAIL:-dev@workout.test}"
PASSWORD="${DEV_PASSWORD:-password123}"

# Create the user if needed (local stack auto-confirms; ignore "already registered").
curl -s -X POST "$SUPABASE_URL/auth/v1/signup" \
  -H "apikey: $APIKEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" >/dev/null || true

# Sign in and print just the access_token.
curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $APIKEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
