@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.platform

actual fun playFeedbackSound(effect: GameFeedbackEffect) {
    val tone = when (effect) {
        GameFeedbackEffect.CORRECT -> WebTone(
            startFrequency = 660.0,
            endFrequency = 880.0,
            durationMillis = 90,
            oscillatorType = "sine",
            volume = 0.08
        )
        GameFeedbackEffect.WRONG -> WebTone(
            startFrequency = 190.0,
            endFrequency = 110.0,
            durationMillis = 160,
            oscillatorType = "square",
            volume = 0.055
        )
        GameFeedbackEffect.WIN -> WebTone(
            startFrequency = 520.0,
            endFrequency = 1_040.0,
            durationMillis = 320,
            oscillatorType = "triangle",
            volume = 0.09
        )
        GameFeedbackEffect.LOSS -> WebTone(
            startFrequency = 260.0,
            endFrequency = 110.0,
            durationMillis = 350,
            oscillatorType = "sawtooth",
            volume = 0.045
        )
    }
    playWebTone(
        startFrequency = tone.startFrequency,
        endFrequency = tone.endFrequency,
        durationMillis = tone.durationMillis,
        oscillatorType = tone.oscillatorType,
        volume = tone.volume
    )
}

private data class WebTone(
    val startFrequency: Double,
    val endFrequency: Double,
    val durationMillis: Int,
    val oscillatorType: String,
    val volume: Double
)

/**
 * Uses one shared AudioContext because browsers limit the number of contexts and block contexts
 * created before a user gesture. Correct/wrong sounds are triggered by a tap, which also resumes
 * the context so the later result sound can play without another interaction.
 */
private fun playWebTone(
    startFrequency: Double,
    endFrequency: Double,
    durationMillis: Int,
    oscillatorType: String,
    volume: Double
): Unit = js(
    """
    {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) return;

        const context = window.__fastToWinAudioContext ||
            (window.__fastToWinAudioContext = new AudioContextClass());
        const play = () => {
            const now = context.currentTime;
            const end = now + durationMillis / 1000;
            const oscillator = context.createOscillator();
            const gain = context.createGain();

            oscillator.type = oscillatorType;
            oscillator.frequency.setValueAtTime(Math.max(20, startFrequency), now);
            oscillator.frequency.exponentialRampToValueAtTime(Math.max(20, endFrequency), end);
            gain.gain.setValueAtTime(0.0001, now);
            gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume), now + 0.012);
            gain.gain.exponentialRampToValueAtTime(0.0001, end);

            oscillator.connect(gain);
            gain.connect(context.destination);
            oscillator.start(now);
            oscillator.stop(end + 0.02);
        };

        if (context.state === 'suspended') {
            context.resume().then(play).catch(() => {});
        } else {
            play();
        }
    }
    """
)
