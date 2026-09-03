ALTER TABLE users
    ADD COLUMN push_room_invitations_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_tournament_invitations_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_mission_rewards_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_daily_check_in_enabled BOOLEAN NOT NULL DEFAULT TRUE;
