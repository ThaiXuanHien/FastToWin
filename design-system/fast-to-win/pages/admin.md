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
- Directory shows name, public player code, email, account type, status and profile action. Search accepts public player codes case-insensitively; UUID remains secondary technical metadata in the profile. Account list shows email, role, status and edit action. Long text wraps without truncating identifying data.
- Profile metrics share a single tonal surface; profile facts and history use light dividers. Keep all existing read-only data accessible.
- Login uses a restrained 440px outlined card with full-width inputs and submit.
- Account creation and access editing open native modal dialogs; width at most 560px, 28px radius, subdued scrim and one primary action. Content scrolls within short viewports. Header close and footer cancel remain available when idle.
- Trap Tab/Shift+Tab within a modal, focus its first field and restore focus to its opener after dismissal. Support Escape and backdrop dismissal. Respect reduced motion.
- Block close/cancel/Escape/backdrop and disable fields while saving. Preserve failed payload and request ID for a retry. Clear abandoned passwords and remove dialog content on close.
- View/session changes remove all dialogs and data. Preserve server role checks, in-memory credentials/CSRF, request fencing and service-worker policy. Do not treat cancelling a request as rollback.

## Verification

`e2e/tests/admin.spec.mjs` covers focus containment/restoration, dismissal, abandoned secrets, pending mutations, retry IDs, expired sessions and role changes. Screenshots cover directory, profile, accounts, create/edit modals, login and audit at 360, 768, 1024 and 1440px. The light-console check verifies white canvas, text contrast including primary hover, and minimum control height in reduced-motion mode. Captures use fixture data and live under `.artifacts/admin-*.png`.

## Gift campaigns

For an individual gift, choose **Một người chơi**, enter the public player code or registered email, then explicitly look up the recipient. Show name/code/email before saving, in preview and at publication confirmation. Editing the reference or audience clears the selection and fences late responses; editing a saved individual draft requires another lookup. Owner and Operator can resolve recipients; Support cannot. Keep UUIDs internal to the existing campaign payload. **Danh sách UUID (nâng cao)** remains available for bulk workflows and accepts 1–10,000 IDs; invalid-format errors must not suggest entering 10,000 IDs. The UUID field is hidden and not required for individual gifts.

The gift modal header stays outside the scrollable form/preview body. Wrap long recipient emails without hiding identifying text. Phone/desktop captures for code/email lookup, directory and profile live under `.artifacts/admin-player-code/`.

The Owner/Operator navigation adds **Quà admin** using the same vector/selected pill pattern. The primary add icon opens a draft modal; familiar edit/view/refresh/back utilities remain 48px icon controls. Save, preview, publish and cancellation confirmations keep readable Vietnamese text. Field labels stay Vietnamese while player content is separately authored in Vietnamese and English.

Reuse the 560px white dialog, with a sticky heading/close control on long gift forms and scroll margins so focused controls remain visible. Rewards, totals and actual paid counters use tonal metric surfaces; bilingual messages preserve line breaks and display text only. Registration audience fields appear only for their selected audience. Date labels explicitly say Bangkok. Unsupported activity targeting has a disabled option and a visible explanation.

Preview shows recipient count, duplicate/invalid IDs and total Vàng/XP before the separate publication confirmation. Published content is read-only; cancellation requires a reason and explains that received gifts remain. Report text explains that delivered/claimed/expired counters overlap. No status-filter control is shown until the server supports filtering across the complete list.

`admin-gifts.spec.mjs` verifies lifecycle, PUT/version checks, exact retries, pending dismissal guards, validation, revocation, Support history access, phone scrolling and focus restoration. Fixture screenshots: `.artifacts/admin-gifts-{360,768,1024,1440}-{list,form,preview,confirm}.png`, with reduced motion enabled.

## Maintenance

All current roles see **Bảo trì**; only Owner sees configuration/disable actions. Environment override is visibly named and disables mutation. Separate the server's latest observed version/time/match count from the saved policy; unavailable observations remain unknown, never zero. A save acknowledgment is not evidence that every socket has closed.

