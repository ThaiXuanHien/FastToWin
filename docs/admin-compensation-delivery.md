# Incident compensation — local delivery record

Approved Issue20 and admin integration Issue21, implemented on `codex/admin-foundation`. This document records local acceptance; GitHub issues remain open until integration/deployment acceptance. No push, merge, production publication or data reset was performed.

## Behavior

Admins manage bilingual incidents and explicit compensation rounds from the white Material **Đền bù** console. Owner/Operator prepare drafts and preview; only Owner publishes/cancels; Support reads history. Publication freezes recipient IDs and player-safe content, and first publication freezes the incident. Supplemental rounds require the next explicit number and a separate reason; retries reuse their committed response.

ALL_REGISTERED and verified USER_IDS are supported. ACTIVITY is explicitly unavailable because timestamp/retention/completeness coverage is not established; requests are rejected with `ACTIVITY_SOURCE_UNAVAILABLE`, never inferred from last-seen or registration dates.

Compensation uses the existing atomic mailbox delivery/claim and separate server-owned COMPENSATION wallet source. Each round's UUID is its gift campaign/source ID. Event multipliers and earned-asset leaderboard totals do not apply. Cancelling unclaimed recipients preserves paid assets and receipts. Internal evidence, cause codes and admin identities stay out of public mailbox content. New aggregate/count JSON uses decimal strings and admin BigInt formatting/pagination.

The player mailbox retains legacy nullable metadata compatibility, Vietnamese/English labels, existing claim/retry/profile refresh and Mission filled coin/star reward glyphs. Admin dialogs have labelled 48px utilities, UTC input/display, separate preview/publication confirmation, pending-dismissal guards and current session/view/dialog/import fences.

## Accepted task evidence

| Scope | Source/report | Retained verification |
|---|---|---|
| Incident/store/source foundation | e19b928 / 42840c3 | 51 server tests including 8 PG compensation and 17 PG legacy gift; 97 protocol; package; review 0 Critical/Important. Early CORS no-store Minor closed in Task2. |
| Round authority/preview/publication/report | fa0a5f7 / f7e1aae / spec62b529d | 114 server tests/18 suites, including45 actual PG, 0 failures/errors/skips; package; architecture/spec review clean. |
| Native mailbox | e6eb69b / 6dc8591 | 100 protocol/204 shared;50 unique Compose previews (46 new+4 legacy), update+validate the same50; APK/JS/Wasm; native review approved. |
| Backend scanner closure | 3962a4f / 4482f28 | Identity legacyFallback wrapper retains complete content;8 affected content tests/scanners/fixtures/package; scoped review clean. |
| Admin/integration | 85ddb127873171bd22a39e821a38b22df3a289e8 | Full admin once:173passed/2configuration-gated live skips of175; final affected30/30 after supplemental-reason presentation fix;2 asset tests/package; task review0Critical/Important. |

These gates overlap and ran at different task source identities; they are not one additive test total. Historical invocation text for some RED/browser/live runs was not retained, so reports preserve the available logs/XML/JSON/scripts without reconstructing commands or retroactive RED. Backend and browser toolchain/environment warnings are recorded.

## Actual local integration and visual checks

The owned browser/Ktor/PostgreSQL run used `http://127.0.0.1:18082` with a separate UUID schema. It created an incident, published round1, delivered and claimed, replayed identical publication/claim requests, created explicit round2 with previous-paid counts1/1/1, claimed and cancelled the unclaimed remainder. A new account after publication was excluded. Served JS/CSS hashes matched tested source; public JSON excluded internal fields; traces were off.

Read-only SQL evidence shows exactly two COMPENSATION receipts for the claimant (100/30 and40/12), total140Vàng/42XP, no ADMIN_GIFT or EVENT_BONUS receipt, lifetime earnedGold/Gems zero, lateRecipientCount zero and claimed receipts retained after cancellation. Real rollback/concurrency/restart proof is retained in the backend repository tests; this browser run does not claim to repeat every race scenario.

Admin settled images cover360/768/1024/1440 list/forms/preview/report, 200% text with normal/reduced motion, top/footer, geometry/focus and reachable controls. Native previews cover phone320/430/desktop1024,VI/EN,dark/light,font1.6,states/legacy/longtext/landscape. Long-height native previews prove layout rather than physical scrolling. No physical Android/iOS, TalkBack, load/provider, production or deployment acceptance is claimed.

