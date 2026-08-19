package com.hienthai.fastowin.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomDeepLinkTest {
    @Test
    fun roomLinkRoundTripsWithoutIncludingAPassword() {
        val roomId = "550e8400-e29b-41d4-a716-446655440000"
        val link = buildRoomDeepLink(roomId)

        assertEquals("fasttowin://room/$roomId", link)
        assertEquals(RoomDeepLink(roomId), parseRoomDeepLink(link))
        assertTrue("password=" !in buildRoomShareText("Phòng Hiền", roomId).lowercase())
    }

    @Test
    fun invalidSchemesAndRoomIdsAreRejected() {
        assertNull(parseRoomDeepLink("https://example.com/room/abc"))
        assertNull(parseRoomDeepLink("fasttowin://room/../../secret"))
        assertNull(parseRoomDeepLink("fasttowin://profile/player"))
    }
}
