CREATE TABLE reward_events (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL CHECK (version > 0),
    payload TEXT NOT NULL
);
CREATE TABLE reward_event_requests (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    safe_result TEXT NOT NULL,
    PRIMARY KEY(actor_id, request_id)
);
