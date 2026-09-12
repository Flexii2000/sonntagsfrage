# Runbook — `wahlen` (fherrmann.com/wahlen)

> Betriebshandbuch für Agenten und für Felix in sechs Monaten.
> Ergänzt `~/Server-Projects/SERVER-CONTEXT.md`, dupliziert es nicht.
>
> Stand: 2026-09-07

---

## 1. Was das ist

Sonntagsfrage-Aggregator für Bundestag, 16 Landtage und das Europaparlament.
Zeigt geglättete Umfrageverläufe, Sitzprojektionen, rechnerische
Koalitionsmehrheiten und Institutseffekte.

```
Browser
  └─ nginx (fherrmann.com)  location /wahlen  →  127.0.0.1:8090
       └─ Container wahlen-app    (Spring Boot 4, Java 25)
            └─ Container wahlen-db (Postgres 18, Named Volume)
                 ▲
                 ├── alle 30 min:  api.dawum.de
                 └── am Wahlabend jede Minute:  CSV der Landeswahlleitung
```

---

## 2. Namen — Achtung, zwei davon

| Ebene | Name |
|---|---|
| Repo, Verzeichnisse, Skripte | **`sonntagsfrage`** |
| App, URL, Container, Datenbank | **`wahlen`** |

Also: das Repo `sonntagsfrage` liegt in `~/services/sonntagsfrage`, wird mit
`~/scripts/update-sonntagsfrage.sh` aktualisiert — und betreibt darin die
Container `wahlen-app` und `wahlen-db` unter `fherrmann.com/wahlen`.
Dasselbe Muster wie bei `~/Server-Projects/CSVExporter` ↔ `~/haspa-exporter/`.

---

## 3. Wo was liegt

| Was | Wo |
|---|---|
| Repo auf dem Server | `/home/flexii/services/sonntagsfrage` |
| Repo lokal | `~/Server-Projects/sonntagsfrage` |
| GitHub | `git@github.com:Flexii2000/sonntagsfrage.git` |
| Update-Skript | `~/scripts/update-sonntagsfrage.sh` |
| Ersteinrichtung | `<repo>/deploy/setup-sonntagsfrage.sh` (braucht sudo) |
| nginx-Snippet | `/etc/nginx/snippets/wahlen.conf`, eingebunden per `include` in `/etc/nginx/sites-available/fherrmann.com` |
| DB-Passwort | `<repo>/.env` auf dem Server (Modus 600, nicht in Git) |
| Postgres-Daten | Docker Named Volume `sonntagsfrage_wahlen-db-data` |

> ⚠️ **Nicht unter `/opt` legen.** Der Docker-Daemon auf dem Server ist die
> Snap-Variante und erlaubt keine Bind-Mounts unterhalb von `/opt`
> (`mkdir /opt/…: read-only file system`). Deshalb weicht dieses Projekt vom
> sonst üblichen `/opt/<name>/app`-Muster ab. Details zur Docker-Doppel-
> installation stehen im CSVExporter-Runbook.

---

## 4. Statusboard

`status.fherrmann.com` hat eine Karte **Sonntagsfrage**. Sie liest — anders
als die übrigen Karten — nicht aus `/status-data/`, sondern direkt
`https://fherrmann.com/wahlen/api/meta` im Browser des Betrachters. Kein
Cron-Sammler, keine Zwischendatei, die still veralten kann.

Dafür gibt die App eine eng gefasste CORS-Freigabe: nur die Herkünfte aus
`wahlen.cors.allowed-origins` (Standard: `https://status.fherrmann.com`), nur
`GET`, nur `/api/**`. Wer die Statusboard-Domain ändert, muss sie dort
nachtragen — sonst zeigt die Karte "Dienst nicht erreichbar", obwohl alles läuft.

Schwellen in `statusboard/web/app.js`:

| Zustand | Auslöser |
|---|---|
| **Kein Abruf** (rot) | seit über 40 Minuten kein Abruf — zwei ausgefallene Zyklen, der Scheduler hängt |
| **Fehler** (rot) | `lastError` gesetzt oder `/api/meta` nicht erreichbar |
| **Quelle still** (gelb) | DAWUM liefert seit über vier Tagen nichts Neues |
| **OK** (grün) | sonst |

---

## 5. Alltag

