CREATE TABLE gift_codes (
    code VARCHAR(24) PRIMARY KEY,
    payload TEXT NOT NULL,
    claims INTEGER NOT NULL DEFAULT 0 CHECK (claims >= 0),
    stopped BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE gift_code_redemptions (
    code VARCHAR(24) NOT NULL REFERENCES gift_codes(code),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    redeemed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (code, user_id)
);

CREATE TABLE gift_code_admin_requests (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    PRIMARY KEY (actor_id, request_id)
);
