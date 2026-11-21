#!/usr/bin/env bash
# T56.1–T56.4 · First-time setup of the FINORA API server (Ubuntu 22.04).
# Usage (as root, from a copy of the repo's deploy/ folder):
#   sudo ./server-setup.sh api.example.lk admin@example.lk
# Safe to re-run: existing secrets, users and certificates are kept.
set -euo pipefail

DOMAIN="${1:?usage: server-setup.sh <domain> <letsencrypt-email>}"
EMAIL="${2:?usage: server-setup.sh <domain> <letsencrypt-email>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[[ $EUID -eq 0 ]] || { echo "Run as root (sudo)." >&2; exit 1; }
if ! grep -q 'VERSION_ID="22.04"' /etc/os-release; then
  echo "WARNING: written for Ubuntu 22.04; continuing anyway." >&2
fi
log() { printf '\n== %s\n' "$*"; }

log "Packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q openjdk-17-jre-headless mysql-server nginx certbot ufw gnupg curl openssl

log "Service user and folders (T56.1)"
id finora &>/dev/null || useradd --system --home-dir /opt/finora --shell /usr/sbin/nologin finora
install -d -o root -g finora -m 750 /opt/finora /opt/finora/releases
install -d -o root -g finora -m 750 /etc/finora
install -d -o root -g root -m 700 /var/backups/finora

log "Firewall: only 22 and 443 open (T56.1)"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp      # SSH first, so enabling ufw cannot lock us out
ufw allow 443/tcp
ufw --force enable
ufw status verbose

log "MySQL bound to localhost (T56.2)"
install -o root -g root -m 644 "$HERE/mysql/99-finora.cnf" /etc/mysql/mysql.conf.d/99-finora.cnf
systemctl restart mysql

log "Database, least-privilege users and secrets (T56.2, T56.3)"
ENV_FILE=/etc/finora/finora.env
if [[ -f $ENV_FILE ]]; then
  echo "$ENV_FILE exists - keeping current users and secrets."
else
  APP_PASS=$(openssl rand -hex 24)
  MIGRATE_PASS=$(openssl rand -hex 24)
  BACKUP_PASS=$(openssl rand -hex 24)
  # root uses auth_socket on Ubuntu, so no password is needed here.
  # Passwords are hex, so they are safe inside SQL quotes.
  mysql <<SQL
CREATE DATABASE IF NOT EXISTS finora CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- Runtime user: data only, no schema changes.
CREATE USER IF NOT EXISTS 'finora_app'@'localhost' IDENTIFIED BY '$APP_PASS';
ALTER USER 'finora_app'@'localhost' IDENTIFIED BY '$APP_PASS';
GRANT SELECT, INSERT, UPDATE, DELETE ON finora.* TO 'finora_app'@'localhost';
-- Flyway user: schema changes on the finora schema only (used at API start-up).
CREATE USER IF NOT EXISTS 'finora_migrate'@'localhost' IDENTIFIED BY '$MIGRATE_PASS';
ALTER USER 'finora_migrate'@'localhost' IDENTIFIED BY '$MIGRATE_PASS';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON finora.* TO 'finora_migrate'@'localhost';
-- Backup user: read-only dump rights.
CREATE USER IF NOT EXISTS 'finora_backup'@'localhost' IDENTIFIED BY '$BACKUP_PASS';
ALTER USER 'finora_backup'@'localhost' IDENTIFIED BY '$BACKUP_PASS';
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES ON finora.* TO 'finora_backup'@'localhost';
FLUSH PRIVILEGES;
SQL

  umask 077
  cat > "$ENV_FILE" <<ENV
# FINORA API secrets (T56.3). Owner finora, mode 400. Never copy into Git.
DB_URL=jdbc:mysql://127.0.0.1:3306/finora?sslMode=DISABLED&serverTimezone=UTC
DB_USER=finora_app
DB_PASS=$APP_PASS
SPRING_FLYWAY_USER=finora_migrate
SPRING_FLYWAY_PASSWORD=$MIGRATE_PASS
JWT_SECRET=$(openssl rand -hex 32)
# Fill in before starting the API (T27/T28). Empty values are allowed until then.
GROQ_API_KEY=
GEMINI_API_KEY=
ENV
  cat > /etc/finora/backup.cnf <<CNF