```bash
# Deployen (nach git push)
~/scripts/update-sonntagsfrage.sh

# Status
cd ~/services/sonntagsfrage && docker compose ps
curl -s localhost:8090/wahlen/actuator/health

# Logs
cd ~/services/sonntagsfrage && docker compose logs --tail=100 -f app

# Datenstand prüfen
curl -s localhost:8090/wahlen/api/meta | python3 -m json.tool
```

Die Seite `/wahlen/daten` zeigt denselben Importstand im Browser — der
schnellste Weg, ohne SSH zu sehen, ob die Daten frisch sind.

---

## 6. Ersteinrichtung (einmalig)

```bash
# 1. auf dem Server, als flexii
mkdir -p ~/services
git clone git@github.com:Flexii2000/sonntagsfrage.git ~/services/sonntagsfrage

# 2. nginx + .env + Container, braucht sudo
sudo ~/services/sonntagsfrage/deploy/setup-sonntagsfrage.sh

# 3. Update-Skript an seinen Platz
cp ~/services/sonntagsfrage/deploy/update-sonntagsfrage.sh ~/scripts/update-sonntagsfrage.sh
chmod +x ~/scripts/update-sonntagsfrage.sh
```

`setup-sonntagsfrage.sh` ist idempotent, legt vor der nginx-Änderung ein Backup an
und stellt es bei einem fehlgeschlagenen `nginx -t` selbst wieder her. Es
lädt nginx erst neu, **nachdem** die App gesund geantwortet hat.

---

## 7. Wie die Daten reinkommen

Beim Start und danach zweimal pro Stunde (`0 7,37 * * * *`, Europe/Berlin):

1. `GET api.dawum.de/last_update.txt` — 39 Bytes. Unverändert → fertig.
2. `GET api.dawum.de/` mit `If-None-Match`. 304 → fertig.
3. Pro Umfrage einen SHA-256-Fingerabdruck vergleichen
   (`survey.content_hash`); nur Geändertes wird angefasst.
4. Danach die Referenzdaten aus dem Repo einlesen, dann Caches leeren.

**Ein fehlgeschlagener Import ändert nichts.** Der letzte gute Stand bleibt
in der Datenbank, der Fehler landet in `import_state.last_error` und ist
über `/wahlen/api/meta` und `/wahlen/daten` sichtbar. Die Seite altert
sichtbar, statt auszufallen — das ist Absicht.

Import von Hand erzwingen (Container-Neustart reicht):

```bash
cd ~/services/sonntagsfrage && docker compose restart app
```

---

## 8. Wartung, die tatsächlich anfällt

### Nach jeder Wahl (der einzige regelmäßige Handgriff)

`src/main/resources/reference/elections.yaml` bearbeiten:

1. Den `SCHEDULED`-Eintrag der gelaufenen Wahl auf `status: HELD` setzen und
   `turnout`, `seats` sowie `results` mit dem amtlichen Endergebnis füllen.
2. Einen neuen `SCHEDULED`-Eintrag für den Folgetermin anlegen. Steht der
   noch nicht amtlich fest: bestes Schätzdatum plus `dateConfirmed: false` —
   dann taucht er im Kalender auf, kapert aber nicht die Startseite.
3. `source:` mitpflegen. Zahlen ohne Beleg kommen hier nicht rein.
4. Committen, pushen, `~/scripts/update-sonntagsfrage.sh`.

Damit endet auch der Wahlabend-Modus der Wahl (Abschnitt 9): der Block wandert
unter die Umfragen und bleibt dort als Archiv des Abends.

⚠️ **Parteikürzel müssen zur DAWUM-Schreibweise des jeweiligen Parlaments
passen:** Bundestag und Europaparlament nutzen `CDU/CSU`, Bayern `CSU`, alle
übrigen Länder `CDU`. Passt es nicht, wird die Zeile mit einer Warnung im Log
ignoriert und das Wahlergebnis fehlt still im Vergleich.

Kontrolle: Die Prozentwerte einer Wahl sollten sich zu 100 addieren.

