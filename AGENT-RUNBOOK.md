# Runbook — `wahlen` (fherrmann.com/wahlen)

> Betriebshandbuch für Agenten und für Felix in sechs Monaten.
> Ergänzt `~/Server-Projects/SERVER-CONTEXT.md`, dupliziert es nicht.
>
> Stand: 2026-08-22

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
                 └── alle 30 min:  api.dawum.de
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

## 4. Alltag

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

## 5. Ersteinrichtung (einmalig)

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

## 6. Wie die Daten reinkommen

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

## 7. Wartung, die tatsächlich anfällt

### Nach jeder Wahl (der einzige regelmäßige Handgriff)

`src/main/resources/reference/elections.yaml` bearbeiten:

1. Den `SCHEDULED`-Eintrag der gelaufenen Wahl auf `status: HELD` setzen und
   `turnout`, `seats` sowie `results` mit dem amtlichen Endergebnis füllen.
2. Einen neuen `SCHEDULED`-Eintrag für den Folgetermin anlegen. Steht der
   noch nicht amtlich fest: bestes Schätzdatum plus `dateConfirmed: false` —
   dann taucht er im Kalender auf, kapert aber nicht die Startseite.
3. `source:` mitpflegen. Zahlen ohne Beleg kommen hier nicht rein.
4. Committen, pushen, `~/scripts/update-sonntagsfrage.sh`.

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

## 8. Bekannte Fallstricke

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

## 9. Wenn etwas kaputt ist

| Symptom | Erster Griff |
|---|---|
| 502 auf `/wahlen` | `docker compose ps` — läuft `wahlen-app`? `docker compose logs --tail=100 app` |
| Seite da, Daten alt | `/wahlen/daten` ansehen. Steht dort ein Fehler, ist DAWUM oder das Netz das Problem — der Stand bleibt gültig |
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

## 10. Offen / als Nächstes

- **Wahlabend-Modus.** Das Datenmodell trägt ihn schon:
  `election_result.kind` kennt `PROGNOSE | HOCHRECHNUNG | VORLAEUFIG |
  AMTLICH`, `reported_at` macht Zwischenstände historisierbar. Es fehlen die
  Adapter je Landeswahlleiter plus ein geschützter POST-Endpoint als
  manueller Notnagel für 18:00 Uhr. Nächste Kandidaten: Sachsen-Anhalt
  (06.09.2026), Berlin und Mecklenburg-Vorpommern (beide 20.09.2026).
- Karte auf `status.fherrmann.com` (der Healthcheck liegt schon unter
  `/wahlen/actuator/health`).
- Fehlerspannen im Chart darstellen.
