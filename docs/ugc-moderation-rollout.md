# UGC moderation rollout and operations

Issue #53, approved design: `superpowers/specs/2026-10-20-ugc-moderation-design.md`.

## Client-first rollout

1. Apply migration V62 on an isolated database and verify moderation/auth/guest integration tests with zero skips. Start backend with `FASTTOWIN_CONTENT_TERMS_ENFORCED=false` (default). No existing client is treated as having accepted terms just because a checkbox was displayed.
2. Publish the matching VI/EN privacy and terms pages. Terms version is `2026-10-20`. Changing required rules is a code/versioned release, not an editable admin free-text field.
3. Release compatible Web and signed Android internal builds. Confirm signup, guest resume after app restart, upgrade, registered reconnect, avatar/profile edits, room/clan/tournament creation, report receipt/retry and access to help/deletion.
4. Verify all UGC report entry points and moderation actions on staging with disposable fixtures; test correct and incorrect roles and session expiry. Check a removed avatar through `/api/avatar/{userId}`, not only a cached UI image. Confirm unrelated content is unchanged.
5. After client availability and physical-device acceptance, explicitly approve switching `FASTTOWIN_CONTENT_TERMS_ENFORCED=true` on production. Docker Compose passes the example variable; Railway sets it on the backend service. Keep it false until this gate is satisfied.
6. Older clients that attempt publication without current acceptance receive `CONTENT_TERMS_REQUIRED`; their protocol cannot implement a consent dialog. Provide the compatible update and explain the requirement. Login/account deletion/help remain available. Do not silently mark consent for old accounts.

### Live content refresh and older clients

Compatible clients request `GetContentTerms` after session readiness, enabling `ContentModerationChanged` messages on that socket. A removal invalidates the exact target and refreshes read-only views. Older sockets remain usable and receive sanitized later snapshots; they are not disconnected just to clear caches. An already cached avatar in an older binary can remain visible until it refreshes or updates. This is a known compatibility limit: require the compatible release before acceptance and enforcement.

## Reviewing reports

- Check queue daily. SUPPORT may view and move a report to REVIEWING; OWNER/OPERATOR may close or dismiss, remove the exact reported revision, and block/unblock the content owner's account with a reason.
- Read the current target alongside recorded text. Avatar reports reference an image revision and never retain image bytes as report evidence. If target changed, review the new content separately; stale report removal must return conflict.
- A report alone does not cause automatic penalties. Account restrictions revoke sessions without changing wallet balances or deleting accounts. Moderation of room/tournament names does not cancel matches or charge/refund entry fees.
- A lost response must retry the same request ID and payload. On a version conflict, reload and review again. Notes and private reasons must never be copied into public issues, push bodies or long-lived audit logs.
- When responding to a support appeal, use the owner's existing support channel. No automatic email sending is part of this feature.

## Retention and deletion

- Active reports remain until handled; monitor backlog instead of allowing an unattended queue to become indefinite storage by accident.
- Closed report content/identities and linked private action reasons expire after 90 days from closure. The cleanup job runs at startup and daily. Standalone account-restriction action reasons have a bounded 90-day expiry; the restriction itself remains until changed or account deletion.
- Account deletion scrubs reporter links and identifying evidence for reports authored by/targeting that player, removes related action details and replay data, and removes consent/restriction links in the identity transaction. Long-lived moderation audit contains only admin actor/action metadata.
- Restoring a database must follow the existing deletion reconciliation gate and 14-day backup policy in `play-store-release-checklist.md`. Do not restore deleted identities into active service.

## Play declarations and reviewer flow

Reports add security/moderation purposes to existing user IDs, app interactions and other user-generated-content collection; they do not add location, phone contacts or photo-library upload. Review Data safety with the exact shipped build before submission, including whether optional report text is now used for security. Internal version 1.0 (1) is unchanged until a new signed AAB is uploaded. AdMob PR52 remains a separate release and requires its own declaration review.

Reviewer flow after release: use the separate approved reviewer player account, inspect another fixture player/clan/room/tournament, open Report, select reason and submit. The app confirms only after a server receipt. A block action is separate; do not report or penalize real users just to demonstrate the feature. Admin moderation requires a separately authorized role and is not available to the Play reviewer player.

## Verification record

Current implementation and evidence status is tracked in #53 and the draft PR; the plan ledger is private during development. Host-rendered Compose previews and localhost browser fixtures are not Samsung acceptance. Do not close #53 on source compilation alone or claim Google approval.