```bash
# im Repo, prüft alle HELD-Einträge auf Vollständigkeit
python3 - <<'PY'
import re
txt = open('src/main/resources/reference/elections.yaml').read()
for b in re.split(r'\n  - parliament: ', txt)[1:]:
    if 'status: HELD' not in b: continue
    name = b.split('\n')[0].strip()
    date = re.search(r'date: (\S+)', b).group(1)
    s = sum(float(m) for m in re.findall(r'^\s+"[^"]+": ([0-9.]+)$', b, re.M))
    print(f"{name:24s} {date}  Summe={s:6.1f}{'   <-- PRUEFEN' if abs(s-100)>0.9 else ''}")
PY
```

### Neue Partei taucht in Umfragen auf

Passiert automatisch — sie bekommt eine deterministisch aus dem Kürzel
abgeleitete Farbe. Wenn sie relevant wird: Eintrag in
`reference/parties.yaml` mit `light`, `dark`, `order` und `spectrum`
(Position im Sitzbogen).

### Neues Parlament bei DAWUM

Wird ebenfalls automatisch angelegt, mit einem aus dem Kürzel abgeleiteten
Slug und 5 % Sperrklausel. Für Sitzprojektion und saubere URL einen Eintrag
in `reference/parliaments.yaml` ergänzen.

---

## 9. Wahlabend

Ab 18 Uhr am Wahltag steht auf `/wahlen/<slug>` ganz oben der
**Wahlabend-Block**: 18-Uhr-Prognose, Hochrechnungen, Auszählungsstand und
vorläufiges Ergebnis; Sitze und rechnerische Mehrheiten daraus; Vergleich mit
der Vorwahl und mit dem geglätteten Umfragestand zum Wahltag; der Verlauf des
Abends als Tabelle. Der Block lädt sich im Browser **jede Minute** nach
(als fertiges HTML-Fragment, `/wahlen/<slug>/wahlabend/fragment`) und bleibt
oben, **bis das amtliche Endergebnis in `elections.yaml` steht**. Danach
wandert er unter die Umfragen und bleibt dort dauerhaft nachlesbar.

Die Startseite featured am Abend und am Folgetag die Wahl (`ELECTION_NIGHT`),
die Hero-Karte rechts zeigt dann den aktuellen Stand statt der Umfragen.

### Woher die Stände kommen

| Was | Weg | Wer |
|---|---|---|
| Zwischenstand, vorläufiges Ergebnis, Sitze der **Landeswahlleitung** | automatisch: `WahlabendPoller` liest die CSV, die in `elections.yaml` unter `live:` steht — in den ersten 36 h nach 18 Uhr jede Minute, danach alle 15 min, immer mit ETag/If-Modified-Since. Unveränderte Datei = kein neuer Stand (Fingerabdruck) | Code |
| **18-Uhr-Prognose und Hochrechnungen** von ARD (infratest dimap) und ZDF (Forschungsgruppe Wahlen) | von Hand: Formular **`/wahlen/<slug>/wahlabend/eintragen`** (Handy reicht; Token wird im Browser gemerkt) oder `deploy/wahlabend.sh` | Felix |

Kein Stand wird je überschrieben — nur angelegt oder gelöscht. Maßgeblich ist
ein vorläufiges Ergebnis, sonst der jüngste Stand (`WahlabendService.pickLatest`).

**Token:** `WAHLABEND_TOKEN` in `~/services/sonntagsfrage/.env` auf dem
Server (Modus 600). `setup-sonntagsfrage.sh` trägt es nach, wenn es fehlt.
Ohne Token ist das Eintragen gesperrt, nicht offen.

### Checkliste am Wahltag

1. **Vormittags:** `live:`-Block der Wahl in `elections.yaml` prüfen —
   antwortet die CSV-URL (`curl -sI <url>`), stimmt die Kopfzeile noch mit
   den konfigurierten Spaltennamen überein? Mecklenburg-Vorpommern kündigt an,
   die endgültige URL erst in der Wahlwoche zu nennen. Änderung → committen,
   pushen, `~/scripts/update-sonntagsfrage.sh`.
2. **Wahl ohne `live:`-Block** (Berlin 2026): sobald die Landeswahlleitung ihre
   Datei zeigt, den Block nach dem MV-Muster anlegen — oder den Abend komplett
   von Hand füttern.
3. **18:00:** Prognose ARD und ZDF eintippen (Formular). Uhrzeit `18:00`,
   Quelle aus der Vorschlagsliste, sieben Zahlen, fertig. "Sonstige" darf
   fehlen, der Rest bis 100 wird ergänzt.
