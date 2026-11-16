-- SEC-03: system-wide daily allowance for account emails, shared by every server replica.
CREATE TABLE email_daily_usage (
    usage_day DATE PRIMARY KEY,
    reserved INT NOT NULL CHECK (reserved >= 0)
);
