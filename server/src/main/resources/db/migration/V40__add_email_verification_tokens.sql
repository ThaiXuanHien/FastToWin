-- Accounts created before email verification was introduced remain usable.
UPDATE users
SET email_verified_at = COALESCE(email_verified_at, created_at)
WHERE account_type = 'REGISTERED' AND email_normalized IS NOT NULL;

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX email_verification_tokens_user_id_idx
    ON email_verification_tokens(user_id);

CREATE INDEX email_verification_tokens_user_code_idx
    ON email_verification_tokens(user_id, code_hash);

CREATE INDEX email_verification_tokens_expiry_idx
    ON email_verification_tokens(expires_at)
    WHERE used_at IS NULL;
