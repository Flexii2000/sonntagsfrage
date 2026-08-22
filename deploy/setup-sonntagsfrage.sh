#!/usr/bin/env bash
#
# Einmalige Einrichtung von fherrmann.com/wahlen auf dem Heimserver.
# Muss mit sudo laufen (nginx-Config + Reload). Alles andere laeuft als flexii.
#
#   sudo ~/services/sonntagsfrage/deploy/setup-sonntagsfrage.sh
#
# Das Skript ist idempotent: bereits erledigte Schritte werden uebersprungen.
set -euo pipefail

APP_USER="flexii"
APP_DIR="/home/${APP_USER}/services/sonntagsfrage"
SITE_CONF="/etc/nginx/sites-available/fherrmann.com"
SNIPPET="/etc/nginx/snippets/wahlen.conf"
INCLUDE_LINE="    include ${SNIPPET};"

if [[ $EUID -ne 0 ]]; then
  echo "Bitte mit sudo ausfuehren: sudo $0" >&2
  exit 1
fi

if [[ ! -d "$APP_DIR" ]]; then
  echo "FEHLER: $APP_DIR existiert nicht." >&2
  echo "Erst das Repo dorthin klonen (als ${APP_USER}, nicht als root):" >&2
  echo "  git clone git@github.com:Flexii2000/sonntagsfrage.git $APP_DIR" >&2
  exit 1
fi

echo "[1/6] Pruefe die nginx-Config von fherrmann.com ..."
if [[ ! -f "$SITE_CONF" ]]; then
  echo "FEHLER: $SITE_CONF nicht gefunden." >&2
  exit 1
fi

# Der include wird ans Ende des letzten Serverblocks gehaengt. Das ist nur
# richtig, wenn der letzte Block tatsaechlich der HTTPS-Block ist — pruefen,
# statt zu hoffen.
LAST_SERVER_LINE="$(grep -n '^[[:space:]]*server[[:space:]]*{' "$SITE_CONF" | tail -1 | cut -d: -f1)"
if ! tail -n +"$LAST_SERVER_LINE" "$SITE_CONF" | grep -q 'listen 443'; then
  echo "FEHLER: Der letzte server-Block in $SITE_CONF ist nicht der HTTPS-Block." >&2
  echo "Bitte diese Zeile von Hand in den 443-Block einfuegen:" >&2
  echo "$INCLUDE_LINE" >&2
  exit 1
fi

echo "[2/6] Lege das nginx-Snippet an ..."
mkdir -p "$(dirname "$SNIPPET")"
install -m 0644 "${APP_DIR}/deploy/nginx-wahlen.conf" "$SNIPPET"

echo "[3/6] Binde das Snippet in den HTTPS-Block ein ..."
if grep -qF "$SNIPPET" "$SITE_CONF"; then
  echo "    include ist schon drin, ueberspringe."
else
  BACKUP="${SITE_CONF}.bak-$(date +%Y%m%d-%H%M%S)"
  cp -a "$SITE_CONF" "$BACKUP"
  echo "    Backup: $BACKUP"

  # Vor die letzte schliessende Klammer der Datei einfuegen.
  LAST_BRACE="$(grep -n '^[[:space:]]*}[[:space:]]*$' "$SITE_CONF" | tail -1 | cut -d: -f1)"
  if [[ -z "$LAST_BRACE" ]]; then
    echo "FEHLER: Keine schliessende Klammer gefunden." >&2
    exit 1
  fi
  awk -v line="$LAST_BRACE" -v ins="$INCLUDE_LINE" \
      'NR == line { print ""; print ins } { print }' "$BACKUP" > "$SITE_CONF"

  if ! nginx -t; then
    echo "FEHLER: nginx -t ist fehlgeschlagen — stelle das Backup wieder her." >&2
    cp -a "$BACKUP" "$SITE_CONF"
    nginx -t
    exit 1
  fi
fi

echo "[4/6] Lege die .env an (falls noch nicht vorhanden) ..."
if [[ -f "${APP_DIR}/.env" ]]; then
  echo "    .env existiert bereits, ueberspringe."
else
  PASSWORD="$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 28)"
  cat > "${APP_DIR}/.env" <<ENVEOF
DB_NAME=wahlen
DB_USER=wahlen
DB_PASSWORD=${PASSWORD}
APP_PORT=8090
ENVEOF
  chown "${APP_USER}:${APP_USER}" "${APP_DIR}/.env"
  chmod 600 "${APP_DIR}/.env"
  echo "    .env mit generiertem Passwort angelegt."
fi

echo "[5/6] Starte die Container (als ${APP_USER}) ..."
sudo -u "$APP_USER" bash -c "cd '$APP_DIR' && docker compose up -d --build"

echo "    Warte auf den Healthcheck ..."
HEALTHY=0
for i in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:8090/wahlen/actuator/health >/dev/null 2>&1; then
    HEALTHY=1
    break
  fi
  sleep 3
done
if [[ $HEALTHY -ne 1 ]]; then
  echo "FEHLER: App wurde nicht gesund. nginx wurde NICHT neu geladen." >&2
  echo "  cd $APP_DIR && docker compose logs --tail=80 app" >&2
  exit 1
fi
echo "    App antwortet auf 127.0.0.1:8090."

echo "[6/6] Lade nginx neu ..."
nginx -t
systemctl reload nginx

echo
echo "Fertig. https://fherrmann.com/wahlen sollte jetzt erreichbar sein."
echo "Fuer kuenftige Updates:  ~/scripts/update-sonntagsfrage.sh"
