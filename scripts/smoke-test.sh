#!/usr/bin/env bash
# Smoke test del backend desplegado (docs/infraestructura.md#smoke-test-del-deploy). Solo curl y jq.
#
#   BASE_URL=https://smartelevate.onrender.com \
#   SMOKE_EMAIL=alguien@uade.edu.ar SMOKE_PASSWORD=... \
#   ADMIN_EMAIL=admin@uade.edu.ar ADMIN_PASSWORD=... \
#   scripts/smoke-test.sh
#
# Variables (nada de esto se guarda en el repo):
#   BASE_URL             Obligatoria. Raiz del backend o del front (con el rewrite de Vercel), sin barra final.
#   SMOKE_EMAIL/PASSWORD Cuenta comun YA verificada. Sin ellas, solo se prueba health.
#   ADMIN_EMAIL/PASSWORD Cuenta ADMIN. Sin ellas se omite el panel admin y el kiosco.
#   SMOKE_ORIGIN         Origin que manda el "navegador" en refresh (default: BASE_URL). Con BASE_URL = Render directo
#                        hay que ponerlo igual a un valor de CORS_ALLOWED_ORIGINS (p. ej. https://smart-elevate.vercel.app).
#   SMOKE_REGISTER=1     Registra SMOKE_EMAIL (con SMOKE_LEGAJO, default aleatorio) y termina: verificá el mail y repetí.
#   SMOKE_CHECKIN=1      En vez de cancelar, espera la ventana de check-in y hace el check-in (puede tardar ~30 min).
#   EXPECT_SIMULATOR=1   Esperar APP_KIOSK_SIMULATOR_ENABLED=true (default: apagado, como en prod).
#   EXPECT_SECURE=0      No exigir el atributo Secure de la cookie (default: se exige si BASE_URL es https).
#
# Sale con codigo 1 en el primer paso que falla. Los pasos que dependen de datos (sin salidas reservables, por
# ejemplo) se saltean con un aviso y el script termina con codigo 0.
set -euo pipefail

command -v curl >/dev/null || { echo "Falta curl" >&2; exit 2; }
command -v jq >/dev/null || { echo "Falta jq" >&2; exit 2; }
: "${BASE_URL:?Falta BASE_URL (p. ej. https://smartelevate.onrender.com)}"
BASE_URL="${BASE_URL%/}"
ORIGIN="${SMOKE_ORIGIN:-$BASE_URL}"
case "$BASE_URL" in https://*) EXPECT_SECURE="${EXPECT_SECURE:-1}" ;; *) EXPECT_SECURE="${EXPECT_SECURE:-0}" ;; esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
HDR="$TMP/h"; BODY="$TMP/b"
CODE=""
SKIPPED=0

step() { printf '\n== %s\n' "$*"; }
ok() { printf '   OK   %s\n' "$*"; }
skip() { printf '   SKIP %s\n' "$*"; SKIPPED=$((SKIPPED + 1)); }
fail() {
  printf '   FAIL %s\n' "$*" >&2
  [ -s "$BODY" ] && { printf '        respuesta: ' >&2; head -c 600 "$BODY" >&2; echo >&2; }
  exit 1
}

# req METODO RUTA [JSON] [headers extra...]  ->  deja $CODE, $HDR y $BODY. Timeout largo por el cold start de Render.
req() {
  local method="$1" path="$2" data="${3:-}"; shift 3
  local args=(-sS --max-time 120 -X "$method" -o "$BODY" -D "$HDR" -w '%{http_code}')
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  CODE="$(curl "${args[@]}" "$@" "$BASE_URL$path")" || fail "curl no pudo conectar con $BASE_URL$path"
}
expect() { # expect CODIGO descripcion
  [ "$CODE" = "$1" ] && ok "$2 -> $CODE" || fail "$2: esperaba $1 y llego $CODE"
}
jqr() { # valor de un campo de la respuesta (false cuenta como valor); falla si no esta o es null
  local v; v="$(jq -r "($1) as \$v | if \$v == null then \"__null__\" else (\$v | tostring) end" "$BODY" 2>/dev/null)" || v=__null__
  [ "$v" != __null__ ] || fail "la respuesta no tiene $1"
  printf '%s' "$v"
}
bearer() { printf 'Authorization: Bearer %s' "$1"; }
csrf=(-H 'X-Requested-With: SmartElevate' -H "Origin: $ORIGIN")

