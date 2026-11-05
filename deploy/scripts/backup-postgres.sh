#!/usr/bin/env bash
# ---------------------------------------------------------------
# Copia de la base de datos de CherryCore (pg_dump, formato custom).
# Borra las copias con más de BACKUP_RETENTION_DAYS días.
#
# Uso:   deploy/scripts/backup-postgres.sh
# Cron:  30 2 * * * /opt/cherrycore/cherry-core-back/deploy/scripts/backup-postgres.sh >> /var/log/cherrycore-backup.log 2>&1
# Lee BACKUP_DIR y BACKUP_RETENTION_DAYS del entorno o de deploy/.env.
# ---------------------------------------------------------------
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DEPLOY_DIR"

env_value() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- || true; }

BACKUP_DIR="${BACKUP_DIR:-$(env_value BACKUP_DIR)}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/cherrycore}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-$(env_value BACKUP_RETENTION_DAYS)}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

umask 077
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/cherrycore-$(date -u +%Y%m%dT%H%M%SZ).dump"
trap 'rm -f "$FILE.partial"' EXIT

docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner' > "$FILE.partial"

if [ ! -s "$FILE.partial" ]; then
  echo "$(date -u +%FT%TZ) ✗ copia vacía, no se guarda" >&2
  exit 1
fi
mv "$FILE.partial" "$FILE"

find "$BACKUP_DIR" -maxdepth 1 -type f -name 'cherrycore-*.dump' -mtime +"$RETENTION_DAYS" -delete
echo "$(date -u +%FT%TZ) ✓ $FILE ($(du -h "$FILE" | cut -f1))"
