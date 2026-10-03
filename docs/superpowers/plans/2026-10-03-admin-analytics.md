# Admin Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin charts show authoritative registrations, GOLD/GEMS item spending and verified real-money/refund trends with honest coverage.

**Architecture:** Transaction-bound immutable facts and source coverage feed read-only PostgreSQL aggregates. Store adapters populate a separate financial ledger from authenticated/signed provider data; they never award wallet funds. Admin renders exact values, bounded charts and accessible tables through existing session/modal infrastructure.

**Tech Stack:** Kotlin/Ktor/JDBC/Flyway, existing authenticated provider HTTP clients, BigDecimal/decimal string JSON, native admin JavaScript and same-origin SVG charts, PostgreSQL/browser integration tests.

**Spec:** `docs/superpowers/specs/2026-10-03-admin-analytics-design.md`; approved canonical Issue #24. Execute after maintenance #23 to avoid concurrent changes in auth/store/runtime files.

## Global Constraints

- Owner: all metrics including money/refunds; Operator: registrations/game economy only; Support: none. Server enforces each metric before query or disclosure.
- Bangkok UTC+7 buckets, week starts Monday, UTC timestamps, [from,until); DAY/WEEK/MONTH, 7/30/90 presets, default30days and maximum24months.
- Registration means first successful REGISTERED transition, including guest upgrade. Never use guest identity created_at or mutable updated_at as an invented registration time.
- Money/currencies and GOLD/GEMS remain separate. Use verified transaction amount, not current catalog price or granted Gem count. Net in period = paid minus refunds in period, before fees/tax adjustments.
- Exact decimal/integer arithmetic and decimal-string JSON for aggregates above2^53; no floating-point authority.
- LIVE only by default; source coverage COMPLETE/PARTIAL/UNAVAILABLE, coverageStart/freshness required. Unknown is not0. No historical backfill without evidence.
- Durable historical facts survive user deletion without exposing identity. Distinct across entire range is computed independently of daily distinct sums.
- Existing admin sessions/current role/no-store/session fencing/audit; no source credentials/receipts in browser/cache/log. Admin CSP unchanged.
- Mandatory ui-ux-pro-max/admin white Material override; same-origin native SVG avoids new chart dependency/CDN, use readable labels, keyboard/touch values and corresponding table.
- No deployment/store-account provisioning/private-production fetch, real refunds, collection of new payment, FX conversion or issue closure in local implementation work.
- Local tests isolate PostgreSQL schemas; no reset/drop existing DB. Keep preview and unrelated artifacts.

## Primary-source planning evidence (03/10/2026)

Google's [Orders resource](https://developers.google.com/android-publisher/api-ref/rest/v3/orders)
provides order identity, state, monetary totals and refund history. Use authenticated
provider totals and processed refund events rather than listingPrice. The adapter
must retain the provider order ID from verified purchase evidence; current wallet
receipt fingerprints are not provider order IDs.

Google's [report documentation](https://support.google.com/googleplay/android-developer/answer/6135870?hl=en)
describes private report access and delayed exports. They can support reconciliation;
they are not an instant complete source. MVP prefers Orders lookup of known verified
orders. Range coverage cannot claim unknown/unfetched orders or refunds are complete.

Apple's [official payload model](https://apple.github.io/app-store-server-library-node/interfaces/JWSTransactionDecodedPayload.html)
describes price in milliunits, currency, purchase/revocation timing and revocation
percentage. Decode only after official signature/application/environment checks.
Missing fields are unavailable; configured offer price alone must not be labelled
the buyer's verified paid amount without confirming its charge semantics. A financial
source that cannot establish the approved amount/refund definition remains unavailable.

These are integration references, not permission to access store accounts. Recheck
the precise provider method and field semantics during Task3 before enabling a source.

## Modules and stable contracts

- `server/.../analytics/AnalyticsModels.kt`: bucket, metric, source/coverage, query and response DTOs.
- `AnalyticsFacts.kt` and migration next after maintenance V51: immutable registrations, purchases, financial events, source sync/coverage, anonymized distinct key.
- Existing PostgresAuthRepository/PostgresPlayerProfileRepository: write facts inside the real successful operation transaction; deletion scrubs linkage without erasing facts.
- `AnalyticsRepository.kt`/`PostgresAnalyticsRepository.kt`: repeatable-read snapshot aggregation and exact distinct/top queries, with connection-bound admin authorization/audit.
- `AnalyticsService.kt`/`AnalyticsRoutes.kt`: query validation/current-role/safe storage errors; route under `/admin/api/analytics`.
- `FinancialSources.kt`, Google/AppStore adapters and durable sync service: source ingestion/reconciliation only, no wallet grant.
- `admin-analytics.js`: chart/table view using existing channel/session/api adapter, registered explicit asset and navigation.

### Task 1: Transaction-bound registration/item facts and historical coverage

