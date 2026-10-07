-- V54 stored only the latest mutation actor. Never infer creator/publisher from it.
-- Only a unique committed lifecycle audit is evidence; missing/ambiguous history stays null.
CREATE TEMPORARY TABLE reward_event_identity_backfill ON COMMIT DROP AS
SELECT target_id,
       CASE WHEN count(*) FILTER (WHERE action='EVENT_CREATE')=1
            THEN min(actor_id::text) FILTER (WHERE action='EVENT_CREATE') END AS creator_id,
       CASE WHEN count(*) FILTER (WHERE action='EVENT_SCHEDULE')=1
            THEN min(actor_id::text) FILTER (WHERE action='EVENT_SCHEDULE') END AS publisher_id
FROM admin_audit
WHERE action IN ('EVENT_CREATE','EVENT_SCHEDULE') AND target_id IS NOT NULL
GROUP BY target_id;

UPDATE reward_events e
SET payload=(e.payload::jsonb || jsonb_build_object(
    'creatorId', (SELECT b.creator_id FROM reward_event_identity_backfill b WHERE b.target_id=e.id),
    'publisherId', CASE WHEN e.payload::jsonb->>'publishedAtEpochMillis' IS NOT NULL
        THEN (SELECT b.publisher_id FROM reward_event_identity_backfill b WHERE b.target_id=e.id) END
))::text;

-- Preserve receipts, fingerprints and historical state/time/version. In particular a
-- cached create/update result remains unpublished even if its event was later scheduled.
UPDATE reward_event_requests r
SET safe_result=(r.safe_result::jsonb || jsonb_build_object(
    'creatorId', (SELECT b.creator_id FROM reward_event_identity_backfill b WHERE b.target_id::text=r.safe_result::jsonb->>'id'),
    'publisherId', CASE WHEN r.safe_result::jsonb->>'publishedAtEpochMillis' IS NOT NULL
        THEN (SELECT b.publisher_id FROM reward_event_identity_backfill b WHERE b.target_id::text=r.safe_result::jsonb->>'id') END
))::text;
