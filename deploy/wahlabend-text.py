#!/usr/bin/env python3
"""Holt eine Webseite wie ein Browser und gibt ihren sichtbaren Text aus.

Gedacht fuer den Wahlabend-Agenten (deploy/wahlabend-agent.sh): die
Liveticker von ARD-Anstalten, zdfheute, koalitions-rechner.de usw. sind fuer
den eingebauten WebFetch von Claude Code teils gesperrt (robots.txt), per curl
mit Browser-Kennung aber lesbar. Skripte, Styles und Tags fliegen raus,
Whitespace wird zusammengezogen.

    deploy/wahlabend-text.py URL                  # ganzer Text
    deploy/wahlabend-text.py URL 'Hochrechnung'   # nur Fenster um Treffer (Regex, Gross/Klein egal)
    deploy/wahlabend-text.py URL 'Hochrechnung' 600   # Fensterbreite in Zeichen (Standard 450)
"""
import html
import re
import sys
import urllib.request

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/128 Safari/537.36")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "de"})
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read()
        charset = r.headers.get_content_charset() or "utf-8"
    try:
        return raw.decode(charset, errors="replace")
    except LookupError:
        return raw.decode("utf-8", errors="replace")


def visible_text(page):
    page = re.sub(r"<script.*?</script>", " ", page, flags=re.S | re.I)
    page = re.sub(r"<style.*?</style>", " ", page, flags=re.S | re.I)
    page = re.sub(r"<(br|p|div|li|h\d|tr|td|th|section|article)[^>]*>", "\n", page, flags=re.I)
    page = html.unescape(re.sub(r"<[^>]+>", " ", page))
    page = re.sub(r"[ \t\r\f\v]+", " ", page)
    return re.sub(r"\n\s*\n+", "\n", page).strip()


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__.strip())
        return 2
    url = argv[1]
    pattern = argv[2] if len(argv) > 2 else None
    window = int(argv[3]) if len(argv) > 3 else 450
    try:
        text = visible_text(fetch(url))
    except Exception as e:  # noqa: BLE001 — der Agent soll den Grund sehen
        print(f"FEHLER beim Abruf von {url}: {e}", file=sys.stderr)
        return 1
    if not pattern:
        print(text)
        return 0
    flat = re.sub(r"\s+", " ", text)
    last_end = -1
    hits = 0
    for m in re.finditer(pattern, flat, flags=re.I):
        start = max(0, m.start() - window // 3)
        if start < last_end:
            continue
        end = min(len(flat), m.end() + window)
        print(f"--- Treffer {hits + 1} (Zeichen {m.start()}) ---")
        print(flat[start:end])
        last_end = end
        hits += 1
    if hits == 0:
        print(f"Kein Treffer fuer /{pattern}/ auf {url} ({len(flat)} Zeichen Text).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
