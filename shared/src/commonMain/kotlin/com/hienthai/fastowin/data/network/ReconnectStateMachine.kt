package com.hienthai.fastowin.data.network

enum class SocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    TERMINAL
}

sealed interface ReconnectEvent {
    data object Start : ReconnectEvent
    data object TransportOpened : ReconnectEvent
    data object Authenticated : ReconnectEvent
    data object TransportLost : ReconnectEvent
    data object AttemptFailed : ReconnectEvent
    data object ManualRetry : ReconnectEvent
    data object SessionExpired : ReconnectEvent
    data object Stop : ReconnectEvent
}

sealed interface ReconnectDecision {
    data object None : ReconnectDecision
    data object ConnectNow : ReconnectDecision
    data class RetryAfter(val delayMillis: Long) : ReconnectDecision
    data object Stop : ReconnectDecision
}

internal fun reconnectDelayMillis(attempt: Int): Long = when (attempt) {
    0 -> 1_000L
    1 -> 2_000L
    2 -> 4_000L
    3 -> 8_000L
    else -> 5_000L
}

class ReconnectStateMachine {
    var state: SocketConnectionState = SocketConnectionState.DISCONNECTED
        private set
    var attempt: Int = 0
        private set

    fun reduce(event: ReconnectEvent): ReconnectDecision {
        if (state == SocketConnectionState.TERMINAL) {
            return if (event == ReconnectEvent.SessionExpired || event == ReconnectEvent.ManualRetry) {
                ReconnectDecision.Stop
            } else {
                ReconnectDecision.None
            }
        }
        return when (event) {
            ReconnectEvent.Start -> {
                state = SocketConnectionState.CONNECTING
                ReconnectDecision.ConnectNow
            }
            ReconnectEvent.TransportOpened -> {
                state = SocketConnectionState.AUTHENTICATING
                ReconnectDecision.None
            }
            ReconnectEvent.Authenticated -> {
                state = SocketConnectionState.CONNECTED
                attempt = 0
                ReconnectDecision.None
            }
            ReconnectEvent.TransportLost, ReconnectEvent.AttemptFailed -> {
                state = SocketConnectionState.RECONNECTING
                val delay = reconnectDelayMillis(attempt)
                attempt++
                ReconnectDecision.RetryAfter(delay)
            }
            ReconnectEvent.ManualRetry -> {
                state = SocketConnectionState.CONNECTING
                attempt = 0
                ReconnectDecision.ConnectNow
            }
            ReconnectEvent.SessionExpired -> {
                state = SocketConnectionState.TERMINAL
                ReconnectDecision.Stop
            }
            ReconnectEvent.Stop -> {
                state = SocketConnectionState.DISCONNECTED
                ReconnectDecision.Stop
            }
        }
    }
}
