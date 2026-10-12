# Connected account activity

Activity means an accepted verified registered-account `/game` connection, including idle and lobby time. It does not measure human attention, foreground time, concurrent players, or match participation. Guests, rejected admission, failed authentication, HTTP-only login and client timestamps do not contribute.

The single backend observes connections every 30 seconds. Failure, restart or gaps exceeding 60 seconds break coverage. It records healthy empty windows, without backfill. Anonymous subject history and coverage are retained for 90 days; the read cutoff applies immediately even before hourly cleanup. Account deletion removes identity resolution but preserves retained anonymous counts. No names, email, tokens or IP addresses enter the activity facts.

## Analytics API

Existing `GET /admin/api/analytics` accepts `metric=ACTIVE_PLAYERS` for Owner and Operator; Support cannot read it. DAY, WEEK and MONTH use existing Asia/Bangkok boundaries. Currency, item, store and product filters are rejected, and activity only uses LIVE SERVER coverage. Financial metrics and provider availability remain unchanged.

The existing AnalyticsResponse contains one SERVER group with unit `PLAYERS`, no currency and exact decimal count strings. Summary and comparison each count distinct subjects over their own whole interval. Bucket counts independently count presence overlapping each half-open bucket; their sum is not the summary. A connection observed at a single instant belongs only to its start bucket.

COMPLETE healthy empty coverage proves `"0"`. UNAVAILABLE returns null values. PARTIAL may show positive observed counts, labelled with partial coverage, but cannot prove zero; unproved empty summaries/buckets remain null. Absolute and percentage changes require both ranges COMPLETE; percentage additionally requires a nonzero prior count. Coverage, counts, buckets and comparison use the same existing repeatable-read snapshot. Current authorization, audit and no-store headers still apply.

## Compensation API

Existing incident/round routes accept `audience: "ACTIVITY"`. New rounds persist optional `round.audience`; old round payloads default it to null and detail/history infer ALL_REGISTERED or USER_IDS only from their saved campaign draft. Display the round metadata as the source: the legacy gift draft envelope remains compatible and is not activity authority. Old committed replay DTOs can omit the new field; readers must retain that saved-draft fallback.

Activity selection uses the incident's affected UTC `[from, until)` interval and requires COMPLETE retained coverage at create/update, preview and publication. Preview/publication select current registered, nondeleted recipients on the existing gift JDBC transaction. The reader checks coverage and selects recipients in one statement. Missing history returns safe 400 `ACTIVITY_SOURCE_UNAVAILABLE`; known incomplete history returns safe 400 `ACTIVITY_RANGE_INCOMPLETE`. Neither selects all registered users as a fallback.

Preview tokens bind the saved source, incident interval/revision, round and campaign versions, connected-account definition version, exact selected identities and prior claims. Publication re-queries the audience and freezes it; new eligible observations, erased identities, source changes, interval edits, retention expiry or incomplete coverage require a fresh valid preview. Exact committed retries retain their original result after current authorization. Existing claim-once, cancellation, safe player content and wallet source behavior remain in force. The ten-user preview sample never limits the frozen cohort.

`GET /admin/api/incidents/capabilities` supplies optional activity `coverage` and `retentionDays: 90`. Its `available` flag reflects retained source freshness within 60 seconds, allowing ordinary 30-second checkpoint lag. This is source availability, not proof that any incident interval is complete. The supplied coverage describes the recent 60-second interval and can be PARTIAL while available is true. The server separately validates the full incident interval; never infer or fabricate trailing coverage through wall-clock now.

Memory/default transactions report activity unavailable. No provider activation, production exercise, automatic compensation, multi-instance completeness or live campaign is implied by this feature.

## Admin console

The existing white Material analytics screen adds **Tài khoản kết nối game**, without item/store/currency filters. It preserves exact counts, native chart/keyboard point inspection/table and clears stale results on refresh/session/view/account changes. The whole-range coverage panel and explanatory copy distinguish observed partial values, unavailable values and complete proven zero; no per-point coverage field is added to the API or inferred from a plotted value.

Incident round forms enable **Kết nối game** from capability availability while naming the full registered-account definition, latest observed source time, 90-day history and actual incident UTC bounds beside the controls. Available recent source coverage can still be partial. Server source/range refusals appear in the focusable error summary and do not expose publication confirmation. Detail, list, edit, preview and confirmation display the saved optional round source with the documented legacy saved-draft fallback. Fixture browser tests and inspected phone/desktop captures under `.artifacts/connected-activity/` verify these behaviors; they do not exercise production, provider activation or physical devices.
