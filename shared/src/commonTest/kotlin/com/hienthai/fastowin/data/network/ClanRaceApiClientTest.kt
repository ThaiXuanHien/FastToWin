package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ClanRaceApiClientTest {
    @Test fun `read routes preserve exact strings and never send a recipient or score`() = runTest {
        val paths = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("Bearer player", request.headers[HttpHeaders.Authorization])
            assertEquals("no-store", request.headers[HttpHeaders.CacheControl])
            assertNull(request.url.parameters["userId"])
            val body = when (request.url.encodedPath.substringAfterLast('/')) {
                "clan-race" -> """{"weekStart":"2026-10-26","startEpochMillis":1,"endEpochMillis":9,"state":"ACTIVE","config":{"goldRate":"0.000001","gemRate":"1","rewards":[{"xp":7,"gold":8,"gems":9},{"xp":4,"gold":5,"gems":6},{"xp":1,"gold":2,"gems":3}]},"revision":8,"serverNowEpochMillis":2}"""
                "standings" -> """{"weekStart":"2026-10-26","items":[],"page":2,"pageSize":25,"total":51,"ownClan":{"rank":51,"clanId":"own","name":"Own","logoId":null,"level":3,"scoreUnits":"99999999999999999999999999999999999999","points":"99999999999999999999999999999999.999999","reachedAtEpochMillis":1},"serverNowEpochMillis":2}"""
                "contributions" -> """{"weekStart":"2026-10-26","clanId":"own","items":[{"userId":"me","displayName":"Me","tenureId":"new","currentMember":true,"eligible":true,"donationUnits":"1","missionUnits":"0","scoreUnits":"1","points":"0.000001"}],"serverNowEpochMillis":2}"""
                else -> """{"items":[{"weekStart":"2026-10-19","startEpochMillis":0,"endEpochMillis":1,"state":"SETTLED","serverNowEpochMillis":2,"settlement":{"state":"SETTLED","result":{"weekStart":"2026-10-19","revision":7,"cutoffEpochMillis":1,"settledAtEpochMillis":2,"standings":[{"rank":1,"clanId":"dissolved","name":"Frozen former clan","logoId":"crown","level":9,"scoreUnits":"1","points":"0.000001","reachedAtEpochMillis":0}],"winners":[]}}}],"page":3,"pageSize":25,"total":1,"serverNowEpochMillis":2}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = ClanRaceApiClient("ws://localhost:8080/game", { "player" }, client = http)
        val current = api.current()
        assertEquals(ClanRaceState.ACTIVE, current.state)
        assertEquals(8, current.revision)
        assertEquals(listOf(7, 4, 1), current.config?.rewards?.map { it.xp })
        assertEquals("99999999999999999999999999999999.999999", api.standings(2).ownClan?.points)
        assertEquals("0.000001", api.contributions().items.single().points)
        val history = api.history(3)
        assertEquals(3, history.page)
        assertEquals("Frozen former clan", history.items.single().settlement?.result?.standings?.single()?.name)
        assertEquals("0.000001", history.items.single().settlement?.result?.standings?.single()?.points)
        assertEquals(listOf("/api/clan-race", "/api/clan-race/standings", "/api/clan-race/contributions", "/api/clan-race/history"), paths)
        api.close()
    }

    @Test fun `unknown unauthorized body still refreshes bearer exactly once and keeps auth semantics`() = runTest {
        val tokens = mutableListOf<String?>()
        val http = HttpClient(MockEngine { request ->
            tokens += request.headers[HttpHeaders.Authorization]
            respond("unknown proxy response", HttpStatusCode.Unauthorized)
        })
        val api = ClanRaceApiClient("http://localhost", { if (it) "fresh" else "old" }, client = http)
        assertEquals("UNAUTHORIZED", assertFailsWith<ClanRaceApiException> { api.current() }.code)
        assertEquals(listOf<String?>("Bearer old", "Bearer fresh"), tokens)
        api.close()
    }

    @Test fun `unknown forbidden and storage bodies retain status meaning without auth retry`() = runTest {
        for ((status, code) in listOf(HttpStatusCode.Forbidden to "FORBIDDEN", HttpStatusCode.ServiceUnavailable to "STORAGE_UNAVAILABLE")) {
            var calls = 0
            val http = HttpClient(MockEngine { calls++; respond("{}", status) })
            val api = ClanRaceApiClient("http://localhost", { "token" }, client = http)
            assertEquals(code, assertFailsWith<ClanRaceApiException> { api.current() }.code)
            assertEquals(1, calls)
            api.close()
        }
    }

    @Test fun `no bearer means no request`() = runTest {
        val api = ClanRaceApiClient("http://localhost", { null }, client = HttpClient(MockEngine { error("must not call") }))
        assertEquals("UNAUTHORIZED", assertFailsWith<ClanRaceApiException> { api.current() }.code)
        api.close()
    }
}
