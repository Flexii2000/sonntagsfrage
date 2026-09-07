# wahlen

Sonntagsfrage für den Bundestag, alle 16 Landtage und das Europaparlament —
mit Verlaufskurven, Sitzprojektion, Koalitionsrechner und Institutsvergleich.

Läuft unter **[fherrmann.com/wahlen](https://fherrmann.com/wahlen)**.

## Warum

[wahlrecht.de/umfragen](https://www.wahlrecht.de/umfragen/) hat die
vollständigsten Daten, aber nur Tabellen und kein Gefühl für den Verlauf.
[David Kriesels Aufbereitung](https://www.dkriesel.com/sonntagsfrage) hat die
richtige analytische Haltung — „nicht jede neue Umfrage ist eine neue
Wahrheit" —, liefert sie aber als statische PNGs. Dieses Projekt nimmt beides
und packt es in eine Oberfläche, die auf dem Handy funktioniert, einen Dark
Mode kennt und auf Hover verrät, was an einem bestimmten Tag Sache war.

## Was drin steckt

- **Verlauf** aller Parteien, Gauß-geglättet, mit einer Glättungsbreite, die
  sich an die Umfragedichte des jeweiligen Parlaments anpasst
- **Automatisch wechselnde Startseite**: 21 Tage vor bis 7 Tage nach einer
  Wahl steht diese vorne, sonst der Bundestag
- **Sitzprojektion** nach Sainte-Laguë inklusive Prozenthürde, als Sitzbogen
- **Koalitionsrechner** — alle rechnerischen Mehrheiten ohne überflüssige
  Partner, wertfrei
- **Institutsvergleich**: wie stark weicht ein Institut im Schnitt vom
  Konsens aller anderen ab (Leave-one-out gerechnet)
- **Wahlabend-Modus**: ab 18 Uhr am Wahltag Prognose, Hochrechnungen und der
  Stand der Landeswahlleitung im Minutentakt, mit Sitzen, Mehrheiten und dem
  Vergleich zu Vorwahl und Umfragen — bis das amtliche Ergebnis da ist, und
  danach als Archiv des Abends
- **Offene JSON-API** unter `/wahlen/api/`

## Datenquelle

Umfragen von **[DAWUM](https://dawum.de/)**, lizenziert unter der
[ODbL](https://opendatacommons.org/licenses/odbl/1-0/). Diese Aufbereitung
und ihre API stehen unter derselben Lizenz.

Wahltermine und amtliche Ergebnisse sind in
`src/main/resources/reference/elections.yaml` von Hand aus den amtlichen
Quellen gepflegt; jeder Eintrag trägt seine Beleg-URL.

## Technik

Spring Boot 4 · Java 25 · PostgreSQL 18 · Flyway · Thymeleaf · Docker Compose.
Charts sind handgeschriebenes SVG — keine Chart-Bibliothek, kein Build-Step
im Frontend, nichts wird von einem CDN nachgeladen.

## Lokal starten

```bash
docker run -d --name wahlen-db-dev \
  -e POSTGRES_DB=wahlen -e POSTGRES_USER=wahlen -e POSTGRES_PASSWORD=wahlen \
  -p 55432:5432 postgres:18-alpine

DB_PORT=55432 mvn spring-boot:run
# → http://localhost:8080/wahlen/

mvn test    # Rechenlogik, braucht keine Datenbank
```

Oder komplett in Containern:

```bash
cp .env.example .env    # Passwort setzen
docker compose up -d --build
```

## Weiterlesen

- `PROJECT-PLAN.md` — Architekturentscheidungen und ihre Begründung
- `AGENT-RUNBOOK.md` — Betrieb, Deploy, bekannte Fallstricke
- `/wahlen/daten` — Methodik, für Leser erklärt

## Kein Wahlomat, keine Prognose

Umfragen messen, wie Menschen heute antworten — nicht, wie sie am Wahltag
abstimmen. Der geglättete Verlauf macht Bewegungen sichtbar; er verlängert
sie nicht in die Zukunft.
