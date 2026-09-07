-- Wahlabend: Prognosen, Hochrechnungen, Auszaehlungsstaende und das vorlaeufige
-- Ergebnis einer Wahl. Jeder "Stand" ist ein election_report mit seinen
-- Parteizeilen in election_result (report_id gesetzt).
--
-- Amtliche Endergebnisse bleiben, wie in V1 vorgesehen, Zeilen ohne report_id
-- und ohne reported_at — sie kommen weiter aus elections.yaml.

CREATE TABLE election_report (
    id              BIGSERIAL    PRIMARY KEY,
    election_id     BIGINT       NOT NULL REFERENCES election (id) ON DELETE CASCADE,
    -- PROGNOSE | HOCHRECHNUNG | AUSZAEHLUNG | VORLAEUFIG
    kind            TEXT         NOT NULL,
    reported_at     TIMESTAMPTZ  NOT NULL,
    -- z.B. "ARD / infratest dimap", "ZDF / Forschungsgruppe Wahlen", "Landeswahlleiterin"
    source          TEXT         NOT NULL,
    source_url      TEXT,
    turnout_percent NUMERIC(5,2),
    -- Freitext, z.B. "Auszaehlungsstand 1.204 von 2.661 Wahlbezirken"
    note            TEXT,
    -- true: die Sitze stammen aus der Quelle; false: Projektion nach Sainte-Lague
    seats_official  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Inhaltshash fuer automatische Quellen: unveraenderte Abrufe erzeugen keinen neuen Stand
    fingerprint     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (election_id, source, kind, reported_at)
);

CREATE INDEX idx_election_report_election ON election_report (election_id, reported_at DESC);

ALTER TABLE election_result
    ADD COLUMN report_id BIGINT REFERENCES election_report (id) ON DELETE CASCADE;

CREATE INDEX idx_election_result_report ON election_result (report_id);
