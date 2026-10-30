CREATE TABLE auth_refresh_recoveries (
    session_id UUID PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
    predecessor_hash TEXT NOT NULL UNIQUE CHECK (predecessor_hash ~ '^[0-9a-f]{64}$'),
    attempt_hash TEXT NOT NULL CHECK (attempt_hash ~ '^[0-9a-f]{64}$'),
    successor_refresh_hash TEXT NOT NULL CHECK (successor_refresh_hash ~ '^[0-9a-f]{64}$'),
    successor_access_hash TEXT NOT NULL CHECK (successor_access_hash ~ '^[0-9a-f]{64}$'),
    codec_version INTEGER NOT NULL CHECK (codec_version = 1),
    nonce BYTEA,
    ciphertext BYTEA,
    issued_at TIMESTAMPTZ NOT NULL,
    recovery_expires_at TIMESTAMPTZ NOT NULL,
    metadata_expires_at TIMESTAMPTZ NOT NULL,
    CHECK (recovery_expires_at = issued_at + INTERVAL '5 minutes'),
    CHECK (metadata_expires_at > recovery_expires_at),
    CHECK ((nonce IS NULL AND ciphertext IS NULL) OR
           (nonce IS NOT NULL AND ciphertext IS NOT NULL AND octet_length(nonce) = 12 AND octet_length(ciphertext) BETWEEN 16 AND 8192))
);
CREATE INDEX auth_refresh_recoveries_payload_expiry ON auth_refresh_recoveries(recovery_expires_at) WHERE ciphertext IS NOT NULL;
CREATE INDEX auth_refresh_recoveries_metadata_expiry ON auth_refresh_recoveries(metadata_expires_at);

-- Every writer, including moderation and legacy rotation, invalidates the previous generation.
-- The caller already holds the session row before touching the receipt.
CREATE FUNCTION invalidate_auth_refresh_recovery() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.revoked_at IS NOT NULL OR NEW.refresh_token_hash IS DISTINCT FROM OLD.refresh_token_hash
       OR NEW.access_token_hash IS DISTINCT FROM OLD.access_token_hash THEN
        DELETE FROM auth_refresh_recoveries WHERE session_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER invalidate_auth_refresh_recovery
    AFTER UPDATE OF revoked_at, refresh_token_hash, access_token_hash ON sessions
    FOR EACH ROW EXECUTE FUNCTION invalidate_auth_refresh_recovery();
