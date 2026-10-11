# Matchmaking queue

Tracking: [Issue #27](https://github.com/ThaiXuanHien/FastToWin/issues/27).

## Matching policy

`GameEngine` records a connected account's mode, match type, Elo and entry time after checking admission, level and quota. Entries in different modes or casual/ranked queues cannot match. Repeating the same search retains its wait time; switching queue resets it.

Ranked searches start at ±100 Elo, grow by 50 every 10 seconds and stop at ±300. A pair is eligible when its Elo difference fits the larger of their two wait ranges. Eligible candidates are ordered by nearest Elo, the existing team-mode latency preference, then entry time. Casual searches retain their latency/FIFO policy. Blocked players cannot pair; a failed block lookup fails closed and can retry on the next pass.

Eligibility is filtered **before** ranking candidates. Previously a nearer but newly queued opponent outside their current range could hide an older eligible opponent. Ranking one candidate and only then testing eligibility left both waiting unnecessarily.

## Queue processing

Arrivals are inserted atomically before checking candidates, making simultaneous arrivals visible to each other. The application also calls `advanceMatchmaking` every second so entries are reconsidered after their Elo range expands without another client command. The client already displays its expanding range locally; idle sweeps send no unsolicited searching acknowledgement.

Sweeps reuse queued profile/Elo data and do not query profiles or quota while waiting. Only one sweep runs at a time, older entries are processed first, and a blocked pair is checked at most once per sweep. Each completed pair is delivered immediately through a callback before checking later pairs, because a published game's timer is already running.

Block checks happen outside the engine mutex. Before reserving a room, the engine checks both original entry identities, connection state, room/tournament membership, queue compatibility and maintenance admission again under the mutex. Cancellation, rejoining or another match invalidates stale attempts. A reserved pair uses the existing prepared-start flow for quota, event rewards and durable room publication; waiting alone never consumes a match allowance.

This queue remains local to one server process. Horizontal scaling still requires the shared-state work described in `production-capacity-and-cost.md`; the change does not introduce bots or cross-instance matchmaking.

## Verification

`MatchmakingQueueTest` uses a controlled clock and I/O barriers to exercise expansion, candidate selection, queue switching, cancellation/rejoin, disconnects, block failures, overlapping sweeps, concurrent arrivals, quota exhaustion and maintenance. A four-player regression ensures an earlier pair is notified while a later lookup is stalled.

`GameWebSocketTest.background matchmaking delivers start to both waiting sockets after elo expansion` exercises the real application scheduler and both socket deliveries without calling the engine tick directly or sending a second search command. Existing engine, event, maintenance and PostgreSQL tests cover the reused start/settlement path.
