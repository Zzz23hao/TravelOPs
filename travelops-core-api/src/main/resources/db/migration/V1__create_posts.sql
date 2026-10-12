CREATE TABLE posts (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    content             TEXT         NOT NULL,
    destination_city    VARCHAR(100) NOT NULL,
    destination_country VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

-- Serves "posts for one destination, newest first" (?destination=...&sort=createdAt_desc).
-- Keyword search uses ILIKE '%...%', which a B-tree index cannot serve.
CREATE INDEX idx_posts_destination_city_created_at ON posts (destination_city, created_at);
