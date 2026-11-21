#!/usr/bin/env bash
# T56.4 · Encrypted backup of the FINORA database (D21).
# mysqldump -> gzip -> gpg AES256, then a decrypt check, then delete backups older than 30 days.
# Installed as /usr/local/sbin/finora-backup; run by finora-backup.timer (nightly) and release.sh.
# Every path can be overridden by an env var (used for local testing).
set -euo pipefail
umask 077

DB_NAME="${DB_NAME:-finora}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/finora}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-/etc/finora/backup.cnf}"
PASSPHRASE_FILE="${PASSPHRASE_FILE:-/etc/finora/backup.pass}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
MYSQLDUMP="${MYSQLDUMP:-mysqldump}"

for f in "$MYSQL_DEFAULTS_FILE" "$PASSPHRASE_FILE"; do
  [[ -r $f ]] || { echo "ERROR: cannot read $f" >&2; exit 1; }
done
mkdir -p "$BACKUP_DIR"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$BACKUP_DIR/finora-$stamp.sql.gz.gpg"
part="$out.part"
trap 'rm -f "$part"' EXIT

gpg_args=(--batch --yes --quiet --pinentry-mode loopback --passphrase-file "$PASSPHRASE_FILE")

# --single-transaction: consistent snapshot without locking InnoDB tables.
# No --databases, so the dump can be restored into any schema name (restore drill).
"$MYSQLDUMP" --defaults-extra-file="$MYSQL_DEFAULTS_FILE" \
    --single-transaction --no-tablespaces --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 "$DB_NAME" \
  | gzip -9 \
  | gpg "${gpg_args[@]}" --symmetric --cipher-algo AES256 --output "$part"

# Check the file decrypts and the dump is complete before we trust it.
if ! gpg "${gpg_args[@]}" --decrypt "$part" | gunzip | tail -n 1 | grep -q '^-- Dump completed'; then
  echo "ERROR: backup check failed for $part" >&2
  exit 1
fi
mv "$part" "$out"
trap - EXIT
echo "Backup OK: $out ($(wc -c < "$out") bytes)"

# D21: keep 30 days. -mmin is exact (-mtime rounds to whole days).
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'finora-*.sql.gz.gpg' \
  -mmin +$((RETENTION_DAYS * 24 * 60)) -print -delete | sed 's/^/Expired: /'
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'finora-*.part' -mmin +1440 -delete
