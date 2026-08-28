ALTER TABLE profiles
    ADD COLUMN gender VARCHAR(8) NOT NULL DEFAULT 'MALE',
    ADD CONSTRAINT profiles_gender_check CHECK (gender IN ('MALE', 'FEMALE'));
