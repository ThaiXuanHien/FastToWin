# Issue #38: Android dialog IME acceptance

Verified locally on 2026-10-15, from master `57b9910`, with Android fix commit `45a7929`. Issue: https://github.com/ThaiXuanHien/FastToWin/issues/38.

## Cause and failing evidence

The Android dialog owns a separate window. Its default `decorFitsSystemWindows=true` selected unspecified soft-input adjustment, observed as `adjustPan` on API 35. `ArcadeDialog` applied `imePadding()` inside that window, to its Surface. Android could therefore pan the Compose viewport independently after `performScrollTo()` positioned a control inside it.

A stronger assertion before tapping the schedule switch failed on the unchanged implementation at 320×568 dp: the switch had root bounds `(630, 428)–(767, 554)` but window position `(630, -142)`. The name retained focus, `mInputShown=true`, and the screenshot placed the switch above the status bar. This reproduced the invalid tap precondition upstream of CI's missing schedule field. It also failed with host window settings matching MainActivity's edge-to-edge and `adjustResize` configuration; the dialog's separate window still panned.

The exact pixels from the earlier CI validation-error visibility failure were not captured locally. The original normal-size baseline passed 178/178; those passes were not treated as a repair.

## Change

Android's existing `NativeDialogInputScope` consumes safe drawing and IME insets before measuring the dialog viewport. Android dialog properties use `decorFitsSystemWindows=false`, preventing native panning on API 35. Existing nested `imePadding()` remains and receives consumed insets, avoiding double padding. iOS/Web retain their previous properties and scope bodies. No theme, colors, typography, or form state logic changed.

The schedule test additionally requires the switch to be displayed before tapping and on afterward. Existing error visibility, disabled submit, deadline visibility, and exact schedule payload assertions remain. No skips, retry logic, sleeps, or timeout increases were added.

## Validation

| Check | Result |
| --- | --- |
| Android app/test APK build and shared Wasm compilation | Pass |
| Focused schedule case, API 35 at 320×568 dp | 3/3 pass |
| Focused schedule case, API 35 Pixel 7 at 1080×2400 px / density 420 | 3/3 pass |
| Full Android UI suite on the final patch | 178/178 pass, no skips or failures |
| Independent scoped review | No actionable findings |

A temporary visual fixture exercised actual name-field tapping with IME open, switch visibility/on state, invalid input and disabled submit, valid replacements and deadlines, then closing the dialog. It passed on the small viewport and a 1920×1080 px / density 160 Android large window. Screenshots show the complete validation message and deadlines above the open keyboard. **The large-window check was Android, not a Web browser or physical desktop device.** The fixture was removed; the final test APK was rebuilt and its DEX checked for the fixture's absence.

Ignored local evidence is under `.artifacts/issue38-20261015/`: `strong-toggle-small/`, `strong-toggle-small-no-tap/`, `strong-toggle-production-window/`, `final-small-*.log`, `final-pixel7-*.log`, `final-suite.log`, and `final-visual-{small,desktop}/`. `README.md` there records commands and detailed artifact provenance. Emulator size/density were restored and the dedicated AVD stopped after validation.

## Limits

Local execution used a Windows-hosted API 35 Google APIs x86_64 emulator, image revision 9; CI uses Linux. API 29–30 and iOS runtime were not exercised. Shared Web code compiled, but no new browser runtime test is claimed. CI at the new commit remains pending. Previously resolved WebKit/public-room observations were not modified. No production or staging deployment was performed.
