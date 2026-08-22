#!/usr/bin/env bash
# Gehoert nach ~/scripts/update-sonntagsfrage.sh auf dem Server.
# Holt den aktuellen Stand und baut den Container neu. Braucht kein sudo:
# flexii ist in der docker-Gruppe.
set -euo pipefail

APP_DIR="/home/flexii/services/sonntagsfrage"

echo "[1/4] Wechsel ins wahlen-Repo ..."
cd "$APP_DIR"
echo "    aktuelles Verzeichnis: $(pwd)"

echo "[2/4] Hole Aenderungen aus dem Remote-Repo ..."
git pull --ff-only

echo "[3/4] Baue und starte die Container neu ..."
docker compose up -d --build

echo "[4/4] Warte auf den Healthcheck ..."
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8090/wahlen/actuator/health >/dev/null 2>&1; then
    echo "    App ist gesund."
    echo "wahlen erfolgreich aktualisiert."
    exit 0
  fi
  sleep 3
done

echo "FEHLER: App wurde nach 90 s nicht gesund." >&2
echo "Logs ansehen:  cd $APP_DIR && docker compose logs --tail=80 app" >&2
exit 1
