package com.hienthai.fastowin.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class MaintenanceProtocolTest {
    @Test fun legacyStatusDecodesWithAdditiveDefaults() {
        val status = Json.decodeFromString<ServiceStatusResponse>("""{"maintenance":true,"message":"Legacy","pollAfterSeconds":60}""")
        assertEquals(MaintenanceState.MAINTENANCE, status.state)
        assertNull(status.mode)
        assertNull(status.version)
        assertNull(status.messageVi)
        assertNull(status.expectedEndEpochMillis)
    }
    @Test fun maintenanceStatusRoundTripsBothLanguagesAndDrainWithoutLegacyClosure() {
        val status = ServiceStatusResponse(state=MaintenanceState.DRAINING, mode=MaintenanceMode.PLANNED,
            version=7, messageVi="Bảo trì", messageEn="Maintenance", transitionAtEpochMillis=1000, expectedEndEpochMillis=2000)
        val result = Json.decodeFromString<ServiceStatusResponse>(Json.encodeToString(status))
        assertFalse(result.maintenance)
        assertEquals(MaintenanceState.DRAINING,result.state)
        assertEquals(7,result.version)
        assertEquals("Bảo trì",result.messageVi)
        assertEquals(1000,result.transitionAtEpochMillis)
    }
}
