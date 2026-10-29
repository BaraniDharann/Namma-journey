#!/usr/bin/env bash
# Zero-downtime deploy on the EC2 server. Costs nothing: it runs on the instance you already have.
#
#   scripts/deploy.sh               # deploy the latest images CI pushed
#   scripts/deploy.sh 3f9c2ab...    # deploy (or roll back to) one exact commit
#   DEPLOY_PROD=1 scripts/deploy.sh # with docker-compose.prod.yml (replicas + RabbitMQ)
#
# How it stays up:
#   1. pulls the new images (built by CI - nothing is compiled on this server)
#   2. checks the new frontend's nginx config before it can replace a working one
#   3. starts new backends NEXT TO the running ones and waits until each is ready
#      (Dockerfile HEALTHCHECK = /actuator/health/readiness: Flyway done, database answering)
#   4. only then stops the old ones, gracefully: Spring finishes in-flight requests (up to 25s)
#      while nginx sends new requests to the new backends
#   5. swaps the frontend (nginx) - the only moment with a gap, about a second
#
# If a new backend never gets ready, it is removed and the old ones keep serving: a failed
# deploy is not an outage. Its database migrations may already have run, though - which is why
# migrations must stay backward compatible (add columns/tables; drop or rename only in a later
# release, once no running version uses them).
#
# Private GHCR images need a one-time login on this server (a token with read:packages):
#   echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin
set -euo pipefail

cd "$(dirname "$0")/.."

export IMAGE_TAG="${1:-latest}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"   # seconds a new backend gets to boot and migrate

FILES=(-f docker-compose.yml -f docker-compose.deploy.yml)
if [ "${DEPLOY_PROD:-0}" = "1" ]; then
  FILES+=(-f docker-compose.prod.yml)
fi
dc() { docker compose "${FILES[@]}" "$@"; }
log() { echo "[deploy $(date +%H:%M:%S)] $*"; }

# Waits until every container named in "$@" reports healthy. Returns 1 on unhealthy, exited or timeout.
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) c status
  for c in "$@"; do
    while :; do
      status=$(docker inspect -f '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$c")
      case "$status" in
        running/healthy) log "  $(docker inspect -f '{{.Name}}' "$c" | tr -d /) is ready"; break ;;
        running/unhealthy|exited/*|dead/*) log "  $c is $status"; return 1 ;;
      esac
      if [ "$SECONDS" -ge "$deadline" ]; then
        log "  $c not ready after ${HEALTH_TIMEOUT}s ($status)"
        return 1
      fi
      sleep 3
    done
  done
}

reload_nginx() {
  # nginx also re-reads the backend list from DNS every 10s on its own; reloading makes it now.
  dc exec -T frontend nginx -s reload >/dev/null 2>&1 || true
}

log "Deploying images tagged '$IMAGE_TAG'"

# Settings first: a missing .env value is not a registry problem, and must not be reported as one.
if [ ! -f .env ]; then
  log "No .env here. Run this on the server, in the checkout that has the production .env."
  exit 1
fi
if ! dc config --quiet; then
  log "docker compose cannot read its settings (above). Add the missing values to this server's .env."
  exit 1
fi

if ! dc pull backend frontend; then
  log "Pull failed. Private images? Log in once: docker login ghcr.io (see the top of this script)"
  exit 1
fi

log "Checking the new frontend's nginx config"
dc run --rm --no-deps -T frontend nginx -t

mapfile -t old < <(dc ps -q backend)

if [ "${#old[@]}" -eq 0 ]; then
  # First deploy (or everything was stopped): nothing to keep serving, so just start it all.
  log "No backend running - starting the whole stack"
  dc up -d
  mapfile -t started < <(dc ps -q backend)
  wait_healthy "${started[@]}"
  echo "$(date -Is) $IMAGE_TAG" >> .deploy-history
  log "Done"
  exit 0
fi

count=${#old[@]}
log "Starting $count new backend(s) next to the $count running"
dc up -d --no-deps --no-recreate --scale "backend=$((count * 2))" backend

mapfile -t all < <(dc ps -q backend)
new=()
for c in "${all[@]}"; do
  [[ " ${old[*]} " == *" $c "* ]] || new+=("$c")
done

log "Waiting for the new backend(s) to be ready (up to ${HEALTH_TIMEOUT}s)"
if ! wait_healthy "${new[@]}"; then
  log "New backend failed to get ready - removing it; the old one(s) keep serving. Its last logs:"
  for c in "${new[@]}"; do docker logs --tail 40 "$c" 2>&1 | sed 's/^/    /'; done
  docker rm -f "${new[@]}" >/dev/null
  reload_nginx
  log "Deploy of '$IMAGE_TAG' FAILED. Nothing changed for users (database migrations may have run)."
  exit 1
fi

reload_nginx
# A moment for nginx workers to pick up the new backends before the old ones stop accepting.
sleep 5

log "Stopping the old backend(s) gracefully (in-flight requests finish first)"
# 35s > Spring's 25s graceful-shutdown phase, so Docker never has to SIGKILL mid-request.
docker stop -t 35 "${old[@]}" >/dev/null
docker rm "${old[@]}" >/dev/null
reload_nginx

log "Updating the frontend"
dc up -d --no-deps frontend

# Old image layers are the biggest thing that slowly fills the disk; this keeps only what
# containers still use (current images) and drops the replaced ones.
docker image prune -f >/dev/null

echo "$(date -Is) $IMAGE_TAG" >> .deploy-history
log "Done - '$IMAGE_TAG' is live. Previous deploys: tail .deploy-history"
