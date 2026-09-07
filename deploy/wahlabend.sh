#!/usr/bin/env bash
#
# Einen Wahlabend-Stand (Prognose, Hochrechnung, ...) per Skript eintragen —
# fuer den Fall, dass das Formular unter /wahlen/<slug>/wahlabend/eintragen
# gerade nicht zur Hand ist.
#
#   deploy/wahlabend.sh <slug> <datum> <art> <uhrzeit> "<quelle>" "PARTEI=PROZENT ..." [Beteiligung]
#
#   deploy/wahlabend.sh sachsen-anhalt 2026-09-06 PROGNOSE 18:00 "ARD / infratest dimap" \
#       "CDU=18.5 AfD=44.5 Linke=9.5 SPD=8.0 FDP=2.0 Grüne=9.0 BSW=5.0"
#
# Art: PROGNOSE | HOCHRECHNUNG | AUSZAEHLUNG | VORLAEUFIG. Uhrzeit HH:mm — ab
# 18 Uhr zaehlt der Wahltag, davor der Folgetag. Fehlt "Sonstige", ist der Rest
# bis 100 gemeint. Sitze optional als "PARTEI=PROZENT/SITZE".
#
# Token: Umgebungsvariable WAHLABEND_TOKEN, sonst aus der .env neben diesem Repo.
# Ziel: WAHLEN_URL (Standard https://fherrmann.com/wahlen).
set -euo pipefail

if [[ $# -lt 6 ]]; then
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
fi

SLUG="$1"; DATE="$2"; KIND="$3"; TIME="$4"; SOURCE="$5"; VALUES="$6"; TURNOUT="${7:-}"
BASE="${WAHLEN_URL:-https://fherrmann.com/wahlen}"

TOKEN="${WAHLABEND_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
  if [[ -f "$ENV_FILE" ]]; then
    TOKEN="$(grep '^WAHLABEND_TOKEN=' "$ENV_FILE" | cut -d= -f2- || true)"
  fi
fi
if [[ -z "$TOKEN" ]]; then
  echo "Kein Token: WAHLABEND_TOKEN setzen oder in .env eintragen." >&2
  exit 1
fi

RESULTS=""; SEATS=""
for pair in $VALUES; do
  party="${pair%%=*}"; rest="${pair#*=}"
  percent="${rest%%/*}"
  RESULTS+="\"${party}\":${percent},"
  if [[ "$rest" == */* ]]; then
    SEATS+="\"${party}\":${rest#*/},"
  fi
done
RESULTS="{${RESULTS%,}}"
SEATS_JSON="null"
[[ -n "$SEATS" ]] && SEATS_JSON="{${SEATS%,}}"
TURNOUT_JSON="null"
[[ -n "$TURNOUT" ]] && TURNOUT_JSON="$TURNOUT"

BODY="{\"kind\":\"${KIND}\",\"reportedAt\":\"${TIME}\",\"source\":\"${SOURCE}\",\"turnout\":${TURNOUT_JSON},\"results\":${RESULTS},\"seats\":${SEATS_JSON}}"

curl -sS -X POST "${BASE}/api/wahlabend/${SLUG}/${DATE}/reports" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "$BODY"
echo
