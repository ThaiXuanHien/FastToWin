-- ADM-04: TOTP second factor for admin accounts.
CREATE TABLE admin_mfa (
    admin_id UUID PRIMARY KEY REFERENCES admin_accounts(id),
    -- AES-256-GCM ciphertext bound to admin_id; the key lives outside the database.
    secret_ciphertext TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Highest accepted TOTP step; a code can never be accepted twice.
    last_used_step BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_mfa_recovery_codes (
    admin_id UUID NOT NULL REFERENCES admin_accounts(id),
    code_hash CHAR(64) NOT NULL,
    used_at TIMESTAMPTZ,
    PRIMARY KEY (admin_id, code_hash)
);

-- A password check issues a short-lived challenge; no session exists until the code verifies.
CREATE TABLE admin_mfa_challenges (
    token_hash CHAR(64) PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES admin_accounts(id),
    enrollment BOOLEAN NOT NULL,
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX admin_mfa_challenges_admin_idx ON admin_mfa_challenges(admin_id);
