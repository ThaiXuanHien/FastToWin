# Public Tournaments and PWA Input Recovery Design

Date: 2026-09-23

## Scope

This change extends the existing private tournament flow with discoverable public tournaments and fixes two native web-input regressions:

- A tournament can be public or private. New tournaments default to public.
- Players can browse, search, filter, and join public tournaments that are still accepting players.
- Tournament hosts can invite eligible friends whether those friends are online or offline.
- The tournament-name example disappears immediately when typing in an Android PWA while the field label remains visible.
- An iOS PWA restores its full usable viewport and bottom navigation after the software keyboard closes.

The existing first-transition safe-area defect from tutorial to Home is not part of this change and remains separate follow-up work.

## Domain Model and Compatibility

Add `TournamentVisibility` with `PUBLIC` and `PRIVATE` values.

`TournamentSnapshot` persists the visibility in `snapshot_json`. Its serialization default is `PRIVATE`, so active tournaments written by older server versions remain private after an upgrade. `ClientMessage.CreateTournament` carries a visibility whose default is `PUBLIC`, matching the approved create-screen default for new clients.

Public discovery exposes a compact `PublicTournamentSummary`, not a full bracket snapshot. The summary contains:

- tournament ID and name;
- host player ID and display name;
- game mode;
- current and maximum player counts;
- entry fee and current prize pool;
- creation time.

Only tournaments satisfying all of the following may appear:

- visibility is `PUBLIC`;
- phase is `LOBBY`;
- participant count is below `maxPlayers`.

The current in-memory tournament registry remains authoritative while the server is running. Active tournament snapshots are already restored from PostgreSQL during startup, so visibility persists without a new relational column. Missing visibility in an older JSON snapshot decodes as `PRIVATE`.

## Protocol and Server Flow

Add a request for public tournaments with optional query, game-mode, player-count, and fee-category filters. Fee category is one of all, free, or paid. The server performs filtering and returns at most 50 summaries, ordered by fewest remaining slots (while still greater than zero) and then newest creation time. This fills nearly complete tournaments first. Blank filters mean “all.”

Add a dedicated join-public-tournament command. Under the existing tournament mutex, the server revalidates that the tournament:

- exists and is public;
- remains in the lobby and has a free slot;
- does not already contain the player;
- does not conflict with another room, tournament, or matchmaking entry.

Paid entry uses the existing idempotent wallet transaction mechanism. The transaction source remains tied to tournament ID and player, so retries cannot charge twice. A failed or racing join must not alter the wallet or prize pool. Invitation acceptance and public joining share one internal admission operation so capacity, busy-state, payment, participant insertion, persistence, and response behavior cannot drift.

Successful create, join, leave, cancel, and start operations emit a lightweight public-list invalidation to connected account clients. A client currently viewing discovery reissues its latest filtered request; other clients do not fetch unnecessarily. Reopening the Tournament screen also requests a fresh list, providing recovery after reconnect without relying exclusively on invalidations.

Expected errors are localized and distinguish at least: no longer public, full, already started/cancelled, already joined, player busy, and insufficient Gold.

## Tournament UI

When the player is not currently in a tournament, the screen order is:

1. Public tournaments.
2. Tournament invitations.
3. Create tournament.
4. Recent tournaments.

The public section contains:

- a name search field;
- compact filters for game mode, size, and fee category;
- loading, empty-result, and reconnect states;
- cards showing name, host, mode, occupancy, entry fee, and prize pool.

Search/filter controls are local UI state but cause a debounced server request. The initial request has no filters. Public cards use a direct **Join** action:

- a free tournament joins immediately;
- a paid tournament first shows a confirmation containing tournament name, Gold to be deducted, and current prize pool;
- dismissing confirmation has no server or wallet effect.

The create form adds a public/private segmented choice and defaults to public. Private tournaments never appear in discovery and remain invitation-only.

## Friend Invitations

In a host-owned lobby with capacity remaining, the invitation section considers all friends who are not already participants. Offline friends are no longer filtered out. Each row retains the presence indicator and an **Invite** action; the backend may deliver the invitation immediately or through push notification according to the recipient's notification settings.

If there are no eligible friends, the section stays visible and explains that the host must add friends before inviting them. The section is hidden only after the tournament is full or leaves the lobby phase.

## Native Web Placeholder

On web targets, the HTML input owns the placeholder. `AppOutlinedTextField` passes the placeholder string into `NativeWebTextInput`, suppresses the Compose placeholder layer for the native path, and keeps the Compose label decoration.

This makes the browser remove the example synchronously as soon as its DOM value becomes non-empty, independent of the controlled-value reconciliation delay. Android and other non-web targets keep the existing Material `OutlinedTextField` behavior.

The change applies to every native web text field, preventing the same placeholder overlap in other PWA forms.

## iOS Keyboard and Viewport Recovery

The current viewport bridge samples `visualViewport` immediately, over animation frames, and once after a short timeout. iOS may finish its keyboard-dismissal animation after that final sample without emitting another useful resize event, leaving the Compose root at an intermediate height.

The PWA bridge will add focus-driven recovery:

- native-input `focusin` records the last full pre-keyboard viewport height;
- native-input `focusout` begins a bounded stabilization cycle;
- the cycle samples `visualViewport.height + offsetTop` over animation frames and timed checkpoints;
- it stops after the viewport has returned to the pre-keyboard height and two equal samples are at least 100 ms apart, or after a 1.2-second deadline;
- each accepted height updates `--fast-to-win-viewport-height`;
- the final stable height emits `fasttowin-viewport-change`, forcing Compose to reread safe-area values.

Only viewport measurement changes. The existing iOS gesture-navigation suppression and safe-area cap remain unchanged. Timers are shared/cancelled so rapid focus changes cannot leave multiple competing recovery loops.

## Testing

Implementation follows test-first development.

### Protocol

- An older tournament snapshot without visibility decodes as private.
- A newly created tournament command defaults to public.
- Public summary and list messages round-trip through `ProtocolJson`.

### Server

- Only public, lobby-phase tournaments with capacity are discoverable.
- Search and filters return the expected capped ordering.
- Private tournaments remain invitation-only.
- Public joining validates busy state and capacity.
- Two concurrent attempts for the last slot admit exactly one player.
- Paid public joining charges and increases the prize pool exactly once.
- A failed join leaves wallet and prize pool unchanged.
- Invitation acceptance and public joining exercise the same admission rules.

### Compose UI

- Create form defaults to public and can select private.
- Public list renders search, filters, occupancy, fee, and prize data.
- Free join invokes immediately; paid join requires confirmation.
- Offline eligible friends retain an Invite button.
- An empty invitation section explains how to add friends.

### Web E2E

- Android PWA tournament-name input initially shows the example, then no longer matches `:placeholder-shown` after typing while retaining its accessible label.
- iOS PWA keyboard close can publish a delayed final viewport height; stabilization eventually restores the root height and keeps the bottom navigation within the visible viewport.
- Existing native-input selection, composition, safe-area, and PWA navigation suites remain green.

## Rollout

Deploy protocol, server, and clients together. Because defaults preserve old snapshots as private, no existing tournament is exposed. After deployment, verify on production with two disposable accounts: create one public paid tournament, discover it from the other account, confirm the charge prompt, join, and verify both player count and wallet balance. Then repeat with a private tournament to prove it is absent from discovery but joinable by invitation.
