#!/usr/bin/env bash
# Continuous deployment without giving GitHub any way into this server.
#
# Runs every 2 minutes (systemd timer, installed once by scripts/install-auto-deploy.sh). If main
# on GitHub has moved past what is running here, and CI has finished building that commit's
# images, it updates this checkout and runs scripts/deploy.sh for exactly that commit.
#
# So the whole release process is: merge the pull request. A few minutes later CI has built the
# images, and within 2 minutes after that this server is running them.
#
# Pull, not push: the server asks GitHub, GitHub never logs in here. No SSH key in GitHub
# secrets and no inbound port opened for it.
#
#   journalctl -u namma-auto-deploy -n 50     # what it did
#   cat .deployed-sha                         # commit live now
#   rm .auto-deploy-failed                    # retry a commit whose deploy failed
set -uo pipefail

cd "$(dirname "$0")/.."

# One at a time: a deploy can take a few minutes, longer than the timer's interval.
exec 9>/tmp/namma-auto-deploy.lock
flock -n 9 || exit 0

log() { echo "[auto-deploy] $*"; }
# The checkout may belong to another user than the one running this; git refuses that otherwise.
g() { git -c safe.directory="$PWD" "$@"; }

if ! g fetch -q origin main; then
  log "Cannot reach GitHub (git fetch failed). Will try again."
  exit 0
fi

target=$(g rev-parse origin/main)
current=$(cat .deployed-sha 2>/dev/null || true)
[ "$target" = "$current" ] && exit 0

# A commit whose deploy already failed is not retried every 2 minutes: the next merge, or
# deleting .auto-deploy-failed, tries again.
if [ "$(cat .auto-deploy-failed 2>/dev/null || true)" = "$target" ]; then
  exit 0
fi

# CI pushes images tagged with the commit a few minutes after the merge. Until they exist there
# is nothing to deploy; if CI failed they never will, and the next merge supersedes this one.
prefix=$(grep -E '^IMAGE_PREFIX=' .env 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r"')
prefix=${prefix:-ghcr.io/baranidharann/namma-journey}
for image in backend frontend; do
  if ! docker manifest inspect "$prefix-$image:$target" >/dev/null 2>&1; then
    log "Waiting for CI to build $image for ${target:0:7}."
    exit 0
  fi
done

# Compose files and scripts come from git; the application itself comes from the images.
if ! g merge --ff-only -q origin/main; then
  log "This checkout has local changes that block the update to ${target:0:7}. Run 'git status' here."
  echo "$target" > .auto-deploy-failed
  exit 1
fi

log "Deploying ${target:0:7}"
if scripts/deploy.sh "$target"; then
  echo "$target" > .deployed-sha
  rm -f .auto-deploy-failed
  log "${target:0:7} is live"
else
  echo "$target" > .auto-deploy-failed
  log "Deploy of ${target:0:7} FAILED - the old version keeps serving. Details above."
  exit 1
fi
