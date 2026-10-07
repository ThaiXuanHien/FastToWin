package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class RewardEventServiceTest {
    @Test fun creatorAndPublisherSurviveDifferentActorsAndReplay() = runTest {
        val f=RewardEventFixture(); f.initialize()
        val other=f.addOwner()
        val creator=f.admin.authenticate(f.operator.token)!!.id
        val publisher=f.admin.authenticate(f.owner.token)!!.id
        val canceller=f.admin.authenticate(other.token)!!.id
        val create=f.createRequest()
        val draft=f.service.create(f.operator,create)
        assertEventIdentities(draft,creator,null)
        val updated=f.service.update(other,draft.id,f.createRequest().copy(expectedVersion=1))
        assertEventIdentities(updated,creator,null)
        eventError(409) { f.service.schedule(f.owner,draft.id,f.mutation(2).copy(previewToken="invalid")) }
        assertEventIdentities(f.service.detail(f.owner,draft.id),creator,null)
        val publish=f.mutation(2).copy(previewToken=f.service.preview(f.owner,draft.id).previewToken)
        val live=f.service.schedule(f.owner,draft.id,publish)
        assertEventIdentities(live,creator,publisher)
        val cancelled=f.service.cancel(other,draft.id,f.mutation(3))
        assertEquals(canceller,cancelled.actorId)
        assertEventIdentities(f.service.detail(f.owner,draft.id),creator,publisher)
        assertEquals(live,f.service.schedule(f.owner,draft.id,publish))
        assertEquals(draft,f.service.create(f.operator,create))
        assertEventIdentities(f.service.create(f.operator,create),creator,null)
        val unpublished=f.service.create(f.operator,f.createRequest())
        assertEventIdentities(f.service.cancel(other,unpublished.id,f.mutation(1)),creator,null)
        val public=Json.encodeToString(f.service.publicDetail(draft.id))
        for(secret in listOf("creatorId","publisherId",creator,publisher,canceller)) assertFalse(secret in public)
    }
    @Test fun halfOpenBoundariesIndependentMaximaAndFrozenCatalog() = runTest {
        val f=RewardEventFixture(); f.initialize()
        val a=f.publish(f.draft().copy(startsAtEpochMillis=1000,endsAtEpochMillis=2000,goldMultiplier=2))
        f.publish(f.draft().copy(startsAtEpochMillis=1500,endsAtEpochMillis=2500,xpMultiplier=2))
        f.publish(f.draft().copy(startsAtEpochMillis=1000,endsAtEpochMillis=2500,goldMultiplier=2,matchTypes=setOf(MatchType.CASUAL)))
        val catalog=f.repository.withStartCatalog { it }
        assertEquals(1,catalog.snapshot(999,MatchType.RANKED).goldMultiplier)
        assertEquals(2,catalog.snapshot(1000,MatchType.RANKED).goldMultiplier)
        assertEquals(2,catalog.snapshot(1500,MatchType.RANKED).xpMultiplier)
        assertEquals(2,catalog.snapshot(1500,MatchType.RANKED).sources.size)
        assertEquals(2,catalog.snapshot(1000,MatchType.CASUAL).goldMultiplier)
        assertEquals(2,catalog.snapshot(1000,MatchType.CASUAL).sources.size)
        assertEquals(catalog.snapshot(1500,MatchType.RANKED).sources.map { it.id }.sorted(),catalog.snapshot(1500,MatchType.RANKED).sources.map { it.id })
        assertEquals(1,catalog.snapshot(2000,MatchType.RANKED).goldMultiplier)
        assertEquals(1,catalog.snapshot(2500,MatchType.RANKED).xpMultiplier)
        f.service.cancel(f.owner,a.id,f.mutation(a.version))
        assertEquals(2,catalog.snapshot(1000,MatchType.RANKED).goldMultiplier)
        assertEquals(1,f.repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) }.goldMultiplier)
        assertEquals(MatchRewardEventSnapshot(),NoRewardEventSource.withStartCatalog { it.snapshot(1500,MatchType.RANKED) })
    }
    @Test fun currentAuthorizationReplayVersionAndPreviewBinding() = runTest {
        val f=RewardEventFixture(); f.initialize()
        val request=f.createRequest()
        eventError(403) { f.service.create(f.support,request) }
        eventError(403) { f.service.create(f.owner.copy(csrfToken=null),request) }
        eventError(403) { f.service.create(f.owner.copy(origin="https://evil.invalid"),request) }
        val created=f.service.create(f.operator,request)
        assertEquals(created,f.service.create(f.operator,request))
        eventError(409) { f.service.create(f.operator,request.copy(reason="different")) }
        val preview=f.service.preview(f.operator,created.id)
        val updated=f.service.update(f.operator,created.id,request.copy(expectedVersion=created.version,requestId=UUID.randomUUID().toString(),draft=request.draft.copy(nameEn="New")))
        eventError(409) { f.service.schedule(f.owner,created.id,f.mutation(updated.version).copy(previewToken=preview.previewToken)) }
        eventError(403) { f.service.schedule(f.operator,created.id,f.mutation(updated.version)) }
        val scheduled=f.service.schedule(f.owner,created.id,f.mutation(updated.version).copy(previewToken=f.service.preview(f.owner,created.id).previewToken))
        eventError(409) { f.service.update(f.owner,created.id,request.copy(expectedVersion=scheduled.version,requestId=UUID.randomUUID().toString())) }
        f.admin.logout(f.operator.token,f.operator.csrfToken,f.operator.origin)
        eventError(401) { f.service.create(f.operator,request) }
    }
    @Test fun safePublicProjectionStatesAndNeutralDefinitions() = runTest {
        val f=RewardEventFixture(); f.initialize()
        val hidden=f.service.create(f.owner,f.createRequest())
        eventError(404) { f.service.publicDetail(hidden.id) }
        val live=f.publish(f.draft())
        assertEquals(1,f.service.publicList().total)
        val text=Json.encodeToString(f.service.publicDetail(live.id))
        for(secret in listOf("actorId","reason","requestId","owner@example")) assertFalse(secret in text)
        assertEquals(RewardEventState.SCHEDULED,f.service.publicDetail(live.id).event.state)
        f.now=1000
        assertEquals(RewardEventState.ACTIVE,f.service.publicDetail(live.id).event.state)
        assertTrue(f.repository.withStartCatalog { it.snapshot(f.now,MatchType.RANKED) }.sources.isEmpty())
        f.now=3000
        assertEquals(RewardEventState.ENDED,f.service.publicDetail(live.id).event.state)
        assertEquals(0,f.service.publicList().total)
        eventError(409) { f.service.cancel(f.owner,live.id,f.mutation(live.version)) }
    }
    @Test fun validationUsesUnicodeCodePointsAndBoundedKnownInputs() = runTest {
        val f=RewardEventFixture(); f.initialize()
        f.service.create(f.owner,f.createRequest().copy(draft=f.draft().copy(nameVi="😀".repeat(200))))
        val invalid=listOf(f.draft().copy(nameEn="😀".repeat(201)),f.draft().copy(contentVi="x".repeat(2001)),
            f.draft().copy(timezone="+07:00"),f.draft().copy(startsAtEpochMillis=-1),f.draft().copy(endsAtEpochMillis=253402300800000),
            f.draft().copy(endsAtEpochMillis=1000),f.draft().copy(matchTypes=emptySet()),f.draft().copy(goldMultiplier=3),f.draft().copy(xpMultiplier=0))
        for(draft in invalid) eventError(400) { f.service.create(f.owner,f.createRequest().copy(draft=draft)) }
        eventError(400) { f.service.list(f.owner,query="x".repeat(129)) }
        val expired=f.service.create(f.owner,f.createRequest()); f.now=3000
        eventError(409) { f.service.schedule(f.owner,expired.id,f.mutation(expired.version).copy(previewToken=f.service.preview(f.owner,expired.id).previewToken)) }
    }
    @Test fun memoryGateHoldsAcrossSuspensionAndReleasesOnCancellation() = runTest {
        val f=RewardEventFixture(); f.initialize(); val live=f.publish(f.draft().copy(goldMultiplier=2))
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        val start=launch { f.repository.withStartCatalog { entered.complete(Unit); release.await() } }
        entered.await()
        val cancel=async { f.service.cancel(f.owner,live.id,f.mutation(live.version)) }
        yield(); assertFalse(cancel.isCompleted)
        start.cancelAndJoin()
        assertEquals(RewardEventState.CANCELLED,cancel.await().state)
    }
    @Test fun failedMemoryAuditDoesNotPublishDefinitionOrReplay() = runTest {
        val f=RewardEventFixture(); f.initialize()
        var fail=false
        val atomicAdmins=object:AdminRepository {
            override suspend fun <T> transaction(block:AdminTransaction.()->T):T=f.admins.transaction {
                val original=this
                object:AdminTransaction by original {
                    override fun appendAudit(entry:AuditEntry) {
                        if(fail && entry.action=="EVENT_SCHEDULE") throw java.sql.SQLException("test audit unavailable")
                        original.appendAudit(entry)
                    }
                }.block()
            }
        }
        val repository=InMemoryRewardEventRepository(atomicAdmins)
        val service=RewardEventService(repository,f.admin,{ f.now })
        val draft=service.create(f.owner,f.createRequest().copy(draft=f.draft().copy(goldMultiplier=2)))
        val request=f.mutation(draft.version).copy(previewToken=service.preview(f.owner,draft.id).previewToken)
        fail=true
        eventError(503) { service.schedule(f.owner,draft.id,request) }
        assertEventIdentities(service.detail(f.owner,draft.id),f.admin.authenticate(f.owner.token)!!.id,null)
        eventError(404) { service.publicDetail(draft.id) }
        assertEquals(1,repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) }.goldMultiplier)
        fail=false
        assertEquals(2,service.schedule(f.owner,draft.id,request).version)
        assertEquals(1,f.admins.transaction { audit(PageRequest()).items.count { it.action=="EVENT_SCHEDULE" } })
    }
    @Test fun pageAndVersionPreventDuplicateConcurrentEdits()=runTest {
        val f=RewardEventFixture(); f.initialize(); val draft=f.service.create(f.owner,f.createRequest())
        val results=coroutineScope { List(2) { async { runCatching { f.service.update(f.owner,draft.id,f.createRequest().copy(expectedVersion=1)) } } }.awaitAll() }
        assertEquals(1,results.count { it.isSuccess }); assertEquals(409,assertIs<AdminException>(results.single { it.isFailure }.exceptionOrNull()).status)
        f.publish(f.draft()); f.publish(f.draft())
        assertEquals(2,f.service.publicList(PageRequest(0,1)).total)
        val first=f.service.publicList(PageRequest(0,1)).items.single().id
        assertNotEquals(first,f.service.publicList(PageRequest(1,1)).items.single().id)
        assertTrue(f.service.publicList(PageRequest(2,1)).items.isEmpty())
    }
}

