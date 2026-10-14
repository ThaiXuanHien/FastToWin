ALTER TABLE profiles ADD COLUMN avatar_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE avatar_objects (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE,
    content_type TEXT NOT NULL CHECK (content_type = 'image/jpeg'),
    byte_size INTEGER NOT NULL CHECK (byte_size BETWEEN 1 AND 46080),
    sha256 CHAR(64) NOT NULL,
    width INTEGER NOT NULL CHECK (width BETWEEN 1 AND 512),
    height INTEGER NOT NULL CHECK (height BETWEEN 1 AND 512),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Deliberately no user FK: intents and deletions must survive account cascade and restart.
CREATE TABLE avatar_cleanup (
    object_key TEXT PRIMARY KEY,
    state TEXT NOT NULL CHECK (state IN ('pending', 'deleting', 'deleted', 'failed')),
    available_at TIMESTAMPTZ NOT NULL,
    intent_expires_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error_code TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX avatar_cleanup_due_idx ON avatar_cleanup(available_at)
    WHERE state IN ('pending', 'deleting');

-- User deletion locks the same parent row as avatar CAS before cascading metadata.
CREATE FUNCTION enqueue_deleted_user_avatar() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO avatar_cleanup(object_key, state, available_at)
        SELECT object_key, 'pending', NOW() FROM avatar_objects WHERE user_id = OLD.id
    ON CONFLICT (object_key) DO UPDATE
        SET state = 'pending', available_at = NOW(), intent_expires_at = NULL,
            attempts = 0, last_error_code = NULL, updated_at = NOW();
    RETURN OLD;
END;
$$;
CREATE TRIGGER users_enqueue_avatar_before_delete BEFORE DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION enqueue_deleted_user_avatar();