# login EMAIL PASSWORD -> deja ACCESS y COOKIE (valor de refresh_token)
login() {
  req POST /api/auth/login "$(jq -n --arg e "$1" --arg p "$2" '{email:$e,password:$p}')"
  expect 200 "login de $1"
  ACCESS="$(jqr .accessToken)"
  local sc; sc="$(tr -d '\r' < "$HDR" | grep -i '^set-cookie: *refresh_token=' | head -1 || true)"
  [ -n "$sc" ] || fail "el login no seteo la cookie refresh_token"
  COOKIE="$(printf '%s' "$sc" | sed -E 's/^[^=]*=([^;]*).*/\1/')"
  for attr in HttpOnly 'SameSite=Lax' 'Path=/api/auth'; do
    printf '%s' "$sc" | grep -qi "$attr" || fail "la cookie no tiene $attr: $sc"
  done
  if [ "$EXPECT_SECURE" = 1 ]; then
    printf '%s' "$sc" | grep -qi '; *Secure' || fail "la cookie no tiene Secure: $sc"
  fi
  ok "cookie refresh_token con HttpOnly, SameSite=Lax, Path=/api/auth$([ "$EXPECT_SECURE" = 1 ] && echo ', Secure')"
}

step "1. Salud"
req GET /api/ping ""
expect 200 "GET /api/ping"
req GET /actuator/health ""
expect 200 "GET /actuator/health"
[ "$(jqr .status)" = UP ] || fail "health no esta UP"
ok "health UP (la base responde)"

if [ -z "${SMOKE_EMAIL:-}" ] || [ -z "${SMOKE_PASSWORD:-}" ]; then
  skip "sin SMOKE_EMAIL/SMOKE_PASSWORD: no se prueba sesion ni reservas"
  echo; echo "Listo (con $SKIPPED paso(s) salteado(s))."; exit 0
fi

if [ "${SMOKE_REGISTER:-0}" = 1 ]; then
  step "2. Registro"
  legajo="${SMOKE_LEGAJO:-$(printf '%09d' $((RANDOM * 32768 + RANDOM)))}"
  req POST /api/auth/register "$(jq -n --arg e "$SMOKE_EMAIL" --arg p "$SMOKE_PASSWORD" --arg l "$legajo" \
    '{fullName:"Smoke Test",email:$e,legajo:$l,password:$p,declaredUserType:"STUDENT"}')"
  expect 201 "POST /api/auth/register ($SMOKE_EMAIL, legajo $legajo)"
  echo "   Con mail activo: abri el link del mail de verificacion. Con APP_MAIL_ENABLED=false: buscalo en el log del backend."
  echo "   Despues repeti el script sin SMOKE_REGISTER."
  exit 0
fi

step "2. Sesion (login, cookie, refresh)"
login "$SMOKE_EMAIL" "$SMOKE_PASSWORD"
req POST /api/auth/refresh ""
[ "$CODE" = 403 ] && ok "refresh sin X-Requested-With -> 403" || fail "refresh sin el header CSRF deberia dar 403 y dio $CODE"
req POST /api/auth/refresh "" "${csrf[@]}"
expect 401 "refresh sin cookie"
req POST /api/auth/refresh "" "${csrf[@]}" -H "Cookie: refresh_token=$COOKIE"
expect 200 "refresh con la cookie del login (la rota)"
ACCESS="$(jqr .accessToken)"
NEW="$(tr -d '\r' < "$HDR" | grep -i '^set-cookie: *refresh_token=' | head -1 | sed -E 's/^[^=]*=([^;]*).*/\1/')"
[ -n "$NEW" ] && [ "$NEW" != "$COOKIE" ] || fail "el refresh no roto la cookie"
ok "la cookie rotó"
COOKIE="$NEW"
USER_ACCESS="$ACCESS"

