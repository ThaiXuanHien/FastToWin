package com.hienthai.fastowin.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ReconnectStateMachineTest {
    @Test
    fun `unexpected close retries with bounded schedule`() {
        val machine = ReconnectStateMachine()
        machine.reduce(ReconnectEvent.Start)
        machine.reduce(ReconnectEvent.TransportOpened)
        machine.reduce(ReconnectEvent.Authenticated)

        assertEquals(ReconnectDecision.RetryAfter(1_000), machine.reduce(ReconnectEvent.TransportLost))
        assertEquals(ReconnectDecision.RetryAfter(2_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(ReconnectDecision.RetryAfter(4_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(ReconnectDecision.RetryAfter(8_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(ReconnectDecision.RetryAfter(5_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(SocketConnectionState.RECONNECTING, machine.state)
    }

    @Test
    fun `manual retry resets attempt and requests immediate fresh transport`() {
        val machine = ReconnectStateMachine()
        machine.reduce(ReconnectEvent.Start)
        machine.reduce(ReconnectEvent.AttemptFailed)
        machine.reduce(ReconnectEvent.AttemptFailed)
        assertEquals(ReconnectDecision.ConnectNow, machine.reduce(ReconnectEvent.ManualRetry))
        assertEquals(0, machine.attempt)
    }

    @Test
    fun `expired or replaced account session is terminal`() {
        val machine = ReconnectStateMachine()
        assertEquals(ReconnectDecision.Stop, machine.reduce(ReconnectEvent.SessionExpired))
        assertEquals(SocketConnectionState.TERMINAL, machine.state)
        assertEquals(ReconnectDecision.Stop, machine.reduce(ReconnectEvent.ManualRetry))
    }
}
