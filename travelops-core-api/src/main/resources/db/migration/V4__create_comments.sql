CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    -- Deleting a post deletes its comments in the database, without loading them into Java first.
    post_id    BIGINT      NOT NULL CONSTRAINT fk_comments_post REFERENCES posts (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL CONSTRAINT fk_comments_user REFERENCES users (id),
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Postgres does not index foreign keys automatically. This serves "comments of one post, oldest first"
-- and the cascade delete above.
CREATE INDEX idx_comments_post_id_created_at ON comments (post_id, created_at);
