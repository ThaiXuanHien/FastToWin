ALTER TABLE player_stats
    ADD COLUMN play_credits BIGINT NOT NULL DEFAULT 0 CHECK (play_credits >= 0);

ALTER TABLE play_quota_match_consumptions
    ADD COLUMN used_play_credit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE wallet_transactions
    ADD COLUMN play_credit_delta INTEGER NOT NULL DEFAULT 0;

ALTER TABLE wallet_transactions DROP CONSTRAINT wallet_transaction_has_value;
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transaction_has_value CHECK (
    gold_delta <> 0 OR gems_delta <> 0 OR xp_delta <> 0 OR play_credit_delta <> 0
);
