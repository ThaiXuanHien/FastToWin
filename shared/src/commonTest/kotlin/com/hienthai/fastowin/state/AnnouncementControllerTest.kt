package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AnnouncementControllerTest {
    @Test fun `admin context localizes authored plain text while old rows keep fallback`() {
        val row = item("a")
        assertEquals("Thông báo a", row.notification.toAppNotification(LocalizationService(AppLanguage.VIETNAMESE)).title)
        assertEquals("Announcement a", row.notification.toAppNotification(LocalizationService(AppLanguage.ENGLISH)).title)
        assertEquals("fallback", row.notification.copy(announcement = null).toAppNotification(LocalizationService(AppLanguage.ENGLISH)).title)
    }
    @Test fun `history pages retain expired cancelled and dismissed rows without duplicate IDs`() = runTest {
        val gateway = Fake()
        gateway.pages = listOf(listOf(item("a", AdminAnnouncementState.EXPIRED), item("b", AdminAnnouncementState.CANCELLED, true)), listOf(item("b"), item("c")))
        val controller = controller(gateway, AnnouncementView.INBOX)
        controller.refresh(); controller.more()
        assertEquals(listOf("a", "b", "c"), controller.state.value.items.map { it.announcement.id })
        assertEquals("3", controller.state.value.total)
        assertTrue(controller.state.value.items[1].dismissed)
        assertEquals(listOf(0, 1), gateway.pageCalls)
    }
    @Test fun `server hide is separate from read dismiss and exclusive end expires selected locally`() = runTest {
        val gateway = Fake(); var now = 0L
        val controller = AnnouncementController(gateway) { now }
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.HOME)
        controller.refresh(); controller.hide("a"); controller.hide("a")
        assertEquals(listOf("a"), gateway.hideBannerCalls)
        assertTrue(gateway.readCalls.isEmpty())
        assertTrue(gateway.dismissCalls.isEmpty())
        assertNull(controller.state.value.banner)
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
        controller.select("a")
        now = 1000
        controller.tick()
        assertEquals("EXPIRED", controller.state.value.selected!!.announcement.state.name)
        assertEquals(listOf<String?>("a"), gateway.readCalls)
    }
    @Test fun `late inbox responses cannot cross account token language view or request epochs`() = runTest {
        for (change in 0..4) {
            val deferred = CompletableDeferred<AnnouncementPlayerPage>()
            val gateway = Fake().apply { latePage = deferred }
            val controller = controller(gateway, AnnouncementView.INBOX)
            val loading = async { controller.refresh() }
            kotlinx.coroutines.yield()
            when (change) {
                0 -> controller.context("other", "token", AppLanguage.ENGLISH, AnnouncementView.INBOX)
                1 -> controller.context("account", "other", AppLanguage.ENGLISH, AnnouncementView.INBOX)
                2 -> controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.INBOX)
                3 -> controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.INACTIVE)
                else -> { gateway.latePage = null; controller.refresh() }
            }
            deferred.complete(AnnouncementPlayerPage(listOf(item("stale")), 0, 25, "1", 1000)); loading.await()
            assertTrue(controller.state.value.items.none { it.announcement.id == "stale" }, "fence $change")
        }
    }
    @Test fun `preferences default false and uncertain identical retry preserves version with conflict refresh`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.SETTINGS)
        controller.refresh()
        assertFalse(controller.state.value.preferences!!.adminEnabled)
        gateway.saveError = "NETWORK_ERROR"
        controller.savePreferences(true, false)
        assertEquals(1, controller.state.value.preferenceIntent!!.expectedVersion)
        assertEquals("NETWORK_ERROR", controller.state.value.error)
        gateway.saveError = null
        controller.retryPreferences()
        assertEquals(listOf(AnnouncementPreferenceRequest(1, true, false), AnnouncementPreferenceRequest(1, true, false)), gateway.saves)
        assertTrue(controller.state.value.preferences!!.adminEnabled)
        gateway.saveError = "CONFLICT"
        controller.savePreferences(false, true)
        assertEquals("CONFLICT", controller.state.value.error)
        assertNull(controller.state.value.preferenceIntent)
    }
    @Test fun `refresh retains ready banner geometry until new response cancels it`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.HOME)
        controller.refresh()
        val deferred = CompletableDeferred<AnnouncementBanners>(); gateway.lateBanners = deferred
        val loading = async { controller.refresh() }; kotlinx.coroutines.yield()
        assertEquals("a", controller.state.value.banner!!.announcement.id)
        deferred.complete(AnnouncementBanners(listOf(item("a", AdminAnnouncementState.CANCELLED)), 1000)); loading.await()
        assertNull(controller.state.value.banner)
    }
    @Test fun `background refresh cannot erase an uncertain preference retry or replace saved choice`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.SETTINGS)
        controller.refresh(); gateway.saveError = "NETWORK_ERROR"
        controller.savePreferences(true, false)
        controller.refresh()
        assertEquals("NETWORK_ERROR", controller.state.value.error)
        assertEquals(AnnouncementPreferenceRequest(1, true, false), controller.state.value.preferenceIntent)
        assertEquals(1, gateway.preferenceCalls)
    }
    @Test fun `inbox read and dismiss receipts keep full history and do not hide banner`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.INBOX)
        controller.refresh(); controller.read("a"); controller.dismiss("a")
        assertTrue(controller.state.value.items.single().notification.isRead)
        assertTrue(controller.state.value.items.single().dismissed)
        assertEquals("1", controller.state.value.total)
        assertEquals(listOf<String?>("a"), gateway.readCalls)
        assertEquals(listOf<String?>("a"), gateway.dismissCalls)
        assertTrue(gateway.hideBannerCalls.isEmpty())
    }
    @Test fun `late mutation after account change cannot restore old rows or consent`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.INBOX)
        controller.refresh()
        val deferred = CompletableDeferred<AnnouncementMutationResult>(); gateway.lateRead = deferred
        val reading = async { controller.read("a") }; kotlinx.coroutines.yield()
        controller.context("other", "other-token", AppLanguage.ENGLISH, AnnouncementView.INBOX)
        deferred.complete(AnnouncementMutationResult("1", 1500)); reading.await()
        assertTrue(controller.state.value.items.isEmpty()); assertFalse(controller.state.value.pending)
    }
    @Test fun `read all respects dismissed receipt history which server excludes from read mutations`() = runTest {
        val gateway = Fake().apply { pages = listOf(listOf(item("a", dismissed = true), item("b"))) }
        val controller = controller(gateway, AnnouncementView.INBOX)
        controller.refresh(); controller.read()
        assertFalse(controller.state.value.items.first().notification.isRead)
        assertTrue(controller.state.value.items.last().notification.isRead)
    }
    @Test fun `late detail cannot expose previous selection while switching announcement IDs`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.DETAIL)
        controller.select("a")
        val deferred = CompletableDeferred<AnnouncementPlayerItem>(); gateway.lateDetail = deferred
        val loading = async { controller.select("b") }; kotlinx.coroutines.yield()
        assertNull(controller.state.value.selected)
        deferred.complete(item("b")); loading.await()
        assertEquals("b", controller.state.value.selected!!.announcement.id)
    }
    private fun controller(gateway: Fake, view: AnnouncementView) = AnnouncementController(gateway).also {
        it.context("account", "token", AppLanguage.ENGLISH, view)
    }
    class Fake : AnnouncementGateway {
        var pages = listOf(listOf(item("a")))
        var latePage: CompletableDeferred<AnnouncementPlayerPage>? = null
        var lateBanners: CompletableDeferred<AnnouncementBanners>? = null
        var saveError: String? = null
        var detailAction = AdminAnnouncementAction.NONE
        var preferenceCalls = 0
        var lateRead: CompletableDeferred<AnnouncementMutationResult>? = null
        var lateDetail: CompletableDeferred<AnnouncementPlayerItem>? = null
        val pageCalls = mutableListOf<Int>(); val hideBannerCalls = mutableListOf<String>()
        val readCalls = mutableListOf<String?>(); val dismissCalls = mutableListOf<String?>()
        val saves = mutableListOf<AnnouncementPreferenceRequest>()
        override suspend fun inbox(page: Int, pageSize: Int): AnnouncementPlayerPage {
            pageCalls += page
            return latePage?.await() ?: AnnouncementPlayerPage(pages.getOrElse(page) { emptyList() }, page, pageSize, if (pages.size > 1) "3" else "1", 1000)
        }
        override suspend fun detail(id: String) = lateDetail?.await() ?: item(id).let { it.copy(announcement = it.announcement.copy(action = detailAction)) }
        override suspend fun banners() = lateBanners?.await() ?: AnnouncementBanners(listOf(item("a")), 1000)
        override suspend fun read(id: String?): AnnouncementMutationResult { readCalls += id; return lateRead?.await() ?: AnnouncementMutationResult("1", 1000) }
        override suspend fun dismiss(id: String?): AnnouncementMutationResult { dismissCalls += id; return AnnouncementMutationResult("1", 1000) }
        override suspend fun hide(id: String): AnnouncementMutationResult { hideBannerCalls += id; return AnnouncementMutationResult("1", 1000) }
        override suspend fun preferences(): AnnouncementPreferenceView { preferenceCalls++; return AnnouncementPreferenceView(1, false, false, 1000) }
        override suspend fun savePreferences(request: AnnouncementPreferenceRequest): AnnouncementPreferenceView {
            saves += request; saveError?.let { throw AnnouncementApiException(it) }
            return AnnouncementPreferenceView(2, request.adminEnabled, request.eventEnabled, 1000)
        }
    }
    companion object {
        fun item(id: String, state: AdminAnnouncementState = AdminAnnouncementState.ACTIVE, dismissed: Boolean = false): AnnouncementPlayerItem {
            val context = AdminAnnouncementContext(id, "Thông báo $id", "Announcement $id", "Nội dung", "Body", AdminAnnouncementCategory.GENERAL, 10, 1000, 2000, state, AdminAnnouncementAction.NONE)
            return AnnouncementPlayerItem(NotificationSnapshot("admin-announcement:$id", NotificationKind.COSMETIC, "fallback", "fallback body", 1000, destination = NotificationDestination.PROFILE, announcement = context), context, dismissed, false)
        }
    }
}
