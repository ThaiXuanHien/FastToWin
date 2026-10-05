# Admin analytics API

Issue #24, Task 2. The API reads the anonymous V52 facts described in
[admin-analytics-facts.md](admin-analytics-facts.md). It does not ingest provider
data or infer revenue from Gem grants, catalog prices, or legacy store rows.

`GET /admin/api/analytics` uses the existing admin session cookie. It exists only
when admin is enabled. With admin enabled but no durable database, authorized
requests return safe `503 STORAGE_UNAVAILABLE`; no empty dashboard is fabricated.
Success and errors have `Cache-Control: no-store` and
`X-Content-Type-Options: nosniff`. A supplied Origin must match the configured
admin origin. The existing console CSP is unchanged.

## Parameters

| Parameter | Contract |
| --- | --- |
| `metric` | Repeated or comma-separated metric names; defaults to REGISTRATIONS; at most ten names |
| `from`, `until` | Both UTC epoch **milliseconds**, inclusive from/exclusive until; omit both for a preset |
| `preset` | 7, 30, or 90 elapsed days; default 30; cannot accompany explicit dates |
| `bucket` | DAY (default), WEEK, MONTH |
| `store` | GOOGLE_PLAY or APP_STORE, for financial metrics |
| `currency` | GOLD/GEMS for item metrics; uppercase three-letter ISO currency for financial metrics |
| `item`, `product` | Stable identifier, 1–128 ASCII letters/digits or `_ . : -`; item for game economy, product for financial metrics |
| `environment` | LIVE (default) or explicit SANDBOX for financial metrics |

Unknown parameters, duplicate scalar parameters, malformed values, incompatible
filters, reversed ranges, a single date endpoint, and ranges over 24 calendar
months are rejected with safe 400 errors. Calendar bounds are checked in Bangkok.
Future `until` is clipped to the server's observed time: comparison uses only the
elapsed portion. A range starting in the future is invalid. The prior comparison
is `[from - (effectiveUntil - from), from)` and is returned explicitly.

Buckets follow Asia/Bangkok (UTC+7), local midnight, Monday weeks, and calendar
months. Returned first/last bucket bounds are clipped to the effective range.
All response timestamps are UTC epoch milliseconds. Fact selection uses
`timestamp >= from AND timestamp < until` before SQL grouping. Whole-range
distinct counts have their own query; bucket distinct counts must not be summed.

## Metric permissions and units

| Metrics | Roles | Summary/unit |
| --- | --- | --- |
| REGISTRATIONS | Owner, Operator | First successful REGISTERED transitions / REGISTRATIONS |
| ITEM_SPEND | Owner, Operator | Committed item debit / GOLD or GEMS, separate groups |
| ITEM_PURCHASES, TOP_ITEMS | Owner, Operator | Successful item purchases / PURCHASES |
| ITEM_BUYERS | Owner, Operator | Whole-range distinct anonymous subjects / BUYERS |
| MONEY_PAID, MONEY_REFUNDED, MONEY_NET | Owner | Exact minor amount / MONEY_MINOR, separate source/currency/exponent groups |
| STORE_PURCHASES | Owner | PAYMENT rows / PURCHASES, separate source groups |
| STORE_BUYERS | Owner | Distinct PAYMENT subjects / BUYERS, separate source groups |

Support cannot read any metric. Every metric in a combined request is checked
before querying facts or coverage; a forbidden metric rejects the entire request.
Current roles and session expiry/revocation use `AdminService.authorize` with
`JdbcAdminTransaction`; there is no separate authorization implementation.
Store count/buyer groups span currencies within their source and honor an
explicit currency/product filter. Refunds never increase store purchases/buyers.
Money net is paid minus refunds **occurring in the selected period**, before
fees/taxes; it can be negative even if the original payment was in another period.
The API never combines currencies or different currency exponents.

## JSON contract

The response is `AnalyticsResponse`:

