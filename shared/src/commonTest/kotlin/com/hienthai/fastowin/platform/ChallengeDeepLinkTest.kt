package com.hienthai.fastowin.platform

import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.createPracticeChallenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeDeepLinkTest {
    @Test
    fun challengeLinkRoundTripsWithACanonicalCode() {
        val challenge = createPracticeChallenge(GameMode.RANDOM_TARGET, seed = 0x12345678)
        val link = buildChallengeDeepLink(challenge.code.lowercase())

        assertEquals("fasttowin://challenge/${challenge.code}", link)
        assertEquals(challenge, parseChallengeDeepLink(link))
        assertEquals(challenge, parseChallengeDeepLink("$link?source=friend#play"))
    }

    @Test
    fun invalidChallengeLinksAreRejected() {
        assertNull(parseChallengeDeepLink("fasttowin://room/FTW-CL-12345678-BE"))
        assertNull(parseChallengeDeepLink("fasttowin://challenge/not-a-code"))
        assertFailsWith<IllegalArgumentException> { buildChallengeDeepLink("invalid") }
    }

    @Test
    fun appRouterRecognizesBothRoomAndChallengeLinks() {
        val challenge = createPracticeChallenge(GameMode.ORDER, seed = 1234)

        assertTrue(AppDeepLinkRouter.openUri("fasttowin://room/room-123"))
        assertEquals(RoomDeepLink("room-123"), RoomDeepLinkRouter.pendingLink.value)
        RoomDeepLinkRouter.consume("room-123")

        assertTrue(AppDeepLinkRouter.openUri(buildChallengeDeepLink(challenge.code)))
        assertEquals(challenge, ChallengeDeepLinkRouter.pendingChallenge.value)
        ChallengeDeepLinkRouter.consume(challenge.code)
    }
}
