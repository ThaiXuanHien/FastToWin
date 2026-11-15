-- Support provisioning allows gameplay without asserting ownership of the mailbox.
ALTER TABLE users ADD COLUMN support_email_verification_exempt BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE admin_player_creation_requests (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_id, request_id)
);
