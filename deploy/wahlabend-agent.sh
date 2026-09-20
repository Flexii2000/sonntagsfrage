#!/usr/bin/env bash
#
# Wahlabend-Agent: am Wahltag ab 17:55 traegt ein kopfloser Claude-Code-Lauf
# alle zehn Minuten die 18-Uhr-Prognose und die Hochrechnungen von ARD und
# ZDF ueber die Wahlabend-API nach — bis 01:00 oder bis jede Wahl des Tages
# ein vorlaeufiges Ergebnis hat. Liegt als Symlink in ~/scripts/ auf dem
# Server; Cron ruft taeglich um 17:55 `start` auf (AGENT-RUNBOOK.md, Abschnitt 9).
#
#   wahlabend-agent.sh start                  # Cron: startet nur, wenn heute gewaehlt wird
#   wahlabend-agent.sh start --force <slug>…  # auch sonst, fuer diese Wahlen (Test, Nachstart)
#   wahlabend-agent.sh run-once <slug>…       # ein einzelner Lauf im Vordergrund
#   wahlabend-agent.sh status                 # laeuft die Session? Cron da? letzte Logzeilen
#   wahlabend-agent.sh stop                   # Session beenden
#
# Der Agent bekommt nur die Werkzeuge, die er braucht (ALLOWED_TOOLS unten):
# lesen, curl gegen die API, das Textabruf-Skript und deploy/wahlabend.sh.
# Alles andere lehnt Claude Code im kopflosen Modus ohne Rueckfrage ab —
# Dateien aendern, deployen, Container anfassen geht damit nicht.
# Prompt: wahlabend-agent-prompt.md daneben. Seitenabruf: wahlabend-text.py.
set -euo pipefail

SESSION="wahlabend-agent"
CLAUDE="$HOME/.local/bin/claude"
WORKDIR="$HOME/Server-Projects"     # dort gelten CLAUDE.md und .claude/settings.json (.env gesperrt)
API="http://localhost:8090/wahlen/api"
LOG="$HOME/scripts/wahlabend-agent.log"
INTERVAL=600                        # Sekunden zwischen zwei Laeufen
RUN_TIMEOUT=540                     # laenger darf ein Lauf nicht dauern
MAX_TURNS=60
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"   # deploy/ im Repo, auch ueber den Symlink
PROMPT_FILE="$HERE/wahlabend-agent-prompt.md"
ALLOWED_TOOLS=(
  "Read" "Grep" "Glob" "WebSearch" "WebFetch"
  "Bash(curl:*)" "Bash(jq:*)" "Bash(head:*)" "Bash(grep:*)" "Bash(date:*)"
  "Bash(python3 sonntagsfrage/deploy/wahlabend-text.py:*)"
  "Bash(sonntagsfrage/deploy/wahlabend.sh:*)"
)

log() { printf '%s  %s\n' "$(date '+%F %T')" "$*" >>"$LOG"; }

# Slugs aller Wahlen, die heute stattfinden — laut Wahlabend-API.
todays_elections() {
  local today slugs slug d
  today="$(date +%F)"
  if ! slugs="$(curl -sf --max-time 15 "$API/parliaments" | jq -r '.[].parliament.slug')"; then
    log "FEHLER: $API/parliaments antwortet nicht — laeuft der Container?"
    return 0
  fi
  for slug in $slugs; do
    d="$(curl -sf --max-time 15 "$API/parliaments/$slug/wahlabend" | jq -r '.electionDate // empty' 2>/dev/null || true)"
    [[ "$d" == "$today" ]] && echo "$slug"
  done
  return 0
}

election_title() {
  curl -sf --max-time 15 "$API/parliaments/$1/wahlabend" | jq -r '.title // .electionName // ""' 2>/dev/null || true
}

build_prompt() {
  local elections="" slug tpl
  for slug in "$@"; do
    elections+="- \`$slug\` — $(election_title "$slug")"$'\n'
  done
  tpl="$(cat "$PROMPT_FILE")"
  tpl="${tpl//__DATE__/$(date +%F)}"
  tpl="${tpl//__NOW__/$(date '+%H:%M')}"
  tpl="${tpl//__ELECTIONS__/$elections}"
  printf '%s\n' "$tpl"
}

