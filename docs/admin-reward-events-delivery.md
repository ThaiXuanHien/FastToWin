# Reward events local delivery — 08/10/2026

Approved Issue #18 and its event section in #21 are complete locally on `codex/admin-foundation`. All four tasks and the mission-icon amendment passed independent specification/quality reviews. Whole-branch review from `b9989de` through `7164816` found one Important integration omission: maintenance admin routes remained registered when admin was disabled. Commit `06a1d05` fixes that guard while preserving the durable runtime; report `299ed1f` and the scoped rereview close it with no remaining Critical/Important findings. This is local acceptance, not remote integration or deployment.

The player banner/details/result/history use Mission's filled MonetizationOn/GoldColor and Star/Material primary. The gold glyph has a small existing navy background for LIGHT contrast. Exact amounts, factors and localized combined accessibility descriptions remain unchanged.

## Verified evidence

The accepted local source/reference map contains91 files with zero hash mismatches at `299ed1f`. Retained scoped executions are not summed into a fictitious full-suite total:

- Final engine covering run:227 server tests /16 suites; includes30 event-engine and5 real PostgreSQL settlement tests.
- Player:203 shared and96 protocol tests; localization scanner, dev APK, JS/Wasm compile pass. Mission-icon amendment regenerates and validates the same24 Compose scenarios, each stage24 tests with zero failures/errors/skips.
- Admin/integration:56 server tests /6 suites; full admin browser145 once and final event25 after the scoped CSS correction;22 actual browser captures inspected.
- Final off-switch regression: compiling RED1 test failed on expected GET/POST404 versus actual503; after the guard,35 tests /6 suites pass with zero failures/errors/skips. The regression also verifies public status remains available and durable runtime transitions/admission continue with admin disabled.
- Actual local browser/Ktor/WebSocket/PostgreSQL integration captures independent ×2 GOLD/XP, preserves the snapshot after cancellation/expiry, credits one MATCH per participant, and returns a fresh rematch to neutral factors. Winner160 GOLD/48 XP, loser40/16; no EVENT_BONUS credit.

Evidence and independent review records are retained under `.artifacts/admin-reward-events-delivery/`, including `accepted-final.json`, `acceptance-evidence.md`, `final-review/` and `final-fix/`. These are local ignored artifacts. Task/fix reports are also preserved in Git history.

## Boundaries and followups

No push, merge, deployment, production event, provider or data reset was performed. Physical-device/iOS, browser cross-engine and production/load acceptance are unclaimed. The four carried nonblocking groups remain: full-history event query cost, periodic ready-to-loading layout movement, gift aggregate numeric JSON precision above2^53−1, and inherited toolchain/environment warnings. The former historical source-test RED sequence limitation remains explicit; later RED evidence cannot retroactively establish the first test-first sequence.

Issue #20 compensation and #22 announcements remain pending. Compensation preflight confirms the reusable gift mailbox/transaction boundary, but incident/round uniqueness and a server-owned COMPENSATION wallet source are not implemented by this event delivery. Activity recipient filters require a separate verified timestamp/retention/identity source before exposure.

## Controller rulings and costs

The following list preserves every execution-ledger ruling in order. The cost describes the tradeoff or rework exposure, not an assertion of a new defect.

1. Reuse the authorized feature checkout and approved design: avoids a repeated approval checkpoint; cost is the broader accumulated branch review.
2. Derive event state from server time and keep published definitions immutable: preserves frozen-source versions; clients must refresh instead of expecting timed version increments.
3. Allow neutral1/1 drafts: matches approved independent factor ranges; admins can publish an inert event, so preview must state its factors.
4. Stage/recover quota before the event gate, acquire that gate before the engine mutex, and avoid another pooled borrow inside capture: removes a concrete pool-two cycle; admission/recovery is more complex and needs concurrency evidence.
5. Serialize memory-only source callbacks with a coroutine mutex outside the admin transaction callback: preserves atomic publication without blocking under admin mutex; development/test starts serialize.
6. Try the engine lock without waiting under a pooled event transaction and retry only after release: avoids the existing engine/JDBC cycle; bounded contention may return a safe retryable error.
7. Retain the original Task1 source-test RED limitation, using real route/mutation evidence plus independent review: the initial test-first sequence evidence is weaker and cannot be reconstructed retroactively.
8. Leave legacy creator/publisher unknown when audit evidence is ambiguous: avoids invented identities; UI must explicitly show unknown values.
9. Use a prepared-start helper, stable quota identity and detached persistence fence: saves the frozen context before playable publication; additional recovery/admission states must reconcile correctly.
10. Make quota reservation lifecycle mandatory on repository adapters: avoids an unsafe consume/no-op fallback; external custom implementations must migrate before recompiling.
11. Remove the client outgoing Cache-Control header and rely on server no-store, network-only worker and uncached Ktor transport: avoids an incompatible CORS preflight; freshness depends on those tested boundaries.
12. Continue the interrupted final review with the strongest available worker after astra usage limits: avoids abandoning the authorized review; architecture capacity is lower, so full coverage/evidence limits are recorded and accepted concurrency reviews retained.
13. Defer unmeasured event full-history query optimization: keeps the final fix focused; catalog latency/memory can grow until a dedicated filtered/indexed-query followup.
14. Defer periodic ready-to-loading presentation movement while retaining expiry/failure guards: avoids stale ACTIVE authority; refresh may still visibly shift layout.
15. Defer very large gift aggregate JSON precision to a decimal-string/BigInt transport amendment: durable claim accounting remains exact; sufficiently large displayed totals may round meanwhile.
16. Defer inherited tooling/environment warning cleanup: avoids changing screenshot/toolchain configuration during a correctness fix; warning noise and compatibility limits remain.

## Banner refresh follow-up — 10 October 2026

The periodic ready-to-loading movement above is now corrected locally. Pending list refresh preserves READY items and the server-time anchor, with `isRefreshing` separated from initial load. The banner reserves 24 dp for its accessible loading indicator, keeps existing coin/star assets and title/factor/status geometry, and does not move focus. Failed/unavailable refresh still hides ACTIVE authority; expiration keeps ticking while pending. Initial loading/known empty, pagination guards, replacement results and reset/account/language/view fences remain distinct. No backend, protocol, reward calculation or database change is included.

Bounded evidence is retained at `.artifacts/reward-event-refresh/`. Before production edits, the controller suite ran 9 tests with 2 expected assertion failures (READY was LOADING, retained item count was zero). Final affected gate passed 16 shared cases across controller/game-state/API suites, no failures/errors/skips, plus localization scanner/fixtures, dev APK and JS/Wasm compilation. The existing Wasm opt-in and screenshot-plugin notices remain; this is not a warning-free delivery.

Four new Compose comparison screenshots show READY/pending at 375 dp light Vietnamese, 1024 dp dark English, 320 dp/font 1.6 dark Vietnamese and 844 dp landscape light English. All were inspected. The first design used a long loading subtitle and increased the large-text card from 287 to 359 raster pixels; the fixed-slot indicator corrected this to 287/287. Final border measurement confirms equal READY/pending heights in all four captures. Baselines were created only after inspection; filtered update and validation each passed the same 4 scenarios, not 8 unique tests. Initial validation lacked new baselines and is recorded as setup failure, not behavioral RED. Existing baseline files were untouched.

This evidence is host-side Compose rendering and controlled HTTP-gateway timing, not physical-device/TalkBack/iOS/browser gameplay or production/load acceptance. The build is local; no push, merge, deployment or production event was performed. Historical review/rulings above remain historical records, rather than current unresolved banner behavior.