Every read role sees the saved cause, start time, actor and change time, with Vietnamese cause labels and explicit Bangkok dates. The disable dialog observes the existing `/health` liveness endpoint with a five-second timeout, observation time, unavailable state and a stale label after thirty seconds. Refresh is a 48px outline icon. Explain that liveness does not verify database, gameplay or security readiness. Observation never reopens the service; Owner still supplies a reason and explicitly confirms. Session/view/dialog fences discard late observations.

Reuse the native white modal, existing API/channel/session fences and explicit asset allowlist. The heading stays visible while form content scrolls; focus starts at the mode field, with the footer reachable independently of the page behind the dialog. Use plain Vietnamese labels, bilingual message fields, Bangkok dates and an explicit advisory-end explanation. Confirmation carries readable actions and text-only messages.

Enabled policies retain saved schedule values in read-only duration fields. Text/cause/advisory edits preserve deadlines; planned policy can escalate to emergency. Explain that a fresh schedule or emergency-to-planned change needs disable then enable. Pending requests guard dismissal; uncertain identical retries preserve the whole payload/request ID. Version or mode conflicts require reload.

`admin-maintenance.spec.mjs` exercises actual controls, exact payloads, roles/override, input bounds, retries, modal/session fences, observed data, reduced motion and touch landscape. Captures: `.artifacts/admin-maintenance-{360,768,1024,1440}-{list,form,form-end,confirm}.png`.

## Analytics

Owner/Operator see **Thống kê** with an outline trend icon. Keep one metric selector,
period selector and bucket selector; contextual filters open the existing native
modal. Six admin destinations use two readable columns on small phones instead
of squeezing every label into one row. Controls keep their 48px minimum.

Show actual Bangkok current/prior ranges, source coverage and freshness before
the summary. One shared tonal surface holds current total and previous-period
comparison for each distinct source/unit. Partial and unavailable states carry
plain text; never replace unknown data with zero or a percentage. White canvas,
blue SVG line/bars, light dividers and system fonts reuse existing tokens.

Native charts have exact scale labels, time bounds and units. A labelled native
point selector with previous/next icon controls supports keyboard and touch;
the exact table follows it. Show compact calendar bounds for whole buckets,
precise time for partial buckets, and one visible exclusive-boundary explanation.
Missing points break the line. Top bars include item/count text; money and game
units never share an aggregate or chart. Exact tables use scoped overflow if needed.

`admin-analytics.spec.mjs` covers role/filter/range controls, exact amounts, source
states, comparison, late responses and session fences. Captures are
`.artifacts/admin-analytics-{360,768,1024,1440}.png` and matching `-filters.png`,
plus `-landscape.png` and `-enlarged.png`. Reduced motion is enabled; enlarged
text is checked at 200%. Fixture data is synthetic, and live test screenshots
exclude identity headers and explicitly mark SANDBOX money as test evidence.

## Reward events

Owner/Operator see **Sự kiện thưởng** with the existing labelled outline navigation and 48px add/view/edit/refresh utilities. Reuse the white Material canvas, native dialogs, focus containment/restoration, pending guards and shared session/navigation fences. Keep Save/Cancel/Schedule and form labels readable. Compact factor summaries use the coin/star vectors plus ×value, with Vietnamese currency names in accessible labels and native titles. Use plain overlap-policy wording rather than exposing an internal policy enum.

List/history has server-side name search and state filters. Detail distinguishes stable creator, publisher, latest actor, unpublished and unknown historical identities. Published definition and ended history are read-only. Preview and separate confirmation show both languages, exact independent factors, eligible modes, overlap maxima, exclusions and exact UTC/IANA bounds. Keep long text/IDs wrapping, without raw HTML.

UTC entry fields are explicitly separate from the IANA display timezone and include a full year. Holiday suggestions require a manually supplied year and create drafts only; lunar New Year dates are entered in the Gregorian calendar. No automatic annual publication or identity targeting. Phone forms scroll inside native dialogs, retain reachable close/cancel controls, and block dismissal/fields while pending.

