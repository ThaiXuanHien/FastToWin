# Google Play financial source — acceptance checklist

> **For agentic workers:** Use `superpowers:executing-plans` inline only after the owner authorizes the concrete access/activation step. This checklist is preparation, not permission to query orders, change access or enable collection. Do not dispatch CI or device tests automatically.

**Goal:** Accept authoritative Google Play buyer payments/refunds against the existing Owner dashboard for #24, without treating test data as LIVE revenue.

**Architecture:** Keep the deployed known-order adapter and reconciliation pipeline. First inspect access and one approved existing order without enabling collection; only later authorize the global collector and reconcile its actual registry scope. No new adapter, dashboard, schema or money-moving endpoint is proposed.

**Tech Stack:** Google Play Orders GET, existing Billing credentials, Kotlin/JVM, PostgreSQL V69, protected admin analytics API.

**Spec:** [Issue #24](https://github.com/ThaiXuanHien/FastToWin/issues/24), [Issue #28](https://github.com/ThaiXuanHien/FastToWin/issues/28), `docs/financial-analytics-sources.md`, `docs/admin-analytics.md`, `docs/store-billing.md`.

## Preparation state — 1 November 2026

- Owner confirmed: **no existing real-money purchase/refund available; prepare the checklist first**. No purchase or refund is requested.
- Backend source6eb2d2f was deployed as `86f53b3f-7af5-4a76-8b8d-6a1e4d8f32d7`; schema69, no new migration; financial collection remains OFF. This baseline comes from the completed deployment receipt, not a new production read in this task.
- Prior Billing receipts establish two test80Gem orders as SANDBOX and physical pending/reopen behavior. They do not establish real-money amounts/refunds or complete history. Current existence/count of LIVE registry rows was not queried.
- Both issues remain OPEN. There was no fresh provider, Console, DB, credential, backup, device or CI access during checklist preparation. Public Google documentation and existing source/sanitized receipts were inspected.

## Global constraints

- Keep LIVE, AdMob and mandatory UGC activation unchanged during preparation. Never start a purchase/refund to manufacture acceptance evidence.
- Reuse the selected Billing identity. Do not read/export its private JSON, replace Firebase credentials or create another key merely to inspect readiness.
- Permission inspection is read-only. Any access expansion, credential change, production DB read, provider request or collector activation needs the corresponding concrete owner authorization.
- Google known-order coverage stays PARTIAL; App Store stays UNAVAILABLE. Unknown/no matching data is not proof of zero revenue.
- Retain only redacted case aliases and approved monetary aggregates. No raw purchase tokens, receipts, buyer address/email, credential values, provider bodies or order IDs in Git, logs or public artifacts.
- SQL and HTTP examples below are prospective checks, not operations executed during preparation.

## 1. Contract cross-check — prepared

Google's [Orders GET](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/get) takes package/order path parameters, an empty body and the Android Publisher OAuth scope. The [Order resource](https://developers.google.com/android-publisher/api-ref/rest/v3/orders) distinguishes customer-paid total from developer proceeds; refund details include refunded tax, and partial refund processing has its own state/time. [Money](https://developers.google.com/android-publisher/api-ref/rest/v3/Money) uses ISO currency, string int64 units and nanos. These contracts were checked on1November2026.

The following expected behavior is taken from the deployed project code, not presented as newly observed Google transaction data:

| Subject | Acceptance rule |
| --- | --- |
| Payment | `total`, at `processedEvent.eventTime`; discount/tax already included, do not add tax again |
| Partial refund | Count only processed partials, using `refundDetails.total` at `processTime`; a pending refund creates no refund fact |
| Full refund | Reconcile only the remaining unpaid-back balance after accepted partials; no duplicated or zero refund fact |
| Money | Exact integer minor units with the stored exponent; no JS floating-point conversion or rounding |
| Currency | Keep each currency/exponent separate; no conversion or combined VND/USD total |
| Net | Payments minus refunds occurring within the chosen period, before fee/tax adjustments; not a bank settlement amount |
| Identity | Package, product, purchase-token fingerprint and verified LIVE/SANDBOX registry classification must agree |
| Unsupported/conflicting data | Withhold/quarantine; retain accepted facts; no fake zero, overwrite or wallet adjustment |

Full-after-partial provider semantics still require actual evidence: the current parser accepts only an original-charge or remaining-balance full-refund amount. A different real response is a diagnosis gate, not permission to relax it or guess a new rule.

## 2. Existing credential/access readiness — pending authorization

- [ ] Identify the already configured Billing identity through an approved, redacted presence/identity check; never export a secret-store inventory.
- [ ] Verify the Play Developer API is enabled and the identity has access to the exact FastToWin app/package `com.hienthai.fastowin`.
- [ ] Inspect existing Play permissions before proposing changes. Google's [setup guide](https://developers.google.com/android-publisher/getting_started) lists financial-data/order viewing and order/subscription management for Billing APIs. It does not establish a least-privilege, GET-only permission set for this specific account. Do not automatically grant broad management access or account-wide access.
- [ ] Confirm the same credential priority as the deployed resolver: nonblank dedicated inline JSON, dedicated file, then legacy file; invalid selected identity stays unavailable without fallback.
- [ ] Stop on401/403 or missing app permission. Record sanitized status/category only; request a precise permission change separately rather than switching identity or logging provider responses.

## 3. One-order read-only comparison — requires an existing approved order

- [ ] Owner selects an existing order that may be inspected and explicitly authorizes the relevant provider/DB read. Do not collect its ID in a public issue/comment.
- [ ] Check its registry binding and provider classification using a server-side path that keeps credentials/tokens in memory. A SANDBOX order can prove API access/binding, but cannot pass the LIVE payment case.
- [ ] Make only the approved GET request, without collector activation, DB import, receipt replay, consume, refund or grant operations. Retain an alias such asCASE_01 rather than the order ID.
- [ ] Compare Google/Console buyer-paid amount, currency and event times with the adapter's parsed result. Distinguish original payment from developer proceeds and refunds. Retain a sanitized result, never the full response.
- [ ] If no real-money order exists, leave LIVE monetary acceptance pending. Do not report it as failed, zero or complete; do not buy an order automatically.

## 4. Global collector scope and collection boundary — separate decision

`FINANCIAL_GOOGLE_ENABLED=true` enables the shared known-order worker. Its SQL selects all due registry rows, including both LIVE and SANDBOX; it has no one-order pilot or environment-only switch. It handles at most25orders per tick, ticks every minute and normally rechecks an order after3600seconds. A one-order diagnostic must therefore remain separate from flag activation.

- [ ] With approved read-only access, inventory registry environments/statuses before choosing the activation scope. No order IDs or user fields are needed for this aggregate:

```sql
BEGIN READ ONLY;
SELECT environment, sync_status, count(*) AS orders,
       min(captured_at) AS first_capture, max(checked_at) AS last_check
FROM store_financial_orders
WHERE source = 'GOOGLE_PLAY'
GROUP BY environment, sync_status
ORDER BY environment, sync_status;
ROLLBACK;
```

- [ ] Select an exact UTC `FINANCIAL_COLLECTION_START` from verified payment evidence, no later than activation time. There is no proposed value yet because no actual order/time has been selected. Do not substitute deployment time, signup time or an arbitrary historical date.
- [ ] Review older registered orders and the consequences of the boundary. Reconciliation requires original payment time at/after the boundary; a later refund does not by itself admit an older payment. Out-of-boundary snapshots are not silently backfilled or manually rewritten.
- [ ] Obtain approval for the exact credential selection/package/boundary, all affected known orders, activation/restart window and monetary access. Credential readiness or this checklist alone is not activation approval.
- [ ] After approved activation, allow the normal durable scheduler to process due rows. Do not modify `next_check_at`, replay a purchase token or restart just to force more polls. Evaluate one captured order only when it is actually due.

## 5. Real-data acceptance matrix

All provider-backed cases are currently **pending: no real-money purchase/refund selected**. Existing fixture/SQL tests remain engineering evidence only. A missing real-world currency/refund scenario stays explicitly unaccepted.

| Case | Evidence required | Pass condition |
| --- | --- | --- |
| Paid LIVE order | Approved provider/Console amount, currency, payment time and matched registry binding | One payment fact, exact amount/scale/time; one purchase; correct distinct buyer |
| Replay/next scheduled read | Same order/provider state on a later due poll; before/after event/count aggregates | No extra facts, amount, purchase or buyer; no Gem grant |
| Test isolation | Approved known SANDBOX case and separated registry/facts | Default LIVE metrics exclude it; selecting SANDBOX remains visibly labelled |
| Partial refund | Actual successfully processed partial event | One exact refund at processing time; pending event excluded |
| Full after partial | Actual complete event history and totals | Total refunds never exceed original payment; only remaining amount added |
| Refund in later period | Payment and refund in different selected intervals | Payment stays in its original bucket; refund in its own bucket; net may be negative for the refund period |
| Multiple currencies | Real, authorized orders in each currency, if available | Separate amount/exponent groups; no combined money total |
| Coverage/freshness | Registry state plus accepted facts and last successful poll | Google stays PARTIAL after accepted evidence; no evidence stays UNAVAILABLE; failed/conflicting poll does not falsely advance freshness |
| Access/session | Existing Owner/Operator/Support sessions as available, without changing roles | Only Owner reads money; mixed forbidden requests rejected; expiry clears private UI; responses no-store |

## 6. Dashboard reconciliation

Use existing Owner access and matching source/environment/range/product/currency filters. Initial read-only API shape (actual dates are selected only with real evidence):

```http
GET /admin/api/analytics?metric=MONEY_PAID,MONEY_REFUNDED,MONEY_NET,STORE_PURCHASES,STORE_BUYERS&preset=30&bucket=DAY&store=GOOGLE_PLAY&environment=LIVE
```

- [ ] Use the same snapshot/filter/range for paid, refunded and net; include the known event dates in the chosen range. The API uses UTC epoch milliseconds and `[from,until)`; buckets display Vietnam UTC+7, Monday weeks and calendar months.
- [ ] Compare exact decimal strings and currency exponents from API groups with the matching provider evidence and, when authorized, the following per-currency baseline:

```sql
BEGIN READ ONLY;
SELECT environment, currency, currency_exponent, event_type,
       count(*) AS events, sum(amount_minor)::text AS amount_minor
FROM analytics_financial_events
WHERE source = 'GOOGLE_PLAY'
GROUP BY environment, currency, currency_exponent, event_type
ORDER BY environment, currency, currency_exponent, event_type;
ROLLBACK;
```

This SQL is an all-time baseline, not a replacement for the dashboard's selected interval or per-order comparison. Compare it only to an equivalent aggregate; never compare a30day chart to all-time totals.

- [ ] Check UI text, chart and accessible value table agree; unknown/partial periods do not become verified zero. Any device/browser acceptance is a separately requested task.
- [ ] Confirm no collector path changes wallet balances/ledger. Do not execute extra payment/grant commands to test that boundary.
- [ ] Record aliases, approved access scope, source/environment, exact amount/currency/exponent/time, poll status, API/UI result and unresolved cases. Keep the private alias mapping outside Git only if needed and explicitly authorized.

## 7. Stop, report and finish

- [ ] Stop the acceptance step on wrong binding, uncertain amount semantics, excess refunds, unauthorized access, credential errors or conflicts. Keep accepted facts; do not patch production rows, restore the DB or issue financial corrections.
- [ ] If an already authorized collector must be disabled after a failed rollout, record the exact flag/restart operation within that activation's approved recovery scope. Do not claim disabling removes historical facts; startup marks coverage unavailable while retaining facts.
- [ ] Report outcomes by case. Keep #24 OPEN until the actual agreed LIVE/refund/dashboard scope is accepted; SANDBOX success or a deployment alone does not close it.

## Preparation self-review

- Scope matches the owner's checklist-only choice; no runtime/interface change or external mutation was made.
- Source/API names, SQL columns, filters and boundary behavior match the existing code and migrations.
- Source citations support the documented API contracts; least-privilege permission uncertainty and real-data gaps are explicit.
- No new test/CI run is needed for this documentation-only preparation. Existing CI results were not queried again.
