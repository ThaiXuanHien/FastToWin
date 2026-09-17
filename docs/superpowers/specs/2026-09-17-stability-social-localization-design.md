# Stability, social, clan economy, and localization design

## Scope

This batch fixes the approved production issues across the server, shared UI,
Android, and Web:

1. A clan leader leaving transfers ownership to the longest-serving remaining
   member; an empty clan is deleted.
2. Clan creation atomically costs 2,000 gold and 20 gems.
3. System/browser Back is consumed exactly once without a stale dimmed preview.
4. Claiming one mission reward does not visually recreate unrelated cards.
5. Web text fields preserve composition and selection. Typing `123` must remain
   `123`, password selection must remain stable, and plain input must not gain
   unexpected accents.
6. Resuming after a long background period reconnects and clears transient
   connection errors after the session is restored.
7. Web content respects safe-area insets.
8. Scrolling the frame collection does not reload unchanged avatars.
9. Header gold and gem balances open the corresponding Shop tab.
10. Incoming friend requests are surfaced as Accept / Later / Decline prompts.
    Later suppresses the prompt for the current app session but leaves the
    request pending and prompts again in a later session.
11. The Home rank uses the authoritative player rank returned by the server.
12. Settings expose only Vietnamese and English. Legacy saved locale codes are
    still readable but resolve to English unless they are Vietnamese.
13. Changing a tournament entry fee keeps the dialog geometry stable.

## Data and transaction rules

Clan creation and clan departure are single PostgreSQL transactions. Creation
locks the wallet row, validates both balances, inserts the clan and leader, and
then deducts both currencies. Any failure rolls back all writes. Departure locks
the clan and its members. The successor is the earliest `joined_at` member,
with player id as a deterministic tie-breaker. If no successor exists, the clan
and dependent records are deleted.

## UI state rules

Repeated UI items use stable keys. Async operations expose item-scoped state so
an update to one mission cannot reset siblings. Avatar requests use a stable
model/cache key based on the avatar URL; changing only the selected frame must
not restart the avatar request.

The tournament fee area always reserves the custom input slot. Selecting a
preset changes its visibility/enabled state without inserting or removing
layout height.

## Web input and navigation rules

The native HTML input remains authoritative while focused or composing. Compose
state is synchronized after the browser `input`/`compositionend` event without
rewriting the DOM value and caret on every recomposition. External value changes
are applied only when they differ from the last browser-authored value, while
preserving `selectionStart` and `selectionEnd`.

Browser `popstate`, Android Back, and app header Back all enter one navigation
dispatcher. A browser-originated Back updates app state without pushing or
replacing another history entry.

## Compatibility

The existing localization enum and catalogs remain decodable so persisted
settings and older clients do not fail. Only the selectable/public language set
is reduced to Vietnamese and English.

## Verification

Add repository integration tests for clan transactions, state/unit tests for
navigation, reconnection, friend prompt deferral, locale migration, and rank
mapping, Compose tests for mission/frame/header/tournament stability, and Web
E2E coverage for text selection, numeric ordering, history, and safe areas.
