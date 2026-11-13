package com.hienthai.fastowin.server.ownership

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnedClusterCliProcessTest {
    @Test fun `packaged cli exits promptly with signed status deadline and unavailable JSON`() = runBlocking {
        val a = ADMISSION_NODE_A; val b = ADMISSION_NODE_B
        val key = ByteArray(32) { 7 }
        val binding = OwnedAdmissionBinding.from(admissionProductionConfig())
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            val transport = OwnerPeerTransport(a, b, URI("http://127.0.0.1:65534"), key)
            routing { ownerControlRoutes(transport) { request ->
                val policy = OwnedAdmissionPolicy(7, OwnedAdmissionState.STOP_NEW, binding.topologySha256, binding.policySha256)
                if (request.operation == "status") OwnedControlResponse(request.operationId, request.operation, request.generation,
                    "ok", a.toString(), UUID(0, 1).toString(), request.candidateSha256, binding.topologySha256,
                    false, policy, 1, 0, 1, accepting = true)
                else OwnedControlResponse(request.operationId, request.operation, request.generation, "pending", policy = policy)
            } }
        }.start()
        val hosts = Files.createTempFile("owned-hosts", ".txt")
        try {
            Files.writeString(hosts, "127.0.0.1 fixture.railway.internal\n")
            val port = server.engine.resolvedConnectors().single().port
            fun run(vararg args: String, host: String = "fixture.railway.internal"): Pair<String, Long> {
                val root = Path.of("build", "install", "server").toAbsolutePath()
                val line = Files.readAllLines(root.resolve("bin/server.bat")).single { it.startsWith("set CLASSPATH=") }
                val entries = line.removePrefix("set CLASSPATH=").split(';').map { root.resolve("lib").resolve(it.substringAfterLast('\\')).toString() }
                val javaName = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
                val javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName).toString()
                val command = mutableListOf(javaExecutable, "-Djdk.net.hosts.file=$hosts", "-cp", entries.joinToString(java.io.File.pathSeparator),
                    "com.hienthai.fastowin.server.ownership.OwnedClusterControlMainKt")
                command.addAll(args)
                val builder = ProcessBuilder(command).redirectErrorStream(true)
                builder.environment().putAll(mapOf("FASTTOWIN_ENV" to "prod", "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
                    "FASTTOWIN_ROOM_OWNERSHIP_ROLE" to "owned-worker", "DATABASE_URL" to "jdbc:postgresql://127.0.0.1:15432/test",
                    "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to b.toString(), "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to a.toString(),
                    "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://$host:$port", "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to "$ADMISSION_HOST,$ADMISSION_GUEST",
                    "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to Base64.getEncoder().encodeToString(key)))
                val start = System.nanoTime(); val child = builder.start()
                if (!child.waitFor(8, TimeUnit.SECONDS)) { child.destroyForcibly(); error("packaged CLI failed to exit within 8 seconds") }
                assertEquals(0, child.exitValue())
                val millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                val output = child.inputStream.bufferedReader().readText().lineSequence().lastOrNull { it.startsWith("{") } ?: error("missing JSON")
                return output to millis
            }
            val op = UUID.randomUUID().toString()
            val (success, successTime) = run("status", op, "7")
            assertEquals("ok", Json.decodeFromString<OwnedControlResponse>(success).outcome)
            assertTrue(successTime < 8_000)
            val (timeout, timeoutTime) = run("await", op, "7", "300")
            assertEquals("deadline", Json.decodeFromString<OwnedAwaitReceipt>(timeout).outcome)
            assertTrue(timeoutTime < 8_000)
            val (unavailable, unavailableTime) = run("await", op, "7", "3000", host = "absent.railway.internal")
            assertEquals("unavailable", Json.decodeFromString<OwnedAwaitReceipt>(unavailable).outcome)
            assertTrue(unavailableTime < 8_000)
        } finally { server.stop(0, 1_000); Files.deleteIfExists(hosts) }
    }
}
