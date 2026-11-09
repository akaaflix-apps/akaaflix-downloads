#!/usr/bin/env bash
# Runs the end-to-end self-test of a built app against the local MOCK Xtream server.
# Usage: scripts/run-selftest.sh "path/to/Akaa Flix.app" [output-dir]
set -uo pipefail
APP="${1:?usage: run-selftest.sh <app> [outdir]}"
OUT="${2:-build/selftest}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8765}"
mkdir -p "$OUT/screenshots"
python3 "$ROOT/SelfTest/mock_xtream_server.py" --port "$PORT" --fixtures "$ROOT/SelfTest/fixtures" > "$OUT/mock-server.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
for _ in $(seq 1 30); do curl -fs "http://127.0.0.1:$PORT/player_api.php?username=test&password=test" >/dev/null && break; sleep 0.5; done
DATA="$(mktemp -d)"
"$APP/Contents/MacOS/Akaa Flix" --self-test \
  --self-test-server="http://127.0.0.1:$PORT/" \
  --self-test-report="$OUT/report.json" \
  --self-test-data="$DATA" \
  --self-test-screenshots="$OUT/screenshots" \
  --self-test-fixtures="$ROOT/SelfTest/fixtures" > "$OUT/app.log" 2>&1 &
APPPID=$!
( sleep "${SELFTEST_TIMEOUT:-420}"
  echo "[self-test] TIMEOUT — sampling the app before killing it" >> "$OUT/app.log"
  sample $APPPID 3 -file "$OUT/hang-sample.txt" >/dev/null 2>&1
  kill -9 $APPPID 2>/dev/null ) &
WATCH=$!
wait $APPPID
CODE=$?
kill $WATCH 2>/dev/null || true
grep "\[self-test\]" "$OUT/app.log" || tail -50 "$OUT/app.log"
if [[ -f "$OUT/hang-sample.txt" ]]; then
  echo "---- main thread at timeout ----"
  awk '/com.apple.main-thread/{p=1} p{print; if (++n > 70) exit}' "$OUT/hang-sample.txt"
fi
echo "self-test exit code: $CODE"
exit $CODE
