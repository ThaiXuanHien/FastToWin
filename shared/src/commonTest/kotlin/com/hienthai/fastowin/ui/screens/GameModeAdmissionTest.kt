package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.data.network.ServiceStatusState
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.MaintenanceState
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameModeAdmissionTest {
    @Test
    fun `unreachable practice can select an unlocked mode`() {
        assertTrue(gameModeChoiceEnabled(GameMode.ORDER, 1, false, false))
    }

    @Test
    fun `draining practice can select an unlocked mode`() {
        val observation = ServiceStatusState(ServiceStatusResponse(state = MaintenanceState.DRAINING), true)
        assertTrue(gameModeChoiceEnabled(GameMode.RANDOM_TARGET, 3, false, observation.newIntentAllowed))
    }

    @Test
    fun `unreachable online mode stays disabled`() {
        assertFalse(gameModeChoiceEnabled(GameMode.ORDER, 1, true, false))
    }

    @Test
    fun `draining online mode stays disabled`() {
        val observation = ServiceStatusState(ServiceStatusResponse(state = MaintenanceState.DRAINING), true)
        assertFalse(gameModeChoiceEnabled(GameMode.RANDOM_TARGET, 3, true, observation.newIntentAllowed))
    }

    @Test
    fun `practice still requires the mode unlock level`() {
        assertFalse(gameModeChoiceEnabled(GameMode.RANDOM_TARGET, 2, false, false))
    }

    @Test
    fun `admitted online mode still requires the mode unlock level`() {
        assertFalse(gameModeChoiceEnabled(GameMode.RANDOM_TARGET, 2, true, true))
        assertTrue(gameModeChoiceEnabled(GameMode.RANDOM_TARGET, 3, true, true))
    }
}