4. **Abend:** Hochrechnungen nach Bedarf nachtragen; die Landeswahlleitung
   kommt von selbst und steht als "Auszählungsstand" mit "x von y
   Wahlbezirken" da.
5. **Nachts:** Das vorläufige Ergebnis erkennt der Poller an
   `districtsCounted == districtsTotal` und holt dann auch die Sitze. Hat die
   Quelle keine Fortschrittsspalte (Sachsen-Anhalt), steht `kind: VORLAEUFIG`
   fest im Block — bei einer laufenden Auszählung vorher entfernen, nachts
   wieder setzen.
6. **Wochen später:** amtliches Endergebnis in `elections.yaml` (Abschnitt 8).
   Damit ist der Wahlabend abgeschlossen und wird zum Archiv.

### Neue Quelle konfigurieren (`live:` in `elections.yaml`)

Alle Landeswahlleitungen liefern dasselbe Muster: eine Zeile für das Land,
eine Spalte je Partei, eine Spalte mit den gültigen Stimmen. Der Adapter
(`CsvResultSource`) ist deshalb Konfiguration, kein Code:

| Feld | Bedeutung |
|---|---|
| `url`, `charset` | CSV-Adresse; Zeichensatz, wenn nicht UTF-8 (MV: `ISO-8859-1`) |
| `row` | Spalte → Wert, das die Landeszeile findet. Leerer Wert = leere Zelle (SA: `Wahllokal: ""` = Urne+Brief) |
| `validVotes` | Spalte mit den gültigen Zweitstimmen — Nenner der Prozente und Anker für die Kopfzeile (Titelzeilen davor stören nicht) |
| `partyPattern` **oder** `partiesAfter` | Regex über Spaltennamen, Gruppe 1 = Partei (SA: `^F\d+\.(.+)$`) — oder: alle Spalten hinter dieser sind Parteien (MV: `Gültige Stimmen`) |
| `ignore` | Spalten, die trotz Position keine Partei sind (`Einzelbewerber`) |
| `aliases` | Quellname → DAWUM-Kürzel, falls die eingebauten (`PartyAliases`) nicht reichen. Unbekannte Namen zählen zu "Sonstige" |
| `turnout` oder `voters`+`eligible` | fertige Wahlbeteiligung, oder sie wird gerechnet |
| `districtsTotal`, `districtsCounted` | Fortschritt; gleich = vorläufiges Ergebnis |
| `timestamp`, `timestampFormat` | Berechnungszeitpunkt aus der Datei; sonst `Last-Modified`, sonst Abrufzeit |
| `kind`, `completeWhen` | Reifegrad fest setzen, bzw. Spalte → Wert, ab dem die Auszählung als fertig gilt |
| `seats.*` | zweite Datei: "lang" (`partyColumn` + `seatsColumn`, SA) oder "breit" (`row` + `partiesAfter`, MV). Sitze werden nur beim vorläufigen Ergebnis übernommen |

Prüfen: `mvn test` (`CsvResultSourceTest` hat je ein Beispiel beider Formate),
dann die Datei mit `curl` holen und die Kopfzeile mit den Feldern vergleichen.
Parteinamen müssen nach `PartyAliases` auf ein DAWUM-Kürzel passen
("Die Linke" → "Linke", "GRÜNE" → "Grüne", "FREIE WÄHLER" → "Freie Wähler"
sind eingebaut).

### API

```bash
# Lesen (offen)
curl -s https://fherrmann.com/wahlen/api/parliaments/sachsen-anhalt/wahlabend | python3 -m json.tool

# Eintragen (Token). Uhrzeit HH:mm: ab 18 Uhr der Wahltag, davor der Folgetag.
curl -s -X POST https://fherrmann.com/wahlen/api/wahlabend/sachsen-anhalt/2026-09-06/reports \
  -H "Authorization: Bearer $WAHLABEND_TOKEN" -H "Content-Type: application/json" \
  -d '{"kind":"HOCHRECHNUNG","reportedAt":"18:28","source":"ARD / infratest dimap",
       "results":{"CDU":18.5,"AfD":44.5,"Linke":9.4,"SPD":8.2,"FDP":2.1,"Grüne":8.9,"BSW":5.0},
       "seats":{"CDU":16,"AfD":39,"Linke":8,"SPD":7,"Grüne":8,"BSW":5}}'

# Löschen
curl -s -X DELETE https://fherrmann.com/wahlen/api/wahlabend/reports/<id> -H "Authorization: Bearer $WAHLABEND_TOKEN"

# Kurzform
deploy/wahlabend.sh sachsen-anhalt 2026-09-06 PROGNOSE 18:00 "ZDF / Forschungsgruppe Wahlen" \
  "CDU=18.5 AfD=44.0 Linke=9.0 SPD=9.0 FDP=2.5 Grüne=8.5 BSW=4.8"
```

