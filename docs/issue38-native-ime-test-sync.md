# Issue #38: native IME synchronization in Android dialog tests

PR #42 integrates the profile draft/reconnect change with master at `603558aaafd7cdaf420a65f24d7e3abd617a8a94`. This follow-up changes Android instrumentation tests only. The dialog layout, field state, profile behavior, and server are unchanged.

## Captured failure and cause

CI failures at this source varied between a missing password field after selecting private room, an invisible public-room submit button, a missing join callback, and a missing first public-tournament create callback. Unchanged-source retries produced both successes and different failures; those successes did not establish a repair.

A diagnostic run of `CriticalFlowsUiTest` on Samsung A36 / API 36 reproduced the private-room failure (29/30 passed). The name had been entered, the injected privacy tap completed, but the password node was absent. Failure pixels show that public remained selected. A native pre-draw trace recorded the privacy control moving during that tap:

| Event | Uptime (ms) | Privacy bounds, y (px) |
| --- | ---: | --- |
| Before IME transition | 211795236 | 980–1115 |
| Before injected tap | 211795390 | 980–1115 |
| Native IME became visible | 211795476 | 980–1115 |
| Layout changed during tap | 211795538 | 788–923 |
| Tap returned | 211795543 | 788–923 |
| Settled keyboard layout | 211795608 | 612–747 |

The root view stayed 1080×2340 at screen position `[0, 0]`. Native IME bottom inset became 969 px. This was legitimate keyboard avoidance moving the dialog content while the test injected a tap using earlier coordinates, rather than the native `adjustPan` defect fixed previously. Compose test idleness did not wait for this separate native IME transition. The exact pixels of the other three historical failures were not captured; their shared input-to-tap/scroll boundary is consistent with this cause, but that relationship is an inference.

## Test repair

`DialogImeSynchronizer` attaches a forwarding native animation observer to the dialog decor, leaving Compose's descendant insets handling intact. Before testing keyboard-open interactions, the tests explicitly request the IME, wait for visible insets and completed native IME animations, then wait for Compose's resulting layout. The observer is removed even on failure.

The four affected workflows still use real injected taps and exact callback payload assertions. They additionally require controls to be fully inside their scroll viewport and above the keyboard. Private selection is asserted before password entry; public-tournament creation is asserted before switching to private. The private-room test checks submit reachability with the IME open, preserves its original IME action, then explicitly requests native hide and waits for hidden insets plus animation completion before a fresh scroll and tap. This controls the transition without assuming that the custom focus-clearing action itself closes the keyboard. Failure-only PNGs and semantics/native-insets diagnostics are saved independently to the dev app cache.

No sleeps, retries, skips, semantic-only click shortcuts, or timeout increases were introduced. Condition waits use the existing Compose default of 1,000 ms.

## Validation

- Exact reviewed source: Samsung A36/API 36 full Android UI suite **189/189 passed**, no failures or skips (221.384 s).
- Exact reviewed source: Pixel 7/API 35 Windows emulator full Android UI suite **189/189 passed**, no failures or skips (129.271 s).
- Final app/test APK build passed. SHA-256 records match all four test source files and the validated APKs.
- Independent review approved the final native-hide wait and independently captured diagnostics, with no remaining findings.
- CI of the new commit must still pass before integration/deployment.

Local ignored evidence is under `.artifacts/issue38-native-ime-20261017/`: the original failure PNG/text with native trace, baseline diagnostic class log, final focused class logs, final build log, and final full-suite logs. Production app data and Samsung global settings were not changed. The dedicated API 35 emulator uses the existing Pixel 7 configuration (1080×2400 px, density 420, animations disabled).

## Limits

The captured reproduction was on Samsung/API 36. API 35 validation used a Windows emulator, while CI uses Linux. No API 29/30 runtime coverage, iOS runtime test, or new browser runtime test is claimed. CI at the forthcoming test-only commit remains a required release gate.

The native observation follows Android's documented [software keyboard insets/animation lifecycle](https://developer.android.com/develop/ui/views/layout/sw-keyboard).