Evidence is retained under `.artifacts/admin-compensation-delivery/`, with task reports, review packages and the ledger archived at final handoff. The combined source/reference hash map overlays previous accepted event evidence with the latest compensation task identities; preparation verified176 paths with0mismatches. Active backend logs are mutable/locked and are excluded from immutable-file comparisons; settings/passwords stay ignored and are never committed.

## Final whole-branch review

Independent whole-branch review of actual merge-base `b9989de8c590016d1fd5bfc9ad56df7e981f7350` through source `85ddb127873171bd22a39e821a38b22df3a289e8` is technically ready for authorized integration: **0 Critical, 0 Important, 5 nonblocking Minor groups**. It covered the accumulated admin/gift/maintenance/analytics/events/compensation production integration, migration, client/service-worker seams, retained tests and actual images. No final fix wave or scoped rereview was necessary; neither was used.

Follow-ups remain: event full-history query cost; event banner movement during refresh; older ordinary-gift numeric JSON precision (new compensation is exact); explicit 2/3/1 prior-count UI assertions; inherited toolchain/environment warnings. None demonstrated a blocking defect at the reviewed source. Earlier disabled-admin maintenance and incident early CORS/no-store defects are closed.

The first strongest-model final review was interrupted by usage limits and left only interim findings; a fresh usable-model independent review owns the complete verdict. Review records and ledger preserve this limit. The reviewed source does not change in this documentation-only delivery commit.

The owned preview runtime was restored after both Java and local PostgreSQL had stopped: exact workspace bin/data/process checks, stopped status and refused SQL preceded one direct hidden PostgreSQL launch; authenticated SELECT1 and existing-schema check preceded Java restart. No bootstrap, schema recreation or data reset occurred. Resumed GET `/admin/` returned200 with no-store. The original execution evidence is retained separately from resumed-runtime metadata.

## Remaining integration

Branch stays local and unmerged. Issue22 admin notifications and verified activity telemetry are outside this compensation plan. Read `docs/admin-compensation.md` for exact APIs, limits, source capability and runtime behavior. Use separate production configuration and explicit release authorization for integration/deployment.

## Counter mapping follow-up — 10 October 2026

This bounded tests-only follow-up addresses the retained M6 observation and the explicit prior-count assertion follow-up for Issue20/21. The compensation renderer already mapped fields correctly; no production rendering, API, reward or database behavior was changed. The default legacy fixture is unchanged. A separate published-round fixture uses distinct decimal-string values for all seven report counters and both paid totals, so a swapped field cannot hide behind equal zeros.

Ten new browser cases pair each report label with its exact literal value for Owner/Operator/Support at360/1440. Paid Gold3700 and XP1110 are asserted independently by displayed text and accessible icon names. Preview for Owner/Operator and Owner's final confirmation explicitly pair prior distinct users2, paid receipts3 and selected prior users1 with their respective labels. Opening confirmation does not send publication/cancellation requests.

Final verification: **12 unique browser cases passed, zero failures/skips** (10 new plus two existing published/cancelled history cases), not a full admin/server suite. Six settled screenshots under `.artifacts/compensation-counter-mapping/screenshots/` were inspected at360/1440 after scrolling the report/preview counts into view; they retain the existing white Material dialogs and Mission coin/star icons.

A controlled browser-response mutation swapped expired/cancelled counters and prior-user/receipt counts without changing production source files. Both old history cases still passed, while the new report and preview cases failed on17→19 and2→3. This establishes assertion sensitivity, not a discovered production bug. Earlier locator and accessible-name assertion errors were corrected test defects, not behavioral RED. Evidence retains `baseline-check.log`, `mutation.log`, `final.log`, `final.xml` and the local mutation harness. These are synthetic HTTP fixtures rendering the real admin assets; no live data, provider, physical-device or release acceptance is implied.

Runnable final gate from `e2e`: `node node_modules/playwright/cli.js test -c admin.playwright.config.mjs admin-compensation.spec.mjs admin-compensation-counters.spec.mjs --grep 'published history is read only|counter mapping' --reporter=list,junit`. The mutation gate used `.tmp/compensation-counter-mutation.config.mjs` and the same selected four baseline/mapping cases. Local progress preserves the earlier delivery/review records as historical evidence.