### Wenn am Wahlabend etwas klemmt

| Symptom | Erster Griff |
|---|---|
| Block zeigt "Abruf gestört: …" | `docker compose logs --tail=80 app \| grep Wahlabend`; CSV-URL im Browser öffnen. Kopfzeile geändert? → `live:`-Block anpassen. Bis dahin von Hand eintragen |
| Automatik sagt "Auszählungsstand", obwohl alles ausgezählt ist | Quelle ohne Fortschrittsspalte: `kind: VORLAEUFIG` setzen, deployen |
| Falscher Stand eingetragen | Formular → "löschen" in der Liste unten, oder `DELETE` per API |
| Formular meldet "nicht konfiguriert" | `WAHLABEND_TOKEN` fehlt in `.env` → `setup-sonntagsfrage.sh` oder von Hand, dann `docker compose up -d` |
| Block bleibt Wochen später "live" | Das ist Absicht — bis das amtliche Ergebnis in `elections.yaml` steht (Abschnitt 8) |
| Prozentsumme abgelehnt | Erwartet werden 95–101; Kürzel prüfen (Fehlermeldung listet die bekannten) |

### Datenmodell (V2)

`election_report` ist ein Stand (Art, Zeitpunkt, Quelle, Beteiligung, Notiz,
Fingerabdruck); seine Parteizeilen liegen in `election_result` mit gesetztem
`report_id`. Amtliche Endergebnisse bleiben, wie in V1, Zeilen ohne
`report_id` aus `elections.yaml`. `ResultKind` kennt zusätzlich
`AUSZAEHLUNG`. Solange ein amtliches Ergebnis fehlt, vertritt der jüngste
Stand es in der API (`lastElection.results`, mit `resultKind`) — so haben die
Balken der Sonntagsfrage schon am Montag einen Referenzstrich.

---

## 10. Bekannte Fallstricke

### ⚠️ Snap-Docker verbietet `/opt`-Bind-Mounts
Siehe oben. Wenn Container-DNS oder Port-Forwarding plötzlich kaputt sind
(Symptom aus dem CSVExporter-Runbook): erst
`sudo systemctl restart snap.docker.dockerd.service`.

### ⚠️ Spring Boot 4 ≠ Spring Boot 3 — drei Stolpersteine
Beim Bau dieses Projekts jeweils erst nach einem Fehlstart gefunden:

1. **Flyway läuft nicht** ohne die Abhängigkeit
   `org.springframework.boot:spring-boot-flyway`. `flyway-core` allein
   bringt keine Autokonfiguration mehr mit; das Symptom ist
   `Schema validation: missing table [...]`.
2. **`RestClient.Builder` wird nicht autokonfiguriert** (steckt im Modul
   `spring-boot-restclient`). Dieses Projekt baut den Client bewusst selbst
   über `RestClient.builder()` — es braucht ohnehin eigene Timeouts.
   Ebenso sind `ClientHttpRequestFactorySettings`/`-Builder` nicht mehr in
   `org.springframework.http.client`; hier wird `JdkClientHttpRequestFactory`
   direkt verwendet.
3. **Jackson 3 ist der Standard** (`tools.jackson.*`, nicht
   `com.fasterxml.jackson.databind`). Das YAML-Modul muss aus derselben
   Generation kommen (`tools.jackson.dataformat:jackson-dataformat-yaml`),
   sonst liegen zwei Jackson-Versionen parallel im Klassenpfad und der
   `ObjectMapper`-Bean lässt sich nicht injizieren. Die **Annotationen**
   bleiben `com.fasterxml.jackson.annotation.*` — das ist kein Fehler.

