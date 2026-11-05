#!/usr/bin/env bash
# ---------------------------------------------------------------
# SOLO DESARROLLO LOCAL. Activa una cuenta de backoffice (p. ej. el
# superadmin creado con SUPER_ADMIN_EMAIL) sin frontend:
#   1. pide el enlace de "olvidé mi contraseña" y lo lee de Mailpit
#   2. te pide la contraseña nueva (no se muestra ni se guarda)
#   3. inicia sesión y configura el MFA con la app del móvil
#
# Uso:  scripts/dev/activar-superadmin.sh [email]
# Requiere: CherryCore en local (perfil local), Mailpit, curl y jq.
# ---------------------------------------------------------------
set -euo pipefail

EMAIL="${1:-admin@cherrycore.local}"
BASE="${CHERRYCORE_URL:-http://localhost:8080}"
MAILPIT="${MAILPIT_URL:-http://localhost:8025}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

xsrf() { awk '$6 == "XSRF-TOKEN" {print $7}' "$JAR"; }

# POST JSON con cookies de sesión y CSRF. Imprime el cuerpo de la respuesta.
post() {
  curl -s -b "$JAR" -c "$JAR" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(xsrf)" \
       -H "Accept-Language: es" -d "$2" "$BASE$1"
}

fail_if_error() {
  if [ "$(echo "$1" | jq -r '.ok')" != "true" ]; then
    echo "  ✗ Error: $(echo "$1" | jq -r '.error.code') — $(echo "$1" | jq -r '.error.description')"
    return 1
  fi
}

echo "▶ CherryCore: $BASE · cuenta: $EMAIL"
curl -sf -o /dev/null "$BASE/actuator/health" || { echo "✗ CherryCore no responde. ¿Está arrancado?"; exit 1; }
curl -s -c "$JAR" -o /dev/null "$BASE/api/auth/csrf"

# Último email de contraseña que ya había (para esperar a uno NUEVO).
SEARCH="$MAILPIT/api/v1/search?query=to:$EMAIL%20subject:contrase%C3%B1a"
PREVIOUS="$(curl -s "$SEARCH" | jq -r '.messages[0].ID // empty')"

echo "▶ 1/3 Pidiendo el enlace para poner la contraseña..."
post /api/auth/staff/password/forgot "$(jq -n --arg e "$EMAIL" '{email: $e}')" > /dev/null

TOKEN=""
for _ in $(seq 1 15); do
  sleep 1
  ID="$(curl -s "$SEARCH" | jq -r '.messages[0].ID // empty')"
  if [ -n "$ID" ] && [ "$ID" != "$PREVIOUS" ]; then
    TOKEN="$(curl -s "$MAILPIT/api/v1/message/$ID" | jq -r '.Text' | grep -o 'token=[A-Za-z0-9_-]*' | head -1 | cut -d= -f2)"
    [ -n "$TOKEN" ] && break
  fi
done
[ -n "$TOKEN" ] || { echo "✗ No ha llegado el email. Míralo en $MAILPIT"; exit 1; }
echo "  ✓ Email recibido en Mailpit"

echo "▶ 2/3 Elige la contraseña (mínimo 10 caracteres, sin contener el email)."
while true; do
  read -r -s -p "  Contraseña: " PASSWORD; echo
  read -r -s -p "  Repítela:   " PASSWORD2; echo
  [ "$PASSWORD" = "$PASSWORD2" ] || { echo "  ✗ No coinciden"; continue; }
  RESULT="$(post /api/auth/password/reset "$(jq -n --arg t "$TOKEN" --arg p "$PASSWORD" '{token: $t, newPassword: $p}')")"
  fail_if_error "$RESULT" && break
done
unset PASSWORD2
echo "  ✓ Contraseña guardada"

echo "▶ 3/3 Iniciando sesión..."
LOGIN="$(post /api/auth/staff/login "$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email: $e, password: $p}')")"
unset PASSWORD
fail_if_error "$LOGIN"
STATUS="$(echo "$LOGIN" | jq -r '.data.status')"

if [ "$STATUS" = "MFA_SETUP_REQUIRED" ]; then
  SETUP="$(post /api/auth/mfa/setup '{}')"
  fail_if_error "$SETUP"
  echo
  echo "  En la app del móvil (Google Authenticator, Authy...): añadir cuenta →"
  echo "  'Introducir clave de configuración' (tipo: basada en tiempo):"
  echo
  echo "     Cuenta: $EMAIL"
  echo "     Clave:  $(echo "$SETUP" | jq -r '.data.secret')"
  echo
  while true; do
    read -r -p "  Código de 6 dígitos que muestra la app: " CODE
    RESULT="$(post /api/auth/mfa/setup/confirm "$(jq -n --arg c "$CODE" '{code: $c}')")"
    fail_if_error "$RESULT" && break
  done
  echo
  echo "  ✓ MFA activado. GUARDA estos códigos de recuperación en un sitio seguro"
  echo "    (sirven si pierdes el móvil; cada uno vale una sola vez):"
  echo "$RESULT" | jq -r '.data.recoveryCodes[]' | sed 's/^/      /'
elif [ "$STATUS" = "MFA_REQUIRED" ]; then
  while true; do
    read -r -p "  Código de 6 dígitos de la app: " CODE
    RESULT="$(post /api/auth/mfa/verify "$(jq -n --arg c "$CODE" '{code: $c}')")"
    fail_if_error "$RESULT" && break
  done
fi

echo
echo "▶ Sesión:"
curl -s -b "$JAR" "$BASE/api/auth/session" | jq '.data'
echo "✓ Listo. La cuenta ya puede entrar con su contraseña y el código del móvil."
