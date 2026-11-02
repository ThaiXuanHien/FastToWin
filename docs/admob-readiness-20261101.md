# AdMob readiness and consent audit — 1 November 2026

Issue: [#51](https://github.com/ThaiXuanHien/FastToWin/issues/51). Scope approved by the owner: inspect app readiness and consent, keep ads disabled and production callback unchanged.

## Result

**Not ready for LIVE.** The AdMob account is approved, but Fast To Win Android is **Requires review**, has no linked store/package entry, and has limited ad serving. This is not evidence that an app review is already running or that simply waiting will complete setup.

The existing code contains the UMP/reward flow. Provider consent forms and the final ads-enabled binary are not accepted by this source audit. No runtime bug was established or fixed in this step.

## Current Console observations

| Area | Observed state | Remaining gate |
| --- | --- | --- |
| Account | Approved, directly shown on AdMob home | Account approval does not establish app readiness |
| App | Fast To Win Android, owned app suffix8507808229, Requires review | Link the correct usable supported-store listing, then complete app readiness |
| Store | Add store; package/store ID shown as a dash | No store link was added during this audit |
| Rewarded unit | `ca-app-pub-1914037185148012/7821587525`, `online_matches` | Existing saved reward is exactly2 `online_matches` |
| SSV | Callback setup dialog has an empty URL; verify/use-verified controls disabled | Production callback is not configured; no probe was sent |
| GDPR messages | Messages tab shows first-message onboarding and Create control, no authored message list | No authored/published GDPR message was observed |
| GDPR coverage setting | Maximize message coverage is already checked; Save/Cancel disabled | Automatic/fallback messaging is not proof of an accepted authored configuration or actual device form |
| US states messages | Messages tab also shows first-message onboarding | No authored/published US message was observed |
| app-ads.txt | Production file HTTP checks passed; on 2 November the saved Store website and matching public-page link were confirmed | AdMob Store linking/crawler/Verified result remain pending |

Reward/SSV settings were opened for inspection only; both edit panels were cancelled. No value was typed, saved, verified or published. GDPR/US settings were not changed. No policy-center, payout/bank, legal-account or account-access change was attempted.

Evidence is local under `.artifacts/admob-readiness-20261101/`: screenshots for app readiness, GDPR/US message onboarding and empty SSV, plus `audit-result.json`. Public IDs match the established `docs/admob-android.md` identities. Do not copy account data or screenshots into GitHub comments automatically.

## Source review and limits

| Existing source | Verified behavior from code |
| --- | --- |
| `app/src/main/java/com/hienthai/fastowin/MainActivity.kt` | Omitted production mode resolves DISABLED; simulation is restricted to omitted Dev Debug mode |
| `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.android.kt` | No native driver when disabled; enabled driver gets a launch refresh; Settings privacy state delegates to the driver |
| `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/AndroidRewardedAdDriver.kt` | Age gate precedes ad initialization/request; UMP update/form precedes authorization; `canRequestAds()` checked before initialize/load/show |
| Same driver | Known eligible age refreshes consent on launch; first unknown-age flow waits for the age choice and updates on authorization; completed choices are not reopened on each retry |
| Same driver and `SettingsScreen.kt` | Required privacy options are surfaced in Settings; opening them destroys a loaded ad and invalidates cached consent completion before another load |
| Same driver | Owned TEST mode requires SDK-recognized test device IDs; account/generation/activity fences reject stale work; opaque SSV custom data is used rather than email/token |
| `VerifiedRewardedAdGateway.kt` and server ads services | Client earned callback is not a quota grant; authoritative signed SSV/status/reconnect handling remains the existing path |
| `app/src/adsDisabled/AndroidManifest.xml` | Disabled builds remove AD_ID and READ_BASIC_PHONE_STATE; enabling ads uses a different manifest outcome and requires declaration review |

No `ConsentDebugSettings`/forced geography path was found in the current app/shared source search. This does not establish a consent defect, but regional provider acceptance needs an explicit real-region or reviewed debug-test setup. No device/emulator, build or test was started for this read-only audit.

Google's [UMP guide](https://developers.google.com/admob/android/privacy) describes updating consent information on launches, displaying required forms, checking request eligibility and exposing required privacy options. The review above checks those seams in source; it does not replace real regional consent acceptance. A refusal of personalization should not be conflated with `canRequestAds()==false`; the effective SDK eligibility and configured ad treatment must be observed.

## Current release and backend boundary

Internal1.0.9(10) is the latest published Internal version in the completed local handoff, with ads disabled and Contains ads/Advertising ID saved as No. The seven-case manifest matrix from31October confirms omitted/disabled prodRelease excludes the two advertising permissions while demo/test/live retain them. These are existing receipts, not a fresh build or a new Play Console read in this task.

The backend86f53b3f deployment receipt from earlier on1November confirms AdMob OFF. No Railway variables or private DB/backup/credential files were read in this audit. The Play review submission receipt says accepted with quick checks pending at its observation time; current Google Play approval/public store availability was not checked here. Do not infer public listing availability or update Play declarations from an AdMob account banner.

Earlier #51 text referring to an unmerged Draft PR, Internal1.0.2(3), twelve languages or the old optional CI preference is historical. It is not the current release/merge/CI acceptance state. This audit did not edit the issue, read CI or reopen completed implementation work.

## Remaining work in order

The [local consent configuration proposal](admob-consent-proposal-20261101.md)
now supplies exact draft values. Google's supported-language list does not include
Vietnamese for GDPR/US messages; it proposes English (US) forms with existing
VI/EN app Settings and Privacy pages. After the owner's separate approval,
both Console drafts and the exact app Privacy URL were saved on1November;
each draft was reopened to check its settings, and both publish switches remain OFF.
Evidence: `.artifacts/admob-consent-drafts-20261101/`.

The subsequent [partner/Privacy baseline review](admob-consent-policy-sources-20261101.md)
read EU commonly-used198, US active345, own purposes0 and the existing defaults;
only the AdMob(default) mediation group was visible. No settings changed.
EU preview210 versus selected198 and precise-location vendor feature handling
still require reconciliation; this is not complete vendor-by-vendor acceptance.
The next local deliverable is a concrete VI/EN advertising-policy draft,
without deploying it or changing the currently accurate ads-OFF policy.

1. **Review partners/purposes and Privacy before consent publication.** The approved EU/US drafts and English Privacy URL are now saved. EU targets EEA/UK/Switzerland with refusal and close-as-refusal enabled; US targets all current/future supported states. Existing account settings were left unchanged. The EU preview mentions210partners and precise geolocation; this is a template observation, not acceptance that every listed vendor/purpose is needed. Inspect the actual inventory and policy before proposing exact account changes or Publish. Preserve the owner's178-region/PC distribution choice.
2. **Confirm usable store availability, then link the correct app deliberately.** The subsequent [Play/AdMob readback](play-admob-link-readiness-20261101.md) confirms submission1's16changes, including Closed1.0.9(10), are Published on1November. The exact Store page opens in the signed-in browser, but anonymous/public download eligibility is not established. AdMob still Requires review with no store link; one exact-URL search returned no matching app, and the wizard was cancelled. Google's [app readiness guide](https://support.google.com/admob/answer/10564477) requires availability in a supported store and a matching link for review. Linking can initiate review; none was performed. Do not infer AdMobReady or production release from Closed publication.
3. **Establish app-ads.txt ownership/verification.** The [handoff](app-ads-preparation-20261101.md) records the exact snippet, dedicated routes, local Caddy tests and successful compatibility build. The prepared ZIP was deployed once to Web production as `800d8bc5-51fb-4ca7-82e9-63b26e71a065`; nine public HTTP checks passed, including the exact 59-byte publisher file and plain-text content type. Backend remains unchanged. On 2 November, after Owner reported saving/publishing the approved Developer website, read-only checks confirmed `https://fasttowin.up.railway.app/` in Console and the matching Website link on the signed-in Google Play page. Privacy still links to `/privacy`. No AdMob link/crawl request or Verified result exists; serving the file and exposing the Store link do not establish a successful Google crawl. `/robots.txt` currently returns SPA HTML, recorded without changing it.
4. **Plan regional and provider TEST acceptance separately.** Explicitly review the build/backend target and supported consent-test setup, then test accept/refuse, required privacy options, age bands, no-fill, early dismiss, cancel/reopen, signed SSV+2 and duplicate safety. Only use the real device when the owner requests it. Existing20October owned TEST SSV+2 evidence remains historical and is not acceptance of the final new binary.
5. **Prepare production activation only after readiness and acceptance.** Review the exact production SSV URL and unit, explicit client LIVE build, backend flag, Privacy and Play ads/Advertising ID/Data safety answers against that binary. Get the exact changes approved before saving or enabling anything. Client mode and backend gate are separate; toggling one alone is not a release.

Google's [SSV guide](https://developers.google.com/admob/android/ssv) describes signed callbacks and configured unit/reward/custom-data parameters. This audit only inspected the empty callback setting; no signed production callback or reward was generated.

## Completed scope

- Read-only Console readiness/consent/unit inspection and source/documentation review completed.
- No ads enabled, message/store link/callback changed, Console form saved, callback probe sent, permission expanded, issue posted, build/deploy performed, CI inspected/dispatched or Samsung test resumed.
- #51 remains OPEN for the concrete setup and provider/device acceptance gates above.

Subsequent scoped action: Owner approved saving exactly two DRAFT messages and
the app Privacy URL. That work is complete; no publication, store linking,
account-level vendor/purpose change, SSV setup or ads activation occurred.
