package com.hienthai.fastowin.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClanEmblemsTest {
    private val emblemIds = listOf(
        "shield", "flag", "sword", "wolf", "eagle", "lion", "dragon", "phoenix", "crown", "kirin"
    )

    @Test
    fun `every catalog emblem has distinct named artwork`() {
        val emblems = emblemIds.map(::clanEmblemImageVector)

        assertEquals(emblemIds.size, emblems.map { it.name }.toSet().size)
        emblems.forEach { assertTrue(it.root.size > 0, "${it.name} must contain artwork") }
        for (left in emblems.indices) {
            for (right in left + 1 until emblems.size) {
                assertNotEquals(emblems[left].root, emblems[right].root)
            }
        }
    }

    @Test
    fun `emblems reuse cached vector instances`() {
        emblemIds.forEach { id ->
            assertSame(clanEmblemImageVector(id), clanEmblemImageVector(id))
        }
    }

    @Test
    fun `missing and unknown ids retain the shield fallback`() {
        val shield = clanEmblemImageVector("shield")

        assertSame(shield, clanEmblemImageVector(null))
        assertSame(shield, clanEmblemImageVector(""))
        assertSame(shield, clanEmblemImageVector("future_emblem"))
    }
}
