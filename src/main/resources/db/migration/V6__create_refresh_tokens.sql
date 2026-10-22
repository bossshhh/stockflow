-- Long-lived refresh tokens. Only a SHA-256 hash of each token is stored: a database leak does not
-- leak usable tokens. All tokens issued from one login share a family_id; reuse of a rotated token
-- revokes the whole family (theft detection).
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    family_id  UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
