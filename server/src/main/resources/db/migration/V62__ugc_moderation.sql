-- Reports deliberately have no raw image bytes or public evidence directory.
-- Separate quota events prevent closing/deleting reports from bypassing rate limits.
CREATE TABLE ugc_content_terms (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    accepted_version VARCHAR(64) NOT NULL,
    accepted_at BIGINT NOT NULL
);
CREATE TABLE ugc_reports (
    id UUID PRIMARY KEY,
    version INTEGER NOT NULL CHECK (version>0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('NEW','REVIEWING','RESOLVED','DISMISSED')),
    reporter_id UUID REFERENCES users(id) ON DELETE SET NULL,
    target_type VARCHAR(24) NOT NULL CHECK (target_type IN ('PLAYER_PROFILE','PLAYER_AVATAR','CLAN','ROOM','TOURNAMENT')),
    target_id UUID,
    target_owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reason VARCHAR(32) NOT NULL,
    note VARCHAR(500) NOT NULL,
    evidence_text VARCHAR(8000) NOT NULL,
    target_revision VARCHAR(128),
    image_reference TEXT,
    created_at BIGINT NOT NULL,
    closed_at BIGINT,
    CHECK ((status IN ('RESOLVED','DISMISSED')) = (closed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ugc_one_open_report ON ugc_reports(reporter_id,target_type,target_id)
    WHERE status IN ('NEW','REVIEWING') AND reporter_id IS NOT NULL AND target_id IS NOT NULL;
CREATE INDEX ugc_reports_queue ON ugc_reports(status,created_at DESC,id);
CREATE INDEX ugc_reports_expiry ON ugc_reports(closed_at) WHERE closed_at IS NOT NULL;
CREATE INDEX ugc_reports_owner ON ugc_reports(target_owner_id);
CREATE TABLE ugc_report_rate_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL
);
CREATE INDEX ugc_rate_identity ON ugc_report_rate_events(user_id,created_at);
CREATE INDEX ugc_rate_expiry ON ugc_report_rate_events(created_at);
CREATE TABLE ugc_request_replays (
    actor_id UUID NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    response_type VARCHAR(24) NOT NULL,
    response_json TEXT NOT NULL,
    report_id UUID REFERENCES ugc_reports(id) ON DELETE CASCADE,
    subject_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY(actor_id,request_id)
);
CREATE INDEX ugc_replay_expiry ON ugc_request_replays(expires_at);
CREATE TABLE ugc_content_suppressions (
    target_type VARCHAR(24) NOT NULL,
    target_id UUID NOT NULL,
    revision VARCHAR(128) NOT NULL,
    name_revision VARCHAR(128),
    owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(target_type,target_id,revision)
);
CREATE TABLE ugc_account_restrictions (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version>0),
    blocked BOOLEAN NOT NULL,
    updated_at BIGINT NOT NULL
);
ALTER TABLE matches ADD COLUMN IF NOT EXISTS room_id UUID;

-- Reasons are private operational evidence, bounded by report retention or 90 days for restrictions.
CREATE TABLE ugc_moderation_actions (
    id UUID PRIMARY KEY,
    sequence BIGSERIAL UNIQUE,
    report_id UUID REFERENCES ugc_reports(id) ON DELETE CASCADE,
    subject_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES admin_accounts(id) ON DELETE SET NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT,
    CHECK (report_id IS NOT NULL OR expires_at IS NOT NULL)
);
CREATE INDEX ugc_actions_report ON ugc_moderation_actions(report_id,created_at DESC,sequence DESC);
CREATE INDEX ugc_actions_subject ON ugc_moderation_actions(subject_user_id);
CREATE INDEX ugc_actions_expiry ON ugc_moderation_actions(expires_at) WHERE expires_at IS NOT NULL;
