package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

internal class MemoryGiftFixture {
    val now = System.currentTimeMillis()
    val repository = InMemoryAdminRepository()
    val admin = AdminService(repository,AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000))
    val gifts = GiftService(InMemoryGiftRepository(repository),admin,{now})
    val auth = AuthenticationService(InMemoryAuthRepository(),passwordHasher=PasswordHasher(1000))
    lateinit var owner: GiftAdminContext
    lateinit var player: String
    lateinit var accessToken: String
    suspend fun initialize() {
        admin.bootstrapOwner("owner@example.com","GoodPassword123!")
        val login=admin.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
        owner=GiftAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
        val account=assertIs<AuthResult.Success>(auth.register("player@example.com","GoodPassword123!","Player","test")).session
        player=account.userId; accessToken=account.accessToken
        repository.seedUser(UserDetail(player,"Player","player@example.com","REGISTERED","ACTIVE",now,1,0,0,0,1000,null,null,null))
    }
    fun request()=GiftDraftRequest("Quà","Gift","Cảm ơn","Thanks","Fixture",100,30,availableAtEpochMillis=now,requestId=UUID.randomUUID().toString())
    suspend fun publish(): GiftCampaign {
        val c=gifts.create(owner,request()).campaign
        val p=gifts.preview(owner,c.id,GiftPreviewRequest(1))
        return gifts.publish(owner,c.id,GiftPublishRequest(1,p.previewToken,UUID.randomUUID().toString(),"Publish")).campaign
    }
}
class InMemoryGiftRepositoryTest {
    @Test fun `mixed gems share wallet history counters and concurrent receipt replay`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        val c = f.gifts.create(f.owner, f.request().copy(gems = 7)).campaign
        val p = f.gifts.preview(f.owner, c.id, GiftPreviewRequest(1))
        assertEquals(7L, p.totalGems)
        f.gifts.publish(f.owner, c.id, GiftPublishRequest(1, p.previewToken, "publish-mixed", "Publish"))
        f.gifts.deliverDueBatch()
        val results = coroutineScope { List(4) { async(Dispatchers.Default) { f.gifts.claim(f.player, c.id) } }.awaitAll() }
        assertTrue(results.all { it == results.first() })
        val receipt = results.first()
        assertEquals(7, receipt.awardedGems); assertEquals(7, receipt.gems); assertEquals(7, receipt.gift.gems)
        val player = f.admin.user(f.owner.token, f.player)
        assertEquals(100, player.gold); assertEquals(30, player.xp); assertEquals(7, player.gems)
        val wallet = f.admin.wallet(f.owner.token, f.player, true, PageRequest())
        assertEquals(1L, wallet.total); assertEquals(7, wallet.items.single().gemsDelta)
        assertEquals(7L, f.gifts.detail(f.owner, c.id).counters.claimedGems)
        assertEquals(0L, f.gifts.mailbox(f.player, PageRequest()).availableCount)
    }
    @Test fun `gem overflow rolls back mixed balances history and entitlement`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        f.repository.seedUser(f.admin.user(f.owner.token, f.player).copy(gold = 11, xp = 22, gems = Int.MAX_VALUE - 4))
        val c = f.gifts.create(f.owner, f.request().copy(gems = 5)).campaign
        val p = f.gifts.preview(f.owner, c.id, GiftPreviewRequest(1))
        f.gifts.publish(f.owner, c.id, GiftPublishRequest(1, p.previewToken, "overflow", "Publish")); f.gifts.deliverDueBatch()
        assertEquals(409, assertFailsWith<AdminException> { f.gifts.claim(f.player, c.id) }.status)
        val player = f.admin.user(f.owner.token, f.player)
        assertEquals(11, player.gold); assertEquals(22, player.xp); assertEquals(Int.MAX_VALUE - 4, player.gems)
        assertEquals(0L, f.admin.wallet(f.owner.token, f.player, true, PageRequest()).total)
        assertEquals(GiftState.AVAILABLE, f.gifts.mailbox(f.player, PageRequest()).items.single().state)
        assertEquals(1L, f.gifts.mailbox(f.player, PageRequest()).availableCount)
    }
    @Test fun `negative gems are rejected even beside a positive gold reward`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        assertEquals(400, assertFailsWith<AdminException> { f.gifts.create(f.owner, f.request().copy(gems = -1)) }.status)
        assertEquals(0L, f.gifts.list(f.owner, PageRequest()).total)
    }
    @Test fun `available count spans pages and excludes every ineligible entitlement`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        var now = f.now
        val gifts = GiftService(f.gifts.repository, f.admin, { now })
        suspend fun publish(input: GiftDraftRequest = f.request()): GiftCampaign {
            val c = gifts.create(f.owner, input).campaign
            val p = gifts.preview(f.owner, c.id, GiftPreviewRequest(1))
            return gifts.publish(f.owner, c.id, GiftPublishRequest(1, p.previewToken, UUID.randomUUID().toString(), "Publish")).campaign
        }
        publish(); publish()
        publish(f.request().copy(expiresAtEpochMillis = now + 10))
        val cancelled = publish(); val claimed = publish()
        val future = publish(f.request().copy(availableAtEpochMillis = now + 100))
        gifts.deliverDueBatch()
        gifts.cancel(f.owner, cancelled.id, GiftCancelRequest(2, "cancel-count", "Cancel"))
        gifts.claim(f.player, claimed.id)
        val pending = publish()
        val draft = gifts.create(f.owner, f.request()).campaign
        gifts.repository.transaction {
            putRecipient(GiftRecipient(future.id, f.player, "DELIVERED"))
            putRecipient(GiftRecipient(draft.id, f.player, "DELIVERED"))
            assertEquals("PENDING", recipient(pending.id, f.player)!!.delivery)
        }
        now += 10
        for (page in listOf(0, 1, 99)) {
            val result = gifts.mailbox(f.player, PageRequest(page, 1))
            assertEquals(2L, result.availableCount)
            assertTrue(result.items.size <= 1)
        }
    }

    @Test fun `gem only draft from admin JSON can be published and claimed`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        val input = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<GiftDraftRequest>(
            """{"titleVi":"Quà","titleEn":"Gift","bodyVi":"Quà","bodyEn":"Gift","reason":"Gem reward","gold":0,"xp":0,"gems":25,"availableAtEpochMillis":${f.now},"requestId":"gem-only"}""")
        val c = f.gifts.create(f.owner, input).campaign
        val p = f.gifts.preview(f.owner, c.id, GiftPreviewRequest(1))
        f.gifts.publish(f.owner, c.id, GiftPublishRequest(1, p.previewToken, "publish-gem", "Publish"))
        f.gifts.deliverDueBatch()
        f.gifts.claim(f.player, c.id)
        assertEquals(25, f.admin.user(f.owner.token, f.player).gems)
    }
    @Test fun `resolved individual recipient uses existing preview publish mailbox and idempotent claim`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        val player = f.admin.user(f.owner.token, f.player).copy(playerCode = "FTW123AB")
        f.repository.seedUser(player)
        val other = player.copy(id = UUID.randomUUID().toString(), email = "other@example.com", playerCode = "OTHER123")
        f.repository.seedUser(other)
        val recipient = f.admin.giftRecipient(f.owner.token, "ftw123ab")
        assertEquals(recipient.id, f.admin.giftRecipient(f.owner.token, "PLAYER@EXAMPLE.COM").id)
        val draft = f.request().copy(audience = GiftAudience.USER_IDS, userIds = listOf(recipient.id))
        val campaign = f.gifts.create(f.owner, draft).campaign
        val preview = f.gifts.preview(f.owner, campaign.id, GiftPreviewRequest(1))
        assertEquals(1, preview.recipientCount); assertEquals(100, preview.totalGold)
        f.gifts.publish(f.owner, campaign.id, GiftPublishRequest(1, preview.previewToken, UUID.randomUUID().toString(), "Individual gift"))
        f.gifts.deliverDueBatch()
        assertEquals(0, f.gifts.mailbox(other.id, PageRequest()).total)
        assertEquals(1, f.gifts.mailbox(player.id, PageRequest()).total)
        repeat(2) { f.gifts.claim(player.id, campaign.id) }
        assertEquals(100, f.admin.user(f.owner.token, player.id).gold)
        assertEquals(1, f.admin.wallet(f.owner.token, player.id, true, PageRequest()).total)
    }
    @Test fun `memory awards share admin player balance history and retry state`() = runTest {
        val f=MemoryGiftFixture(); f.initialize(); val c=f.publish(); f.gifts.deliverDueBatch()
        coroutineScope { List(2) { async { f.gifts.claim(f.player,c.id) } }.awaitAll() }
        val player=f.admin.user(f.owner.token,f.player)
        assertEquals(100,player.gold); assertEquals(30,player.xp)
        assertEquals(1,f.admin.wallet(f.owner.token,f.player,true,PageRequest()).total)
        assertEquals(GiftState.CLAIMED,f.gifts.mailbox(f.player,PageRequest()).items.single().state)
        val counters=f.gifts.detail(f.owner,c.id).counters
        assertEquals(100,counters.claimedGold); assertEquals(30,counters.claimedXp)
    }
    @Test fun `memory overflow rolls back entitlement and shared wallet`() = runTest {
        val f=MemoryGiftFixture(); f.initialize(); val c=f.publish(); f.gifts.deliverDueBatch()
        val player=f.admin.user(f.owner.token,f.player)
        f.repository.seedUser(player.copy(xp=Int.MAX_VALUE))
        assertEquals(409,assertFailsWith<AdminException> { f.gifts.claim(f.player,c.id) }.status)
        assertEquals(0,f.admin.user(f.owner.token,f.player).gold)
        assertEquals(0,f.admin.wallet(f.owner.token,f.player,true,PageRequest()).total)
        assertEquals(GiftState.AVAILABLE,f.gifts.mailbox(f.player,PageRequest()).items.single().state)
    }
}
