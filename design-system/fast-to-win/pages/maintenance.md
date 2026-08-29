# Maintenance screen

This page extends `../MASTER.md` without changing the global design direction.

## Purpose

- Explain planned server maintenance without confusing it with a connection failure.
- Preserve trust by confirming that account progress remains safe.
- Lock online interaction until the server explicitly reports that maintenance has ended.

## Layout

- Full-screen Arcade backdrop with a centered content column capped at 520 dp.
- Gold maintenance status pill, server-and-lightning illustration, short headline and body copy.
- Status panel explains that the app will reopen automatically.
- No buttons, links, pull-to-refresh, or other actions are exposed.
- Content scrolls for small-height devices and large system text.

## Behavior

- Show only when `GET /status` returns `maintenance: true`.
- A timeout, failed status request, disconnected socket, or unavailable network must never activate it.
- Consume system Back and iOS edge-back gestures while active.
- Poll the status endpoint at the server-provided interval and hide only after an explicit `maintenance: false` response.
- Keep the last explicit maintenance state when a later poll fails, so a long maintenance window remains locked.

## Accessibility

- The illustration and status icons are decorative; the explanatory copy carries the full meaning.
- Content scrolls at large text sizes without introducing an interactive affordance.
