-- Clan chat: 30-day / 500-message history per clan, enforced by the server on write and by cleanup.
-- Messages of departed members stay (with the name they had); account deletion cascades them away.
CREATE TABLE clan_chat_messages (
    id UUID PRIMARY KEY,
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    kind VARCHAR(8) NOT NULL CHECK (kind IN ('USER', 'SYSTEM')),
    sender_id UUID REFERENCES users(id) ON DELETE CASCADE,
    sender_name VARCHAR(64) NOT NULL DEFAULT '',
    body VARCHAR(300) NOT NULL DEFAULT '',
    system_event VARCHAR(32),
    system_args TEXT NOT NULL DEFAULT '{}',
    created_at BIGINT NOT NULL,
    deleted_at BIGINT,
    CHECK ((kind = 'USER') = (sender_id IS NOT NULL AND system_event IS NULL))
);
CREATE INDEX clan_chat_messages_page ON clan_chat_messages (clan_id, created_at DESC, id DESC);
CREATE INDEX clan_chat_messages_expiry ON clan_chat_messages (created_at);

CREATE TABLE clan_chat_mutes (
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    muted_until BIGINT NOT NULL,
    muted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (clan_id, user_id)
);

-- Read position (unread badge on every device) and push throttle (one push per 30 minutes).
CREATE TABLE clan_chat_members (
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at BIGINT NOT NULL DEFAULT 0,
    last_push_at BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, user_id)
);

-- Uploaded logos live beside clans so logo writes never fire the clan race history triggers.
-- The row survives removal so the revision keeps increasing and cached images never reappear.
CREATE TABLE clan_logos (
    clan_id UUID PRIMARY KEY REFERENCES clans(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0,
    legacy_data TEXT,
    object_key TEXT UNIQUE,
    content_type TEXT CHECK (content_type IS NULL OR content_type = 'image/jpeg'),
    byte_size INTEGER CHECK (byte_size IS NULL OR byte_size BETWEEN 1 AND 46080),
    sha256 CHAR(64),
    width INTEGER CHECK (width IS NULL OR width BETWEEN 1 AND 512),
    height INTEGER CHECK (height IS NULL OR height BETWEEN 1 AND 512),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (object_key IS NULL OR (content_type IS NOT NULL AND byte_size IS NOT NULL AND sha256 IS NOT NULL
        AND width IS NOT NULL AND height IS NOT NULL))
);

CREATE TABLE clan_logo_changes (
    id BIGSERIAL PRIMARY KEY,
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    changed_at BIGINT NOT NULL
);
CREATE INDEX clan_logo_changes_window ON clan_logo_changes (clan_id, changed_at);

-- Dissolving a clan (or any cascade) queues its stored object for the shared avatar object cleaner.
CREATE FUNCTION enqueue_deleted_clan_logo() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.object_key IS NOT NULL THEN
        INSERT INTO avatar_cleanup(object_key, state, available_at) VALUES (OLD.object_key, 'pending', NOW())
        ON CONFLICT (object_key) DO UPDATE
            SET state = 'pending', available_at = NOW(), intent_expires_at = NULL,
                attempts = 0, last_error_code = NULL, updated_at = NOW();
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER clan_logos_enqueue_before_delete BEFORE DELETE ON clan_logos
    FOR EACH ROW EXECUTE FUNCTION enqueue_deleted_clan_logo();

ALTER TABLE ugc_reports DROP CONSTRAINT IF EXISTS ugc_reports_target_type_check;
ALTER TABLE ugc_reports ADD CONSTRAINT ugc_reports_target_type_check CHECK (target_type IN
    ('PLAYER_PROFILE', 'PLAYER_AVATAR', 'CLAN', 'ROOM', 'TOURNAMENT', 'CLAN_LOGO', 'CLAN_MESSAGE'));