`admin-events.spec.mjs` covers exact payload/version/token/CSRF, permissions, retries, pending dismissal, request/import/session fences, legacy identities, UTC consistency, holiday suggestions and 200% text. Actual viewport dialog captures avoid full-page native-dialog compositing artifacts. Task4 evidence and inspected 360/768/1024/1440 plus enlarged screenshots live under `.artifacts/admin-reward-events-delivery/task4/`; the isolated live runner verifies real match capture and committed wallet/history in the owned loopback preview.

## Incident compensation

**Đền bù** uses the approved white Material shell and native dialogs. Every current role reads history; Owner/Operator create/edit unpublished incident and round drafts and preview; only Owner confirms publication/cancellation. Keep one primary create, labelled 48px add/view/edit/back/refresh utilities and readable save/cancel/confirmation text.

Separate bilingual player explanation from labelled internal cause/evidence/responsibility. UTC fields include the full year and explain inclusive start/exclusive end. Supplemental preview and confirmation name the explicit round number and internal reason plus three independent previous-payment counts. Show ACTIVITY as unavailable with its real source explanation. Never imply automatic detection, annual publication, inferred activity targeting or automatic next-round creation on retry.

Amount summaries use Mission's filled MonetizationOn-style gold coin and primary filled star, with light tonal backing, visible exact numbers and accessible Vàng/Kinh nghiệm names. Avoid extra GOLD/XP text in compact pills. Decimal strings go directly to BigInt formatting, including pagination. Published history and incident freeze retain true first-publication evidence after cancellation. Report wording explains overlapping counters and delivery versus payout.

Use a fixed native-dialog header and an independently scrolling form body so close remains reachable at 360px and 200% text. Reset preview content to its top after the response replaces the request form. Preserve focus containment/restoration, pending dismissal guards, same-payload/request-ID retry and current role/session/navigation/dialog fencing. Long copy and UUIDs wrap; motion respects reduced-motion preferences.

Actual settled fixture images (list, incident form, round form, preview, report; modal top/footer) and geometry at 360/768/1024/1440 plus 200% normal/reduced motion live under `.artifacts/admin-compensation-delivery/task4/`. Native modals use viewport captures after animation finishes; avoid full-page dialog compositing. The owned browser/Ktor/PostgreSQL runner and SQL evidence are local acceptance only.

## Announcements

Every current role sees **Thông báo** and reads history/report. Owner/Operator create or edit drafts and preview; only Owner confirms publication/cancellation. Keep the existing white Material canvas, one primary create, labelled 48px vector utilities, readable form/confirmation actions and native modal focus containment/restoration. Published copy stays read-only; a correction creates a new draft referencing the old UUID.

Draft fields explicitly name UTC inclusive start/exclusive end, bilingual plain-text copy and verified registration audience. In-app is always enabled; banner and push are explicit optional channels, push off by default. Capability is a provider fact, with unknown distinguished from unavailable, and never a configuration flow. Preview shows full in-app copy separately from safely shortened push reminders, exact recipient/duplicate counts, current version and invalid UUIDs. Internal preview tokens stay in memory.

Report counts describe independent, overlapping facts: durable storage, reading, provider acceptance and unknown outcome cannot be added or equated. Unknown push is never automatically retried. Cancellation preserves history and acknowledges an in-flight external send can finish; announcements never change wallet, gifts or maintenance.

The skill's narrow `modal keyboard focus scroll --domain ux` search verified the All-platform **Focus States** result and Web **Focus Not Obscured (Minimum)** recommendation. Apply visible focus and a fixed header with an independently scrolling modal body; enhanced full visibility is useful guidance without claiming AAA compliance. Body/footer remain reachable with wrapping at 200% text. `.artifacts/admin-announcements-delivery/task4/` retains actual settled viewport captures and geometry; no full-page native-dialog compositing.
