package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MissionApiClientTest {
    @Test fun `personal and clan reads use authenticated network only routes and refresh expired bearer once`() = runTest {
        val seen=mutableListOf<String?>(); val paths=mutableListOf<String>()
        val client=HttpClient(MockEngine { request ->
            seen+=request.headers[HttpHeaders.Authorization]; paths+=request.url.encodedPath
            assertEquals("no-store",request.headers[HttpHeaders.CacheControl])
            assertEquals("yes",request.headers["X-Test-Auth"])
            if(seen.size==1) respond("""{"code":"UNAUTHORIZED","message":"expired"}""",HttpStatusCode.Unauthorized,headersOf(HttpHeaders.ContentType,"application/json"))
            else respond("""{"items":[],"serverNowEpochMillis":123}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api=MissionApiClient("wss://example.invalid/game", { force -> if(force) "fresh" else "old" }, object:AuthRequestConfigurator {
            override fun configure(request:io.ktor.client.request.HttpRequestBuilder) { request.headers.append("X-Test-Auth","yes") }
        },client)
        assertEquals(123L,api.list(MissionScope.PERSONAL).serverNowEpochMillis)
        assertTrue(api.list(MissionScope.CLAN).items.isEmpty())
        assertEquals(listOf("/api/missions/personal","/api/missions/personal","/api/missions/clan"),paths)
        assertEquals(listOf<String?>("Bearer old","Bearer fresh","Bearer old"),seen); api.close()
    }
    @Test fun `claim sends only stable intent on exact instance route and exposes stable error`() = runTest {
        var payload=""
        val client=HttpClient(MockEngine { request ->
            assertEquals("/api/missions/instance-7/claim",request.url.encodedPath); assertEquals(HttpMethod.Post,request.method)
            payload=(request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond("""{"code":"MISSION_NOT_COMPLETED","message":"unfinished"}""",HttpStatusCode.Conflict,headersOf(HttpHeaders.ContentType,"application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api=MissionApiClient("https://example.invalid", { "player" },client=client)
        val error=assertFailsWith<MissionApiException> { api.claim("instance-7","intent-4") }
        assertEquals("MISSION_NOT_COMPLETED",error.code); assertEquals("""{"requestId":"intent-4"}""",payload); api.close()
    }
    @Test fun `missing auth never sends a request and malformed error stays a stable network failure`() = runTest {
        var requests=0
        val client=HttpClient(MockEngine { requests++; respond("unavailable",HttpStatusCode.ServiceUnavailable) }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val missing=MissionApiClient("http://localhost",{null},client=client)
        assertEquals("UNAUTHORIZED",assertFailsWith<MissionApiException> { missing.list(MissionScope.PERSONAL) }.code); assertEquals(0,requests)
        val api=MissionApiClient("http://localhost",{"player"},client=client)
        assertEquals("NETWORK_ERROR",assertFailsWith<MissionApiException> { api.list(MissionScope.CLAN) }.code); assertEquals(1,requests); api.close()
    }
}