### ⚠️ Das JRE-Image bringt weder `curl` noch `wget` mit
`eclipse-temurin:25-jre` hat keins von beidem. Ein Healthcheck, der eines
davon aufruft, scheitert dauerhaft mit `executable file not found in $PATH` —
der Container bleibt für immer auf `health: starting` bzw. `unhealthy`, obwohl
die App tadellos läuft. Deshalb installiert das Dockerfile `curl` nach. Wer
den Healthcheck ändert: vorher `docker exec wahlen-app command -v <tool>`.

### ⚠️ Postgres 18: Volume gehört an `/var/lib/postgresql`
Nicht an `/var/lib/postgresql/data`. Die 18er-Images legen die Daten in ein
versionsspezifisches Unterverzeichnis und **verweigern den Start**, wenn direkt
auf `.../data` gemountet wird — mit einer langen Meldung über `pg_upgrade`, die
so klingt, als sei ein Upgrade schiefgegangen. Ist sie nicht; es ist der
falsche Mountpunkt. Fällt bei einem `docker run` ohne Volume nicht auf.

### ⚠️ `orphanRemoval` + Unique-Index: erst flushen, dann neu anlegen
`ReferenceDataLoader` löscht die alten Wahlergebnisse und legt sie neu an.
Ohne `saveAndFlush()` dazwischen ordnet Hibernate die INSERTs vor die DELETEs
und läuft in `uq_election_result_final`. Symptom: der **erste** Start geht
gut, der **zweite** wirft `duplicate key value violates unique constraint`.

### ⚠️ Glättungsbreite ist nicht konstant
σ passt sich der Umfragedichte an (`TrendCalculator.adaptiveSigma`): Median
des Abstands zwischen Umfragen × 1,5, gedeckelt bei 45 Tagen. Der Bundestag
bleibt bei 10 Tagen, dünn befragte Länder gehen hoch — sonst zerfällt die
Kurve dort in Striche. Die Kopfzahlen ("Aktueller Stand") nutzen bewusst ein
eigenes, kurzes Fenster von 120 Tagen, damit sie die jüngste Bewegung nicht
wegglätten. Wer hier schraubt: beide Stellen prüfen, `sigmaDays` steht im
API-Feld `trend.sigmaDays` und wird im UI ausgewiesen.

### ⚠️ Nach Änderungen am nginx-Snippet: Setup erneut laufen lassen
`deploy/nginx-wahlen.conf` liegt im Repo, aktiv ist aber die Kopie unter
`/etc/nginx/snippets/wahlen.conf`. Ein `git pull` allein ändert daran nichts.
Übernehmen mit `sudo ~/services/sonntagsfrage/deploy/setup-sonntagsfrage.sh` —
das Skript ist idempotent, kopiert das Snippet neu und lädt nginx.

Ob der Live-Stand hinterherhinkt, sieht man ohne sudo:

```bash
diff ~/services/sonntagsfrage/deploy/nginx-wahlen.conf /etc/nginx/snippets/wahlen.conf
curl -sI https://fherrmann.com/wahlen/js/app.js | grep -i cache-control   # muss no-cache liefern
```

`update-sonntagsfrage.sh` macht den Vergleich seit 2026-09-12 nach jedem Deploy
selbst und warnt laut. Anlass: das Live-Snippet stand noch auf `expires 1h`,
obwohl das Repo längst `no-cache` hatte — nach dem Deploy der
Koalitionsschalter traf eine Stunde lang neues HTML auf altes JavaScript, die
Schalter wirkten kaputt, bis der Browser neu lud.

Die statischen Dateien stehen bewusst auf `no-cache` (immer revalidieren,
in der Praxis 304 ohne Daten). Ein festes `max-age` wäre hier falsch: die
Dateinamen tragen keine Versionskennung, weil `app.js` das Modul `chart.js`
als statisches ES-Modul importiert und sich das nicht automatisch umschreiben
lässt. Mit `max-age` trifft nach jedem Deploy bis zu eine Stunde lang neues
HTML auf altes JavaScript.

