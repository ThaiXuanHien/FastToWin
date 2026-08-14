package com.hienthai.fastowin.platform

import platform.AudioToolbox.AudioServicesPlaySystemSound

actual fun playFeedbackSound(effect: GameFeedbackEffect) {
    val soundId = when (effect) {
        GameFeedbackEffect.CORRECT -> 1104u
        GameFeedbackEffect.WRONG -> 1053u
        GameFeedbackEffect.WIN -> 1025u
        GameFeedbackEffect.LOSS -> 1073u
    }
    AudioServicesPlaySystemSound(soundId)
}
