CREATE TABLE player_referrals (
    invitee_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    inviter_id UUID REFERENCES users(id) ON DELETE SET NULL,
    completed_at TIMESTAMPTZ,
    inviter_rewarded BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (invitee_id <> inviter_id),
    CHECK (NOT inviter_rewarded OR completed_at IS NOT NULL)
);

CREATE INDEX player_referrals_inviter_idx ON player_referrals(inviter_id);

CREATE TABLE player_referral_monthly_rewards (
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reward_month DATE NOT NULL,
    reward_count INTEGER NOT NULL CHECK (reward_count > 0),
    PRIMARY KEY (inviter_id, reward_month)
);
