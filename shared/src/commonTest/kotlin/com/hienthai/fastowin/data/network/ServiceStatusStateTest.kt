package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.localization.AppLanguage
import kotlin.test.*

class ServiceStatusStateTest {
    @Test fun `drain preserves game while disabling new intent and unknown never becomes active`() {
        assertFalse(ServiceStatusState().newIntentAllowed)
        for (state in MaintenanceState.entries) {
            val view = ServiceStatusState().received(ServiceStatusResponse(state = state))
            assertEquals(state == MaintenanceState.MAINTENANCE, view.fullyClosed)
            assertEquals(state == MaintenanceState.ACTIVE || state == MaintenanceState.ANNOUNCING, view.newIntentAllowed)
            assertEquals(state == MaintenanceState.ANNOUNCING || state == MaintenanceState.DRAINING, view.showBanner)
            assertFalse(view.received(null).newIntentAllowed)
        }
    }
    @Test fun `bilingual runtime message fallback countdown and Bangkok advisory are deterministic`() {
        val value = ServiceStatusResponse(message = "legacy", messageVi = "VI", messageEn = "EN")
        assertEquals("VI", value.localizedMaintenanceMessage(AppLanguage.VIETNAMESE))
        assertEquals("EN", value.localizedMaintenanceMessage(AppLanguage.ENGLISH))
        assertEquals("VI", value.copy(messageEn = " ").localizedMaintenanceMessage(AppLanguage.ENGLISH))
        assertEquals("legacy", value.copy(messageEn = null, messageVi = null).localizedMaintenanceMessage(AppLanguage.ENGLISH))
        assertEquals(2, maintenanceCountdownSeconds(2000, 999))
        assertEquals(0, maintenanceCountdownSeconds(2000, 3000))
        assertNull(maintenanceCountdownSeconds(null, 0))
        assertEquals("1970-01-01 07:00", maintenanceBangkokTime(0))
    }
    @Test fun `failed recheck retains explicit closure until explicit reopening`() {
        val closed = ServiceStatusResponse(true, pollAfterSeconds = 5)
        val failed = ServiceStatusState().received(closed).received(null)
        assertEquals(closed, failed.status)
        assertEquals(false, failed.reachable)
        assertEquals(MaintenanceState.ACTIVE, failed.received(ServiceStatusResponse(false)).status?.state)
        assertNull(ServiceStatusState().received(null).status)
    }
    @Test fun `advertised five second maintenance polling is honored with safe bounds`() {
        assertEquals(5, ServiceStatusState().received(ServiceStatusResponse(true, pollAfterSeconds = 5)).pollSeconds)
        assertEquals(30, ServiceStatusState().received(ServiceStatusResponse(false, pollAfterSeconds = 30)).pollSeconds)
        assertEquals(5, ServiceStatusState().received(ServiceStatusResponse(true, pollAfterSeconds = 0)).pollSeconds)
        assertEquals(300, ServiceStatusState().received(ServiceStatusResponse(false, pollAfterSeconds = 999)).pollSeconds)
    }
}
