package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.state.AnnouncementState

internal enum class NotificationInboxPresentation { LOADING, ERROR, EMPTY, CONTENT }

internal fun notificationInboxPresentation(
    notificationCount: Int,
    announcementState: AnnouncementState?,
    hasLoadedNotifications: Boolean = true,
    notificationError: String? = null
): NotificationInboxPresentation = when {
    notificationCount > 0 -> NotificationInboxPresentation.CONTENT
    notificationError != null || announcementState?.error != null -> NotificationInboxPresentation.ERROR
    !hasLoadedNotifications || announcementState?.hasLoadedInbox == false -> NotificationInboxPresentation.LOADING
    else -> NotificationInboxPresentation.EMPTY
}
