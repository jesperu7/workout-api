#!/usr/bin/env bash
# Mint a Supabase access token (ES256 JWT) against the LOCAL stack.
#
#   TOKEN=$(scripts/dev-token.sh)           # member: dev@workout.test (password sign-in)
#   TOKEN=$(scripts/dev-token.sh --guest)   # guest: anonymous sign-in (is_anonymous=true)
#   curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
#
# Env overrides: SUPABASE_URL, SUPABASE_ANON_KEY, DEV_EMAIL, DEV_PASSWORD.
# The apikey below is the local stack's shared publishable default (not a real secret).
set -euo pipefail

SUPABASE_URL="${SUPABASE_URL:-http://127.0.0.1:54321}"
APIKEY="${SUPABASE_ANON_KEY:-sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH}"
EMAIL="${DEV_EMAIL:-dev@workout.test}"
PASSWORD="${DEV_PASSWORD:-password123}"

if [[ "${1:-}" == "--guest" ]]; then
  # Anonymous sign-in: an EMPTY signup body creates a guest session (this is what
  # supabase-js signInAnonymously() calls). Requires enable_anonymous_sign_ins = true
  # in supabase/config.toml and a restarted stack. Note: every call mints a brand-new
  # guest user (that's the nature of anonymous accounts).
  curl -s -X POST "$SUPABASE_URL/auth/v1/signup" \
    -H "apikey: $APIKEY" -H "Content-Type: application/json" \
    -d '{}' \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
  exit 0
fi

# Create the user if needed (local stack auto-confirms; ignore "already registered").
curl -s -X POST "$SUPABASE_URL/auth/v1/signup" \
  -H "apikey: $APIKEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" >/dev/null || true

# Sign in and print just the access_token.
curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $APIKEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
