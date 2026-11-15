-- Immutable assignment facts survive live clan deletion and process restarts.
CREATE TABLE clan_race_pool_weeks (
 week_start DATE PRIMARY KEY REFERENCES clan_race_revisions(week_start),
 frozen_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE clan_race_pool_members (
 week_start DATE NOT NULL REFERENCES clan_race_pool_weeks(week_start),
 clan_id UUID NOT NULL REFERENCES clan_race_clans(clan_id),
 pool_id VARCHAR(64) NOT NULL,
 prior_score_units NUMERIC NOT NULL CHECK(prior_score_units>=0),
 active_members INT NOT NULL CHECK(active_members>=0),
 PRIMARY KEY(week_start,clan_id)
);
CREATE INDEX clan_race_pool_lookup ON clan_race_pool_members(week_start,pool_id);
CREATE TRIGGER clan_race_pool_member_immutable BEFORE UPDATE OR DELETE ON clan_race_pool_members
 FOR EACH ROW EXECUTE FUNCTION clan_race_immutable();
