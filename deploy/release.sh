#!/usr/bin/env bash
# T56.6 · Release a new API jar with a pre-release backup, health check and automatic rollback.
# Installed as /usr/local/sbin/finora-release.  Usage:  sudo finora-release /tmp/api-0.0.1-SNAPSHOT.jar
set -euo pipefail

JAR="${1:?usage: finora-release <api.jar>}"
APP_DIR=/opt/finora
RELEASES="$APP_DIR/releases"
KEEP=5
HEALTH_URL=http://127.0.0.1:8080/actuator/health

[[ $EUID -eq 0 ]] || { echo "Run as root (sudo)." >&2; exit 1; }
[[ -r $JAR ]] || { echo "ERROR: cannot read $JAR" >&2; exit 1; }

wait_healthy() {
  local i
  for i in $(seq 1 45); do
    if curl -fsS --max-time 2 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then return 0; fi
    sleep 2
  done
  return 1
}
switch_to() {  # atomic symlink swap
  ln -sfn "$1" "$APP_DIR/app.jar.new"
  mv -T "$APP_DIR/app.jar.new" "$APP_DIR/app.jar"
}

echo "== Backup before release (needed if this release's migrations must be undone)"
systemctl start finora-backup.service
ls -1t /var/backups/finora/finora-*.sql.gz.gpg | head -n 1

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
new="$RELEASES/api-$stamp.jar"
install -o root -g finora -m 640 "$JAR" "$new"
prev="$(readlink -f "$APP_DIR/app.jar" 2>/dev/null || true)"
[[ -n $prev && -f $prev ]] && echo "$prev" > "$APP_DIR/previous-release"

echo "== Switching to $new"
switch_to "$new"
systemctl restart finora-api

if wait_healthy; then
  echo "== Release OK: $new"
  # Keep the newest $KEEP jars, never deleting the current or previous one.
  ls -1t "$RELEASES"/api-*.jar | tail -n +$((KEEP + 1)) | while read -r old; do
    [[ $old == "$new" || $old == "$prev" ]] || rm -f "$old"
  done
  exit 0
fi

echo "!! Health check failed - recent log:" >&2
journalctl -u finora-api -n 40 --no-pager >&2 || true
if [[ -n $prev && -f $prev ]]; then
  echo "!! Rolling back to $prev" >&2
  switch_to "$prev"
  systemctl restart finora-api
  wait_healthy && echo "!! Rolled back; previous release is healthy." >&2 \
               || echo "!! Previous release is ALSO unhealthy - see docs/deploy.md (Rollback, step 3)." >&2
fi
exit 1
