package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.platform.AppUpdateBridge
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AnnouncementSafetyTest {
    private val target = "12345678-1234-1234-1234-123456789abc"
    @Test fun `reserved and contextual server notifications cannot echo through client sync`() {
        val admin = AnnouncementControllerTest.item("a").notification.toAppNotification(LocalizationService(AppLanguage.ENGLISH))
        val legacy = admin.copy(id = "cosmetic:old", announcement = null)
        assertEquals(listOf("cosmetic:old"), listOf(admin, admin.copy(announcement = null), admin.copy(id = "other"), legacy).clientSyncSnapshots().map { it.id })
    }
    @Test fun `refresh and detail do not dispatch dialogs awards navigation or update before clicks`() = runTest {
        val navigation = RecordingNavigation(); val updateBridge = RecordingUpdate()
        val gateway = AnnouncementControllerTest.Fake()
        gateway.detailAction = AdminAnnouncementAction.APP_UPDATE
        val controller = AnnouncementController(gateway, navigation, updateBridge) { 0 }
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.HOME)
        controller.refresh()
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
        controller.select("a"); controller.tick()
        val automaticDialogOrAwardCalls = navigation.effects.size + updateBridge.applyCalls
        assertEquals(0, automaticDialogOrAwardCalls)
        assertEquals(0, updateBridge.applyCalls)
        assertTrue(controller.action(false))
        assertEquals(listOf("update"), navigation.effects)
        assertEquals(0, updateBridge.applyCalls)
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.UPDATE)
        assertTrue(controller.applyUpdate(false))
        assertEquals(1, updateBridge.applyCalls)
    }
    @Test fun `active matches and external or malformed targets cannot navigate or install`() {
        val navigation = RecordingNavigation(); val updateBridge = RecordingUpdate()
        for (action in AdminAnnouncementAction.entries) {
            val context = AnnouncementControllerTest.item("a").announcement.copy(action = action, targetId = if (action == AdminAnnouncementAction.REWARD_EVENT || action == AdminAnnouncementAction.GIFT_MAILBOX) target else null)
            assertFalse(dispatchAnnouncementAction(context, true, navigation))
        }
        assertFalse(dispatchAnnouncementAction(AnnouncementControllerTest.item("a").announcement.copy(action = AdminAnnouncementAction.REWARD_EVENT, targetId = "https://evil.invalid"), false, navigation))
        assertFalse(dispatchAnnouncementCorrection("javascript:alert(1)", false, navigation))
        assertFalse(applyAnnouncementUpdate(updateBridge, true))
        assertTrue(navigation.effects.isEmpty()); assertEquals(0, updateBridge.applyCalls)
        assertTrue(dispatchAnnouncementAction(AnnouncementControllerTest.item("a").announcement.copy(action = AdminAnnouncementAction.REWARD_EVENT, targetId = target), false, navigation))
        assertTrue(dispatchAnnouncementAction(AnnouncementControllerTest.item("a").announcement.copy(action = AdminAnnouncementAction.GIFT_MAILBOX, targetId = target), false, navigation))
        assertTrue(dispatchAnnouncementCorrection(target, false, navigation))
        assertEquals(listOf("event:$target", "mailbox", "correction:$target"), navigation.effects)
        updateBridge.updateAvailable = false
        assertFalse(applyAnnouncementUpdate(updateBridge, false))
    }
    class RecordingNavigation : AnnouncementNavigation {
        val effects = mutableListOf<String>()
        override fun event(id: String) { effects += "event:$id" }
        override fun mailbox() { effects += "mailbox" }
        override fun update() { effects += "update" }
        override fun correction(id: String) { effects += "correction:$id" }
    }
    class RecordingUpdate : AppUpdateBridge {
        var applyCalls = 0
        override var updateAvailable = true
        override fun observe(onUpdateAvailable: () -> Unit): () -> Unit = {}
        override fun applyUpdate() { applyCalls++ }
        override fun dismissUpdate() = Unit
    }
}
