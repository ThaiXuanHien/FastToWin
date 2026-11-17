-- Five more opt-out push categories. Existing players start opted in, like the original four (V39).
ALTER TABLE users
    ADD COLUMN push_friend_activity_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_tournament_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_gifts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_season_updates_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN push_clan_activity_enabled BOOLEAN NOT NULL DEFAULT TRUE;
