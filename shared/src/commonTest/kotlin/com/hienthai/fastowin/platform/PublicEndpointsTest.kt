package com.hienthai.fastowin.platform

import com.hienthai.fastowin.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicEndpointsTest {
    @Test fun `addresses derive from the single configured host`() {
        assertEquals("fasttowin.up.railway.app", PublicEndpoints.host)
        assertEquals("wss://fasttowin.up.railway.app/game", PublicEndpoints.productionGameServerUrl)
        assertEquals("https://fasttowin.up.railway.app/privacy", PublicEndpoints.legalUrl("privacy", AppLanguage.VIETNAMESE))
        assertEquals("https://fasttowin.up.railway.app/delete-account/en", PublicEndpoints.legalUrl("delete-account", AppLanguage.ENGLISH))
    }

    @Test fun `update destinations follow the platform`() {
        assertEquals("https://play.google.com/store/apps/details?id=com.hienthai.fastowin", PublicEndpoints.updateUrl("android"))
        assertEquals("https://fasttowin.up.railway.app", PublicEndpoints.updateUrl("ios"))
        assertNull(PublicEndpoints.updateUrl("web"))
    }
}
