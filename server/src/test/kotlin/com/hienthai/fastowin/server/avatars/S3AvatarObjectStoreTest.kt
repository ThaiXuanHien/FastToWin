package com.hienthai.fastowin.server.avatars

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.*

class S3AvatarObjectStoreTest {
    @Test fun `official SDK sends private bucket requests and bounds reads including chunked bodies`() = runTest {
        var responseBytes = byteArrayOf(1, 2, 3)
        var responseCode = 200
        var chunked = false
        var mime = "image/jpeg"
        val requests = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        var uploaded = byteArrayOf()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(exchange.requestMethod to exchange.requestURI.path)
            val body = exchange.requestBody.use { it.readBytes() }
            if (exchange.requestMethod == "PUT") uploaded = body
            exchange.responseHeaders.add("Content-Type", mime)
            if (exchange.requestMethod == "GET") {
                exchange.sendResponseHeaders(responseCode, if (chunked) 0 else responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            } else {
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
        }
        server.start()
        try {
            val configuration = R2AvatarConfiguration("a".repeat(32), "private-test", "a".repeat(32), "b".repeat(64))
            val client = S3AvatarObjectStore.clientBuilder(configuration)
                .endpointOverride(URI("http://127.0.0.1:${server.address.port}"))
                .build()
            S3AvatarObjectStore(client, "private-test").use { store ->
                val key = "avatars/player/version.jpg"
                store.put(key, byteArrayOf(1, 2, 3), "image/jpeg")
                assertContentEquals(byteArrayOf(1, 2, 3), uploaded)
                assertContentEquals(byteArrayOf(1, 2, 3), store.read(key, 45 * 1024))
                store.delete(key)
                assertTrue(requests.contains("PUT" to "/private-test/avatars/player/version.jpg"))
                assertTrue(requests.contains("DELETE" to "/private-test/avatars/player/version.jpg"))
                responseBytes = ByteArray(45 * 1024 + 1)
                assertFailsWith<Exception> { store.read(key, 45 * 1024) }
                chunked = true
                assertFailsWith<Exception> { store.read(key, 45 * 1024) }
                chunked = false
                responseBytes = byteArrayOf(1)
                mime = "text/html"
                assertFailsWith<Exception> { store.read(key, 45 * 1024) }
                mime = "image/jpeg"
                responseCode = 404
                assertNull(store.read(key, 45 * 1024))
                responseCode = 503
                assertFailsWith<Exception> { store.read(key, 45 * 1024) }
            }
        } finally { server.stop(0) }
    }
}