**Files:** create analytics fact module/migration, modify PostgresAuthRepository.kt,
PostgresPlayerProfileRepository.kt and Database.kt as needed; test real DB registration,
guest-upgrade, cosmetic spending/deletion, rollback and retry under server tests.

**Interfaces:** `AnalyticsFacts.recordRegistration(connection,userId,registeredAt)`;
`recordItemPurchase(connection,eventId,userId,itemId,currency,amount,purchasedAt)`;
`eraseIdentityLink(connection,userId)`. Facts have no cascading users FK; event uniqueness
enforced by schema. Stable random subject key mapping is private and removed/anonymized
with the current deletion path; retained facts cannot restore account lookup.

- [ ] Write behavioral RED tests in isolated Postgres schemas: guest created Monday upgraded Friday counts Friday once; direct register/retry once; failure rolls back fact and identity; two buys by same player yield correct events/distinct; grant ownership excluded; concurrent buy duplicate produces one wallet/fact; delete preserves anonymous range counts but removes account linkage.
  ```kotlin
  // In existing upgradeGuest transaction after the successful conditional update:
  AnalyticsFacts.recordRegistration(connection, guest.userId.toString(), upgrade.session.nowMillis)
  // Event uniqueness makes registration retry safe; no created_at substitution.
  ```
- [ ] Create schema with exact timestamps/event keys, indexes on event-time/currency/item/source, trusted collection-start coverage. Insert facts with successful registration/upgrade and actual COSMETIC_PURCHASE debit transaction. Do not add facts to read-only/profile/equip/restore or failed operations.
  ```kotlin
  // Item price/currency come from the same authoritative catalog used for debit.
  AnalyticsFacts.recordItemPurchase(connection, walletEventId, playerId,
      cosmeticId, currency.name, cost.toLong(), nowMillis)
  ```
- [ ] Historical debit may support total spend where trusted; missing item identity prevents claimed top coverage. Existing REGISTERED inventory is separate from new-registration series. No migration assigns invented dates/prices. Apply existing deletion behavior with fact linkage scrubbing in its transaction.
- [ ] Run focused auth/upgrade/shop/delete/fact Postgres tests, inspect XML/no skips, scanner/diff. Document coverage and exact schema/interface; commit `feat: persist authoritative analytics facts`, report evidence.

### Task 2: Exact aggregate API, time ranges and metric permissions

**Files:** create analytics models/repository/service/routes, integrate optional admin
module in Application/Main/Database, add service/routes/real-PG tests, docs/admin-analytics.md.

**Interfaces:** `GET /admin/api/analytics?from=epoch&until=epoch&bucket=DAY|WEEK|MONTH&metric=...`
with validated store/currency/item filters. Response contains exact-string summary values,
series timestamps, range/timezone/unit, total-range distinct, top10, snapshot/freshness and
per-metric coverage. Metric names: REGISTRATIONS, ITEM_SPEND, ITEM_PURCHASES, ITEM_BUYERS,
TOP_ITEMS, MONEY_PAID, MONEY_REFUNDED, MONEY_NET, STORE_PURCHASES, STORE_BUYERS.
Server may internally group compatible metric requests, but never returns unauthorized metrics.

- [ ] Write RED cases: Sunday/Monday and UTC+7 midnight/month boundaries; sparse zero buckets only within complete coverage;24month bound; same buyer multiple buckets counted once in summary; monetary net negative; absent coverage/partial prior periods; malformed filter; Support403/Operator-money403/Owner200; revoked role/audit error/storage503.
  ```sql
  SELECT date_trunc('week', occurred_at AT TIME ZONE 'Asia/Bangkok') AS bucket,
         SUM(amount)::text AS amount, COUNT(DISTINCT subject_key)::text AS buyers
  FROM analytics_item_purchases
  WHERE occurred_at >= ? AND occurred_at < ? AND currency = ?
  GROUP BY bucket ORDER BY bucket;
  ```
- [ ] Implement aggregates in one consistent DB snapshot; summary distinct gets its own whole-range query, not sum(series.buyers). Permissions audited transactionally using existing shared admin authorization lock; parameterized queries only. Wire optional trusted sources without unavailable source returning0. Safe no-store errors and no-DB unavailable response.
- [ ] Compare preceding equal-length elapsed range; label precise dates, suppress % with insufficient coverage or zero denominator. Serialize numeric counts/money as exact strings. Amount values retain currency scale metadata; no implicit mixed-unit totals.
- [ ] Run focused API/PG suites plus existing admin authorization tests, serialization >2^53, scanner/build/diff; contract/report and explicit commit `feat: expose exact admin analytics aggregates`.

### Task 3: Verified financial-source adapters and reconciliation ledger

**Files:** create FinancialSources.kt, provider adapters and sync repository/service/tests;
extend StorePurchases.kt/shared store service from maintenance Task3 only to retain verified
provider order identity without replacing economy receipt fingerprint. Configuration uses
existing secrets-file conventions; default all financial sources disabled/unavailable.

