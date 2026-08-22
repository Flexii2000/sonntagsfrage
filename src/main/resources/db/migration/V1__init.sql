-- Stammdaten aus der DAWUM-API (IDs werden 1:1 uebernommen, damit der
-- Import ein simpler Upsert bleibt und Datensaetze reimport-stabil sind).

CREATE TABLE parliament (
    id                INTEGER      PRIMARY KEY,
    slug              TEXT         NOT NULL UNIQUE,
    shortcut          TEXT         NOT NULL,
    name              TEXT         NOT NULL,
    election_name     TEXT         NOT NULL,
    level             TEXT         NOT NULL,
    threshold_percent NUMERIC(4,2) NOT NULL DEFAULT 5.0,
    seats_total       INTEGER,
    -- Parteien, die von der Sperrklausel befreit sind (Komma-separierte IDs).
    -- Praxisfall: der SSW in Schleswig-Holstein als Minderheitenpartei.
    threshold_exempt  TEXT,
    sort_order        INTEGER      NOT NULL DEFAULT 100
);

CREATE TABLE party (
    id          INTEGER PRIMARY KEY,
    shortcut    TEXT    NOT NULL,
    name        TEXT    NOT NULL,
    color_light TEXT,
    color_dark  TEXT,
    -- Position von links nach rechts fuer den Sitzbogen (Konvention, keine Wertung).
    spectrum    INTEGER NOT NULL DEFAULT 99,
    sort_order  INTEGER NOT NULL DEFAULT 100
);

CREATE TABLE institute (
    id   INTEGER PRIMARY KEY,
    name TEXT    NOT NULL
);

CREATE TABLE tasker (
    id   INTEGER PRIMARY KEY,
    name TEXT    NOT NULL
);

CREATE TABLE method (
    id   INTEGER PRIMARY KEY,
    name TEXT    NOT NULL
);

CREATE TABLE survey (
    id               INTEGER PRIMARY KEY,
    parliament_id    INTEGER NOT NULL REFERENCES parliament (id),
    institute_id     INTEGER REFERENCES institute (id),
    tasker_id        INTEGER REFERENCES tasker (id),
    method_id        INTEGER REFERENCES method (id),
    published_on     DATE    NOT NULL,
    period_start     DATE,
    period_end       DATE,
    surveyed_persons INTEGER,
    -- Fingerabdruck ueber alle fachlichen Felder inkl. Ergebnisse. Der Import
    -- vergleicht ihn und laesst unveraenderte Umfragen komplett in Ruhe.
    content_hash     TEXT
);

CREATE INDEX idx_survey_parliament_published ON survey (parliament_id, published_on DESC);
CREATE INDEX idx_survey_institute ON survey (institute_id);

CREATE TABLE survey_result (
    survey_id INTEGER      NOT NULL REFERENCES survey (id) ON DELETE CASCADE,
    party_id  INTEGER      NOT NULL REFERENCES party (id),
    percent   NUMERIC(5,2) NOT NULL,
    PRIMARY KEY (survey_id, party_id)
);

-- Wahltermine und Wahlergebnisse. Kommen NICHT aus DAWUM, sondern aus den
-- kuratierten Referenzdaten (src/main/resources/reference/elections.yaml).

CREATE TABLE election (
    id              BIGSERIAL PRIMARY KEY,
    parliament_id   INTEGER   NOT NULL REFERENCES parliament (id),
    election_date   DATE      NOT NULL,
    status          TEXT      NOT NULL,
    date_confirmed  BOOLEAN   NOT NULL DEFAULT TRUE,
    turnout_percent NUMERIC(5,2),
    seats_total     INTEGER,
    source_url      TEXT,
    UNIQUE (parliament_id, election_date)
);

CREATE INDEX idx_election_date ON election (election_date);

-- kind: AMTLICH | VORLAEUFIG | HOCHRECHNUNG | PROGNOSE
-- reported_at ist nur fuer den Wahlabend (Phase 2) gesetzt; Endergebnisse
-- haben NULL und sind damit pro (election, party, kind) eindeutig.
CREATE TABLE election_result (
    id          BIGSERIAL    PRIMARY KEY,
    election_id BIGINT       NOT NULL REFERENCES election (id) ON DELETE CASCADE,
    party_id    INTEGER      NOT NULL REFERENCES party (id),
    percent     NUMERIC(5,2) NOT NULL,
    seats       INTEGER,
    kind        TEXT         NOT NULL DEFAULT 'AMTLICH',
    reported_at TIMESTAMPTZ,
    source      TEXT
);

CREATE UNIQUE INDEX uq_election_result_final
    ON election_result (election_id, party_id, kind)
    WHERE reported_at IS NULL;

CREATE INDEX idx_election_result_lookup
    ON election_result (election_id, kind, reported_at DESC);

-- Kleiner Key-Value-Store fuer den Importzustand (DAWUM-Zeitstempel, ETag,
-- letzter erfolgreicher Lauf).
CREATE TABLE import_state (
    state_key   TEXT        PRIMARY KEY,
    state_value TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
