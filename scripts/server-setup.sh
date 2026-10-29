#!/usr/bin/env bash
# One-time preparation of the EC2 server for scripts/deploy.sh, followed by the first deploy.
# Safe to run again: each step checks before it changes anything.
#
#   git stash; git pull origin main; bash scripts/server-setup.sh
set -euo pipefail

cd "$(dirname "$0")/.."
log() { echo "[setup $(date +%H:%M:%S)] $*"; }

if [ ! -f .env ]; then
  log "No .env here. Run this in the server checkout that has the production .env (cp .env.example .env, then fill it in)."
  exit 1
fi

# 1. Settings compose refuses to start without. Generated only when missing - and never
#    REDIS_PASSWORD while Redis runs: Redis keeps the password it started with, so a new one
#    here would lock the backend out of it.
redis_running=$(docker compose ps -q redis 2>/dev/null || true)
for v in REDIS_PASSWORD METRICS_SCRAPE_TOKEN GRAFANA_ADMIN_PASSWORD; do
  grep -qE "^$v=.+" .env && continue
  if [ "$v" = REDIS_PASSWORD ] && [ -n "$redis_running" ]; then
    log "REDIS_PASSWORD is empty in .env but Redis is running with one. Put that password in .env."
    exit 1
  fi
  sed -i "/^$v=\$/d" .env
  echo "$v=$(openssl rand -hex 32)" >> .env
  log "Generated $v in .env"
done

# 2. A database backup before anything touches the running stack.
if [ -n "$(docker compose ps -q postgres 2>/dev/null || true)" ]; then
  mkdir -p ~/db-backups
  out=~/db-backups/$(date +%F-%H%M).sql
  docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > "$out"
  if ! grep -q "PostgreSQL database dump" "$out"; then
    log "Database backup $out looks empty - stopping before the deploy."
    exit 1
  fi
  log "Database backed up to $out ($(du -h "$out" | cut -f1))"
  # Keep the last 10.
  ls -1t ~/db-backups/*.sql | tail -n +11 | xargs -r rm --
fi

# 3. Swap, so the minute two backends run side by side cannot run the server out of memory.
sudo bash scripts/setup-swap.sh

# 4. The deploy itself (pulls the CI images, rolls the backend, swaps the frontend).
bash scripts/deploy.sh "$@"
