# FastToWin admin — Material Design, white theme

Scoped override of `../MASTER.md` for `/admin/`. The approved admin direction is Material Design 3 with a white background, blue actions and modal forms. Apply `.agents/skills/ui-ux-pro-max/SKILL.md` to future UI work. Player-facing styling remains defined by the master system.

## Direction and references

Keep the default screen focused on the current list: compact app bar, rounded selected navigation, one primary action and light dividers. Remove decorative breadcrumbs, individual row cards and permanently expanded forms. Show user IDs and creation dates in the profile rather than repeating them in the directory.

The skill's targeted Material search supplied tonal surfaces, pill buttons, outlined fields, state feedback and restrained motion. Its generic design-system output suggested a marketing landing page; that pattern is not applicable here. Use web dialog guidance instead of the search result's mobile-only haptics/animation libraries.

- [Material Web dialogs](https://github.com/material-components/material-web/blob/main/docs/components/dialog.md): headline, content, actions, accessible naming and focus containment.
- [Native HTML dialog](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/dialog): `showModal`, inert background, cancellation and backdrop.

## Tokens

Runtime source: `server/src/main/resources/admin/admin.css`.

| Role | Value |
|---|---|
| Canvas / modal | `#FFFFFF` |
| Surface | `#F7F9FC` |
| Tonal control | `#EDF2FA` |
| Primary action | `#245BD7` |
| Primary hover | `#1B50BB` |
| On primary | `#FFFFFF` |
| Selected navigation | `#E2ECFF` / `#16366C` |
| Main text | `#1C2028` |
| Secondary text | `#596273` |
| Divider | `#DDE2EB` |
| Input outline | `#737D8C` |
| Focus | `#245BD7` |
| Success | `#21613D` / `#EAF5ED` |
| Error | `#A12633` / `#FFF0F1` |

Use semantic tokens for hover/pressed states. Text contrast must exceed 4.5:1; field boundaries and focus indicators exceed 3:1. Status includes text as well as color.

## Components

- Use locally available Roboto/Segoe UI/system fonts with Vietnamese support; 16px body, 30px page title (26px on phones), 13px metadata. Keep 8px spacing rhythm.
- Pill buttons, selected navigation and light surface groups; avoid repeated boxes and heavy borders. Minimum control height: 48px. Outlined fields have persistent floating labels.
- Familiar utility actions use 48×48px icon buttons: pagination chevrons, edit pencil, view eye, refresh, back, add, logout and dialog close. Search uses 56×56px to align with its input. Keep accessible Vietnamese names and tooltips on hover/keyboard focus; account edit names include the target email. Keep section names, form labels and save/cancel/sign-in actions readable as text.
- Directory shows name, email, account type, status and profile action. Account list shows email, role, status and edit action. Long text wraps without truncating identifying data.
- Profile metrics share a single tonal surface; profile facts and history use light dividers. Keep all existing read-only data accessible.
- Login uses a restrained 440px outlined card with full-width inputs and submit.
- Account creation and access editing open native modal dialogs; width at most 560px, 28px radius, subdued scrim and one primary action. Content scrolls within short viewports. Header close and footer cancel remain available when idle.
- Trap Tab/Shift+Tab within a modal, focus its first field and restore focus to its opener after dismissal. Support Escape and backdrop dismissal. Respect reduced motion.
- Block close/cancel/Escape/backdrop and disable fields while saving. Preserve failed payload and request ID for a retry. Clear abandoned passwords and remove dialog content on close.
- View/session changes remove all dialogs and data. Preserve server role checks, in-memory credentials/CSRF, request fencing and service-worker policy. Do not treat cancelling a request as rollback.

## Verification

`e2e/tests/admin.spec.mjs` covers focus containment/restoration, dismissal, abandoned secrets, pending mutations, retry IDs, expired sessions and role changes. Screenshots cover directory, profile, accounts, create/edit modals, login and audit at 360, 768, 1024 and 1440px. The light-console check verifies white canvas, text contrast including primary hover, and minimum control height in reduced-motion mode. Captures use fixture data and live under `.artifacts/admin-*.png`.

## Gift campaigns

The Owner/Operator navigation adds **Quà admin** using the same vector/selected pill pattern. The primary add icon opens a draft modal; familiar edit/view/refresh/back utilities remain 48px icon controls. Save, preview, publish and cancellation confirmations keep readable Vietnamese text. Field labels stay Vietnamese while player content is separately authored in Vietnamese and English.

Reuse the 560px white dialog, with a sticky heading/close control on long gift forms and scroll margins so focused controls remain visible. Rewards, totals and actual paid counters use tonal metric surfaces; bilingual messages preserve line breaks and display text only. Registration audience fields appear only for their selected audience. Date labels explicitly say Bangkok. Unsupported activity targeting has a disabled option and a visible explanation.

Preview shows recipient count, duplicate/invalid IDs and total Vàng/XP before the separate publication confirmation. Published content is read-only; cancellation requires a reason and explains that received gifts remain. Report text explains that delivered/claimed/expired counters overlap. No status-filter control is shown until the server supports filtering across the complete list.

`admin-gifts.spec.mjs` verifies lifecycle, PUT/version checks, exact retries, pending dismissal guards, validation, revocation, Support history access, phone scrolling and focus restoration. Fixture screenshots: `.artifacts/admin-gifts-{360,768,1024,1440}-{list,form,preview,confirm}.png`, with reduced motion enabled.

## Maintenance

All current roles see **Bảo trì**; only Owner sees configuration/disable actions. Environment override is visibly named and disables mutation. Separate the server's latest observed version/time/match count from the saved policy; unavailable observations remain unknown, never zero. A save acknowledgment is not evidence that every socket has closed.

Reuse the native white modal, existing API/channel/session fences and explicit asset allowlist. The heading stays visible while form content scrolls; focus starts at the mode field, with the footer reachable independently of the page behind the dialog. Use plain Vietnamese labels, bilingual message fields, Bangkok dates and an explicit advisory-end explanation. Confirmation carries readable actions and text-only messages.

Enabled policies retain saved schedule values in read-only duration fields. Text/cause/advisory edits preserve deadlines; planned policy can escalate to emergency. Explain that a fresh schedule or emergency-to-planned change needs disable then enable. Pending requests guard dismissal; uncertain identical retries preserve the whole payload/request ID. Version or mode conflicts require reload.

`admin-maintenance.spec.mjs` exercises actual controls, exact payloads, roles/override, input bounds, retries, modal/session fences, observed data, reduced motion and touch landscape. Captures: `.artifacts/admin-maintenance-{360,768,1024,1440}-{list,form,form-end,confirm}.png`.
