CREATE TABLE gold_exchange_requests (
    request_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    offer_id VARCHAR(32) NOT NULL,
    gems_spent INTEGER NOT NULL CHECK (gems_spent > 0),
    gold_granted INTEGER NOT NULL CHECK (gold_granted > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX gold_exchange_requests_user_history_idx
    ON gold_exchange_requests (user_id, created_at DESC);
