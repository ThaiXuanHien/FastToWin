package com.hienthai.fastowin.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ArcadePaginationTest {
    @Test
    fun `load more advances one page and stops at total`() {
        assertEquals(40, nextArcadePageItemCount(currentVisibleCount = 20, totalItemCount = 55))
        assertEquals(55, nextArcadePageItemCount(currentVisibleCount = 40, totalItemCount = 55))
        assertEquals(55, nextArcadePageItemCount(currentVisibleCount = 55, totalItemCount = 55))
    }

    @Test
    fun `load more normalizes invalid counts`() {
        assertEquals(1, nextArcadePageItemCount(currentVisibleCount = -5, totalItemCount = 3, pageSize = 0))
        assertEquals(0, nextArcadePageItemCount(currentVisibleCount = 20, totalItemCount = -1))
    }
}
