#!/usr/bin/env bash
# ---------------------------------------------------------------
# Restaura una copia hecha con backup-postgres.sh. SUSTITUYE los datos
# actuales: para el backend, restaura y lo vuelve a arrancar.
#
# Uso: deploy/scripts/restore-postgres.sh /var/backups/cherrycore/cherrycore-XXXX.dump
# ---------------------------------------------------------------
set -euo pipefail

DUMP="${1:?Uso: $0 fichero.dump}"
[ -s "$DUMP" ] || { echo "✗ No existe o está vacío: $DUMP"; exit 1; }

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f docker-compose.prod.yml)

read -r -p "Se reemplazarán los datos actuales con $DUMP. Escribe RESTAURAR para seguir: " ANSWER
[ "$ANSWER" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

"${COMPOSE[@]}" stop backend
"${COMPOSE[@]}" exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --single-transaction' < "$DUMP"
"${COMPOSE[@]}" start backend
echo "✓ Restaurado. Comprueba: ${COMPOSE[*]} ps"
