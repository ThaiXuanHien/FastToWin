package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.data.network.AnnouncementPlayerPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

class AnnouncementInboxReadinessTest {
    @Test fun `empty inbox becomes authoritative only after its accepted response`() = runTest {
        val page = CompletableDeferred<AnnouncementPlayerPage>()
        val gateway = AnnouncementControllerTest.Fake().apply { latePage = page }
        val controller = AnnouncementController(gateway)
        controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.INBOX)
        assertFalse(controller.state.value.hasLoadedInbox)
        val request = async { controller.refresh() }; yield()
        assertTrue(controller.state.value.loading)
        assertFalse(controller.state.value.hasLoadedInbox)
        page.complete(AnnouncementPlayerPage(emptyList(), 0, 25, "0", 1000))
        request.await()
        assertTrue(controller.state.value.hasLoadedInbox)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.items.isEmpty())
    }

    @Test fun `failed first request is never an authoritative empty inbox and retry can settle it`() = runTest {
        val gateway = AnnouncementControllerTest.Fake().apply { failure = IllegalStateException("offline"); pages = emptyList() }
        val controller = AnnouncementController(gateway)
        controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.INBOX)
        controller.refresh()
        assertFalse(controller.state.value.hasLoadedInbox)
        assertNotNull(controller.state.value.error)
        gateway.failure = null
        controller.refresh()
        assertTrue(controller.state.value.hasLoadedInbox)
        controller.context("other-account", "other-token", AppLanguage.VIETNAMESE, AnnouncementView.INBOX)
        assertFalse(controller.state.value.hasLoadedInbox)
    }

    @Test fun `background banner response cannot mark inbox loaded`() = runTest {
        val controller = AnnouncementController(AnnouncementControllerTest.Fake())
        controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.HOME)
        controller.refresh()
        assertFalse(controller.state.value.hasLoadedInbox)
        controller.context("account", "token", AppLanguage.VIETNAMESE, AnnouncementView.INBOX)
        assertFalse(controller.state.value.hasLoadedInbox)
    }
}
