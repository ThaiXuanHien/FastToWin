# Authoritative analytics facts

Task 1 of Issue #24 adds collection and historical retention only. Aggregate APIs,
provider ingestion and the dashboard are subsequent tasks.

## Transaction contract

`AnalyticsFacts` accepts the caller's JDBC `Connection` with autocommit disabled.
It neither opens another connection nor commits. Any fact/link/coverage failure
rolls back with the account/session or wallet/ownership operation.

| Interface | Authority |
| --- | --- |
| `recordRegistration(connection, userId, registeredAt)` | First successful REGISTERED transition; server session timestamp in epoch milliseconds |
| `recordItemPurchase(connection, eventId, userId, itemId, currency, amount, purchasedAt)` | Successful COSMETIC_PURCHASE debit; identical wallet UUID, positive exact debit amount, currency, item and UTC epoch timestamp |
| `subjectKey(connection, userId)` | Shared private random UUID for an existing identity; transaction locks the live user row before looking up/creating the key |
| `eraseIdentityLink(connection, userId)` | Removes the private mapping in the account deletion transaction; caller holds the user row lock |

Direct registration and guest upgrade write after the successful identity/session
changes and before commit. Login/session replacement does not record registration.
Guest `created_at` and mutable `updated_at` never substitute for registration time.

The existing repository purchase method debits GOLD and acquires ownership before
writing the wallet and item fact with one event UUID and timestamp. Duplicate
ownership, insufficient balance, nonpositive price or a fact error produces no
committed debit or purchase fact. The price supplied to this server repository is
also the price actually debited; browser input is not a financial authority.
The retired `GameEngine` shop command remains rejected. No new shop route or Gem
purchase path is introduced; GEMS is a separate supported fact currency for a
future authoritative transaction. Profile reads, default/granted ownership,
equipping, rewards and currency exchange do not produce purchase facts.

Deletion locks the eligible active registered user, scrubs its mapping and deletes
the identity in one transaction. Failure restores the mapping and identity. Writers
take the same user lock; a writer waiting behind a committed deletion cannot create
a new key for the absent account. Facts retain their random key for historical
counts/distinct, with no account lookup available after deletion.

## V52 schema

| Table | Durable contract |
| --- | --- |
| `analytics_subject_links` | Private `user_id` UUID primary key/FK with cascade; unique random `subject_key` UUID |
| `analytics_registrations` | `subject_key` primary key, `registered_at` and `recorded_at` TIMESTAMPTZ; one first transition |
| `analytics_item_purchases` | Wallet `event_id` UUID primary key; anonymous subject, stable item, GOLD/GEMS, positive BIGINT amount, purchase/record times |
| `analytics_financial_events` | Minimal exact future ledger: source/environment/event primary key; PAYMENT/REFUND, original transaction ID, product, anonymous subject, positive NUMERIC(38,0) minor amount, ISO currency/exponent, source occurrence/record times |
| `analytics_source_coverage` | Metric/source/environment primary key; COMPLETE/PARTIAL/UNAVAILABLE, nullable collection start/freshness and update time |

Retained fact tables have no foreign key to users or the private mapping. Financial
payments additionally have a unique source/environment/transaction key; refunds
have their own event keys. Time/currency/item/product/source indexes support the
next task's bounded aggregate queries. Wallet entries outside cosmetic purchases
keep their existing database timestamp default.

Financial rows are fixtures/foundation only here. No provider source is enabled,
no receipt/token is stored and no financial ingestion adapter is implemented.
Future adapters must use the shared subject key in their caller transaction and
verify amounts, refund limits and source coverage before reporting live money.
The foundation alone does not prove a store is complete or a refund is legitimate.

## Coverage and history

V52 initializes SERVER/LIVE REGISTRATIONS and ITEM_PURCHASES coverage at the
database migration timestamp, with freshness present. COMPLETE means collection
from that start; a query reaching before it must report PARTIAL, not historical
zero. Updates use monotonic database wall time, including transactions committed
out of start order. Coverage updates roll back with failed facts.

GOOGLE_PLAY/LIVE and APP_STORE/LIVE financial coverage starts UNAVAILABLE with
null start/freshness. Empty fixture tables or existing store Gem grants do not
establish revenue coverage. Missing coverage is unknown, not zero.

Migration invents no old registration timestamps, item identities, prices, Gem
sales or payment amounts. Existing REGISTERED inventory is a separate current
count, not a historical series. Trusted old wallet debits may later support a
separate total-spend query; missing item identity cannot establish top-item
coverage. Task 2 must preserve these distinctions and calculate range distinct
independently of bucket distinct sums.
