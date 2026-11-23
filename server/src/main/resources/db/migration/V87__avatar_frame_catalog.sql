CREATE TABLE avatar_frames (
    id VARCHAR(32) PRIMARY KEY CHECK (id ~ '^frame_[a-z0-9]+(_[a-z0-9]+)*$'),
    name_vi VARCHAR(80) NOT NULL CHECK (length(trim(name_vi)) > 0),
    name_en VARCHAR(80) NOT NULL CHECK (length(trim(name_en)) > 0),
    version INTEGER NOT NULL CHECK (version > 0),
    png BYTEA CHECK (octet_length(png) BETWEEN 1 AND 262144)
);
