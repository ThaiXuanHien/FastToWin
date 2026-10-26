package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.state.AnnouncementState
import kotlin.test.*

class NotificationInboxPresentationTest {
    @Test fun `first paint and pending HTTP cannot claim inbox is empty`() {
        assertEquals(NotificationInboxPresentation.LOADING, notificationInboxPresentation(0, AnnouncementState()))
        assertEquals(NotificationInboxPresentation.LOADING, notificationInboxPresentation(0, AnnouncementState(loading = true)))
    }
    @Test fun `both sources must finish before empty is shown`() {
        val loaded = AnnouncementState(hasLoadedInbox = true)
        assertEquals(NotificationInboxPresentation.LOADING, notificationInboxPresentation(0, loaded, hasLoadedNotifications = false))
        assertEquals(NotificationInboxPresentation.EMPTY, notificationInboxPresentation(0, loaded))
        assertEquals(NotificationInboxPresentation.EMPTY, notificationInboxPresentation(0, null))
    }
    @Test fun `failure before first response is an error not an empty inbox`() {
        assertEquals(NotificationInboxPresentation.ERROR, notificationInboxPresentation(0, AnnouncementState(error = "offline")))
        assertEquals(NotificationInboxPresentation.ERROR, notificationInboxPresentation(0, null, notificationError = "offline"))
    }
    @Test fun `cached content remains visible through refresh or either source error`() {
        assertEquals(NotificationInboxPresentation.CONTENT, notificationInboxPresentation(1, AnnouncementState(loading = true)))
        assertEquals(NotificationInboxPresentation.CONTENT, notificationInboxPresentation(1, AnnouncementState(error = "offline")))
        assertEquals(NotificationInboxPresentation.CONTENT, notificationInboxPresentation(1, null, notificationError = "offline"))
    }
}
