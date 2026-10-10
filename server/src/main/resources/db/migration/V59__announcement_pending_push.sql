-- Internal indexed work keys, maintained with recipient checkpoints, claims and cancellation.
-- Selection reads keys only; authority is revalidated under announcement/user/recipient locks.
CREATE TABLE admin_announcement_pending_push (
    announcement_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (announcement_id, user_id),
    FOREIGN KEY (announcement_id, user_id)
        REFERENCES admin_announcement_recipients(announcement_id, user_id) ON DELETE CASCADE
);

-- Existing stored history needs exactly one outcome; settled/ambiguous claims never re-enter.
INSERT INTO admin_announcement_pending_push(announcement_id, user_id)
SELECT r.announcement_id, r.user_id
FROM admin_announcement_recipients r
JOIN admin_announcements a ON a.id = r.announcement_id
WHERE a.status = 'PUBLISHED'
  AND r.payload::jsonb->>'storedAtEpochMillis' IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM admin_announcement_push_attempts p
      WHERE p.announcement_id = r.announcement_id AND p.user_id = r.user_id
  );