run_once() {
  [[ $# -gt 0 ]] || { echo "run-once braucht mindestens einen Slug" >&2; return 2; }
  [[ -x "$CLAUDE" ]] || { log "FEHLER: $CLAUDE nicht ausfuehrbar"; return 1; }
  [[ -r "$PROMPT_FILE" ]] || { log "FEHLER: Prompt $PROMPT_FILE fehlt"; return 1; }
  local started rc=0
  started=$(date +%s)
  log "Lauf startet: $*"
  (
    cd "$WORKDIR"
    build_prompt "$@" | timeout "$RUN_TIMEOUT" "$CLAUDE" -p \
      --max-turns "$MAX_TURNS" \
      --output-format text \
      --allowedTools "${ALLOWED_TOOLS[@]}"
  ) >>"$LOG" 2>&1 || rc=$?
  log "Lauf beendet nach $(( $(date +%s) - started )) s, exit=$rc"
  return 0
}

# true, wenn jede Wahl ein vorlaeufiges Ergebnis als massgeblichen Stand hat
all_final() {
  local slug kind
  for slug in "$@"; do
    kind="$(curl -sf --max-time 15 "$API/parliaments/$slug/wahlabend" | jq -r '.latest.kind // empty' 2>/dev/null || true)"
    [[ "$kind" == "VORLAEUFIG" ]] || return 1
  done
  return 0
}

loop() {
  [[ $# -gt 0 ]] || { log "loop ohne Slugs — nichts zu tun"; return 0; }
  local slugs=("$@") day end final_after t18 now
  day="$(date +%F)"
  if (( 10#$(date +%H) >= 12 )); then
    end=$(date -d "$day 01:00 + 1 day" +%s)
  else
    end=$(date -d "$day 01:00" +%s)
  fi
  final_after=$(date -d "$day 22:00" +%s)
  t18=$(date -d "$day 18:01" +%s)
  now=$(date +%s)
  if (( now < t18 )); then
    log "warte bis 18:01 ($(( t18 - now )) s)"
    sleep $(( t18 - now ))
  fi
  log "Schleife bis $(date -d "@$end" '+%F %H:%M'), alle $INTERVAL s, fuer: ${slugs[*]}"
  while (( $(date +%s) < end )); do
    run_once "${slugs[@]}"
    if (( $(date +%s) > final_after )) && all_final "${slugs[@]}"; then
      log "jede Wahl hat ein vorlaeufiges Ergebnis — Schluss fuer heute"
      break
    fi
    sleep "$INTERVAL"
  done
  log "Schleife beendet"
}

start() {
  local force=false slugs=()
  if [[ "${1:-}" == "--force" ]]; then force=true; shift; fi
  if tmux has-session -t "$SESSION" 2>/dev/null; then
    log "Session '$SESSION' laeuft bereits — nichts zu tun"
    return 0
  fi
  if $force && [[ $# -gt 0 ]]; then
    slugs=("$@")
  else
    mapfile -t slugs < <(todays_elections)
  fi
  if [[ ${#slugs[@]} -eq 0 ]]; then
    $force && log "keine Wahl heute — nichts gestartet"
    return 0
  fi
  [[ -x "$CLAUDE" ]] || { log "FEHLER: $CLAUDE nicht ausfuehrbar"; return 1; }
  tmux new-session -d -s "$SESSION" -c "$WORKDIR" "$(readlink -f "$0") loop ${slugs[*]}"
  log "Session '$SESSION' gestartet fuer: ${slugs[*]}  (tmux attach -t $SESSION; Log: $LOG)"
}

status() {
  if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Session '$SESSION' laeuft (tmux attach -t $SESSION)."
  else
    echo "Keine Session '$SESSION'."
  fi
  echo "Cron:"
  crontab -l 2>/dev/null | grep -F "$(basename "$0")" || echo "  (kein Eintrag)"
  echo "Heute laut API: $(todays_elections | tr '\n' ' ')"
  echo "Log $LOG, letzte Zeilen:"
  tail -n 25 "$LOG" 2>/dev/null || echo "  (leer)"
}

stop() {
  if tmux has-session -t "$SESSION" 2>/dev/null; then
    tmux kill-session -t "$SESSION"
    log "Session '$SESSION' beendet (stop)"
    echo "beendet"
  else
    echo "lief nicht"
  fi
}

case "${1:-}" in
  start)    shift; start "$@" ;;
  loop)     shift; loop "$@" ;;
  run-once) shift; run_once "$@" ;;
  status)   status ;;
  stop)     stop ;;
  *)        sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
