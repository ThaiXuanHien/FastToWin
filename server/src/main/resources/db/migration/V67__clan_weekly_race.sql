-- No live foreign keys: user/clan deletion must retain race identities and score facts.
CREATE TABLE clan_race_configs (
 week_start DATE PRIMARY KEY CHECK(extract(isodow from week_start)=1), version BIGINT NOT NULL CHECK(version>0),
 state VARCHAR(16) NOT NULL CHECK(state IN ('DRAFT','PUBLISHED','INACTIVE')), config TEXT NOT NULL, published_revision BIGINT
);
CREATE TABLE clan_race_revisions (week_start DATE PRIMARY KEY REFERENCES clan_race_configs(week_start), revision BIGINT NOT NULL, config TEXT NOT NULL);
CREATE FUNCTION clan_race_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Race facts are immutable'; END $$;
CREATE TRIGGER clan_race_revision_immutable BEFORE UPDATE OR DELETE ON clan_race_revisions FOR EACH ROW EXECUTE FUNCTION clan_race_immutable();
CREATE TABLE clan_race_admin_requests (actor_id UUID NOT NULL,request_id VARCHAR(128) NOT NULL,fingerprint TEXT NOT NULL,result TEXT NOT NULL,PRIMARY KEY(actor_id,request_id));
CREATE TABLE clan_race_clans (clan_id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL,deleted_at TIMESTAMPTZ);
CREATE TABLE clan_race_clan_versions (id BIGSERIAL PRIMARY KEY,clan_id UUID NOT NULL REFERENCES clan_race_clans(clan_id),name TEXT NOT NULL,logo_id TEXT,level INT NOT NULL,valid_from TIMESTAMPTZ NOT NULL,valid_until TIMESTAMPTZ);
CREATE INDEX clan_race_versions_idx ON clan_race_clan_versions(clan_id,valid_from);
CREATE TABLE clan_race_tenures (id UUID PRIMARY KEY,clan_id UUID NOT NULL REFERENCES clan_race_clans(clan_id),user_id UUID NOT NULL,joined_at TIMESTAMPTZ NOT NULL,ended_at TIMESTAMPTZ,exit_kind VARCHAR(32),cutover BOOLEAN NOT NULL DEFAULT FALSE);
CREATE UNIQUE INDEX clan_race_current_tenure ON clan_race_tenures(user_id) WHERE ended_at IS NULL;
CREATE INDEX clan_race_tenure_cutoff ON clan_race_tenures(clan_id,joined_at,ended_at);
ALTER TABLE mission_progress_events ADD COLUMN race_tenure_id UUID REFERENCES clan_race_tenures(id);
CREATE TABLE clan_race_events (
 id BIGSERIAL PRIMARY KEY,week_start DATE NOT NULL REFERENCES clan_race_revisions(week_start),source_kind VARCHAR(16) NOT NULL CHECK(source_kind IN ('DONATION','MISSION')),
 source_id VARCHAR(160) NOT NULL,user_id UUID NOT NULL,clan_id UUID NOT NULL REFERENCES clan_race_clans(clan_id),tenure_id UUID NOT NULL REFERENCES clan_race_tenures(id),
 occurred_at TIMESTAMPTZ NOT NULL,score_units NUMERIC(38,0) NOT NULL CHECK(score_units>0),UNIQUE(source_kind,source_id,user_id)
);
CREATE INDEX clan_race_events_week ON clan_race_events(week_start,clan_id,occurred_at);
CREATE TRIGGER clan_race_event_immutable BEFORE UPDATE OR DELETE ON clan_race_events FOR EACH ROW EXECUTE FUNCTION clan_race_immutable();
CREATE FUNCTION clan_race_gate() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock(7421092187355); RETURN NULL; END $$;
-- Statement triggers take the gate before SQL acquires any row locks, including cascades/direct SQL.
CREATE TRIGGER clan_race_clan_gate BEFORE INSERT OR UPDATE OR DELETE ON clans FOR EACH STATEMENT EXECUTE FUNCTION clan_race_gate();
CREATE TRIGGER clan_race_member_gate BEFORE INSERT OR UPDATE OR DELETE ON clan_members FOR EACH STATEMENT EXECUTE FUNCTION clan_race_gate();
CREATE TRIGGER clan_race_user_delete_gate BEFORE DELETE ON users FOR EACH STATEMENT EXECUTE FUNCTION clan_race_gate();
CREATE FUNCTION clan_race_time() RETURNS TIMESTAMPTZ LANGUAGE sql VOLATILE AS $$ SELECT coalesce(nullif(current_setting('fasttowin.race_now',true), '')::timestamptz,clock_timestamp()) $$;
CREATE FUNCTION clan_race_clan_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clan_race_time();
BEGIN
 IF TG_OP='INSERT' THEN
  INSERT INTO clan_race_clans VALUES (NEW.id,at_time,NULL);
 ELSIF TG_OP='DELETE' THEN
  UPDATE clan_race_clans SET deleted_at=at_time WHERE clan_id=OLD.id;
  UPDATE clan_race_clan_versions SET valid_until=at_time WHERE clan_id=OLD.id AND valid_until IS NULL;
  UPDATE clan_race_tenures SET ended_at=at_time,exit_kind='DISSOLVED' WHERE clan_id=OLD.id AND ended_at IS NULL;
  RETURN OLD;
 ELSIF (NEW.name,NEW.logo_id,NEW.level) IS NOT DISTINCT FROM (OLD.name,OLD.logo_id,OLD.level) THEN RETURN NEW;
 ELSE UPDATE clan_race_clan_versions SET valid_until=at_time WHERE clan_id=OLD.id AND valid_until IS NULL;
 END IF;
 INSERT INTO clan_race_clan_versions(clan_id,name,logo_id,level,valid_from) VALUES (NEW.id,NEW.name,NEW.logo_id,NEW.level,at_time);
 RETURN NEW;
END $$;
CREATE TRIGGER clan_race_clan_history AFTER INSERT OR UPDATE OR DELETE ON clans FOR EACH ROW EXECUTE FUNCTION clan_race_clan_history();
CREATE FUNCTION clan_race_member_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clan_race_time();
BEGIN
 IF TG_OP='DELETE' THEN
  UPDATE clan_race_tenures SET ended_at=at_time,exit_kind='LEFT_OR_REMOVED' WHERE clan_id=OLD.clan_id AND user_id=OLD.user_id AND ended_at IS NULL;
  RETURN OLD;
 END IF;
 INSERT INTO clan_race_tenures(id,clan_id,user_id,joined_at) VALUES(gen_random_uuid(),NEW.clan_id,NEW.user_id,at_time);
 RETURN NEW;
END $$;
CREATE TRIGGER clan_race_member_history AFTER INSERT OR DELETE ON clan_members FOR EACH ROW EXECUTE FUNCTION clan_race_member_history();
-- Existing membership is marked at cutover; no historical progress or claim becomes a score.
INSERT INTO clan_race_clans SELECT id,CURRENT_TIMESTAMP,NULL FROM clans;
INSERT INTO clan_race_clan_versions(clan_id,name,logo_id,level,valid_from) SELECT id,name,logo_id,level,CURRENT_TIMESTAMP FROM clans;
INSERT INTO clan_race_tenures(id,clan_id,user_id,joined_at,cutover) SELECT gen_random_uuid(),clan_id,user_id,CURRENT_TIMESTAMP,TRUE FROM clan_members;
