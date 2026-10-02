# FastToWin admin — dark console

Scoped override of `../MASTER.md` for `/admin/`. Player-facing arcade styling remains defined by the master design system.

## Direction

Modern, minimal internal console with a graphite background, restrained blue accents and clear typography. Apply `.agents/skills/ui-ux-pro-max/SKILL.md` when changing this interface. Use the Minimalism & Swiss Style guidance for hierarchy and spacing; discard marketing landing-page patterns from generic search results.

## Tokens

Runtime source: `server/src/main/resources/admin/admin.css`.

| Role | Value |
|---|---|
| Canvas | `#0B0E14` |
| Surface | `#131821` |
| Raised / neutral badge | `#1A2230` |
| Primary action | `#2463EB` |
| Primary hover | `#2C6DEB` |
| Selected navigation | `#192B4B` / `#B6D0FF` |
| Main text | `#F2F5FA` |
| Secondary text | `#A4AFC1` |
| Divider | `#2A3342` |
| Input / secondary button boundary | `#596579` |
| Keyboard focus | `#9DC1FF` |
| Success | `#7BDAB1` / `#102D29` |
| Error / revoked access | `#FFB6BC` / `#351D26` |

Primary white text contrast: 5.17:1, hover 4.67:1. Secondary text contrast: 8.72:1 on canvas and 8.03:1 on surface. Input boundaries exceed 3:1 on both. Status always includes text, not color alone.

## Components and layout

- Use system fonts with Vietnamese support; 16px body, 32px page heading (28px on phones), 13px metadata. Keep an 8px spacing rhythm and 16px surface radius.
- Compact header with vector brand mark, account identity and logout. Sidebar on desktop; wrapping navigation above content at 900px and below.
- Group directory/account entries in one surface with dividers. Show initials, identity, readable status badges and one action. Wrap long emails and IDs without truncating data.
- Profile metrics: five columns on desktop, two on phones. Facts: two columns, then one on phones. History selectors use a blue underline and `aria-pressed`.
- Account creation and access editing use distinct form surfaces; hide the empty editor. Collapse to one column at 900px.
- Login uses a centered 460px card; form fields and submit remain full width on phones.
- Controls have at least 44px height, visible keyboard focus and explicit labels. Decorative SVGs are hidden from assistive technology. Honor reduced motion and mobile safe-area spacing.
- Keep loading, empty, failure and pending states. Do not add sample metrics, decorative charts or unimplemented admin actions.

## Verification

`e2e/tests/admin.spec.mjs` captures login, directory, profile, accounts, access editor and audit views. Responsive checks cover 360, 768, 1024 and 1440px. The dark-console check validates dark canvas, text contrast (including primary hover), minimum control height and reduced-motion mode. Captures use fixture data and are saved under `.artifacts/admin-*.png`.
