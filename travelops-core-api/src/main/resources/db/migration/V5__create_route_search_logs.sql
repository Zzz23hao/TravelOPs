CREATE TABLE route_search_logs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL CONSTRAINT fk_route_search_logs_user REFERENCES users (id),
    source_airport      VARCHAR(3)  NOT NULL,
    destination_airport VARCHAR(3)  NOT NULL,
    search_type         VARCHAR(20) NOT NULL,
    -- FOUND, NOT_FOUND, REJECTED or UNAVAILABLE: lets one GROUP BY show how often the route service fails.
    outcome             VARCHAR(20) NOT NULL,
    result_summary      TEXT        NOT NULL,
    latency_ms          INTEGER     NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL
);
