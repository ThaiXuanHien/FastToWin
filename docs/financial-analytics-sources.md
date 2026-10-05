# Financial analytics sources

The shipped configuration enables no financial source. Google Play can collect
authenticated, already verified one-time purchase orders. Its coverage is always
PARTIAL: known-order polling cannot prove complete store history. App Store is
UNAVAILABLE, pending [StoreKit dependency #14](https://github.com/ThaiXuanHien/FastToWin/issues/14)
and an established source of actual buyer charges/refunds. No Apple signed events
are accepted; a future adapter must use official signature, app and environment
verification as well as authoritative monetary amounts.

## Configuration

Use the existing deployment secret-file convention. Set these server-only values
when authorized to enable collection:

| Setting | Purpose |
| --- | --- |
| `FINANCIAL_GOOGLE_ENABLED=true` | Explicit opt-in; omitted/false makes no financial provider requests |
| `GOOGLE_APPLICATION_CREDENTIALS` | Mounted service-account JSON file; same file-path convention as store verification |
| `GOOGLE_PLAY_PACKAGE_NAME` | Exact application package used by both purchase verification and Orders requests |
| `FINANCIAL_COLLECTION_START` | Required UTC RFC3339 instant, no later than now; earliest supported payment time |

The service account needs access to the configured app and the Android Publisher
scope. Missing/malformed credentials or configuration keep financial collection
disabled. Denied credentials and failed provider calls record safe failure status.
No credential values, provider response text, buyer address, purchase tokens or raw
receipts are logged or retained by analytics. This implementation does not provision
accounts, enable APIs, fetch production data, or perform payment/refund operations.

The server initializes financial coverage to UNAVAILABLE at startup, including
when collection is disabled. This explicitly invalidates earlier active-source
coverage, while retaining every accepted fact and its historical freshness. The
first successful supported order establishes PARTIAL coverage. A later failed or
conflicting poll keeps accepted facts PARTIAL and does not advance freshness.
An empty registry or a failed first poll remains UNAVAILABLE, never a verified zero.
Historical payments before the configured boundary are withheld. Widening a
boundary later does not claim the intervening history is complete.

## Capture and reconciliation

Successful provider verification retains order ID, package and LIVE/SANDBOX
classification separately from the existing receipt fingerprint. The durable
recheck registry is written in the same transaction as the wallet grant, with the
same anonymous subject as registration/item facts. Receipt grant idempotency and
Gem amounts are unchanged. Existing verified retries can capture an order without
granting again; an absent provider order ID leaves the financial source unknown.

Each server tick processes at most 25 due orders; ticks run every minute and each
order is rechecked hourly, including fully refunded orders. The database persists
the next check time and last accepted event time. Row locking with SKIP LOCKED
serializes overlapping workers. Restart resumes the registry. The 20-second Orders
request is bounded, does not follow redirects, and reads at most 1 MiB. Credentials
refresh through the official Google auth library. Production endpoints are fixed;
loopback HTTP constructor injection exists for executable local tests, not an
environment-configurable arbitrary provider endpoint.

Each response must match the captured order, configured package request, receipt
fingerprint and single product. Payment/refund arithmetic uses exact decimal and
integer values with ISO currency scale validation; no rounding of money is allowed.
Source timestamps are truncated to PostgreSQL microsecond precision before both
comparison and insertion, avoiding driver rounding at bucket boundaries. Pending
refunds do not become facts. Zero-cost/unsupported or malformed orders are withheld.

Processed partial refunds keep their provider times. A full refund adds only the
remaining original charge at its provider time, and is accepted only when its
reported amount equals the original charge or the exact remaining balance. A zero
remainder creates no zero-valued event. Ambiguous duplicate partial identities,
over-refunds and inconsistent chronological/state evidence are rejected.

Reconciliation inserts new compatible facts atomically under the original order
lock. Repeated snapshots do not add facts. Older snapshots, equal-time additions,
missing previously accepted events, changed amounts/currency/times and decreasing
refund histories are quarantined (`sync_status=CONFLICT`), retaining prior facts.
The last event time is not a revision authorizing correction. Genuine conflicting
historical corrections require a future authoritative reconciliation mechanism;
this release does not silently overwrite them or claim COMPLETE coverage.

The registry and facts have no user foreign key. Deletion erases the private subject
link and ordinary account data; late refunds use the retained anonymous subject,
never recreate a user link and never modify a wallet. Registry fingerprints are
one-way bindings, not receipts. No browser import or financial mutation route exists.

## Local validation and reference bounds

`GoogleOrdersFinancialSourceTest` runs actual loopback HTTP for Orders and OAuth,
with an ephemeral RSA service-account key and signature/scope validation at the
fake token endpoint. `PostgresFinancialSyncTest` migrates isolated UUID schemas and
tests real grants, rollback, deletion, rechecks, exact USD/JPY/VND, duplicate/conflict
handling and concurrent workers. It requires the standard `TEST_DATABASE_*` settings;
missing settings skip PostgreSQL tests and are not sufficient acceptance evidence.
Neither suite calls production providers. No Apple signature path is claimed.

Primary references rechecked 2026-10-05:

- [Google Orders](https://developers.google.com/android-publisher/api-ref/rest/v3/orders):
  buyer-paid total, processed events, partial/full refund fields and last event time.
- [Orders GET](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/get):
  authenticated package/order retrieval.
- [Google Money](https://developers.google.com/android-publisher/api-ref/rest/v3/Money):
  int64 whole units and signed nanounits.
- [Google service-account credentials](https://docs.cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.ServiceAccountCredentials):
  credential-specific file loading, scoping and OAuth refresh.
- [Apple transaction payload](https://apple.github.io/app-store-server-library-node/interfaces/JWSTransactionDecodedPayload.html):
  product/offer price and revocation metadata do not establish actual charged/refunded amounts here.

Full-after-partial amount interpretation remains conservatively bounded as above;
known-order completeness and historical correction authority are explicit limits.
