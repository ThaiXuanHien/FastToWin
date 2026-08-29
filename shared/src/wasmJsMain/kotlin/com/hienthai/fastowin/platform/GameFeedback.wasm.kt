package com.hienthai.fastowin.platform

actual fun playFeedbackSound(effect: GameFeedbackEffect) {
    // Web audio is added after the browser interaction layer is finalized.
}
