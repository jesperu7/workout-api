#!/usr/bin/env bash
# Seed a little Bench Press history for the dev user by calling the REAL API endpoints,
# so it doubles as a live end-to-end test of the logging flow AND gives the history
# feature (M5) real data to read back.
#
# Requires `supabase start` and `./gradlew bootRun` to be running.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="${BASE_URL:-http://localhost:8080}"
TOKEN="$("$DIR/dev-token.sh")"
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

jget() { python3 -c "import sys,json;print(json.load(sys.stdin)['$1'])"; }

bench=$(
  curl -s "${AUTH[@]}" "$BASE/api/exercises" \
    | python3 -c "import sys,json;print(next(e['id'] for e in json.load(sys.stdin) if e['name']=='Bench Press'))"
)
echo "Bench Press = $bench"

# session <iso-date> <weightxreps>...   e.g. session 2026-05-01T17:00:00Z 100x5 100x5
session() {
  local date="$1"
  shift
  local w we i=0
  w=$(curl -s "${AUTH[@]}" -d "{\"performedAt\":\"$date\",\"notes\":\"bench session\"}" "$BASE/api/workouts" | jget id)
  we=$(curl -s "${AUTH[@]}" -d "{\"exerciseId\":\"$bench\"}" "$BASE/api/workouts/$w/exercises" | jget id)
  for spec in "$@"; do
    curl -s -o /dev/null "${AUTH[@]}" \
      -d "{\"setIndex\":$i,\"weight\":${spec%x*},\"reps\":${spec#*x},\"performedAt\":\"$date\"}" \
      "$BASE/api/workout-exercises/$we/sets"
    i=$((i + 1))
  done
  echo "  $date  ->  $*"
}

echo "Seeding Bench Press history..."
session "2026-05-01T17:00:00Z" 100x5 100x5 100x4
session "2026-05-08T17:00:00Z" 100x6 102.5x5 102.5x4
session "2026-05-15T17:00:00Z" 102.5x5 102.5x5 105x3
echo "Done."
