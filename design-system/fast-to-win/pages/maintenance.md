# Maintenance feedback

This page extends `../MASTER.md`; player surfaces retain the Arcade palette and components.

## States and layout

- `GET /api/status` supplies ACTIVE, ANNOUNCING, DRAINING and MAINTENANCE. `/status` remains a server compatibility alias.
- ANNOUNCING/DRAINING show a compact navy notice with a gold status, estimated countdown and a native 48dp **Details** action. The game remains composed below it. Long messages do not increase the notice height.
- Details uses the existing Arcade dialog, with the full VI/EN runtime message, preparation/drain guidance, expected-end advisory and failed-recheck explanation. Its body scrolls independently of the close action. Text is plain content, never markup.
- Only MAINTENANCE (or the legacy explicit maintenance Boolean) replaces game content with the established full-screen Arcade illustration and centered column capped at 520dp. That screen scrolls at large text sizes and consumes Back; it offers no new gameplay action.
- Expected end is shown in Bangkok time and explicitly advisory. Copy says the game resumes when the server reopens; the clock never unlocks it.

## Reachability and lifetime

- Failed requests do not fabricate ACTIVE or maintenance. Keep the last explicit status and indicate unavailable rechecks in the notice details/full closure. Unknown/unreachable state disables new online intent and checkout.
- New rooms/matches/rematches/tournament starts and checkouts are disabled in DRAINING. Existing match controls remain available; the server owns admission and deadline enforcement.
- Poll server intervals with a 5–300 second bound, retry failures after five seconds. ACTIVE currently advertises 30 seconds; discovering a change can therefore take that interval plus request time. A displayed countdown is only an estimate, not proof of a server transition.
- Root authentication and paid-receipt finalization remain alive when full maintenance replaces game composition. No new checkout is started from the closure screen.

## Accessibility and verification

Use native buttons/dialog semantics, stable status live-region announcements and a descriptive countdown label. Avoid per-second assertive announcements. At large text sizes the compact title may visually ellipsize; its full label and full dialog remain available.

Maintenance-only Compose screenshot fixtures cover phone, desktop, short landscape, 2× text, 1,000-character notices, open notice dialogs and matched game-only constrained layouts. Existing short-landscape game cells are smaller than recommended touch sizes; these renders do not establish universal playability or screen-reader/device acceptance. Admin interactions are separately exercised in Chromium. No unrelated screenshot references are updated.
