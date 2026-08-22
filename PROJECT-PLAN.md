# Projektplan — `wahlen` (fherrmann.com/wahlen)

> Sonntagsfrage-Aggregator für Bundestag, alle 16 Landtage und das
> Europaparlament. Inspiriert von [wahlrecht.de/umfragen](https://www.wahlrecht.de/umfragen/)
> (Datenvollständigkeit) und [dkriesel.com/sonntagsfrage](https://www.dkriesel.com/sonntagsfrage)
> (Analyse-Tiefe) — aber mit interaktivem, modernem, dark-mode-fähigem UI
> statt Tabellenwüste bzw. statischer PNGs.
>
> Stand: 2026-08-22 · Autor: Claude, im Auftrag von Felix

---

## 1. Ziel & Abgrenzung

**Was es kann (v1):**

- Sonntagsfrage für **18 Parlamente**: Bundestag, 16 Landtage, Europaparlament
- **Featured-Ansicht** auf der Startseite, die sich automatisch an den
  Wahlkalender anpasst
- **Interaktiver Trendchart** (geglättete Verlaufskurve + Rohwerte je Institut)
- **Sitzverteilung** (Sainte-Laguë, mit Sperrklausel) inkl. **Koalitionsrechner**
- **Institutsvergleich** ("house effects" — welches Institut sieht wen
  systematisch stärker?)
- Vollständige, filterbare **Umfragetabelle** im wahlrecht.de-Stil, nur lesbar
- Vergleich gegen das **Ergebnis der letzten Wahl**

**Was v1 bewusst NICHT kann (aber vorbereitet ist):**

- Wahlabend-Modus: Prognosen (18:00), Hochrechnungen, vorläufiges amtliches
  Ergebnis von offiziellen Quellen. Datenmodell und UI-Slot sind da,
  die Adapter fehlen. → siehe [§8](#8-phase-2--wahlabend)

**Nicht-Ziele:** eigene Wahlprognosemodelle, Wahlkreis-Ebene, historische
Analysen vor 2017 (DAWUM-Datenbasis beginnt dort), Nutzerkonten.

---

## 2. Datenbeschaffung — Entscheidung und Begründung

Drei Optionen wurden geprüft:

| Option | Bewertung |
|---|---|
| **wahlrecht.de scrapen** | Kanonische Quelle, aber HTML-Tabellen ohne stabile Struktur, keine Lizenz zur Weiterverwendung, brüchig bei jedem Layout-Change. ❌ |
| **Institute einzeln** (INSA, Forsa, …) | Keine offenen APIs, teils Paywall, 10+ Integrationen. ❌ |
| **DAWUM API** | ✅ **Gewählt.** |

### Warum DAWUM

- `https://api.dawum.de/` liefert die **komplette** Datenbank als ein JSON
  (~1 MB, 3.910 Umfragen, Stand 2026-08-22) — alle Parlamente, alle Institute,
  zurück bis 2017.
- **Lizenz: ODC-ODbL** — ausdrücklich zur Weiterverwendung freigegeben.
  Auflage: Namensnennung + Link auf dawum.de und die Lizenz. Wird im Footer
  und auf `/wahlen/daten` umgesetzt.
- Betreiber pflegt dieselben Quellen wie wahlrecht.de, aktualisiert mehrfach
  täglich.
- **Billiges Polling:** `https://api.dawum.de/last_update.txt` ist 39 Bytes.
  Zusätzlich liefert der Haupt-Endpoint `ETag` + `Last-Modified`.

### Import-Strategie

```
alle 30 min (@Scheduled) + einmal beim Start:
  1. GET last_update.txt              (39 Bytes)
     └─ == gespeicherter Wert?  →  fertig, kein Traffic
  2. GET / mit If-None-Match: <etag>
     └─ 304?                    →  fertig
  3. Parsen → Upsert in Postgres (eine Transaktion)
  4. Caches invalidieren, Import-Zeitstempel schreiben
```

Höflichkeit: eigener `User-Agent`
(`wahlen.fherrmann.com/1.0 (+https://fherrmann.com/wahlen)`), Timeouts,
Retry mit Backoff, und bei Fehlern bleibt der letzte Stand in der DB stehen
(die Seite geht also nie „offline", nur der Datenstand altert — und das
wird im UI angezeigt).

### Was DAWUM NICHT liefert

Wahltermine und amtliche Wahlergebnisse. Die kommen aus **kuratierten
Referenzdaten im Repo** (`src/main/resources/reference/elections.yaml`),
gepflegt aus den offiziellen Quellen (Bundeswahlleiterin, Statistische
Landesämter/Landeswahlleiter). Das sind ~18 Datensätze, die sich nach einer
Wahl genau einmal ändern — Handpflege ist hier robuster als jede Automatik.
Jeder Eintrag trägt seine Quellen-URL.

---

## 3. Technologie-Entscheidungen

| Baustein | Wahl | Begründung |
|---|---|---|
| Sprache/Runtime | **Java 25 (LTS)** | Auf dem Mac vorhanden (Temurin 25.0.1); im Container `eclipse-temurin:25-jre` |
| Framework | **Spring Boot 4.1.1** | Aktuellster Stable-Release. Felix will Spring üben → aktuelle Version statt Legacy-3.x |
| Build | **Maven** | 3.9.12 lokal vorhanden, kein Gradle installiert |
| Persistenz | **PostgreSQL 18** + Spring Data JPA + **Flyway** | Der Standard-Spring-Stack, genau das was man üben will. Eigener Container, **nicht** der Firefly-Postgres auf `:5432` |
| Views | **Thymeleaf** (Shell, SEO, First Paint) + **JSON-API** + Vanilla-JS | Kein npm-Build, kein Node auf dem Server, kein Framework-Churn. Der interessante Teil bleibt Java |
| Charts | **Handgeschriebenes SVG** | Volle Kontrolle über die Optik — genau der Punkt, an dem wahlrecht.de und dkriesel.com „nicht schocken". Keine CDN-Abhängigkeit |
| Deployment | **Docker Compose** | Server hat **kein Java** und `flexii` hat **kein passwortloses sudo** → Container ist der Weg ohne Root |
| Reverse Proxy | nginx `location /wahlen` → `127.0.0.1:8090` | Gleiches Muster wie `/aspria/` in derselben Config |

### Warum Postgres und nicht „einfach in-memory"

Die ganze DAWUM-Datenbank passt in ~1 MB RAM — technisch bräuchte es keine
Datenbank. Trotzdem Postgres, weil:

1. Felix will **Spring Data JPA / Flyway / Transaktionen** üben. Das ist der
   realistische Stack.
2. Der Wahlabend-Modus (Phase 2) braucht echte Persistenz: Prognosen und
   Hochrechnungen kommen im Minutentakt rein und sollen historisiert werden.
3. Kaltstart ohne Netz: die Seite funktioniert auch, wenn DAWUM gerade weg ist.

---

## 4. Domänenmodell

```
Parliament          id, shortcut, name, electionName, slug, level(BUND|LAND|EU),
                    thresholdPercent, seatsTotal, sortOrder
Party               id, shortcut, name, colorLight, colorDark, sortOrder
Institute           id, name
Tasker              id, name              (Auftraggeber)
Method              id, name              (Online / Telefon / …)

Survey              id, parliament, institute, tasker, method,
                    publishedOn, periodStart, periodEnd, surveyedPersons
SurveyResult        survey, party, percent            (n:1 zu Survey)

Election            id, parliament, electionDate, status(SCHEDULED|HELD),
                    turnoutPercent, sourceUrl
ElectionResult      election, party, percent, seats,
                    kind(PROGNOSE|HOCHRECHNUNG|VORLAEUFIG|AMTLICH),   ← Phase 2
                    reportedAt, source                                 ← Phase 2

ImportState         key, value, updatedAt   (dawum_last_update, etag, last_run)
```

IDs von DAWUM werden **übernommen** (nicht neu vergeben) — dadurch ist der
Import ein simpler Upsert und Datensätze bleiben über Reimports stabil.

---

## 5. Fachlogik — die vier interessanten Rechnungen

### 5.1 Trendglättung (`TrendCalculator`)

Für jede Partei eine Kurve über die Zeit. Verfahren:
**Gauß-gewichteter gleitender Durchschnitt**, σ = 10 Tage (konfigurierbar).

```
wert(tag, partei) = Σ (gewicht_i · prozent_i) / Σ gewicht_i
gewicht_i = exp(−0.5 · ((tag − umfragedatum_i) / σ)²)
```

Bewusst kein LOESS: Gauß-Kernel ist in einem Satz erklärbar, hat keine
Randartefakte durch lokale Regression und ist O(n·m) trivial zu
implementieren und zu testen. Die Methode wird auf `/wahlen/daten`
offengelegt — Nachvollziehbarkeit ist bei Wahlumfragen Pflicht.

Zusätzlich: Umfragen werden auf die **Mitte des Erhebungszeitraums** datiert,
nicht auf das Veröffentlichungsdatum (das ist die methodisch saubere Variante
und ein Punkt, an dem viele Aufbereitungen schludern).

### 5.2 Institutseffekte (`HouseEffectCalculator`)

Für jedes Institut und jede Partei: mittlere Abweichung der eigenen Umfragen
vom geglätteten Konsens am selben Tag, über die letzten 24 Monate.

> „INSA sieht die AfD im Schnitt +1,8 Punkte über dem Konsens."

Das ist die Kernaussage von Kriesels Aufbereitung („nicht jede Umfrage ist
eine neue Wahrheit") — hier als Zahl statt als Bauchgefühl.

### 5.3 Sitzverteilung (`SeatCalculator`)

1. Sperrklausel anwenden (Default 5 %, pro Parlament konfigurierbar;
   Sonderfall SSW in Schleswig-Holstein ist befreit)
2. **Sainte-Laguë/Schepers** auf die Parlamentsgröße
3. Ausgabe als Halbkreis-Diagramm („Sitzbogen")

Klar als **vereinfachte Projektion** gekennzeichnet: Überhang-/Ausgleichs-
mandate, Grundmandatsklausel und Direktmandate werden nicht modelliert.

### 5.4 Koalitionsrechner (`CoalitionFinder`)

Alle Kombinationen aus den Parlamentsparteien, die eine Mehrheit erreichen,
sortiert nach Größe. Bekannte Konstellationen bekommen ihren Namen
(Große Koalition, Ampel, Jamaika, Kenia, Deutschland, Schwarz-Grün,
Schwarz-Rot, Rot-Rot-Grün …), hergeleitet aus den Parteifarben.
Neutral: es wird **nicht** gefiltert, wer mit wem „will" — nur, was
rechnerisch geht. Ausgeschlossene Konstellationen bewertet die Seite nicht.

### 5.5 Featured-Auswahl (`FeaturedService`)

```
1. Wahl in den nächsten 21 Tagen?      → diese (die nächstliegende)
2. Wahl in den letzten 7 Tagen?        → diese
3. sonst                               → Bundestag
```

Beide Fenster sind konfigurierbar (`wahlen.featured.*`).
Aktueller Stand (2026-08-22): Sachsen-Anhalt wählt am **06.09.2026**,
also in 15 Tagen → Startseite featured Sachsen-Anhalt. Am 20.09. folgen
Berlin und Mecklenburg-Vorpommern (Fall „zwei Wahlen am selben Tag" ist
abgedeckt: beide werden gezeigt, gefeatured wird die mit den meisten
Wahlberechtigten).

---

## 6. HTTP-Oberfläche

### Seiten (Thymeleaf)

| Route | Inhalt |
|---|---|
| `/wahlen/` | Featured-Hero (großer Trendchart + aktuelle Umfragen + Sitzbogen), Wahlkalender, Grid aller 18 Parlamente mit Sparklines |
| `/wahlen/{slug}` | Detailseite: Chart mit Zeitraum-/Partei-Steuerung, Sitzverteilung, Koalitionen, Institutsvergleich, letzte Umfragen |
| `/wahlen/{slug}/umfragen` | Vollständige Tabelle, filterbar nach Institut/Zeitraum |
| `/wahlen/daten` | Quellen, Lizenz/ODbL-Attribution, Methodik der Glättung, Import-Status |

Slugs: `bundestag`, `bayern`, `sachsen-anhalt`, `europaparlament`, …

### JSON-API

| Endpoint | Zweck |
|---|---|
| `GET /wahlen/api/parliaments` | Übersicht + nächste/letzte Wahl + neuestes Umfragedatum |
| `GET /wahlen/api/parliaments/{slug}` | Alles für die Detailseite in einem Request |
| `GET /wahlen/api/parliaments/{slug}/trend?from&to&sigma` | geglättete Serien + Rohpunkte |
| `GET /wahlen/api/parliaments/{slug}/surveys?from&to&institute` | Rohdaten |
| `GET /wahlen/api/parliaments/{slug}/institutes` | Institutseffekte |
| `GET /wahlen/api/parliaments/{slug}/seats` | Sitzverteilung + Koalitionen |
| `GET /wahlen/api/featured` | was gerade gefeatured wird und warum |
| `GET /wahlen/api/meta` | Importstand, DAWUM-Zeitstempel, Lizenz |
| `GET /wahlen/actuator/health` | für status.fherrmann.com |

Die API ist bewusst öffentlich und dokumentiert — sie ist selbst ein
Weiterverwendungs-Angebot (ODbL: abgeleitete Datenbanken müssen ebenfalls
ODbL sein, steht so auf `/wahlen/daten`).

---

## 7. UI-Konzept

Design-Sprache knüpft an fherrmann.com an (gleiche CSS-Variablen:
`--bg`, `--card-bg`, `--text`, `--muted`, `--border`, `--accent`,
`color-scheme: light dark`, System-Font-Stack, 14px-Radius-Karten) —
aber mit deutlich mehr Substanz auf der Fläche.

**Was es besser macht als die Vorbilder:**

| Problem beim Vorbild | Lösung hier |
|---|---|
| wahlrecht.de: Tabellen ohne Verlauf, man muss selbst rechnen | Trendchart zuerst, Tabelle darunter |
| wahlrecht.de: kein Dark Mode, nicht mobil bedienbar | Mobile-first, Dark Mode automatisch |
| dkriesel.com: statische PNGs, kein Hover, kein Zoom | Interaktives SVG: Crosshair-Tooltip mit allen Parteien zum Datum, Zeitraum-Umschaltung, Parteien ein-/ausblendbar |
| Beide: nur Bundestag prominent | Alle 18 Parlamente gleichwertig, Featured wechselt automatisch |
| Beide: „was heißt das für Mehrheiten?" bleibt offen | Sitzbogen + Koalitionsrechner direkt daneben |

**Parteifarben:** kuratierte Palette mit je einem Light- und einem
Dark-Mode-Wert (Schwarz für CDU/CSU und Gelb für FDP brauchen im Dark Mode
zwingend eigene Werte, sonst verschwinden sie). Exotische Kleinparteien
bekommen eine deterministisch aus dem Namen abgeleitete Farbe.
Kontrast wird gegen beide Hintergründe geprüft; Serien sind zusätzlich
über direkte Beschriftung am Kurvenende identifizierbar, nicht nur über
Farbe.

---

## 8. Phase 2 — Wahlabend

Nicht in v1 implementiert, aber das Modell ist so gebaut, dass es rein
additiv wird:

- `ElectionResult.kind` unterscheidet schon jetzt
  `PROGNOSE | HOCHRECHNUNG | VORLAEUFIG | AMTLICH`, mit `reportedAt` und
  `source` → mehrere Stände pro Wahl sind historisierbar, der Verlauf des
  Abends wird darstellbar.
- Geplantes Interface `ResultSource` mit einer Implementierung je
  Landeswahlleiter (die Wahlabend-Endpoints unterscheiden sich pro Land
  erheblich; Sachsen-Anhalt, Berlin und MV sind die nächsten drei und
  damit die ersten Kandidaten).
- Manueller Fallback: geschützter POST-Endpoint, über den Felix am
  Wahlabend um 18:00 die ARD/ZDF-Prognose eintippen kann, falls der
  automatische Adapter klemmt.
- UI: die Featured-Karte schaltet am Wahltag ab 18:00 in den
  Ergebnis-Modus (Ergebnis vs. letzte Umfrage vs. letzte Wahl),
  Auto-Refresh per Polling.

---

## 9. Betrieb

```
Server:  /home/flexii/services/sonntagsfrage          ← NICHT /opt (Snap-Docker!)
Compose: wahlen-app  (Spring Boot)  → 127.0.0.1:8090
         wahlen-db   (Postgres 18)  → nur intern, kein Port-Mapping
nginx:   location /wahlen  in /etc/nginx/sites-available/fherrmann.com
Update:  ~/scripts/update-sonntagsfrage.sh   (git pull + compose up -d --build)
Setup:   deploy/setup-sonntagsfrage.sh       (einmalig, braucht sudo)
```

⚠️ **Der Snap-Docker-Daemon auf dem Server erlaubt keine Bind-Mounts unter
`/opt`** (dokumentiert im CSVExporter-Runbook). Deshalb liegt alles unter
`/home/flexii/services/` — abweichend vom sonst üblichen
`/opt/<name>/app`-Muster. Postgres nutzt ein Named Volume, keinen Bind-Mount.

---

## 10. Umsetzungsreihenfolge

1. **Gerüst** — Maven-POM, Spring-Boot-App, Flyway-Schema, Docker-Compose,
   lokal lauffähig
2. **Import** — DAWUM-Client, DTOs, Upsert-Service, Scheduler, Import-State
3. **Referenzdaten** — `elections.yaml` + `parties.yaml`, Loader, Wahlkalender
4. **Fachlogik** — Trend, Institutseffekte, Sitze, Koalitionen, Featured
   (jeweils mit Unit-Tests, das ist der Teil, der Tests wirklich verdient)
5. **API** — REST-Controller + DTOs
6. **Frontend** — Thymeleaf-Shell, CSS, SVG-Chartmodul, Detailseiten
7. **Deploy** — Dockerfile, Compose, nginx-Snippet, Setup-/Update-Skripte
8. **Doku** — `AGENT-RUNBOOK.md`, `CLAUDE.md`, `README.md`,
   `SERVER-CONTEXT.md` nachziehen, Link-Karte auf fherrmann.com

## 11. Risiken

| Risiko | Umgang |
|---|---|
| DAWUM fällt aus / ändert Format | Letzter Stand bleibt in der DB, UI zeigt Datenalter; Import-Fehler werden geloggt und über `/actuator/health` sichtbar |
| Snap-Docker-Netzwerkprobleme (bekannt!) | Runbook-Eintrag: erst `sudo systemctl restart snap.docker.dockerd.service` |
| nginx-Config von fherrmann.com wird beim Setup beschädigt | Setup-Skript legt Backup an, fügt nur eine `include`-Zeile ein, `nginx -t` vor Reload, Rollback bei Fehler |
| Speicher auf dem Server (8,6 GB frei) | JVM auf 256 MB Heap begrenzt, Postgres-Container klein konfiguriert |
| Parteifarben/Sperrklausel-Sonderfälle | Alles in Referenzdaten, kein Code-Change nötig |

---

## 12. Was sich beim Bauen gegenüber diesem Plan geändert hat

Nachgetragen am 2026-08-22, damit dieses Dokument nicht das Falsche behauptet.

- **Repo heißt `sonntagsfrage`, nicht `wahlen`.** App, URL, Container und
  Datenbank behalten `wahlen`. Siehe Abschnitt 2 im `AGENT-RUNBOOK.md`.
- **Die Glättungsbreite ist nicht konstant.** σ = 10 Tage hätte für dünn
  befragte Landtage nicht gereicht — die Kurve zerfiel dort in Striche.
  Jetzt: Median des Abstands zwischen Umfragen × 1,5, gedeckelt bei 45 Tagen
  (`TrendCalculator.adaptiveSigma`). Der Bundestag bleibt bei 10.
  Die Kopfzahlen ("Aktueller Stand") nutzen ein eigenes, kurzes Fenster von
  120 Tagen, sonst würden sie die jüngste Bewegung wegglätten.
- **Serien mit Lücken fliegen aus dem Chart.** Parteien, die in weniger als
  30 % der Umfragen eines Zeitraums überhaupt abgefragt werden (typisch: Freie
  Wähler beim Bundestag), erschienen als Strichkette. In den Tabellen bleiben
  sie (`ParliamentViewService.MIN_COVERAGE`).
- **Der Sitzbogen sortiert nach politischem Spektrum**, nicht nach
  Fraktionsgröße — dafür gibt es `spectrum` in `reference/parties.yaml`. Nach
  Größe sortiert sah der Bogen für jeden, der Sitzbögen kennt, schlicht falsch
  aus.
- **Jackson 3 statt Jackson 2.** Spring Boot 4 nutzt `tools.jackson.*`;
  das YAML-Modul musste aus derselben Generation kommen. Siehe die
  Spring-Boot-4-Fallstricke im Runbook.
- **Parteifarben fallen absichtlich durch die Palettenprüfung.** Gemessen:
  SPD-Rot gegen Grünen-Grün liegt bei ΔE 3,7 im Deutan-Modell (Zielwert wäre
  ≥ 8). Semantisch vorgegebene Farben lassen sich nicht wegoptimieren, ohne
  die Darstellung falsch zu machen. Ausgeglichen wird das durch
  Sekundärkodierung: Direktbeschriftung am Kurvenende, Legende mit Text,
  Kürzel im Tooltip, Tabellenansicht, Strichmuster-Schalter. Nur dort, wo
  Freiheit bestand (BSW gegen Linke im Dark Mode), wurde nachgesteuert.
- **Postgres 18 will das Volume an `/var/lib/postgresql`**, nicht an
  `.../data` — erst beim Deploy auf dem Server aufgefallen.
- **`eclipse-temurin:25-jre` hat weder `curl` noch `wget`**, der
  Container-Healthcheck lief deshalb zunächst ins Leere.
