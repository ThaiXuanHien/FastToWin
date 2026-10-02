CREATE TABLE admin_gift_campaigns (
    id UUID PRIMARY KEY,
    version INTEGER NOT NULL CHECK(version > 0),
    state VARCHAR(16) NOT NULL CHECK(state IN ('DRAFT','PUBLISHED','CANCELLED')),
    available_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at > available_at),
    payload TEXT NOT NULL
);
CREATE INDEX admin_gift_campaigns_due_idx ON admin_gift_campaigns(available_at,id) WHERE state='PUBLISHED';
-- Deliberately no users foreign key: a frozen audience retains a durable SKIPPED result after hard deletion.
-- DELIVERED rows are entitlements; PENDING rows are the durable delivery checkpoint.
CREATE TABLE admin_gift_recipients (
    campaign_id UUID NOT NULL REFERENCES admin_gift_campaigns(id),
    user_id UUID NOT NULL,
    delivery VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(delivery IN ('PENDING','DELIVERED','SKIPPED')),
    claimed_at TIMESTAMPTZ,
    PRIMARY KEY(campaign_id,user_id),
    CHECK(claimed_at IS NULL OR delivery='DELIVERED')
);
CREATE INDEX admin_gift_pending_idx ON admin_gift_recipients(campaign_id,user_id) WHERE delivery='PENDING';
CREATE INDEX admin_gift_mailbox_idx ON admin_gift_recipients(user_id,campaign_id) WHERE delivery='DELIVERED';
CREATE TABLE admin_gift_requests (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    safe_result TEXT NOT NULL,
    PRIMARY KEY(actor_id,request_id)
);
