package com.hienthai.fastowin.platform

enum class GameFeedbackEffect {
    CORRECT,
    WRONG,
    WIN,
    LOSS
}

expect fun playFeedbackSound(effect: GameFeedbackEffect)
