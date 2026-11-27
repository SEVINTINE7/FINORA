#!/usr/bin/env bash
# T56.4 · Restore an encrypted FINORA backup into a database.
#   Restore drill (safe, live DB untouched):
#     sudo finora-restore /var/backups/finora/finora-<stamp>.sql.gz.gpg finora_restore_drill --compare finora
#   Real recovery (stop the API first; overwrites live data):
#     sudo systemctl stop finora-api
#     sudo finora-restore <backup> finora --i-know-this-overwrites-production
# --compare prints row counts per table for both databases and fails on any difference.
set -euo pipefail

BACKUP="${1:?usage: finora-restore <backup.sql.gz.gpg> <target-db> [--compare <db>] [--i-know-this-overwrites-production]}"
TARGET="${2:?target database name required}"
shift 2
COMPARE=""
FORCE_PROD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --compare) COMPARE="${2:?--compare needs a database name}"; shift 2 ;;
    --i-know-this-overwrites-production) FORCE_PROD=1; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

PASSPHRASE_FILE="${PASSPHRASE_FILE:-/etc/finora/backup.pass}"
PROD_DB="${PROD_DB:-finora}"
MYSQL="${MYSQL:-mysql}"
# Empty = connect as root through the Unix socket (Ubuntu default).
MYSQL_ADMIN_DEFAULTS="${MYSQL_ADMIN_DEFAULTS:-}"

[[ $TARGET =~ ^[A-Za-z0-9_]+$ ]] || { echo "ERROR: bad database name" >&2; exit 2; }
[[ -z $COMPARE || $COMPARE =~ ^[A-Za-z0-9_]+$ ]] || { echo "ERROR: bad database name" >&2; exit 2; }
if [[ $TARGET == "$PROD_DB" && $FORCE_PROD -ne 1 ]]; then
  echo "ERROR: refusing to overwrite '$PROD_DB' without --i-know-this-overwrites-production" >&2
  exit 2
fi
[[ -r $BACKUP ]] || { echo "ERROR: cannot read $BACKUP" >&2; exit 1; }

mysql_cmd=("$MYSQL")
[[ -n $MYSQL_ADMIN_DEFAULTS ]] && mysql_cmd+=(--defaults-extra-file="$MYSQL_ADMIN_DEFAULTS")
mysql_cmd+=(--default-character-set=utf8mb4)

if [[ $TARGET != "$PROD_DB" ]]; then
  # Drill target: start from an empty schema every time.
  "${mysql_cmd[@]}" -e "DROP DATABASE IF EXISTS \`$TARGET\`; CREATE DATABASE \`$TARGET\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
fi

echo "Restoring $BACKUP -> $TARGET ..."
gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$PASSPHRASE_FILE" --decrypt "$BACKUP" \
  | gunzip \
  | "${mysql_cmd[@]}" "$TARGET"
echo "Restore finished."

row_counts() {
  local db="$1" tables t count
  tables="$("${mysql_cmd[@]}" -N -B -e \
      "SELECT table_name FROM information_schema.tables WHERE table_schema='$db' AND table_type='BASE TABLE' ORDER BY table_name;")" || return 1
  [[ -n $tables ]] || return 1
  while IFS= read -r t; do
    t=${t%$'\r'}
    [[ $t =~ ^[A-Za-z0-9_]+$ ]] || return 1
    count="$("${mysql_cmd[@]}" -N -B -e "SELECT COUNT(*) FROM \`$db\`.\`$t\`;")" || return 1
    count=${count%$'\r'}
    [[ $count =~ ^[0-9]+$ ]] || return 1
    printf '%s %s\n' "$t" "$count"
  done <<< "$tables"
}

restored="$(row_counts "$TARGET")" || { echo "RESTORE DRILL FAILED: could not count every table in $TARGET." >&2; exit 1; }
if [[ -z $COMPARE ]]; then
  echo "Rows per table in $TARGET:"; echo "$restored"
  exit 0
fi

source_counts="$(row_counts "$COMPARE")" || { echo "RESTORE DRILL FAILED: could not count every table in $COMPARE." >&2; exit 1; }
printf '%-32s %10s %10s\n' "table" "$COMPARE" "$TARGET"
join -a1 -a2 -e MISSING -o 0,1.2,2.2 <(echo "$source_counts" | sort) <(echo "$restored" | sort) \
  | awk '{ printf "%-32s %10s %10s%s\n", $1, $2, $3, ($2==$3 ? "" : "   <-- DIFFERENT") }'
if [[ "$(echo "$source_counts" | sort)" == "$(echo "$restored" | sort)" ]]; then
  echo "RESTORE DRILL PASSED: every table has the same row count."
else
  echo "RESTORE DRILL FAILED: row counts differ (data may have changed since the backup - take a fresh backup and retry)." >&2
  exit 1
fi
