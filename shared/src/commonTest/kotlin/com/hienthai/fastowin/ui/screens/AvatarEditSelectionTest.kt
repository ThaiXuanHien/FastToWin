package com.hienthai.fastowin.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarEditSelectionTest {
    @Test
    fun `save waits for selected avatar preparation and then uploads it`() {
        val selected = AvatarEditSelection<String>()
        val uploaded = mutableListOf<ByteArray>()
        val saved = mutableListOf<String>()
        val chosen = byteArrayOf(7)

        selected.beginPreparation()
        assertFalse(selected.canSave("New name", isProfileSaving = false))
        selected.save("New name", "avatar_default", isProfileSaving = false, uploaded::add) { name, _ ->
            saved += name
        }
        assertTrue(uploaded.isEmpty())
        assertTrue(saved.isEmpty())

        selected.finishPreparation(chosen, "preview")
        assertTrue(selected.canSave("New name", isProfileSaving = false))
        selected.save("New name", "avatar_default", isProfileSaving = false, uploaded::add) { name, _ ->
            saved += name
        }
        assertEquals(listOf(chosen), uploaded)
        assertEquals(listOf("New name"), saved)
    }

    @Test
    fun `failed or cancelled picker restores Save and retains earlier preview`() {
        val selected = AvatarEditSelection<String>()
        val earlier = byteArrayOf(3)
        selected.beginPreparation()
        selected.finishPreparation(earlier, "earlier preview")

        selected.beginPreparation()
        assertFalse(selected.canSave("Name", isProfileSaving = false))
        selected.finishPreparation(null, null)

        assertTrue(selected.canSave("Name", isProfileSaving = false))
        assertEquals("earlier preview", selected.preview)
        assertTrue(selected.bytes === earlier)

        selected.clear()
        assertFalse(selected.isPreparing)
        assertEquals(null, selected.bytes)
    }
}
