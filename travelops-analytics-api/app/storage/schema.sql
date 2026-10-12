-- This service owns the analytics schema; core-api's Flyway owns public. Neither writes to the
-- other's tables, and Flyway never sees these tables in its own schema.
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.data_import_jobs (
    id              BIGSERIAL PRIMARY KEY,
    filename        TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('processing', 'completed', 'failed')),
    total_records   INTEGER,
    valid_records   INTEGER,
    invalid_records INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS analytics.airports (
    id           BIGSERIAL PRIMARY KEY,
    airport_code TEXT NOT NULL UNIQUE,
    city         TEXT NOT NULL,
    country      TEXT NOT NULL,
    -- The flight CSV carries no airport names, so this stays NULL for imported airports.
    name         TEXT
);

-- TEXT rather than VARCHAR(n): a long airline name must not fail the whole import transaction.
-- The CHECKs repeat the parser's rules so the table stays valid even if a bug lets a bad row through.
CREATE TABLE IF NOT EXISTS analytics.flights (
    id                  BIGSERIAL PRIMARY KEY,
    flight_id           TEXT           NOT NULL UNIQUE,
    airline             TEXT           NOT NULL,
    source_airport      TEXT           NOT NULL REFERENCES analytics.airports (airport_code),
    destination_airport TEXT           NOT NULL REFERENCES analytics.airports (airport_code),
    source_city         TEXT           NOT NULL,
    destination_city    TEXT           NOT NULL,
    source_country      TEXT           NOT NULL,
    destination_country TEXT           NOT NULL,
    distance_km         NUMERIC(10, 2) NOT NULL CHECK (distance_km > 0),
    duration_minutes    INTEGER        NOT NULL CHECK (duration_minutes > 0),
    price               NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    import_job_id       BIGINT         NOT NULL REFERENCES analytics.data_import_jobs (id),
    CHECK (source_airport <> destination_airport)
);
