CREATE SEQUENCE owned_admission_generation_seq AS bigint MINVALUE 1 NO CYCLE;

CREATE TABLE owned_admission_policy (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    generation bigint NOT NULL DEFAULT nextval('owned_admission_generation_seq') CHECK (generation > 0),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'STOP_NEW', 'QUIESCING', 'DISABLED')),
    topology_sha256 text CHECK (topology_sha256 ~ '^[0-9a-f]{64}$'),
    policy_sha256 text CHECK (policy_sha256 ~ '^[0-9a-f]{64}$'),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((topology_sha256 IS NULL) = (policy_sha256 IS NULL)),
    CHECK (state <> 'ACTIVE' OR (topology_sha256 IS NOT NULL AND policy_sha256 IS NOT NULL))
);

INSERT INTO owned_admission_policy(singleton, state) VALUES (true, 'STOP_NEW');
