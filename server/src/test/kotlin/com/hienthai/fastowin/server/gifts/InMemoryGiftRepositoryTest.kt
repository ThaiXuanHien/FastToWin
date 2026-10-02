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
