ALTER TABLE user_notifications
    ADD COLUMN title_key VARCHAR(120),
    ADD COLUMN title_args JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN message_key VARCHAR(120),
    ADD COLUMN message_args JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT user_notifications_title_key_nonblank
        CHECK (title_key IS NULL OR BTRIM(title_key) <> ''),
    ADD CONSTRAINT user_notifications_message_key_nonblank
        CHECK (message_key IS NULL OR BTRIM(message_key) <> ''),
    ADD CONSTRAINT user_notifications_title_args_object
        CHECK (jsonb_typeof(title_args) = 'object'),
    ADD CONSTRAINT user_notifications_message_args_object
        CHECK (jsonb_typeof(message_args) = 'object');
