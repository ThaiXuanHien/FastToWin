package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.InMemoryAuthRepository
import com.hienthai.fastowin.server.InMemoryRateLimiter
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.gifts.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class AdminDualControlTest {
    private class Fixture {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<AdminAlert>()
        val repository = InMemoryAdminRepository()
        val origin = "http://localhost:8080"
        val admin = AdminService(repository, AdminConfiguration(origin, false), PasswordHasher(1000), { now }, InMemoryRateLimiter({ now }),
            alerts = AdminAlertSink { alerts += it },
            publishLimits = AdminPublishLimits(RewardAmounts(gold = 1_000, gems = 10, xp = 500, plays = 5), RewardAmounts(gold = 1_500, gems = 20, xp = Long.MAX_VALUE, plays = Long.MAX_VALUE)))
        val gifts = GiftService(InMemoryGiftRepository(repository), admin, { now })
        lateinit var ownerA: GiftAdminContext
        lateinit var ownerB: GiftAdminContext

        suspend fun initialize(players: Int = 2) {
            admin.bootstrapOwner("a@example.com", "GoodPassword123!")
            val a = admin.login("a@example.com", "GoodPassword123!", origin, "127.0.0.1")
            admin.createAccount(a.token, a.response.csrfToken, origin, CreateAdminRequest("b@example.com", "GoodPassword123!", AdminRole.OWNER, "Owner thứ hai", "create-b"))
            val b = admin.login("b@example.com", "GoodPassword123!", origin, "127.0.0.1")
            ownerA = GiftAdminContext(a.token, a.response.csrfToken, origin)
            ownerB = GiftAdminContext(b.token, b.response.csrfToken, origin)
            val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
            repeat(players) { index ->
                val id = assertIs<AuthResult.Success>(auth.register("p$index@example.com", "GoodPassword123!", "Player $index", "test")).session.userId
                repository.seedUser(UserDetail(id, "Player $index", "p$index@example.com", "REGISTERED", "ACTIVE", now, 1, 0, 0, 0, 1000, null, null, null))
            }
        }

        suspend fun draft(gold: Int): Pair<GiftCampaign, String> {
            val campaign = gifts.create(ownerA, GiftDraftRequest("Quà", "Gift", "Cảm ơn", "Thanks", "Fixture", gold, 0,
                availableAtEpochMillis = now, requestId = UUID.randomUUID().toString())).campaign
            return campaign to gifts.preview(ownerA, campaign.id, GiftPreviewRequest(1)).previewToken
        }

        suspend fun publish(owner: GiftAdminContext, campaign: GiftCampaign, token: String) =
            gifts.publish(owner, campaign.id, GiftPublishRequest(1, token, UUID.randomUUID().toString(), "Publish"))
    }

    @Test fun `limits report per recipient and total overruns`() {
        val limits = AdminPublishLimits(RewardAmounts(gold = 100, gems = 5), RewardAmounts(gold = 1_000, gems = Long.MAX_VALUE))
        assertTrue(limits.exceeded(RewardAmounts(gold = 100, gems = 5), 10).isEmpty())
        assertEquals(1, limits.exceeded(RewardAmounts(gold = 101), 1).size)
        assertTrue(limits.exceeded(RewardAmounts(gold = 100), 11).single().startsWith("Vàng tổng"))
        assertTrue(limits.exceeded(RewardAmounts(gold = 1), null).single().contains("không giới hạn"))
        // Gems have no total cap, so unlimited claims of a small gem reward stay within limits.
        assertTrue(limits.exceeded(RewardAmounts(gems = 1), null).isEmpty())
        assertEquals(AdminPublishLimits(), AdminPublishLimits.fromEnvironment(emptyMap()))
        assertEquals(7L, AdminPublishLimits.fromEnvironment(mapOf("FASTTOWIN_GIFT_CAP_RECIPIENT_GEMS" to "7")).perRecipient.gems)
        assertFailsWith<IllegalArgumentException> { AdminPublishLimits.fromEnvironment(mapOf("FASTTOWIN_GIFT_CAP_TOTAL_GOLD" to "0")) }
    }

    @Test fun `campaigns within caps publish with one owner`() = runTest {
        val f = Fixture(); f.initialize()
        val (campaign, token) = f.draft(gold = 500)
        assertEquals(CampaignState.PUBLISHED, f.publish(f.ownerA, campaign, token).campaign.state)
    }

    @Test fun `over-cap campaigns wait for a different owner to publish the same version`() = runTest {
        val f = Fixture(); f.initialize(players = 2)
        // 2 recipients x 800 gold = 1,600 > total cap 1,500.
        val (campaign, token) = f.draft(gold = 800)

        val first = assertFailsWith<AdminException> { f.publish(f.ownerA, campaign, token) }
        assertEquals("SECOND_OWNER_APPROVAL_REQUIRED", first.code)
        assertEquals(CampaignState.DRAFT, f.gifts.detail(f.ownerA, campaign.id).campaign.state)
        assertEquals("Yêu cầu duyệt công bố quà vượt trần", f.alerts.last().title)
        // The requester cannot approve their own request.
        assertEquals("SECOND_OWNER_APPROVAL_REQUIRED", assertFailsWith<AdminException> { f.publish(f.ownerA, campaign, token) }.code)
        val alertsBefore = f.alerts.size

        val published = f.publish(f.ownerB, campaign, token)

        assertEquals(CampaignState.PUBLISHED, published.campaign.state)
        assertEquals("Đã duyệt và công bố quà vượt trần", f.alerts.drop(alertsBefore).single().title)
        val actions = f.admin.audit(f.ownerA.token, PageRequest(pageSize = 100)).items.map { it.action }
        assertTrue("PUBLISH_APPROVAL_REQUESTED" in actions && "PUBLISH_APPROVAL_GRANTED" in actions)
    }

    @Test fun `editing a draft needs a fresh approval`() = runTest {
        val f = Fixture(); f.initialize(players = 1)
        val (campaign, token) = f.draft(gold = 2_000)
        assertFailsWith<AdminException> { f.publish(f.ownerA, campaign, token) }
        val edited = f.gifts.update(f.ownerA, campaign.id, GiftDraftRequest("Quà", "Gift", "Cảm ơn", "Thanks", "Edit", 3_000, 0,
            availableAtEpochMillis = f.now, requestId = UUID.randomUUID().toString(), expectedVersion = 1)).campaign
        val freshToken = f.gifts.preview(f.ownerB, edited.id, GiftPreviewRequest(2)).previewToken

        // Owner B publishing version 2 only starts a new request; A must now approve it.
        assertEquals("SECOND_OWNER_APPROVAL_REQUIRED", assertFailsWith<AdminException> {
            f.gifts.publish(f.ownerB, edited.id, GiftPublishRequest(2, freshToken, UUID.randomUUID().toString(), "Publish"))
        }.code)
        assertEquals(CampaignState.PUBLISHED, f.gifts.publish(f.ownerA, edited.id, GiftPublishRequest(2, freshToken, UUID.randomUUID().toString(), "Publish")).campaign.state)
    }

    @Test fun `admin sign-ins access changes and repeated failures raise alerts`() = runTest {
        val f = Fixture(); f.initialize(players = 0)
        assertTrue(f.alerts.any { it.title == "Đăng nhập quản trị" && ("Email" to "b@example.com") in it.details })
        assertTrue(f.alerts.any { it.title == "Cấp tài khoản quản trị mới" })
        repeat(5) { assertFailsWith<AdminException> { f.admin.login("a@example.com", "WrongPassword123!", f.origin, "10.0.0.9") } }
        val failure = f.alerts.single { it.title == "Nhiều lần đăng nhập quản trị thất bại" }
        assertTrue(("IP" to "10.0.0.9") in failure.details)
        assertFalse(f.alerts.toString().contains("WrongPassword"))
    }
}
