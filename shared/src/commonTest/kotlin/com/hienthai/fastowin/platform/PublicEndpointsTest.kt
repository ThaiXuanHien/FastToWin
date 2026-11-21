package com.hienthai.fastowin.platform

import com.hienthai.fastowin.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicEndpointsTest {
    @Test fun `addresses derive from the single configured host`() {
        assertEquals("fasttowin.xyz", PublicEndpoints.host)
        assertEquals("wss://fasttowin.xyz/game", PublicEndpoints.productionGameServerUrl)
        assertEquals("https://fasttowin.xyz/privacy", PublicEndpoints.legalUrl("privacy", AppLanguage.VIETNAMESE))
        assertEquals("https://fasttowin.xyz/delete-account/en", PublicEndpoints.legalUrl("delete-account", AppLanguage.ENGLISH))
        assertEquals(listOf("wss://fasttowin.up.railway.app/game"), PublicEndpoints.previousGameServerUrls)
    }

    @Test fun `update destinations follow the platform`() {
        assertEquals("https://play.google.com/store/apps/details?id=com.hienthai.fastowin", PublicEndpoints.updateUrl("android"))
        assertEquals("https://fasttowin.xyz", PublicEndpoints.updateUrl("ios"))
        assertNull(PublicEndpoints.updateUrl("web"))
    }
}
