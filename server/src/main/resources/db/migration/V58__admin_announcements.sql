-- Additive announcement facts. Recipient identities survive account hard deletion for audit totals.
ALTER TABLE user_notifications
    ALTER COLUMN title TYPE VARCHAR(160),
    ALTER COLUMN message TYPE VARCHAR(4000),
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'LEGACY'
        CHECK (source IN ('LEGACY', 'ADMIN_ANNOUNCEMENT')),
    ADD COLUMN announcement_context JSONB;
ALTER TABLE user_notifications ADD CONSTRAINT user_notifications_announcement_context_object
    CHECK (announcement_context IS NULL OR jsonb_typeof(announcement_context) = 'object');

CREATE TABLE admin_announcements (
    id UUID PRIMARY KEY,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    priority INTEGER NOT NULL CHECK (priority BETWEEN 0 AND 100),
    payload TEXT NOT NULL,
    CHECK (starts_at < ends_at)
);
CREATE INDEX admin_announcements_due_idx ON admin_announcements(status, starts_at, id);

CREATE TABLE admin_announcement_recipients (
    announcement_id UUID NOT NULL REFERENCES admin_announcements(id),
    user_id UUID NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (announcement_id, user_id)
);
CREATE INDEX admin_announcement_recipients_user_idx ON admin_announcement_recipients(user_id, announcement_id);

CREATE TABLE admin_announcement_replays (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL CHECK (request_id ~ '^[A-Za-z0-9_-]{1,128}$'),
    payload TEXT NOT NULL,
    PRIMARY KEY (actor_id, request_id)
);

CREATE TABLE admin_announcement_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    admin_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    event_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE admin_announcement_push_attempts (
    announcement_id UUID NOT NULL REFERENCES admin_announcements(id),
    user_id UUID NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (announcement_id, user_id)
);
