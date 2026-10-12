-- NOT NULL without a default only succeeds on an empty posts table: posts created before
-- accounts existed have no owner to assign. v0 has no production data to preserve.
ALTER TABLE posts
    ADD COLUMN user_id BIGINT NOT NULL CONSTRAINT fk_posts_user REFERENCES users (id);
