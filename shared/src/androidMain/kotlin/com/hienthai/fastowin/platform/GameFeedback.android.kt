package com.hienthai.fastowin.platform

import android.media.AudioManager
import android.media.ToneGenerator

private val feedbackToneGenerator by lazy {
    ToneGenerator(AudioManager.STREAM_MUSIC, 65)
}

actual fun playFeedbackSound(effect: GameFeedbackEffect) {
    val (tone, durationMillis) = when (effect) {
        GameFeedbackEffect.CORRECT -> ToneGenerator.TONE_PROP_ACK to 70
        GameFeedbackEffect.WRONG -> ToneGenerator.TONE_PROP_NACK to 120
        GameFeedbackEffect.WIN -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 220
        GameFeedbackEffect.LOSS -> ToneGenerator.TONE_CDMA_ABBR_REORDER to 180
    }
    feedbackToneGenerator.startTone(tone, durationMillis)
}
