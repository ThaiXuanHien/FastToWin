-- One durable authoritative snapshot. Never seed this in memory in production.
CREATE TABLE runtime_maintenance (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    version BIGINT NOT NULL CHECK (version >= 0),
    state VARCHAR(20) NOT NULL CHECK (state IN ('ACTIVE','ANNOUNCING','DRAINING','MAINTENANCE')),
    payload TEXT NOT NULL
);
INSERT INTO runtime_maintenance(singleton,version,state,payload)
VALUES (TRUE,0,'ACTIVE','{"version":0,"state":"ACTIVE"}');

-- Separate from account-access and gift replay tables; payload is a safe admin DTO.
CREATE TABLE runtime_maintenance_requests (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    safe_result TEXT NOT NULL,
    PRIMARY KEY(actor_id,request_id)
);