**Interfaces:** source returns normalized verified PAID/REFUND events with stable source
key, source order identity, LIVE/SANDBOX, product, provider time, exact amount/currency,
and evidence coverage. Ingestion upserts idempotently with validation of linked original
purchase and cumulative refunds; it cannot change wallet grants. No browser money import.

- [ ] Recheck the primary references above and write bounded source fixtures for authenticated Google Orders paid/partial/full refunds and signed Apple events. Tests include auth/signature/app/environment/amount checks, foreign product/package, malformed currency/scale, pending refund ignored, repeated snapshot/import and correction, source downtime and late events. Mock provider transports, not the source verification decision itself.
  ```kotlin
  val decimal = BigDecimal(units).add(BigDecimal(nanos).movePointLeft(9))
  // Keep exact decimal or validated currency-minor integer; never Double.
  require(decimal >= BigDecimal.ZERO)
  // PAID total is provider charged total; refund events require processed status.
  ```
- [ ] Google adapter queries only verified known provider order IDs with configured authenticated credentials/package; checks response order/token/product binding before recording. Durable cursor/recheck known orders lets refunds arrive later. Apple adapter uses official verified signed payload/library; if charge/refund amount semantics cannot be established, mark unsupported metric/source unavailable rather than manufacture a price. Unsupported/unconfigured AppStore is explicit and linked to #14 dependency, not fake success.
- [ ] Store normalized financial facts independently of identity FK, discard provider tokens/raw receipts from analytics storage/output. Repeated source snapshots cannot double-count a partial+full refund; if provider represents cumulative totals, convert to deltas under lock. Conflicting corrections reconcile deterministically and adjust historical bucket freshness/coverage.
- [ ] Configure collection start and supported history boundaries; credentials missing/denied/source failure keeps coverage partial/unavailable. Document source setup and validation, not private values. No production provision/fetch required; executable local fake HTTP/certificate integration proves source paths before user configures access.
- [ ] Run focused financial/ledger/role/serialization/store-regression tests and exact round-trip money cases including USD/JPY/VND and >2^53. Report actual enabled-source coverage/limitations; commit `feat: ingest verified financial analytics`.

### Task 4: Material chart dashboard, accessible table and live evidence

**Files:** admin-analytics.js, admin.js/CSS/AdminAssets.kt, design-system admin override,
e2e analytics fixture/spec/config, docs/admin-analytics.md; no global player restyling.

**Interfaces:** reuse existing admin API/channel/session/modal adapters; native SVG path
charts and corresponding exact-value tables. UI selected metric/filter determines allowed
query. Summary/table is precision authority; SVG coordinates are normalized bounded ratios.

- [ ] RED browser cases for permitted roles/metric filters, presets/range/buckets,
  mixed-currency separation, unknown versus zero, partial coverage/freshness/late import,
  prior-period comparison, >2^53 labels, loading/error/no stale snapshot, account/role fences,
  keyboard/touch point inspection and responsive screenshots.
- [ ] Add dashboard navigation and stat/time-series/top10 SVG/table views with Material
  tokens, consistent outline icons,48px controls, accessible labels/legend/text alternatives.
  Register explicit same-origin asset and keep CSP/worker no-store. Avoid translating
  arbitrary amounts through Number before formatting.
  ```javascript
  const exact = BigInt(row.amountMinor);
  const major = exact / 100n, remainder = exact % 100n;
  // This example scale2 belongs only to currencies whose response scale is2;
  // formatting uses supplied scale and sign, never assumes all currencies use cents.
  ```
- [ ] Complete admin browser suite from e2e cwd, inspect360/768/1024/1440 screenshots,
  keyboard/reduced-motion/landscape and touch interactions. Real loopback data check
  uses seeded test registration/item buys and known fixtures; source fixture money is
  labelled test evidence, not live store turnover. No personal data/secret screenshot.
- [ ] Run server/admin/source tests appropriate to changed integration plus scanner/APK/JS/Wasm,
  diffcheck and verified preview restart. Report exact coverage and store prerequisites,
  retain preview/local branch, explicit commit `feat: add admin analytics dashboard`.

## Self-review / spec coverage

- Task1 covers registration/upgrade dates, real item debits/ownership, rollback/deletion/legacy coverage.
- Task2 covers exact summary/series/distinct/time filters/comparison/role/audit/error contract.
- Task3 covers verified paid/refund identity, sandbox exclusion, correction/replay/precision/source coverage.
- Task4 covers Material chart/table/phone/desktop/session fences/precision and real local acceptance.
- Source references inform implementation; unsupported sources remain visible as unavailable.
  Complete monetary-source acceptance requires trustworthy real configuration, not seeded amounts.
- DAU/retention/errors/automatic maintenance/FX/admin refunds remain outside approved MVP.
