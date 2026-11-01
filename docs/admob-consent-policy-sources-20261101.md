# Consent partners and Privacy: primary-source review — 1 November 2026

Scope: official Google documentation plus public policy source; read-only research for #51. No Console settings, policy HTML, app, flags, credentials, deployment, CI or device were changed. Current AdMob inventory is being inspected separately; this note does not certify that inventory or legal compliance.

## Findings that affect the next decision

| Area | Evidence and implication |
| --- | --- |
| EU partner count | Google’s commonly used list includes programmatic demand partners, beyond SDK mediation adapters. Google Advertising Products is mandatory. A large count alone is not a defect, and absence of third-party adapters does not establish that only one partner is needed. Match chosen partners to intended demand, then disclose that list. [Manage GDPR ad partners](https://support.google.com/admob/answer/10113004?hl=en) |
| List update policy | The commonly used option can update selections automatically; a custom list does not follow those updates. Selections feed message partner disclosures; shared Ad Manager/AdMob accounts can combine lists. These are broader settings, not a harmless draft text edit. Any proposed reduction should name the exact resulting list and intended ad-serving impact. [Manage GDPR ad partners](https://support.google.com/admob/answer/10113004?hl=en) |
| Precise geolocation wording | Google’s vendor interoperability rules classify precise geolocation as Special Feature1 and describe disclosure plus user opt-in. Preview wording is not evidence the app requests GPS, nor proof that the feature is disabled. Inspect selected vendors’ registered features and effective form choices before promising “no precise location” or selecting a setting. Special Feature2 is a different feature: active scanning for identification. [Google vendor TCF guidance](https://support.google.com/admob/answer/9461778?hl=en) |
| Own purposes | Publisher-own purposes are configured separately with None/Consent/Legitimate interest. Leaving own purposes at None does not remove vendors’ purposes or exempt ordinary account/game data practices. Do not add purposes merely to mirror every existing backend function. [Own use of data](https://support.google.com/admob/answer/10305345?hl=en) |
| Legitimate-interest controls | Displaying the controls and their default ON/OFF state are separate decisions. Removing the controls changes what legal bases partners may use and can affect serving. Keep the existing settings during this audit; propose specific changes only after assessing selected partners/purposes. [Legitimate-interest settings](https://support.google.com/admob/answer/12474312?hl=en) |
| US partners | US settings select third-party buyers allowed for eligible traffic, while the message captures opt-out choices. The absence of an adapter does not by itself identify the buyer list. Review actual partner selection and restricted-data-processing strategy together. [US privacy controls](https://support.google.com/admob/answer/9561022?hl=en), [US message setup](https://support.google.com/admob/answer/10860309?hl=en) |
| Revocation and recipient information | Google’s EU consent policy requires identifying parties using/receiving personal data, accessible information on their use, consent records and clear revocation instructions. A saved draft does not meet these operational requirements on its own. [EU user consent policy](https://www.google.com/about/company/user-consent-policy/) |

## Privacy and SDK evidence

The local VI and EN policy sources both state that the current release has AdMob disabled and promise an update before a release with active ads. Their advertising section does not yet describe active ad SDK data, partner information or ad privacy controls. This is consistent with the current OFF state; it is a concrete content gap for an ads-enabled release:

- [Vietnamese policy source](../webApp/src/wasmJsMain/resources/legal/privacy/index.html), section7.
- [English policy source](../webApp/src/wasmJsMain/resources/legal/privacy/en/index.html), section7.
- Public URLs: [VI](https://fasttowin.up.railway.app/privacy/), [EN](https://fasttowin.up.railway.app/privacy/en/). Fresh retrieval was attempted in this research session, but web access failed and shell DNS could not resolve the host. This is a tool-access limitation, not evidence that production is down. Use the parent’s public browser/receipt check to establish deployed content.

The repository uses **GMA Next-Gen1.5.0 and UMP4.0.0** in [version catalog](../gradle/libs.versions.toml), with the Next-Gen dependency in [shared build](../shared/build.gradle.kts). Use the [Next-Gen Data safety guidance](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure), not the Legacy SDK page. Google describes default collection/sharing of IP-derived general location, ad interactions, diagnostics and identifiers for advertising, analytics and fraud prevention. AD_ID is optional; removing it does not imply every SDK data category disappears. The page covers the latest SDK, so verify differences against the exact candidate version/configuration before entering Play answers. This research does not propose an SDK upgrade.

A targeted source search found no ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION or explicit location setter in app/shared Android source. [App manifest](../app/src/main/AndroidManifest.xml) declares INTERNET; [Android ad driver](../shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/AndroidRewardedAdDriver.kt) configures age, personalization, consent and test IDs. These observations do not establish the merged dependency manifest, ad-network behavior, or every vendor’s data handling. Do not claim GPS sharing merely because the Google preview contains a location sentence.

## Concrete next work, before any publish or activation

1. Complete Console inventory readback: EU/US list mode and selected identities, actual mediation/bidding demand, own purposes, legitimate-interest defaults, special-feature behavior. Resolve preview count/wording from that evidence; no blanket partner removal based on counts.
2. Prepare a VI/EN policy patch for the exact proposed ads behavior: optional rewarded format/reward, data categories and purposes, Google/partner information links, consent/opt-out and Settings entry, age handling, and choices without promising that refusal always prevents every ad request. Keep the currently accurate OFF policy until the release timing is approved.
3. Prepare the corresponding Play Contains ads/Advertising ID/Data safety changes against the actual ads-enabled AAB. Do not silently reuse the v10 ads-OFF answers or treat SDK presence alone as proof of active collection.
4. Ask approval for named account setting/policy changes and Publish only after the reviewable values/patch exist. Device/regional TEST, SSV and LIVE activation remain separate gates.

This is a configuration/release evidence review, not a legal determination. No test, build, purchase, callback or device traffic was generated.

## Parent Console readback and bounded outcome

Read directly on1November2026, without changing settings:

| Surface | Current observation |
| --- | --- |
| EU partner selection | Automatically include commonly used partners ON;198selected. Selector lists1738available results, which is not the selected count. Google Advertising Products, GVL755, is selected and cannot be deselected. Initial selected chips include Google,6Sense,A Million Ads,Aarki,Acuityads,Ad Lightning,Adara,Adelaide Metrics,Adform,Adikteev,Adjust,Adloox,ADMASTER; this is a visible subset, not a complete198-name export. |
| EU other settings | Auto-add ad sources OFF; maximize coverage/fallback ON; RTB creative partner consent check OFF; legitimate-interest controls ON and default ON; ads consent mode OFF; Special Feature2 OFF; own purposes0. No consent-sync group was shown. Save and Cancel changes disabled on entry and after selector cancellation. |
| US partner selection | Use active ad partners ON, custom list OFF;345selected. Selector explains it covers Authorized Buyers and Google demand partners. Full rendered selector snapshot retained locally; examples include Google,Amazon,AppLovin,Meta,Unity Ads. These names do not establish SDK adapters installed in the app. |
| Mediation groups | Exactly one row, AdMob(default), and no A/B test. No custom mediation group visible. This does not remove programmatic buyers from default demand or certify every buyer's effective data practice. |
| Draft preview mismatch | Previously saved EU preview says210partners and precise geolocation, while account selector says198. Neither count establishes a bug. The exact12difference and each vendor's registered features have not been resolved in this pass; do not claim a complete vendor-by-vendor acceptance. |

Evidence: `.artifacts/admob-vendor-review-20261101/` contains EU settings/selector, US settings/selector, and mediation group AX snapshots. A single attempted download of EU partner information did not return a usable file before the browser tool timed out; no complete EU export is claimed and no blind retry was made. After recovery, EU Save/Cancel remained disabled. Both selectors were cancelled without any checkbox/radio changes or save.

The baseline configuration/policy review is recorded; exact recipient/feature acceptance is still open. Recommendation: retain the current settings while ads are OFF, prepare a concrete local VI/EN advertising-policy draft against GMA Next-Gen and the intended rewarded flow, then reconcile the effective form/partner disclosures before approval to Publish or enable ads. No exact vendor removal, legal-basis change or new collection is recommended from the counts alone. No current public Privacy content readback was obtained in this pass; local policy and the earlier successful public receipt remain separate evidence.

Subsequent local deliverable: [VI/EN advertising-policy draft](privacy-admob-vi-en-draft-20261101.md) supplies proposed section7 and section6/9 additions, with separate pre-publication gates. It does not replace the deployed ads-OFF policy or authorize Console/HTML/deployment changes.

Further readback: [partner/location reconciliation](admob-partner-reconciliation-20261101.md)
confirms selected AdMaxim(GVL9)/Index Exchange(GVL10), their precise-location
disclosure in preview and a separate default-OFF location opt-in. The editor
question is partly resolved; delivered device behavior and198/210remain open.
No account setting changed or support message sent.

Later separately approved action: the [official SDK support request](admob-support-request-20261101.md)
was sent after the owner explicitly approved the complete form payload.
Google's receipt says “Your email has been sent”; no case ID or technical
answer was shown. Ads and consent publication remain unchanged.
