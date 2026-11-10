package com.hienthai.fastowin.server.ownership

import kotlin.test.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class RoomFaultControlTest {
    @Test fun deadlineBoundsEntireTrickledHttpAcknowledgement() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",8181),0)
        val sent = AtomicInteger()
        server.createContext("/control") { exchange ->
            exchange.use {
                it.requestBody.readAllBytes()
                it.responseHeaders.set("Content-Type","application/json")
                it.sendResponseHeaders(200,24)
                try {
                    repeat(24) { _ ->
                        exchange.responseBody.write(32)
                        exchange.responseBody.flush()
                        sent.incrementAndGet()
                        Thread.sleep(60)
                    }
                } catch (_: java.io.IOException) { /* the caller expired */ }
            }
        }
        server.start()
        try {
            PrivateControlTransport(mapOf("a" to "127.0.0.1")).use { transport ->
                assertFails { transport.post("a","Bearer local-test","{}",System.currentTimeMillis()+700L) }
            }
            assertTrue(sent.get() in 1..23)
        } finally { server.stop(0) }
    }

    @Test fun recoveryIncludesControlAcknowledgementAndObservation() = runBlocking {
        var clock = 0L
        val timer = RecoveryTimer.inject({ clock }) { clock += 3_000_000_000L }
        clock += 7_000_000_000L
        assertEquals(10000, timer.elapsedMillis())
    }

    @Test fun dockerRouting() = runBlocking {
        val seen = mutableListOf<String>()
        val control = DockerRoomFaultControl({ op, service -> seen += "$op $service" }, { n, b -> seen += "$n $b" })
        BackendAction.entries.forEach { control.backend(0, it) }
        StoreRole.entries.forEach { control.store(it, true); control.store(it, false) }
        control.partition(1,true)
        assertEquals(listOf("kill backend-a","start backend-a","pause backend-a","unpause backend-a",
            "stop postgres","start postgres","stop redis","start redis","1 true"), seen)
        assertFailsWith<IllegalArgumentException> { control.backend(2,BackendAction.KILL) }
        Unit
    }

    private class Peer : ControlTransport {
        var clock = 1000L
        var modify: (String) -> String = { it }
        val states = mutableMapOf<String,String>()
        val generations = mutableMapOf<String,Long>()
        val ids = mutableSetOf<String>()
        val ops = mutableListOf<String>()
        val adapter = SupervisorRoomFaultControl("fixture", "a".repeat(64), 100000, this) { clock }
        override suspend fun post(role: String, authorization: String, body: String, deadline: Long): Pair<Int,String> {
            val req = Json.parseToJsonElement(body).jsonObject
            assertEquals("Bearer " + "a".repeat(64), authorization)
            assertEquals(setOf("fixtureId","requestId","operation","generation","deadlineUnixMillis","leaseMillis"), req.keys)
            fun field(key: String) = req.getValue(key).jsonPrimitive.content
            assertEquals("fixture",field("fixtureId"))
            assertEquals(deadline,field("deadlineUnixMillis").toLong())
            assertTrue(ids.add(field("requestId")))
            val op = field("operation")
            val gen = generations[role] ?: 1L
            if (op != "status") { assertEquals(gen, field("generation").toLong()); ops += "$role $op" }
            val state = when(op) {
                "status" -> states[role] ?: "running"
                "pause" -> "paused"
                "kill" -> "stopped"
                "block-a","block-b" -> op.replace("block-","blocked-")
                else -> "running"
            }
            states[role] = state
            generations[role] = if (op == "start") gen+1 else gen
            return 200 to modify("""{"requestId":"${field("requestId")}","role":"$role","generation":${generations[role]},"state":"$state","verified":true,"failed":false,"errorCode":""}""")
        }
    }

    @Test fun authenticatedTransitions() = runBlocking {
        val peer = Peer()
        with(peer.adapter) {
            BackendAction.entries.forEach { backend(1,it) }
            StoreRole.entries.forEach { store(it,true); store(it,false) }
            for (node in 0..1) { partition(node,true); partition(node,false) }
            verifyHealthy()
        }
        assertEquals(listOf("b kill","b start","b pause","b resume","pg pause","pg resume","redis pause","redis resume",
            "proxy block-a","proxy unblock-a","proxy block-b","proxy unblock-b"),peer.ops)
    }

    @Test fun rejectsBadOrLateProof() = runBlocking {
        val invalid = listOf<Pair<String,String>>("true" to "false", "\"failed\":false" to "\"failed\":true",
            "\"generation\":1" to "\"generation\":0", "\"role\":\"a\"" to "\"role\":\"b\"",
            "\"requestId\":\"" to "\"requestId\":\"replay", "\"errorCode\":\"\"" to "\"errorCode\":\"expired\"",
            "stopped" to "running", "}" to ",\"verified\":true}", "}" to ",\"unknown\":0}", "}" to "}{}", "}" to "}".padEnd(4100))
        for ((from,to) in invalid) {
            val peer = Peer().apply { modify = { it.replace(from,to) } }
            assertFails { peer.adapter.backend(0,BackendAction.KILL) }
        }
        val stale = Peer().apply { modify = { if ("stopped" in it) it.replace("\"generation\":1","\"generation\":2") else it } }
        assertFails { stale.adapter.backend(0,BackendAction.KILL) }
        val late = Peer().apply { modify = { clock = 100001; it } }
        assertFails { late.adapter.backend(0,BackendAction.KILL) }
        val repeated = Peer()
        repeated.adapter.backend(0,BackendAction.PAUSE)
        assertFails { repeated.adapter.backend(0,BackendAction.PAUSE) }
        Unit
    }
}
