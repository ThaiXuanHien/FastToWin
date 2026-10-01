CREATE TABLE admin_accounts (
    id UUID PRIMARY KEY,
    email_normalized VARCHAR(254) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role VARCHAR(16) NOT NULL CHECK(role IN ('OWNER','OPERATOR','SUPPORT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE admin_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES admin_accounts(id),
    csrf_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX admin_sessions_account_idx ON admin_sessions(admin_id);
CREATE INDEX admin_sessions_expiry_idx ON admin_sessions(expires_at) WHERE NOT revoked;
CREATE TABLE admin_requests (
    actor_id UUID NOT NULL REFERENCES admin_accounts(id),
    request_id VARCHAR(128) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    safe_result TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(actor_id,request_id)
);
CREATE TABLE admin_audit (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES admin_accounts(id),
    action VARCHAR(64) NOT NULL,
    target_id UUID,
    request_id VARCHAR(128),
    reason VARCHAR(500),
    before_summary VARCHAR(500),
    after_summary VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX admin_audit_history_idx ON admin_audit(created_at DESC,id DESC);
CREATE INDEX admin_audit_target_idx ON admin_audit(target_id,created_at DESC);