internal class RewardEventFixture {
    var now=500L
    val admins=InMemoryAdminRepository()
    val admin=AdminService(admins,AdminConfiguration(ORIGIN,false),PasswordHasher(1000),{ now })
    val repository=InMemoryRewardEventRepository(admins)
    val service=RewardEventService(repository,admin,{ now })
    lateinit var owner:RewardEventAdminContext
    lateinit var operator:RewardEventAdminContext
    lateinit var support:RewardEventAdminContext
    suspend fun initialize() {
        admin.bootstrapOwner("owner@example.com","GoodPassword123!")
        suspend fun login(email:String):RewardEventAdminContext {
            val value=admin.login(email,"GoodPassword123!",ORIGIN,"127.0.0.1")
            return RewardEventAdminContext(value.token,value.response.csrfToken,ORIGIN)
        }
        owner=login("owner@example.com")
        for(role in listOf(AdminRole.OPERATOR,AdminRole.SUPPORT)) admin.createAccount(owner.token,owner.csrfToken,ORIGIN,
            CreateAdminRequest("${role.name.lowercase()}@example.com","GoodPassword123!",role,"test",UUID.randomUUID().toString()))
        operator=login("operator@example.com"); support=login("support@example.com")
    }
    suspend fun addOwner():RewardEventAdminContext {
        admin.createAccount(owner.token,owner.csrfToken,ORIGIN,
            CreateAdminRequest("second-owner@example.com","GoodPassword123!",AdminRole.OWNER,"test",UUID.randomUUID().toString()))
        val result=admin.login("second-owner@example.com","GoodPassword123!",ORIGIN,"127.0.0.1")
        return RewardEventAdminContext(result.token,result.response.csrfToken,ORIGIN)
    }
    fun draft()=RewardEventDraft("Sự kiện","Event","Nội dung","Content","Asia/Ho_Chi_Minh",1000,3000,1,1,setOf(MatchType.CASUAL,MatchType.RANKED))
    fun createRequest()=RewardEventDraftRequest(0,UUID.randomUUID().toString(),"Internal reason",draft())
    fun mutation(version:Long)=RewardEventMutationRequest(version,UUID.randomUUID().toString(),"Internal reason")
    suspend fun publish(draft:RewardEventDraft):RewardEventAdminView {
        val value=service.create(owner,createRequest().copy(draft=draft))
        return service.schedule(owner,value.id,mutation(value.version).copy(previewToken=service.preview(owner,value.id).previewToken))
    }
    companion object { const val ORIGIN="http://localhost:8080" }
}
internal suspend fun eventError(status:Int,block:suspend ()->Unit) { assertEquals(status,assertFailsWith<AdminException> { block() }.status) }
private val identityTestJson=Json { encodeDefaults=true }
internal fun assertEventIdentities(value:RewardEventAdminView,creator:String?,publisher:String?) {
    val json=identityTestJson.encodeToJsonElement(value).jsonObject
    assertTrue("creatorId" in json,"Admin response must expose stable creator identity")
    assertTrue("publisherId" in json,"Admin response must expose publication identity")
    assertEquals(creator,json.getValue("creatorId").jsonPrimitive.contentOrNull)
    assertEquals(publisher,json.getValue("publisherId").jsonPrimitive.contentOrNull)
}
