# Fast To Win — 2D Arcade Design System

This document is the visual source of truth for Android and iOS. Runtime tokens live in
`shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/theme`.

## Direction

- Personality: fast, playful, competitive, friendly.
- Style: crisp 2D vector shapes, bold hierarchy, controlled gradients, small static speed accents.
- Preserve readability and navigation before decorative game effects.
- Use vector icons for controls and navigation. Emoji remain limited to player reactions.

## Token layers

### Primitive

| Role | Value |
|---|---|
| Electric blue | `#246BFD` |
| Deep blue | `#1552D8` |
| Violet | `#7048E8` |
| Coral | `#FF4D67` |
| Reward gold | `#FFC928` |
| Currency mint | `#39DDB0` |
| Arcade navy | `#071A44` |
| Cloud background | `#F4F8FF` |

### Semantic

- Primary/action: electric blue.
- Progression/collection: violet.
- Opponent/warning: coral.
- Reward/selected navigation: gold.
- Gem/social/success: mint.
- Header and top-level navigation: arcade navy.
- Background and surfaces always come from `MaterialTheme.colorScheme` for light/dark support.

### Components

- Header: navy, compact separated currency pills, no settings action.
- Bottom navigation: straight full-width navy bar, no shadow, gold selected indicator.
- Primary hero: blue-to-violet gradient, 2 dp highlight border.
- Cards: semantic surface, 16–22 dp radius, 1 dp accent border, low elevation.
- Buttons: minimum 48 dp touch target; yellow is reserved for the strongest play/reward CTA.
- Game board: deterministic blue/violet/coral/surface rhythm; completed cells lose emphasis.
- Player score: local player uses blue, opponent uses coral.

## Production artwork

- `arcade_home_hero.png`: full-bleed arena art for the home match hero. Keep text on the dark left side.
- `fast_to_win_logo_banner.png`: opaque 3:1 brand banner used by authentication screens.
- `arcade_screen_background.png`: shared portrait arena backdrop with a quiet center crop.
- `arcade_room_portal.png`: room browser and invitation portal artwork.
- `arcade_leaderboard_trophy.png`: individual/clan leaderboard and season-history artwork.
- `arcade_clan_crest.png`: crossed-sword clan crest and clan-list thumbnail.
- `arcade_tournament_trophy.png`: four-player knockout bracket illustration.
- `arcade_shop_chest.png`: shop and empty-catalog illustration.
- `arcade_notifications_inbox.png`: notification hero and empty-inbox illustration.
- All assets live in `shared/src/commonMain/composeResources/drawable` and are packaged through
  Compose Multiplatform resources for Android and iOS.
- Do not extract art from a compressed UI mockup. Regenerate or export each source asset at its intended
  aspect ratio so edges, text, and safe crop regions remain clean.

## Typography and spacing

- System sans-serif keeps Vietnamese rendering reliable across Android and iOS.
- Headings use extra-bold/black weight; body remains regular.
- 4/8 dp spacing rhythm; adaptive page gutters remain controlled by `ResponsiveScreen`.
- Never shrink body copy to compensate for long content; wrap and scroll instead.

## Interaction and accessibility

- Android touch targets are at least 48 dp; iOS layouts retain at least 44 pt.
- Color is decorative support, not the only indicator of state.
- Disabled controls use native disabled semantics.
- Loading feedback must reserve its layout bounds to avoid UI flicker.
- Motion is optional and must respect the existing visual-effects preference.
- High-contrast board mode remains independent from the Arcade palette.