- `range`, `comparisonRange`: `{from, until}` effective/prior UTC bounds.
- `timezone`: `Asia/Bangkok`; `bucket`: requested enum; `snapshotAt`: database
  observation time after synchronized authorization.
- `metrics`: requested metrics only, each `{metric, coverage, groups}`.
- `coverage`: one record per selected source: `{source, environment, status,
  coverageStart, freshness}` with nullable timestamps.
- `groups`: `{source, unit, currency, currencyExponent, summary, series,
  comparison, top}`. Currency/exponent are null for registrations and store counts.
- `series`: ordered `{from, until, value}` points with exact string or null values.
- `comparison`: `{range, value, absoluteChange, percentageChange, coverage}`.
- `top`: only TOP_ITEMS has rows, at most ten `{item, purchases, spend, buyers}`
  ordered by purchase count descending, spend descending, stable item ascending.

Every count, spend, monetary amount, absolute change and percentage is a decimal
**string**, never a floating-point JSON number. Minor amount `"100"` with exponent
2 denotes 1.00 currency units. UI consumers should retain exact strings/BigInt
authority. Percentages use exact decimal arithmetic, rounded half-up to six
decimal places and stripped of trailing zeroes:
`(current - previous) * 100 / abs(previous)`. Both periods must be COMPLETE.
When previous is zero, absolute change remains available but percentage is null.
Insufficient coverage suppresses both change fields.

## Coverage and unavailable data

COMPLETE establishes collection only from `coverageStart`. Earlier requested
ranges become PARTIAL. Missing coverage is UNAVAILABLE. Explicit PARTIAL never
establishes complete empty buckets. Empty summary/bucket values become `"0"`
only under complete applicable coverage; otherwise they are null. Partial ranges
may return observed nonempty aggregates, labeled PARTIAL. A range-derived PARTIAL
may contain later complete buckets that correctly return zero.

Current and comparison coverage are calculated separately from the same snapshot.
Source freshness describes recorded source updates; it is not invented as the
query time or presented as an independently verified provider watermark.
Unavailable financial sources have no groups and expose no ledger aggregates.
Known financial units come from bounded current/prior ledger facts of either event
kind. Complete sources without a known unit return empty groups rather than
inventing a currency scale. Paid/refunded/net groups for known units include
complete zero values. Both financial LIVE sources remain UNAVAILABLE by default
until verified adapters explicitly establish coverage. Tests mark only their own
isolated, trusted ledger fixtures complete; that is not live revenue evidence.

## Snapshot, audit, and query boundary

The reader first acquires session advisory lock `7421092187349` in autocommit,
then begins a repeatable-read transaction. It thereby waits behind current admin
mutations **before** establishing its authorization/fact snapshot. The same
connection checks the current role/session, appends `ANALYTICS_READ`, and performs
coverage, independent summary distinct, SQL bucket grouping, prior comparison,
and SQL top-ten queries. The audit contains actor, validated range and metric
names, never credentials, receipts, player identities, or raw filters. An audit
or query failure rolls back and returns safe 503 without aggregates.

The shared lock fences existing admin authorization/access changes. Facts can
continue committing concurrently; repeatable read keeps all aggregate portions
consistent. Finally the transaction is completed/rolled back, the session lock
is explicitly released, and connection isolation is restored. An unlock failure
aborts the connection so a locked connection cannot return to the pool.

All user filters are statement parameters. Table names, expressions and grouping
fragments are fixed server constants; the server never loads raw transactions or
users into JVM/browser memory to group them. V52 schema and Task 1 writers are
unchanged. `DatabaseRuntime.analyticsRepository`, optional `AnalyticsService`,
and `/admin/api/analytics` are the Task 3/4 integration interfaces.

## Financial source setup

See [financial analytics sources](financial-analytics-sources.md) for the default
disabled configuration, Google known-order PARTIAL coverage, durable rechecks and
explicit App Store dependency. Financial facts never alter wallet grants.
