# Admin player support and verified commerce

The Players dashboard shows three commerce facts for seven Bangkok calendar days,
including the current partial day. Its response carries inclusive `from`, exclusive
`until`, source coverage and the latest retained record time. IAP purchases and
distinct buyers come from `store_purchases.verified_at`; rewarded ad counts come
from `rewarded_ad_grants.verified_at`. These are retained verified receipts and
reward grants, not revenue, client impressions or a claim of provider completeness.
Unavailable storage displays unknown values. The former daily/weekly connection,
average Elo and guest count cards are removed. The existing daily activity chart
retains its own source coverage and freshness.

Only Owner can create a registered player using `POST /admin/api/users`. The form
collects email, display name, an initial password of 12–128 characters with upper
and lower case letters, a digit and a symbol, and a support reason. It uses the
existing Origin, session, CSRF, native dialog and request fences. Pending requests
block dismissal; identical uncertain retries retain their request ID. Abandoned
passwords are cleared when the dialog closes.

V79 adds an explicit per-account `support_email_verification_exempt` flag. Support
creation keeps `email_verified_at` null. Authentication responses and stored client
sessions carry the exemption; the app and WebSocket gate allow gameplay when the
flag is true. Password reset and actual email verification still require their
existing proofs. Refresh reads the current account verification metadata rather
than assuming that a newly issued token proves email ownership.

Account/profile creation, the registration analytics fact, deduplication receipt and
`PLAYER_SUPPORT_CREATE` audit entry commit in one transaction. A collision with an
existing non-deleted email is rejected. Deduplication stores a non-secret payload
fingerprint and player ID; password replay is checked against the account's slow
password hash. Audit summaries record only account and verification flags. The
in-memory admin repository reports provisioning unavailable because it has no
shared authentication database.

Browser coverage is in `e2e/tests/admin-player-metrics.spec.mjs`: data/unknown states,
Owner-only creation, retry IDs, focus restoration, abandoned secrets and pending
guards. Phone and desktop captures are under
`.artifacts/admin-player-metrics-20261015/`. PostgreSQL tests cover creation,
authorization, email collisions, password rules, rollback on audit failure, login
and refresh metadata, and commerce interval boundaries. The shared controller test
covers restored unverified support accounts entering the Playing stage.
