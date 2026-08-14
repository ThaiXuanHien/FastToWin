CREATE TABLE user_notifications (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_id VARCHAR(160) NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('FRIEND_REQUEST', 'ROOM_INVITATION', 'MISSION', 'ACHIEVEMENT', 'COSMETIC')),
    title VARCHAR(120) NOT NULL,
    message VARCHAR(300) NOT NULL,
    destination VARCHAR(16) NOT NULL CHECK (destination IN ('FRIENDS', 'PROFILE')),
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, notification_id)
);

CREATE INDEX user_notifications_visible_idx
    ON user_notifications(user_id, created_at DESC)
    WHERE dismissed_at IS NULL;

CREATE TABLE room_invitations (
    id UUID PRIMARY KEY,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL,
    inviter_display_name VARCHAR(32) NOT NULL,
    room_name VARCHAR(48) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (inviter_id, invitee_id)
);

CREATE INDEX room_invitations_invitee_expiry_idx ON room_invitations(invitee_id, expires_at DESC);
CREATE INDEX room_invitations_expiry_idx ON room_invitations(expires_at);
