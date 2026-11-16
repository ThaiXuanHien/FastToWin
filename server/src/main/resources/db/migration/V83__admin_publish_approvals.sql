-- ADM-12: a publication above the gift caps waits here until a different Owner publishes
-- the same version. Rows are removed when the second Owner completes it.
CREATE TABLE admin_publish_approvals (
    kind VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    version_key VARCHAR(128) NOT NULL,
    requested_by UUID NOT NULL REFERENCES admin_accounts(id),
    requested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (kind, target_id, version_key)
);
