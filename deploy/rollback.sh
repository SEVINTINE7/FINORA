#!/usr/bin/env bash
# T56.6 · Go back to the previous API jar (or a given one from /opt/finora/releases).
# Installed as /usr/local/sbin/finora-rollback.  Usage:  sudo finora-rollback [jar]
# This does NOT undo database migrations - see docs/deploy.md (Rollback).
set -euo pipefail

APP_DIR=/opt/finora
[[ $EUID -eq 0 ]] || { echo "Run as root (sudo)." >&2; exit 1; }

target="${1:-$(cat "$APP_DIR/previous-release" 2>/dev/null || true)}"
[[ -n $target && -f $target ]] || { echo "ERROR: no previous release found; pass a jar from $APP_DIR/releases" >&2; exit 1; }
current="$(readlink -f "$APP_DIR/app.jar" 2>/dev/null || true)"

echo "Rolling back: $current -> $target"
ln -sfn "$target" "$APP_DIR/app.jar.new"
mv -T "$APP_DIR/app.jar.new" "$APP_DIR/app.jar"
[[ -n $current ]] && echo "$current" > "$APP_DIR/previous-release"
systemctl restart finora-api

for _ in $(seq 1 45); do
  if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "Rollback OK - API healthy on $target"
    exit 0
  fi
  sleep 2
done
echo "ERROR: API not healthy after rollback:" >&2
journalctl -u finora-api -n 40 --no-pager >&2 || true
exit 1
