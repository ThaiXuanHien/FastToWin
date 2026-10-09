package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementState
import com.hienthai.fastowin.server.InMemoryNotificationRepository
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.admin.AdminConfiguration
import com.hienthai.fastowin.server.admin.AdminException
import com.hienthai.fastowin.server.admin.AdminRole
import com.hienthai.fastowin.server.admin.ChangeAdminAccessRequest
import com.hienthai.fastowin.server.admin.CreateAdminRequest
import com.hienthai.fastowin.server.admin.AdminService
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import com.hienthai.fastowin.server.admin.UserDetail
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AnnouncementServiceTest {
    private class Fixture {
        var now = 1_800_000_000_000L
        val admins = InMemoryAdminRepository()
        val adminService = AdminService(admins, AdminConfiguration("http://localhost:8080", false), PasswordHasher(iterations = 1_000), { now })
        val notifications = InMemoryNotificationRepository(admins)
        val service = AnnouncementService(notifications.announcements, adminService, nowMillis = { now })
        val playerId = UUID.randomUUID().toString()
        lateinit var context: AnnouncementAdminContext
        suspend fun setup() {
            adminService.bootstrapOwner("owner@example.com", "GoodPassword123!")
            val login = adminService.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
            context = AnnouncementAdminContext(login.token, login.response.csrfToken, "http://localhost:8080")
            admins.seedUser(UserDetail(playerId, "Player", "player@example.com", "REGISTERED", "ACTIVE", now,
                1, 0, 17, 0, 1000, null, null, null))
        }
        fun draft(requestId: String = UUID.randomUUID().toString(), starts: Long = now, ends: Long = now + 10_000) = AnnouncementDraftRequest(
            "Tiêu đề", "Title", "Nội dung", "Body", AdminAnnouncementCategory.GENERAL,
            startsAtEpochMillis = starts, endsAtEpochMillis = ends, showBanner = true,
            requestId = requestId, reason = "Notice")
        suspend fun published(draft: AnnouncementDraftRequest = draft()): AnnouncementAdminView {
            val created = service.create(context, draft)
            val preview = service.preview(context, created.announcement.id)
            return service.publish(context, created.announcement.id,
                AnnouncementMutationRequest(created.announcement.version, UUID.randomUUID().toString(), "Approved", preview.previewToken))
        }
    }

    @Test fun `publish freezes audience and replays response while future delivery waits for start`() = runTest {
        var now = 1_800_000_000_000L
        val admins = InMemoryAdminRepository()
        val adminService = AdminService(admins, AdminConfiguration("http://localhost:8080", false), PasswordHasher(iterations = 1_000), { now })
        adminService.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val login = adminService.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
        val playerId = UUID.randomUUID().toString()
        admins.seedUser(UserDetail(playerId, "Player", "player@example.com", "REGISTERED", "ACTIVE", now, 1, 0, 0, 0, 1000, null, null, null))
        val notifications = InMemoryNotificationRepository(admins)
        val service = AnnouncementService(notifications.announcements, adminService, nowMillis = { now })
        val context = AnnouncementAdminContext(login.token, login.response.csrfToken, "http://localhost:8080")
        val draft = service.create(context, AnnouncementDraftRequest(
            "Tiêu đề", "Title", "Nội dung", "Body", AdminAnnouncementCategory.GENERAL,
            startsAtEpochMillis = now + 1000, endsAtEpochMillis = now + 2000,
            requestId = "create-1", reason = "Notice"
        ))
        val preview = service.preview(context, draft.announcement.id)
        val request = AnnouncementMutationRequest(draft.announcement.version, "publish-1", "Approved", preview.previewToken)
        val first = service.publish(context, draft.announcement.id, request)
        assertEquals("1", first.counts.frozen)
        assertEquals(first, service.publish(context, draft.announcement.id, request))
        assertEquals(0, service.deliverDueBatch())
        assertTrue(notifications.loadNotifications(playerId).isEmpty())
        now += 1000
        assertEquals(1, service.deliverDueBatch())
        assertEquals(1, notifications.loadNotifications(playerId).size)
    }

    @Test fun `changed replay and stale version conflict while revoked owner cannot replay`() = runTest {
        val f = Fixture(); f.setup()
        val request = f.draft("create-replay")
        val first = f.service.create(f.context, request)
        assertEquals(first, f.service.create(f.context, request))
        assertEquals(409, assertFailsWith<AdminException> {
            f.service.create(f.context, request.copy(titleEn = "Changed"))
        }.status)
        val preview = f.service.preview(f.context, first.announcement.id)
        assertEquals(409, assertFailsWith<AdminException> {
            f.service.publish(f.context, first.announcement.id,
                AnnouncementMutationRequest(3, "stale", "Approved", preview.previewToken))
        }.status)
        val owner = f.adminService.createAccount(f.context.token, f.context.csrfToken, f.context.origin,
            CreateAdminRequest("second@example.com", "GoodPassword123!", AdminRole.OWNER, "Second", "owner-2"))
        val second = f.adminService.login(owner.email, "GoodPassword123!", f.context.origin, "127.0.0.1")
        f.adminService.changeAccess(second.token, second.response.csrfToken, f.context.origin,
            f.adminService.session(f.context.token).admin.id,
            ChangeAdminAccessRequest(AdminRole.SUPPORT, true, "Revoke publication", "revoke-original"))
        assertEquals(401, assertFailsWith<AdminException> {
            f.service.publish(f.context, first.announcement.id,
                AnnouncementMutationRequest(1, "publish-after-revoke", "Approved", preview.previewToken))
        }.status)
    }

    @Test fun `cancelled schedule stays empty and read plus banner hide persist across service reload`() = runTest {
        val f = Fixture(); f.setup()
        val scheduled = f.published(f.draft(starts = f.now + 1000, ends = f.now + 2000))
        f.service.cancel(f.context, scheduled.announcement.id,
            AnnouncementMutationRequest(scheduled.announcement.version, "cancel-scheduled", "Stop"))
        f.now += 1000
        assertEquals(0, f.service.deliverDueBatch())

        val active = f.published()
        assertEquals(1, f.service.deliverDueBatch())
        val deviceB = AnnouncementService(f.notifications.announcements, f.adminService, nowMillis = { f.now })
        val walletBefore = f.adminService.user(f.context.token, f.playerId).gold
        assertEquals(1, deviceB.banners(f.playerId).items.size)
        f.service.markRead(f.playerId, active.announcement.id)
        f.service.hideBanner(f.playerId, active.announcement.id)
        assertTrue(deviceB.banners(f.playerId).items.isEmpty())
        assertTrue(deviceB.playerDetail(f.playerId, active.announcement.id).notification.isRead)
        assertEquals(walletBefore, f.adminService.user(f.context.token, f.playerId).gold)
        f.service.cancel(f.context, active.announcement.id,
            AnnouncementMutationRequest(active.announcement.version, "cancel-active", "Stop"))
        assertEquals(AdminAnnouncementState.CANCELLED, deviceB.playerDetail(f.playerId, active.announcement.id).announcement.state)
        assertEquals("1", f.service.dismiss(f.playerId, active.announcement.id).affected)
        assertEquals("0", f.service.dismiss(f.playerId, active.announcement.id).affected)
        assertEquals("1", deviceB.inbox(f.playerId).total)
        assertTrue(deviceB.playerDetail(f.playerId, active.announcement.id).dismissed)
    }

    @Test fun `separate preference retry is idempotent and invalid selected guest cannot publish`() = runTest {
        val f = Fixture(); f.setup()
        assertEquals(1, f.service.preference(f.playerId).version)
        val request = AnnouncementPreferenceRequest(1, adminEnabled = true, eventEnabled = false)
        assertEquals(2, f.service.updatePreference(f.playerId, request).version)
        assertEquals(2, f.service.updatePreference(f.playerId, request).version)
        assertEquals(409, assertFailsWith<AdminException> {
            f.service.updatePreference(f.playerId, request.copy(eventEnabled = true))
        }.status)
        val guestId = UUID.randomUUID().toString()
        f.admins.seedUser(UserDetail(guestId, "Guest", null, "GUEST", "ACTIVE", f.now,
            1, 0, 0, 0, 1000, null, null, null))
        val draft = f.service.create(f.context, f.draft().copy(audience = AnnouncementAudience.USER_IDS,
            selectedUserIds = listOf(f.playerId, guestId)))
        val preview = f.service.preview(f.context, draft.announcement.id)
        assertEquals(listOf(guestId), preview.invalidUserIds)
        assertEquals(409, assertFailsWith<AdminException> {
            f.service.publish(f.context, draft.announcement.id,
                AnnouncementMutationRequest(draft.announcement.version, "publish-guest", "Approved", preview.previewToken))
        }.status)
        val linked = f.service.create(f.context, f.draft().copy(action = AdminAnnouncementAction.REWARD_EVENT,
            targetId = UUID.randomUUID().toString()))
        val linkPreview = f.service.preview(f.context, linked.announcement.id)
        assertEquals(400, assertFailsWith<AdminException> {
            f.service.publish(f.context, linked.announcement.id,
                AnnouncementMutationRequest(linked.announcement.version, "publish-link", "Approved", linkPreview.previewToken))
        }.status)
    }

    @Test fun `draft duplicate user ids are reported and published only once`() = runTest {
        val f = Fixture(); f.setup()
        val draft = f.service.create(f.context, f.draft().copy(
            audience = AnnouncementAudience.USER_IDS, selectedUserIds = listOf(f.playerId, f.playerId)))
        val preview = f.service.preview(f.context, draft.announcement.id)
        assertEquals(1, preview.duplicateCount)
        assertEquals("1", preview.recipientCount)
        val published = f.service.publish(f.context, draft.announcement.id,
            AnnouncementMutationRequest(draft.announcement.version, "duplicate-publish", "Approved", preview.previewToken))
        assertEquals("1", published.counts.frozen)
        assertEquals(listOf(f.playerId), published.announcement.selectedUserIds)
        assertEquals(1, f.service.deliverDueBatch())
        assertEquals(1, f.notifications.loadNotifications(f.playerId).size)
    }
}
