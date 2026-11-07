CREATE TABLE room_ownership_pilot_fixture (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    fixture_id UUID NOT NULL
);
