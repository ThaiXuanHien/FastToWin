package com.hienthai.fastowin.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarSelectionTokenTest {
    @Test
    fun `new chooser and disposal reject callbacks from older selections`() {
        val selections = AvatarSelectionToken()
        val first = selections.next()
        val second = selections.next()

        assertFalse(selections.isCurrent(first))
        assertTrue(selections.isCurrent(second))

        selections.invalidate()
        assertFalse(selections.isCurrent(second))
    }
}
