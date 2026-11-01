#!/usr/bin/env bash
# One-time setup of automatic deploys on the EC2 server (see scripts/auto-deploy.sh).
#
#   sudo scripts/install-auto-deploy.sh
#
# Installs a systemd timer that checks GitHub every 2 minutes and deploys main once CI has
# built it. Safe to run again: it rewrites the same two units.
#
#   systemctl list-timers namma-auto-deploy     # next run
#   journalctl -u namma-auto-deploy -f          # watch it
#   sudo systemctl disable --now namma-auto-deploy.timer   # turn it off
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run with sudo: sudo $0"
  exit 1
fi

APP_DIR=$(cd "$(dirname "$0")/.." && pwd)
if [ ! -f "$APP_DIR/.env" ]; then
  echo "No .env in $APP_DIR - run this from the server's production checkout."
  exit 1
fi

# Run as whoever owns the checkout, so git keeps writing files as them - if they may use docker.
RUN_AS=$(stat -c %U "$APP_DIR")
if [ "$RUN_AS" != "root" ] && ! id -nG "$RUN_AS" | tr ' ' '\n' | grep -qx docker; then
  RUN_AS=root
fi

chmod +x "$APP_DIR/scripts/auto-deploy.sh" "$APP_DIR/scripts/deploy.sh"

cat > /etc/systemd/system/namma-auto-deploy.service <<EOF
[Unit]
Description=Namma Journey - deploy the latest main once CI has built it
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=$RUN_AS
WorkingDirectory=$APP_DIR
ExecStart=$APP_DIR/scripts/auto-deploy.sh
# A full deploy waits up to 3 minutes for the new backend; leave room for that.
TimeoutStartSec=900
EOF

cat > /etc/systemd/system/namma-auto-deploy.timer <<EOF
[Unit]
Description=Check GitHub for a new Namma Journey release every 2 minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=2min

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now namma-auto-deploy.timer

echo "Automatic deploys are on for $APP_DIR (running as $RUN_AS)."
echo "First check runs now; watch it with: journalctl -u namma-auto-deploy -f"
systemctl start --no-block namma-auto-deploy.service
