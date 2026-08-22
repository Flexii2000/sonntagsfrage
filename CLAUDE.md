# Arbeitsregeln für dieses Repo

Sonntagsfrage-Aggregator, erreichbar unter `fherrmann.com/wahlen`.
Spring Boot 4 + Postgres, ausgeliefert als Docker-Compose-Stack.

## Zuerst lesen

- **`AGENT-RUNBOOK.md`** — Betrieb, Deploy, bekannte Fallstricke. Bei allem,
  was den Server betrifft, dort zuerst nachsehen.
- `PROJECT-PLAN.md` — warum die Architektur so aussieht, wie sie aussieht.
- `~/Server-Projects/SERVER-CONTEXT.md` — allgemeiner Serverkontext.

## Harte Regeln

- **Referenzdaten sind die Wahrheit, nicht die Datenbank.** Wahltermine,
  Wahlergebnisse, Parteifarben, Sperrklauseln und Slugs stehen in
  `src/main/resources/reference/*.yaml`. Eine Korrektur ist eine
  YAML-Änderung plus Deploy — **niemals ein SQL-Update**. Der Loader läuft
  bei jedem Start und schreibt die Datenbank neu.
- **Jeder Eintrag in `elections.yaml` braucht eine `source`-URL.** Zahlen
  ohne Beleg kommen hier nicht rein.
- **Die Parteifarben nicht "verbessern".** Maßgeblich ist die Farbe, die sich
  die Partei selbst gibt (die Linke etwa `#BE3075`), nicht das Ergebnis einer
  Kontrastoptimierung. Dass Rot, Grün und Gelb dadurch dicht beieinander
  liegen, ist hingenommen; Farbe ist ohnehin nicht der einzige
  Identitätskanal (Direktbeschriftung, Legende mit Text, Tooltip,
  Tabellenansicht, Strichmuster-Schalter). Details in `reference/parties.yaml`.
- **Der Koalitionsrechner bewertet nicht.** Er zeigt, was arithmetisch geht,
  und filtert nicht danach, wer mit wem koalieren würde.
- **Die Sitzprojektion ist eine Näherung** und muss im UI auch so
  gekennzeichnet bleiben.
- **DAWUM höflich behandeln.** Fester User-Agent, `last_update.txt` vor dem
  Vollabruf, ETag mitschicken. Den Abrufrhythmus nicht ohne Grund erhöhen.
- **Die ODbL-Namensnennung im Footer nicht entfernen** — sie ist
  Lizenzbedingung, keine Höflichkeit.

## Entwickeln

```bash
colima start          # falls Docker lokal noch nicht läuft

docker run -d --name wahlen-db-dev \
  -e POSTGRES_DB=wahlen -e POSTGRES_USER=wahlen -e POSTGRES_PASSWORD=wahlen \
  -p 55432:5432 postgres:18-alpine

DB_PORT=55432 mvn spring-boot:run      # → http://localhost:8080/wahlen/
mvn test                               # Rechenlogik, braucht keine Datenbank
```

Tests decken die Rechnungen ab (Trend, Sitze, Koalitionen, Fingerabdruck) —
genau die Stellen, an denen ein Fehler still falsche Zahlen erzeugt. Neue
Fachlogik bitte dort mit abdecken.

## Schema

Flyway, `src/main/resources/db/migration/`. Hibernate steht auf `validate`
und legt nichts an. Änderungen als **neue** `V<n>__*.sql`; die V1 gilt als
ausgeliefert.
