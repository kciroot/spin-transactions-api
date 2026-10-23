#!/usr/bin/env bash
# Logical backup of the transactions database (pg_dump custom format) with retention and integrity check.
# Config via env: PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE BACKUP_DIR RETENTION_DAYS
set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-transactions}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/${PGDATABASE}_$(date -u +%Y%m%dT%H%M%SZ).dump"
tmp="${file}.partial"

echo "[backup] dumping ${PGDATABASE}@${PGHOST}:${PGPORT} -> ${file}"
pg_dump --format=custom --no-owner --no-privileges --file="$tmp"

# a dump that cannot be listed is not a backup
pg_restore --list "$tmp" > /dev/null
mv "$tmp" "$file"

echo "[backup] ok ($(du -h "$file" | cut -f1)); pruning files older than ${RETENTION_DAYS} days"
find "$BACKUP_DIR" -name "${PGDATABASE}_*.dump" -type f -mtime +"$RETENTION_DAYS" -print -delete