step "3. Perfil y salidas"
req GET /api/users/me "" -H "$(bearer "$ACCESS")"
expect 200 "GET /api/users/me"
ok "usuario $(jqr .email) con rol $(jqr .role)"
req GET /api/departures "" -H "$(bearer "$ACCESS")"
expect 200 "GET /api/departures"
DEPARTURES="$(cat "$BODY")"
req GET /api/elevators ""
expect 200 "GET /api/elevators (publico)"
ELEVATORS="$(cat "$BODY")"
DEP="$(jq -c --arg mode "${SMOKE_CHECKIN:-0}" '[.[] | select(.remainingSeats > 0)]
  | sort_by(.departureAt) | if length == 0 then empty elif $mode == "1" then (map(select((.departureAt | sub("\\.[0-9]+";"") | fromdateiso8601) > now + 150)) | first // empty) else last end' <<<"$DEPARTURES")"

RES_ID=""; DEP_AT=""
if [ -z "$DEP" ]; then
  skip "no hay salidas reservables ahora (fuera del horario de operacion o de la ventana): reservar, cancelar y check-in"
else
  step "4. Reservar"
  dep_id="$(jq -r .id <<<"$DEP")"; elev="$(jq -r .elevator <<<"$DEP")"; DEP_AT="$(jq -r .departureAt <<<"$DEP")"
  floors="$(jq -c --arg c "$elev" '.[] | select(.code == $c) | .floors' <<<"$ELEVATORS")"
  origin="$(jq -r 'if index(0) != null then 0 else min end' <<<"$floors")"
  dest="$(jq -r 'max' <<<"$floors")"
  req POST /api/reservations "$(jq -n --argjson d "$dep_id" --argjson o "$origin" --argjson t "$dest" \
    '{departureId:$d,originFloor:$o,destinationFloor:$t}')" -H "$(bearer "$ACCESS")"
  expect 201 "POST /api/reservations (salida $dep_id, ascensor $elev, piso $origin -> $dest)"
  RES_ID="$(jqr .id)"
fi

if [ -n "$RES_ID" ] && [ "${SMOKE_CHECKIN:-0}" != 1 ]; then
  step "5. Cancelar"
  req DELETE "/api/reservations/$RES_ID" "" -H "$(bearer "$ACCESS")"
  expect 204 "DELETE /api/reservations/$RES_ID"
fi

if [ -z "${ADMIN_EMAIL:-}" ] || [ -z "${ADMIN_PASSWORD:-}" ]; then
  skip "sin ADMIN_EMAIL/ADMIN_PASSWORD: no se prueba el panel admin ni el kiosco"
  [ -n "$RES_ID" ] && [ "${SMOKE_CHECKIN:-0}" = 1 ] && skip "check-in: necesita un kiosco, que lo crea un ADMIN"
  echo; echo "Listo (con $SKIPPED paso(s) salteado(s))."; exit 0
fi

step "6. Panel admin"
login "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
ADMIN_ACCESS="$ACCESS"
req GET /api/users/me "" -H "$(bearer "$ADMIN_ACCESS")"
[ "$(jqr .role)" = ADMIN ] || fail "$ADMIN_EMAIL no es ADMIN (ver 'Crear el primer ADMIN' en docs/infraestructura.md)"
req GET /api/admin/kpis?period=TODAY "" -H "$(bearer "$ADMIN_ACCESS")"
expect 200 "GET /api/admin/kpis?period=TODAY"
req GET /api/admin/kiosks "" -H "$(bearer "$ADMIN_ACCESS")"
expect 200 "GET /api/admin/kiosks"
SIM="$(jqr .simulatorEnabled)"
if [ "${EXPECT_SIMULATOR:-0}" = 1 ]; then
  [ "$SIM" = true ] || fail "simulatorEnabled=$SIM y se esperaba true"
else
  [ "$SIM" = false ] || fail "simulatorEnabled=$SIM: APP_KIOSK_SIMULATOR_ENABLED tiene que estar apagado en prod"
  ok "simulador apagado"
fi

step "7. Kiosco no simulado y QR"
ELEV_CODE="${elev:-$(jq -r '[.[] | select(.active)][0].code' <<<"$ELEVATORS")}"
if [ "${EXPECT_SIMULATOR:-0}" != 1 ]; then
  req POST /api/admin/kiosks "$(jq -n --arg e "$ELEV_CODE" '{elevatorId:$e,simulated:true}')" -H "$(bearer "$ADMIN_ACCESS")"
  expect 409 "crear kiosco simulado con el simulador apagado"
  [ "$(jqr .code)" = SIMULATOR_DISABLED ] || fail "el code deberia ser SIMULATOR_DISABLED"
fi
req POST /api/admin/kiosks "$(jq -n --arg e "$ELEV_CODE" '{elevatorId:$e,simulated:false}')" -H "$(bearer "$ADMIN_ACCESS")"
expect 201 "POST /api/admin/kiosks (ascensor $ELEV_CODE, real)"
KIOSK_ID="$(jqr .id)"; KIOSK_SECRET="$(jqr .secret)"
req GET /api/kiosks/me/qr-token "" -H "$(bearer "$KIOSK_SECRET")"
expect 200 "GET /api/kiosks/me/qr-token con la credencial del kiosco"
QR="$(jqr .token)"; ok "token del QR para el ascensor $(jqr .elevatorId), vence $(jqr .expiresAt)"

if [ -n "$RES_ID" ] && [ "${SMOKE_CHECKIN:-0}" = 1 ]; then
  step "8. Check-in (espera la ventana: abre 1 min antes de la salida $DEP_AT)"
  wait_s="$(jq -n --arg t "$DEP_AT" '($t | sub("\\.[0-9]+";"") | fromdateiso8601) - now - 30 | floor')"
  [ "$wait_s" -gt 0 ] && { echo "   esperando ${wait_s}s..."; sleep "$wait_s"; }
  req GET /api/kiosks/me/qr-token "" -H "$(bearer "$KIOSK_SECRET")"   # el token anterior pudo vencer mientras esperaba
  QR="$(jqr .token)"
  req POST /api/check-ins "$(jq -n --arg t "$QR" '{token:$t}')" -H "$(bearer "$USER_ACCESS")"
  expect 201 "POST /api/check-ins"
  ok "outcome $(jqr .outcome), source $(jqr .source)"
elif [ -n "$RES_ID" ]; then
  skip "check-in: se canceló la reserva; repetir con SMOKE_CHECKIN=1 para probarlo (espera hasta la salida)"
else
  skip "check-in: no habia salida reservable"
fi

req POST "/api/admin/kiosks/$KIOSK_ID/revoke" "" -H "$(bearer "$ADMIN_ACCESS")"
expect 204 "revocar el kiosco de prueba"
req GET /api/kiosks/me/qr-token "" -H "$(bearer "$KIOSK_SECRET")"
expect 401 "el kiosco revocado ya no pide tokens"

step "9. Logout"
req POST /api/auth/logout "" "${csrf[@]}" -H "Cookie: refresh_token=$COOKIE"
expect 204 "POST /api/auth/logout"

echo; echo "Todo OK$([ "$SKIPPED" -gt 0 ] && echo " ($SKIPPED paso(s) salteado(s))")."