[client]
user=finora_backup
password=$BACKUP_PASS
host=localhost
CNF
  umask 022
fi
chown finora:finora "$ENV_FILE";        chmod 400 "$ENV_FILE"
chown root:root /etc/finora/backup.cnf;  chmod 400 /etc/finora/backup.cnf

if [[ ! -f /etc/finora/backup.pass ]]; then
  (umask 077; openssl rand -base64 48 > /etc/finora/backup.pass)
  echo ">>> NEW backup passphrase in /etc/finora/backup.pass - copy it to the offline store NOW (see docs/deploy.md)."
fi
chown root:root /etc/finora/backup.pass; chmod 400 /etc/finora/backup.pass

log "systemd units (T56.1, T56.4)"
install -o root -g root -m 755 "$HERE/backup/finora-backup.sh"  /usr/local/sbin/finora-backup
install -o root -g root -m 755 "$HERE/backup/finora-restore.sh" /usr/local/sbin/finora-restore
install -o root -g root -m 755 "$HERE/release.sh"               /usr/local/sbin/finora-release
install -o root -g root -m 755 "$HERE/rollback.sh"              /usr/local/sbin/finora-rollback
install -o root -g root -m 644 "$HERE"/systemd/finora-api.service    /etc/systemd/system/
install -o root -g root -m 644 "$HERE"/systemd/finora-backup.service /etc/systemd/system/
install -o root -g root -m 644 "$HERE"/systemd/finora-backup.timer   /etc/systemd/system/
systemctl daemon-reload
systemctl enable finora-api.service      # starts once a jar is released (release.sh)
systemctl enable --now finora-backup.timer

log "HTTPS certificate (T56.3)"
# Port 80 stays closed. The hooks open it only for the few seconds certbot needs.
install -d /etc/letsencrypt/renewal-hooks/pre /etc/letsencrypt/renewal-hooks/post /etc/letsencrypt/renewal-hooks/deploy
install -m 755 "$HERE/letsencrypt/pre-open-port-80.sh"   /etc/letsencrypt/renewal-hooks/pre/finora-open-80.sh
install -m 755 "$HERE/letsencrypt/post-close-port-80.sh" /etc/letsencrypt/renewal-hooks/post/finora-close-80.sh
install -m 755 "$HERE/letsencrypt/deploy-reload-nginx.sh" /etc/letsencrypt/renewal-hooks/deploy/finora-reload-nginx.sh
rm -f /etc/nginx/sites-enabled/default
if [[ ! -d /etc/letsencrypt/live/$DOMAIN ]]; then
  certbot certonly --standalone --non-interactive --agree-tos -m "$EMAIL" -d "$DOMAIN" \
    --pre-hook  /etc/letsencrypt/renewal-hooks/pre/finora-open-80.sh \
    --post-hook /etc/letsencrypt/renewal-hooks/post/finora-close-80.sh
fi

log "Nginx reverse proxy (T56.3)"
sed "s/__DOMAIN__/$DOMAIN/g" "$HERE/nginx/finora.conf" > /etc/nginx/sites-available/finora.conf
ln -sfn /etc/nginx/sites-available/finora.conf /etc/nginx/sites-enabled/finora.conf
nginx -t
systemctl reload-or-restart nginx

log "Done"
cat <<EOF
Next steps (docs/deploy.md):
  1. Copy /etc/finora/backup.pass to the offline store.
  2. Fill GROQ_API_KEY / GEMINI_API_KEY in /etc/finora/finora.env when T27/T28 need them.
  3. Upload the API jar and run:  sudo finora-release /tmp/api.jar
  4. From a phone: https://$DOMAIN/actuator/health  ->  {"status":"UP"}
EOF
