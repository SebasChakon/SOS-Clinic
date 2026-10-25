#!/usr/bin/env bash
# Backup diario de SOS Clinic: PostgreSQL + MySQL + configuracion minima.
#
# Se ejecuta como root desde sos-clinic-backup.service (systemd timer a las
# 21:30 America/Bogota). También se puede lanzar a mano:
#   sudo /opt/sos-clinic/infra/azure/backup.sh
#
# Destino: Azure Blob Storage privado, prefijo sos-clinic/<timestamp>/.
# Los backups locales se conservan 7 dias; el lifecycle de Azure los borra a
# los 30 dias. El contenedor de blobs no tiene acceso publico.

set -euo pipefail
umask 077

ENV_FILE="${ENV_FILE:-/opt/sos-clinic/.env}"
REPO_DIR="${REPO_DIR:-/opt/sos-clinic}"
LOCAL_DIR="${LOCAL_DIR:-/var/backups/sos-clinic}"

if [[ ! -r "$ENV_FILE" ]]; then
  echo "No existe o no se puede leer $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_USERNAME:?DB_USERNAME es obligatorio}"
: "${DB_PASSWORD:?DB_PASSWORD es obligatorio}"
: "${ANALITICA_DB_PASSWORD:?ANALITICA_DB_PASSWORD es obligatorio}"
: "${STORAGE_ACCOUNT:?STORAGE_ACCOUNT es obligatorio}"
: "${BACKUP_CONTAINER:?BACKUP_CONTAINER es obligatorio}"

DB_NAME="${DB_NAME:-sosclinic}"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$LOCAL_DIR"

echo "==> Backup PostgreSQL ($DB_NAME)"
docker exec sos-postgres pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc \
  > "$WORK/postgres-$STAMP.dump"

echo "==> Backup MySQL (urgencias_hospital)"
docker exec -e MYSQL_PWD="$ANALITICA_DB_PASSWORD" sos-mysql \
  mysqldump -uroot --single-transaction --quick --routines --events \
  --databases urgencias_hospital \
  | gzip -9 > "$WORK/mysql-$STAMP.sql.gz"

echo "==> Backup de configuracion"
CONFIG_FILES=(".env")
for relativo in \
  "infra/seed/credenciales.local.env" \
  "infra/seed/credenciales.local.md"; do
  if [[ -f "$REPO_DIR/$relativo" ]]; then
    CONFIG_FILES+=("$relativo")
  fi
done
tar -czf "$WORK/config-$STAMP.tar.gz" -C "$REPO_DIR" "${CONFIG_FILES[@]}"

echo "==> Copiando respaldo local a $LOCAL_DIR"
for archivo in "$WORK"/*; do
  install -m 600 "$archivo" "$LOCAL_DIR/"
done

if ! az account show >/dev/null 2>&1; then
  echo "==> Autenticando con la identidad administrada de la VM"
  az login --identity --allow-no-subscriptions --only-show-errors >/dev/null
fi

echo "==> Subiendo a $STORAGE_ACCOUNT/$BACKUP_CONTAINER/sos-clinic/$STAMP/"
for archivo in "$WORK"/*; do
  az storage blob upload \
    --account-name "$STORAGE_ACCOUNT" \
    --container-name "$BACKUP_CONTAINER" \
    --name "sos-clinic/$STAMP/$(basename "$archivo")" \
    --file "$archivo" \
    --auth-mode login \
    --overwrite \
    --only-show-errors \
    >/dev/null
done

echo "==> Conservando copias locales 7 dias"
find "$LOCAL_DIR" -maxdepth 1 -type f -mtime +7 -delete

echo "Backup completado: $STAMP"
ls -lh "$LOCAL_DIR" | tail -n 10
