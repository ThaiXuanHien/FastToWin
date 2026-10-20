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
    @Test fun `refresh reconciles loaded history window and keeps the next page after new first page rows`() = runTest {
        val gateway = Fake().apply {
            pageTotal = "6"
            pages = listOf(listOf(item("a"), item("b")), listOf(item("c"), item("d")), listOf(item("e")))
        }
        val controller = controller(gateway, AnnouncementView.INBOX)
        controller.refresh(); controller.more()
        val pageZero = CompletableDeferred<AnnouncementPlayerPage>()
        gateway.latePage = pageZero
        val refreshing = async { controller.refresh() }; kotlinx.coroutines.yield()
        assertEquals(listOf("a", "b", "c", "d"), controller.state.value.items.map { it.announcement.id })
        gateway.latePage = null
        gateway.pages = listOf(listOf(item("n"), item("a")), listOf(item("b", AdminAnnouncementState.CANCELLED, true), item("c")), listOf(item("d"), item("e")))
        pageZero.complete(AnnouncementPlayerPage(listOf(item("n"), item("a").let { it.copy(notification = it.notification.copy(isRead = true)) }), 0, 25, "6", 1000))
        refreshing.await()
        assertEquals(listOf("n", "a", "b", "c", "d"), controller.state.value.items.map { it.announcement.id })
        assertTrue(controller.state.value.items.first { it.announcement.id == "a" }.notification.isRead)
        assertTrue(controller.state.value.items.first { it.announcement.id == "b" }.dismissed)
        assertEquals(AdminAnnouncementState.CANCELLED, controller.state.value.items.first { it.announcement.id == "b" }.announcement.state)
        controller.more()
        assertEquals(listOf(0, 1, 0, 1, 2), gateway.pageCalls)
        assertEquals(listOf("n", "a", "b", "c", "d", "e"), controller.state.value.items.map { it.announcement.id })
        assertFalse(controller.state.value.hasMore)
    }
    @Test fun `fresh direct detail expires with read-only server time when read mutation fails`() = runTest {
        val gateway = Fake().apply { readError = "NETWORK_ERROR" }; var now = 0L
        val controller = AnnouncementController(gateway) { now }
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
        controller.select("a")
        assertEquals("NETWORK_ERROR", controller.state.value.error)
        now = 1000; controller.tick()
        assertEquals(AdminAnnouncementState.EXPIRED, controller.state.value.selected!!.announcement.state)
        assertFalse(controller.state.value.selected!!.notification.isRead)
        assertEquals(1, gateway.bannerCalls)
    }
    @Test fun `fresh direct detail ticks across exclusive end while read mutation is still waiting`() = runTest {
        val receipt = CompletableDeferred<AnnouncementMutationResult>()
        val gateway = Fake().apply { lateRead = receipt }; var now = 0L
        val controller = AnnouncementController(gateway) { now }
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
        val selecting = async { controller.select("a") }; kotlinx.coroutines.yield()
        try {
            assertEquals(listOf<String?>("a"), gateway.readCalls)
            now = 999; controller.tick()
            assertEquals(AdminAnnouncementState.ACTIVE, controller.state.value.selected!!.announcement.state)
            now = 1000; controller.tick()
            assertEquals(AdminAnnouncementState.EXPIRED, controller.state.value.selected!!.announcement.state)
            assertFalse(selecting.isCompleted)
        } finally { receipt.complete(AnnouncementMutationResult("1", 1000)); selecting.await() }
        assertEquals(AdminAnnouncementState.EXPIRED, controller.state.value.selected!!.announcement.state)
    }
    @Test fun `late detail clock envelope cannot cross account token language view or request epochs`() = runTest {
        for (change in 0..4) {
            val clock = CompletableDeferred<AnnouncementBanners>()
            val gateway = Fake().apply { lateBanners = clock }
            val controller = controller(gateway, AnnouncementView.DETAIL)
            val selecting = async { controller.select("a") }; kotlinx.coroutines.yield()
            try {
                assertEquals(1, gateway.bannerCalls)
                when (change) {
                    0 -> controller.context("other", "token", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
                    1 -> controller.context("account", "other", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
                    2 -> controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.DETAIL)
                    3 -> controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.INACTIVE)
                    else -> { gateway.lateBanners = null; controller.select("b") }
                }
            } finally { clock.complete(AnnouncementBanners(emptyList(), 90000)); selecting.await() }
            assertTrue(controller.state.value.selected?.announcement?.id != "a", "fence $change")
            assertTrue((controller.state.value.serverNowEpochMillis ?: 0) < 90000, "clock fence $change")
            assertEquals(if (change == 4) listOf<String?>("b") else emptyList(), gateway.readCalls)
        }
    }
    @Test fun `fetch Error during detail reports network failure and allows retry`() = runTest {
        val gateway = Fake().apply { failure = Error("Fail to fetch") }
        val controller = controller(gateway, AnnouncementView.DETAIL)
        controller.select("a")
        assertEquals("NETWORK_ERROR", controller.state.value.error)
        assertFalse(controller.state.value.loading)
        assertNull(controller.state.value.selected)
        gateway.failure = null
        controller.select("a")
        assertNull(controller.state.value.error)
        assertTrue(controller.state.value.selected!!.notification.isRead)
    }
    @Test fun `fetch Error during mutations leaves receipts unchanged and allows retry`() = runTest {
        for (operation in listOf("read", "dismiss", "hide")) {
            val gateway = Fake()
            val controller = controller(gateway, if (operation == "hide") AnnouncementView.HOME else AnnouncementView.INBOX)
            controller.refresh()
            suspend fun mutate() = when (operation) {
                "read" -> controller.read("a")
                "dismiss" -> controller.dismiss("a")
                else -> controller.hide("a")
            }
            gateway.failure = Error("Fail to fetch")
            mutate()
            assertEquals("NETWORK_ERROR", controller.state.value.error, operation)
            assertFalse(controller.state.value.pending, operation)
            if (operation == "hide") assertNotNull(controller.state.value.banner)
            else {
                assertFalse(controller.state.value.items.single().notification.isRead)
                assertFalse(controller.state.value.items.single().dismissed)
            }
            gateway.failure = null
            mutate()
            assertNull(controller.state.value.error)
            when (operation) {
                "read" -> assertTrue(controller.state.value.items.single().notification.isRead)
                "dismiss" -> assertTrue(controller.state.value.items.single().dismissed)
                else -> assertNull(controller.state.value.banner)
            }
        }
    }
    @Test fun `fetch Error during consent save preserves uncertain intent for identical retry`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.SETTINGS)
        controller.refresh()
        gateway.failure = Error("Fail to fetch")
        controller.savePreferences(true, false)
        assertEquals("NETWORK_ERROR", controller.state.value.error)
        assertFalse(controller.state.value.pending)
        assertFalse(controller.state.value.preferences!!.adminEnabled)
        assertEquals(AnnouncementPreferenceRequest(1, true, false), controller.state.value.preferenceIntent)
        gateway.failure = null
        controller.retryPreferences()
        assertTrue(controller.state.value.preferences!!.adminEnabled)
        assertNull(controller.state.value.preferenceIntent)
        assertNull(controller.state.value.error)
    }
    @Test fun `fetch Error during conflict reload leaves consent unavailable until authority returns`() = runTest {
        val gateway = Fake(); val controller = controller(gateway, AnnouncementView.SETTINGS)
        controller.refresh()
        gateway.saveError = "CONFLICT"; gateway.preferenceFailure = Error("Fail to fetch")
        controller.savePreferences(true, false)
        assertEquals("CONFLICT", controller.state.value.error)
        assertFalse(controller.state.value.pending)
        assertNull(controller.state.value.preferences)
        assertNull(controller.state.value.preferenceIntent)
        gateway.preferenceFailure = null
        controller.refresh()
        assertNotNull(controller.state.value.preferences)
    }
    @Test fun `fetch Error obtaining detail clock does not prevent a successful read receipt`() = runTest {
        val gateway = Fake().apply { lateBanners = CompletableDeferred<AnnouncementBanners>().also { it.completeExceptionally(Error("Fail to fetch")) } }
        // Assert the receipt clock without depending on real milliseconds elapsed on CI.
        val controller = AnnouncementController(gateway) { 0L }
        controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.DETAIL)
        controller.select("a")
        assertTrue(controller.state.value.selected!!.notification.isRead)
        assertEquals(1000L, controller.state.value.serverNowEpochMillis)
        assertNull(controller.state.value.error)
        assertFalse(controller.state.value.loading)
    }
    @Test fun `late fetch Error cannot overwrite a new account token language view or request`() = runTest {
        for (change in 0..4) {
            val response = CompletableDeferred<AnnouncementBanners>()
            val gateway = Fake().apply { lateBanners = response }
            val controller = controller(gateway, AnnouncementView.HOME)
            val loading = async { controller.refresh() }; kotlinx.coroutines.yield()
            when (change) {
                0 -> controller.context("other", "token", AppLanguage.ENGLISH, AnnouncementView.HOME)
                1 -> controller.context("account", "other", AppLanguage.ENGLISH, AnnouncementView.HOME)
                2 -> controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.HOME)
                3 -> controller.context("account", "token", AppLanguage.ENGLISH, AnnouncementView.INACTIVE)
                else -> { gateway.lateBanners = null; controller.refresh() }
            }
            response.completeExceptionally(Error("Fail to fetch")); loading.await()
            assertNull(controller.state.value.error, "fence $change")
            assertFalse(controller.state.value.loading, "fence $change")
            if (change == 4) assertNotNull(controller.state.value.banner)
        }
    }
    @Test fun `cancellation propagates without becoming network failure at every request boundary`() = runTest {
        for (operation in listOf("load", "read", "preferences")) {
            val gateway = Fake()
            val controller = controller(gateway, if (operation == "preferences") AnnouncementView.SETTINGS else AnnouncementView.INBOX)
            controller.refresh()
            val cancellation = kotlinx.coroutines.CancellationException("Owned cancellation")
            gateway.failure = cancellation
            val caught = assertFailsWith<kotlinx.coroutines.CancellationException> {
                when (operation) {
                    "load" -> controller.refresh()
                    "read" -> controller.read("a")
                    else -> controller.savePreferences(true, false)
                }
            }
            assertSame(cancellation, caught)
            assertNull(controller.state.value.error)
            assertFalse(controller.state.value.loading)
            assertFalse(controller.state.value.pending)
        }
    }
    private fun controller(gateway: Fake, view: AnnouncementView) = AnnouncementController(gateway).also {
        it.context("account", "token", AppLanguage.ENGLISH, view)
    }
    class Fake : AnnouncementGateway {
        var failure: Throwable? = null
        var preferenceFailure: Throwable? = null
        private fun fail() { failure?.let { throw it } }
        var pages = listOf(listOf(item("a")))
        var pageTotal: String? = null
        var latePage: CompletableDeferred<AnnouncementPlayerPage>? = null
        var lateBanners: CompletableDeferred<AnnouncementBanners>? = null
        var saveError: String? = null
        var detailAction = AdminAnnouncementAction.NONE
        var readError: String? = null
        var bannerCalls = 0
        var preferenceCalls = 0
        var lateRead: CompletableDeferred<AnnouncementMutationResult>? = null
        var lateDetail: CompletableDeferred<AnnouncementPlayerItem>? = null
        val pageCalls = mutableListOf<Int>(); val hideBannerCalls = mutableListOf<String>()
        val readCalls = mutableListOf<String?>(); val dismissCalls = mutableListOf<String?>()
        val saves = mutableListOf<AnnouncementPreferenceRequest>()
        override suspend fun inbox(page: Int, pageSize: Int): AnnouncementPlayerPage {
            fail()
            pageCalls += page
            return latePage?.await() ?: AnnouncementPlayerPage(pages.getOrElse(page) { emptyList() }, page, pageSize, pageTotal ?: if (pages.size > 1) "3" else "1", 1000)
        }
        override suspend fun detail(id: String): AnnouncementPlayerItem { fail(); return lateDetail?.await() ?: item(id).let { it.copy(announcement = it.announcement.copy(action = detailAction)) } }
        override suspend fun banners(): AnnouncementBanners { fail(); bannerCalls++; return lateBanners?.await() ?: AnnouncementBanners(listOf(item("a")), 1000) }
        override suspend fun read(id: String?): AnnouncementMutationResult { fail(); readCalls += id; readError?.let { throw AnnouncementApiException(it) }; return lateRead?.await() ?: AnnouncementMutationResult("1", 1000) }
        override suspend fun dismiss(id: String?): AnnouncementMutationResult { fail(); dismissCalls += id; return AnnouncementMutationResult("1", 1000) }
        override suspend fun hide(id: String): AnnouncementMutationResult { fail(); hideBannerCalls += id; return AnnouncementMutationResult("1", 1000) }
        override suspend fun preferences(): AnnouncementPreferenceView { fail(); preferenceFailure?.let { throw it }; preferenceCalls++; return AnnouncementPreferenceView(1, false, false, 1000) }
        override suspend fun savePreferences(request: AnnouncementPreferenceRequest): AnnouncementPreferenceView {
            fail()
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
