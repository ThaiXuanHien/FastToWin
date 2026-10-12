-- Anonymous observations only. No migration-time coverage or historical backfill.
CREATE TABLE activity_connection_observations (
    observation_id UUID PRIMARY KEY,
    connection_id UUID NOT NULL,
    subject_key UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    observed_until TIMESTAMPTZ NOT NULL,
    CHECK (observed_until >= started_at)
);
CREATE INDEX activity_observations_range_idx ON activity_connection_observations (started_at, observed_until);
CREATE INDEX activity_observations_until_idx ON activity_connection_observations (observed_until);
CREATE INDEX activity_observations_subject_idx ON activity_connection_observations (subject_key);

CREATE TABLE activity_coverage_windows (
    window_id UUID PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    observed_until TIMESTAMPTZ NOT NULL,
    CHECK (observed_until >= started_at)
);
CREATE INDEX activity_coverage_range_idx ON activity_coverage_windows (started_at, observed_until);
CREATE INDEX activity_coverage_until_idx ON activity_coverage_windows (observed_until);
