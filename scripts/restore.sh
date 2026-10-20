#!/usr/bin/env bash
# Restores a dump produced by backup.sh into the database given by PGDATABASE (it is DROPPED and recreated
# data-wise: --clean --if-exists). Requires --yes to avoid accidents.
# Usage: PGHOST=... PGUSER=... PGPASSWORD=... PGDATABASE=transactions_restore ./restore.sh <file.dump> --yes
set -euo pipefail

file="${1:?usage: restore.sh <file.dump> --yes}"
[[ "${2:-}" == "--yes" ]] || { echo "Refusing to restore without --yes"; exit 1; }
: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
PGPORT="${PGPORT:-5432}"
: "${PGDATABASE:?PGDATABASE is required}"

echo "[restore] ${file} -> ${PGDATABASE}@${PGHOST}:${PGPORT}"
pg_restore --clean --if-exists --no-owner --no-privileges --dbname="$PGDATABASE" "$file"

echo "[restore] sanity check:"
psql --no-psqlrc -tA -c "SELECT status, count(*) FROM transactions GROUP BY status ORDER BY status;"
