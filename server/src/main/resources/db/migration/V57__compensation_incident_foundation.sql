ALTER TABLE admin_gift_campaigns ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'ADMIN_GIFT'
    CHECK(source_type IN ('ADMIN_GIFT','COMPENSATION'));
CREATE INDEX admin_gift_campaigns_source_idx ON admin_gift_campaigns(source_type,id);
CREATE TABLE compensation_incidents (
    id UUID PRIMARY KEY,
    incident_key VARCHAR(128) NOT NULL UNIQUE CHECK(incident_key ~ '^[A-Z0-9][A-Z0-9_-]{0,127}$'),
    version INTEGER NOT NULL CHECK(version>0),
    responsible_admin_id UUID NOT NULL REFERENCES admin_accounts(id),
    payload TEXT NOT NULL
);
CREATE TABLE compensation_rounds (
    campaign_id UUID PRIMARY KEY REFERENCES admin_gift_campaigns(id),
    incident_id UUID NOT NULL REFERENCES compensation_incidents(id),
    round_number INTEGER NOT NULL CHECK(round_number>0),
    payload TEXT NOT NULL,
    UNIQUE(incident_id,round_number)
);
CREATE TABLE compensation_replays (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL CHECK(request_id ~ '^[A-Za-z0-9_-]{1,128}$'),
    fingerprint CHAR(64) NOT NULL,
    response_type VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    PRIMARY KEY(actor_id,request_id)
);
