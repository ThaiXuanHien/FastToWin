-- Retain settlement and entitlement facts independently of live users/clans.
CREATE TABLE clan_race_settlements (
 week_start DATE PRIMARY KEY REFERENCES clan_race_revisions(week_start),
 settled_at TIMESTAMPTZ NOT NULL, payload TEXT NOT NULL
);
CREATE TRIGGER clan_race_settlement_immutable BEFORE UPDATE OR DELETE ON clan_race_settlements
 FOR EACH ROW EXECUTE FUNCTION clan_race_immutable();
CREATE TABLE clan_race_settlement_work (
 week_start DATE PRIMARY KEY REFERENCES clan_race_revisions(week_start),
 state VARCHAR(16) NOT NULL CHECK(state IN ('FAILED','SETTLED')),
 attempts INTEGER NOT NULL CHECK(attempts>0), last_error_code VARCHAR(64), updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE clan_race_entitlements (
 id UUID PRIMARY KEY, week_start DATE NOT NULL REFERENCES clan_race_settlements(week_start),
 clan_id UUID NOT NULL REFERENCES clan_race_clans(clan_id), user_id UUID NOT NULL,
 tenure_id UUID NOT NULL REFERENCES clan_race_tenures(id), payload TEXT NOT NULL,
 UNIQUE(week_start,clan_id,user_id)
);
CREATE TRIGGER clan_race_entitlement_immutable BEFORE UPDATE OR DELETE ON clan_race_entitlements
 FOR EACH ROW EXECUTE FUNCTION clan_race_immutable();
-- The outbox row is inserted atomically with the immutable entitlement and settlement.
CREATE TABLE clan_race_outbox (
 entitlement_id UUID PRIMARY KEY REFERENCES clan_race_entitlements(id),
 state VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DELIVERED','ACCOUNT_UNAVAILABLE')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0), last_error_code VARCHAR(64),
 processed_at TIMESTAMPTZ, available_at TIMESTAMPTZ, expires_at TIMESTAMPTZ,
 CHECK((state='DELIVERED' AND available_at IS NOT NULL AND expires_at=available_at+interval '7 days')
    OR (state<>'DELIVERED' AND available_at IS NULL AND expires_at IS NULL))
);
CREATE INDEX clan_race_outbox_pending ON clan_race_outbox(entitlement_id) WHERE state='PENDING';
ALTER TABLE admin_gift_campaigns DROP CONSTRAINT admin_gift_campaigns_source_type_check;
ALTER TABLE admin_gift_campaigns ALTER COLUMN source_type TYPE VARCHAR(32);
ALTER TABLE admin_gift_campaigns ADD CONSTRAINT admin_gift_campaigns_source_type_check
 CHECK(source_type IN ('ADMIN_GIFT','COMPENSATION','CLAN_WEEKLY_RACE'));
