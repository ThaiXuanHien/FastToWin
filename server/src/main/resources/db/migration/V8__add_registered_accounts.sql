ALTER TABLE users
    ADD COLUMN email_normalized VARCHAR(254),
    ADD COLUMN password_hash TEXT,
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE UNIQUE INDEX users_email_normalized_unique_idx
    ON users (email_normalized)
    WHERE email_normalized IS NOT NULL AND status <> 'DELETED';

ALTER TABLE sessions
    ALTER COLUMN resume_token_hash DROP NOT NULL,
    ADD COLUMN access_token_hash CHAR(64),
    ADD COLUMN refresh_token_hash CHAR(64),
    ADD COLUMN access_expires_at TIMESTAMPTZ,
    ADD COLUMN refreshed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX sessions_access_token_hash_unique_idx
    ON sessions (access_token_hash)
    WHERE access_token_hash IS NOT NULL;

CREATE UNIQUE INDEX sessions_refresh_token_hash_unique_idx
    ON sessions (refresh_token_hash)
    WHERE refresh_token_hash IS NOT NULL;

CREATE INDEX sessions_active_refresh_idx
    ON sessions (refresh_token_hash, expires_at)
    WHERE refresh_token_hash IS NOT NULL AND revoked_at IS NULL;

ALTER TABLE users ADD CONSTRAINT registered_account_credentials_check CHECK (
    account_type <> 'REGISTERED'
    OR (email_normalized IS NOT NULL AND password_hash IS NOT NULL)
);

ALTER TABLE sessions ADD CONSTRAINT session_token_type_check CHECK (
    resume_token_hash IS NOT NULL
    OR (access_token_hash IS NOT NULL AND refresh_token_hash IS NOT NULL AND access_expires_at IS NOT NULL)
);
