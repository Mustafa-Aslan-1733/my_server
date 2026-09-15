#!/usr/bin/env bash
# Daily logical backup of the whole PostgreSQL cluster.
#
# pg_dumpall rather than pg_dump: it carries the roles and grants as well, so a restore onto
# a fresh machine does not depend on anyone remembering how the cluster was set up.
#
# Runs through `docker exec` since the database moved from the host's systemd service into
# the pse-postgres container on 2026-08-21. Runs as root (it needs the docker socket), not
# as the postgres user as it did before that move.

set -euo pipefail

CONTAINER=pse-postgres
DEST=/var/backups/postgres
RETENTION_DAYS=14

# Fail loudly rather than writing a zero-byte "backup" when the database is not running.
if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]; then
  echo "$CONTAINER is not running -- no backup taken" >&2
  exit 1
fi

mkdir -p "$DEST"
chmod 700 "$DEST"

target="$DEST/pgdumpall-$(date -u +%Y%m%d-%H%M%S).sql.gz"
partial="$target.partial"

# Write under a .partial name first. An interrupted run must not leave behind a short file
# that looks like a usable backup -- that is the failure you only discover while restoring,
# which is the worst possible moment.
trap 'rm -f "$partial"' EXIT
docker exec "$CONTAINER" pg_dumpall -U postgres --clean --if-exists | gzip -9 > "$partial"

# pipefail catches a failing pg_dumpall, but a truncated gzip stream is the failure that
# actually reaches disk, so check the archive is complete before publishing it.
gzip -t "$partial"

# A dump that ends early can still be valid gzip. The trailer pg_dumpall writes last is the
# only proof the database was read all the way through.
if ! zcat "$partial" | grep -q 'PostgreSQL database cluster dump complete'; then
  echo "dump is missing its completion marker -- not publishing it" >&2
  exit 1
fi

mv "$partial" "$target"
trap - EXIT
chmod 600 "$target"

find "$DEST" -name 'pgdumpall-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete
find "$DEST" -name '*.partial' -mtime +1 -delete

echo "wrote $target ($(du -h "$target" | cut -f1))"
