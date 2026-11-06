#!/usr/bin/env bash
#
# Dump the qasa database to /var/backups/qasa and keep the last 14 days. Installed as
# /usr/local/sbin/qasa-backup-db and run nightly by qasa-backup.timer, as postgres.
#
# A dump on this disk does not survive losing the server: copy them elsewhere too (set
# QASA_BACKUP_REMOTE in the timer's service to an rclone remote once one is configured).
#
# Restore into an empty qasa database:
#   sudo -u postgres pg_restore -d qasa --clean --if-exists /var/backups/qasa/<file>.dump
#
set -euo pipefail

DIR=/var/backups/qasa
KEEP_DAYS=14
FILE="$DIR/qasa-$(date -u +%Y%m%d-%H%M).dump"

umask 077
# Custom format: compressed, and pg_restore can pick single tables out of it.
pg_dump -d qasa -Fc -f "$FILE.partial"
mv "$FILE.partial" "$FILE"
echo "wrote $FILE ($(du -h "$FILE" | cut -f1))"

find "$DIR" -name 'qasa-*.dump' -mtime +"$KEEP_DAYS" -delete

if [ -n "${QASA_BACKUP_REMOTE:-}" ]; then
  rclone copy "$FILE" "$QASA_BACKUP_REMOTE"
  echo "copied to $QASA_BACKUP_REMOTE"
fi