### ⚠️ Lücken in der Kurve sind echt — sie werden überbrückt, nicht gefüllt
Wo monatelang gar nicht befragt wurde (Bremen: 3 Umfragen im Jahr, Saarland:
8 im Fenster), liefert die Glättung bewusst keinen Wert. Die API gibt dort
`null` zurück. Das Chart zieht darüber eine **dünne gestrichelte Brücke** und
blendet den Hinweis `.gap-note` ein — der Verlauf bleibt lesbar, und man sieht
trotzdem, wo interpoliert statt gemessen wurde.

Ebenso: der **Endpunkt einer Serie sitzt am letzten Datenpunkt**, nicht am
rechten Rand. Beim Saarland endet die Kurve dadurch sichtbar im Februar,
obwohl die Achse bis heute läuft. Das ist Absicht — es macht veraltete
Datenstände sichtbar, statt Aktualität vorzutäuschen. Wer den Endpunkt wieder
an den Rand legt, baut genau diese Täuschung ein.

### ⚠️ Serien mit Löchern
Parteien, die in weniger als 30 % der Umfragen eines Zeitraums abgefragt
werden (typisch: Freie Wähler beim Bundestag), fliegen aus dem Chart —
sonst wird die Linie zur Strichkette. In den Tabellen bleiben sie.
Konstante: `ParliamentViewService.MIN_COVERAGE`.

### ⚠️ Kein passwortloses sudo für `flexii`
Alles, was Root braucht (nginx), läuft ausschließlich über
`deploy/setup-sonntagsfrage.sh` und muss von Felix am Terminal gestartet werden.
Der Alltag (`update-sonntagsfrage.sh`) kommt ohne sudo aus.

---

## 11. Wenn etwas kaputt ist

| Symptom | Erster Griff |
|---|---|
| 502 auf `/wahlen` | `docker compose ps` — läuft `wahlen-app`? `docker compose logs --tail=100 app` |
| Statusboard-Karte rot ("Fehler"), Import läuft aber | Bis 2026-09-07 blieb ein alter `last_error` stehen, bis DAWUM das nächste Mal wirklich neue Daten hatte. Seitdem löscht jeder erfolgreiche Abruf ihn. Bleibt er, ist der Fehler echt: `/wahlen/daten` |
| Seite da, Daten alt | `/wahlen/daten` ansehen: dort stehen getrennt "zuletzt nachgefragt" und "zuletzt neue Daten übernommen". Liegen die weit auseinander, hat DAWUM einfach nichts Neues — das ist der Normalfall, kein Fehler |
| Statusboard-Karte sagt "Dienst nicht erreichbar", Seite läuft aber | CORS: steht die Statusboard-Herkunft in `wahlen.cors.allowed-origins`? Prüfen mit `curl -s -i -H "Origin: https://status.fherrmann.com" https://fherrmann.com/wahlen/api/meta \| grep -i access-control` |
| `Schema validation: missing table` | Flyway lief nicht, siehe Fallstrick oben |
| `duplicate key ... uq_election_result_final` | Fehlender Flush im ReferenceDataLoader, siehe oben |
| Wahlergebnis fehlt im Vergleich | Parteikürzel in `elections.yaml` passt nicht zur DAWUM-Schreibweise dieses Parlaments — Log nach "Partei ... unbekannt" durchsuchen |
| Container startet, DB nicht erreichbar | `docker compose logs db`; bei Netzwerksymptomen den Snap-Daemon neu starten |
| Alles kaputt, schnell zurück | `git -C ~/services/sonntagsfrage checkout <letzter guter Commit> && ~/scripts/update-sonntagsfrage.sh` |

Datenbank komplett neu aufbauen (verlustfrei — alles kommt aus DAWUM und dem
Repo, es gibt keine schreibenden Nutzer):

```bash
cd ~/services/sonntagsfrage
docker compose down
docker volume rm sonntagsfrage_wahlen-db-data
docker compose up -d --build     # importiert beim Start alles neu, dauert ~1 min
```

---

## 12. Offen / als Nächstes

- **Berlin 2026 (20.09.):** `live:`-Block anlegen, sobald die
  Landeswahlleitung ihr Datenformat zeigt (siehe Abschnitt 9). Bis dahin
  Handeingabe.
- **Wahlabend-Chart:** der Verlauf des Abends ist bisher nur eine Tabelle; ein
  Linienchart je Partei über die Uhrzeit wäre der nächste Schritt.
- Fehlerspannen im Chart darstellen.
