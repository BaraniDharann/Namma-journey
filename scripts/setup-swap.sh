#!/usr/bin/env bash
# One-time, on the EC2 server: adds a 2 GB swap file so a memory spike (two backends side by
# side during scripts/deploy.sh, a burst of photo uploads) slows the box down instead of the
# kernel's OOM killer shooting a process - possibly Postgres. Costs nothing: it uses the disk
# you already have (gp3 EBS), so make sure ~2 GB is free (df -h /).
#
#   sudo bash scripts/setup-swap.sh
#
# Safe to run again: it does nothing if the swap file already exists.
set -euo pipefail

SWAPFILE=/swapfile
SIZE=${SWAP_SIZE:-2G}

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if swapon --show=NAME --noheadings | grep -qx "$SWAPFILE"; then
  echo "Swap already active on $SWAPFILE:"
  swapon --show
  exit 0
fi

fallocate -l "$SIZE" "$SWAPFILE"
chmod 600 "$SWAPFILE"   # swap can hold anything that was in memory, secrets included
mkswap "$SWAPFILE"
swapon "$SWAPFILE"

# Keep it across reboots.
grep -q "^$SWAPFILE " /etc/fstab || echo "$SWAPFILE none swap sw 0 0" >> /etc/fstab

# Only swap under real pressure: the default (60) pushes idle JVM and Postgres pages out early
# and every request touching them pays a disk read.
sysctl -w vm.swappiness=10
echo "vm.swappiness=10" > /etc/sysctl.d/99-namma-swap.conf

echo "Swap enabled:"
swapon --show
free -h
