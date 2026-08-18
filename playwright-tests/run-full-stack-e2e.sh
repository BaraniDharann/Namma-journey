#!/usr/bin/env bash
#
# Boot the whole stack, run the E2E suite against it, then tear it down again.
#
# Everything lives and dies inside this one script so the servers cannot be reaped
# part-way through a run — a backend that disappears mid-suite shows up as unrelated
# "flaky" failures in whichever spec happened to be executing.
#
#   ./run-full-stack-e2e.sh                     # whole suite
#   ./run-full-stack-e2e.sh tests/07-frontend.spec.ts   # one spec (args are passed through)
#
# Assumes Postgres is already running; it is the one dependency with real data in it.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/target/travel-booking-platform-1.0.0.jar"
LOGDIR="${TMPDIR:-/tmp}/nj-e2e"
mkdir -p "$LOGDIR"

BACKEND_PID=""
FRONTEND_PID=""

# Whatever process is listening on $1, if any. netstat is the one view of this that works
# the same under Git Bash, WSL and CI, where lsof and ss are not reliably present.
port_owner_pid() {
  netstat -ano 2>/dev/null | grep -i listening | grep ":$1 " | awk '{print $NF}' | head -1
}

# Kill by port as well as by job id. The servers are launched inside subshells, so the pid
# recorded below belongs to the subshell and not to the java/node process doing the work —
# on Windows killing the parent leaves the child holding the port, and the next run then
# silently tests against that orphan.
kill_port() {
  local pid
  pid="$(port_owner_pid "$1")"
  if [ -n "$pid" ]; then
    taskkill //PID "$pid" //T //F >/dev/null 2>&1 || kill -9 "$pid" 2>/dev/null
  fi
}

cleanup() {
  echo
  echo "[e2e] stopping servers…"
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
  kill_port 5173
  kill_port 8080
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

# Refuse to start on top of somebody else's server. Without this the run happily proceeds
# against a leftover process: a backend that logged "Port 8080 was already in use" and died
# still leaves the port occupied, wait_for_port sees a listener and reports success, and the
# whole suite then exercises the wrong build.
require_free_port() {
  local port="$1" label="$2" pid
  pid="$(port_owner_pid "$port")"
  if [ -n "$pid" ]; then
    echo "[e2e] port $port is already in use by pid $pid — refusing to start $label on top of it."
    echo "[e2e] stop that process first:  taskkill //PID $pid //T //F"
    exit 1
  fi
}

# Waits for OUR server, not for any server. The pid argument is watched so that a backend
# which exits during startup fails the run immediately instead of timing out three minutes
# later against whatever else happens to be on the port.
wait_for_port() {
  local port="$1" label="$2" pid="$3" tries=0
  until netstat -ano 2>/dev/null | grep -i listening | grep -q ":$port"; do
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      echo "[e2e] $label exited before it bound port $port — see $LOGDIR"
      exit 1
    fi
    tries=$((tries + 1))
    if [ "$tries" -gt 180 ]; then
      echo "[e2e] $label never came up on port $port — see $LOGDIR"
      exit 1
    fi
    sleep 1
  done
  echo "[e2e] $label up on :$port"
}

if [ ! -f "$JAR" ]; then
  echo "[e2e] $JAR is missing. Build it first:"
  echo "      JAVA_HOME=/c/Program\\ Files/Java/jdk-17 ./apache-maven-3.9.6/bin/mvn clean package -DskipTests"
  exit 1
fi

# otp.test.mode        — OTPs are readable from the DB instead of being emailed.
# mail.delivery.enabled— suppress ALL outbound mail. otp.test.mode only covers OTPs, so without
#                        this a run delivers "trip assigned" and driver-credential mail to the
#                        real addresses sitting in fixture data.
# app.cache.type       — no Redis dependency for a local run.
# trusted-proxies      — the suite fakes a client IP per context, and X-Forwarded-For is only
#                        believed from a trusted peer. "localhost" arrives as ::1, not
#                        127.0.0.1, so both loopback forms are needed.
require_free_port 8080 backend
require_free_port 5173 frontend

echo "[e2e] starting backend…"
( cd "$ROOT" && java -jar "$JAR" \
    --otp.test.mode=true \
    --mail.delivery.enabled=false \
    --app.cache.type=caffeine \
    --app.ratelimit.trusted-proxies=127.0.0.1,::1 \
    > "$LOGDIR/backend.log" 2>&1 ) &
BACKEND_PID=$!
wait_for_port 8080 backend "$BACKEND_PID"

echo "[e2e] starting frontend…"
( cd "$ROOT/frontend" && npm run dev > "$LOGDIR/frontend.log" 2>&1 ) &
FRONTEND_PID=$!
wait_for_port 5173 frontend "$FRONTEND_PID"

echo "[e2e] running suite…"
( cd "$ROOT/playwright-tests" && npx playwright test --project=chromium "$@" )
STATUS=$?

echo
echo "[e2e] suite exited with status $STATUS"
echo "[e2e] backend log:  $LOGDIR/backend.log"
exit $STATUS
