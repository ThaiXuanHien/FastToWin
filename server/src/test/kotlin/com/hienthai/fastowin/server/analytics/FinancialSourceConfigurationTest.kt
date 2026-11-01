package com.hienthai.fastowin.server.analytics

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.io.TempDir

class FinancialSourceConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test fun `collector authenticates with dedicated inline Billing identity before file and legacy`() = provider { endpoint, calls ->
        val billingFile = credentialFile("billing-file", endpoint)
        val legacyFile = credentialFile("legacy", endpoint)
        val values = enabled + mapOf(
            "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to credential("inline", endpoint),
            "GOOGLE_PLAY_CREDENTIALS_FILE" to billingFile.toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to legacyFile.toString()
        )
        authenticate(values, endpoint, "inline", calls)
    }

    @Test fun `collector authenticates with dedicated Billing file before legacy`() = provider { endpoint, calls ->
        val values = enabled + mapOf(
            "GOOGLE_PLAY_CREDENTIALS_FILE" to credentialFile("billing-file", endpoint).toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to credentialFile("legacy", endpoint).toString()
        )
        authenticate(values, endpoint, "billing-file", calls)
    }

    @Test fun `collector retains legacy service account configuration`() = provider { endpoint, calls ->
        authenticate(enabled + ("GOOGLE_APPLICATION_CREDENTIALS" to credentialFile("legacy", endpoint).toString()),
            endpoint, "legacy", calls)
    }

    @Test fun `dedicated inline Billing identity needs no legacy setting`() = provider { endpoint, calls ->
        authenticate(enabled + ("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to credential("inline", endpoint)),
            endpoint, "inline", calls)
    }

    @Test fun `dedicated Billing file needs no legacy setting`() = provider { endpoint, calls ->
        authenticate(enabled + ("GOOGLE_PLAY_CREDENTIALS_FILE" to credentialFile("billing-file", endpoint).toString()),
            endpoint, "billing-file", calls)
    }

    @Test fun `invalid dedicated inline identity never falls back to valid file or legacy`() = provider { endpoint, calls ->
        assertNull(configuredFinancialSource(enabled + mapOf(
            "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to "private-invalid-credential",
            "GOOGLE_PLAY_CREDENTIALS_FILE" to credentialFile("billing-file", endpoint).toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to credentialFile("legacy", endpoint).toString()
        ), endpoint))
        assertEquals(0, calls.total.get())
    }

    @Test fun `unreadable dedicated file never falls back to valid legacy`() = provider { endpoint, calls ->
        assertNull(configuredFinancialSource(enabled + mapOf(
            "GOOGLE_PLAY_CREDENTIALS_FILE" to directory.resolve("missing-billing.json").toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to credentialFile("legacy", endpoint).toString()
        ), endpoint))
        assertEquals(0, calls.total.get())
    }

    @Test fun `collector stays disabled without exact opt in and never reads credentials`() {
        for (value in listOf(null, "false", "TRUE", "1", "")) {
            val values = enabled - "FINANCIAL_GOOGLE_ENABLED" + (value?.let { mapOf("FINANCIAL_GOOGLE_ENABLED" to it) } ?: emptyMap())
            assertNull(configuredFinancialSource(withoutCredentialReads(values)))
        }
    }

    @Test fun `invalid collection boundary or package never reads credentials`() {
        val invalid = listOf(
            enabled - "FINANCIAL_COLLECTION_START",
            enabled + ("FINANCIAL_COLLECTION_START" to "not-an-instant"),
            enabled + ("FINANCIAL_COLLECTION_START" to "9999-01-01T00:00:00Z"),
            enabled - "GOOGLE_PLAY_PACKAGE_NAME",
            enabled + ("GOOGLE_PLAY_PACKAGE_NAME" to "invalid/package")
        )
        for (values in invalid) assertNull(configuredFinancialSource(withoutCredentialReads(values)))
    }

    @Test fun `missing or malformed credentials keep collector unavailable`() {
        for (values in listOf(
            enabled,
            enabled + ("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to "not-json-private-material"),
            enabled + ("GOOGLE_APPLICATION_CREDENTIALS" to directory.resolve("missing-legacy.json").toString())
        )) assertNull(configuredFinancialSource(values))
    }

    @Test fun `blank dedicated settings retain legacy fallback`() = provider { endpoint, calls ->
        authenticate(enabled + mapOf(
            "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to " ",
            "GOOGLE_PLAY_CREDENTIALS_FILE" to "",
            "GOOGLE_APPLICATION_CREDENTIALS" to credentialFile("legacy", endpoint).toString()
        ), endpoint, "legacy", calls)
    }

    private fun withoutCredentialReads(values: Map<String, String>) = object : Map<String, String> by values {
        override fun get(key: String): String? {
            if (key in credentialSettings) throw AssertionError("Disabled or invalid collector read credential setting")
            return values[key]
        }
    }

    private fun authenticate(values: Map<String, String>, endpoint: URI, identity: String, calls: Calls) {
        val configured = assertNotNull(configuredFinancialSource(values, endpoint))
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), configured.second)
        assertEquals(0, calls.total.get(), "Configuration must not contact OAuth or Orders")
        val result = configured.first.fetch(GoogleOrdersFinancialSourceTest.known())
        assertEquals("9007199254740993", result.events.single().amount.minor.toString())
        assertEquals(identity, calls.identity)
        assertEquals(1, calls.tokens.get())
        assertEquals(1, calls.orders.get())
    }

    private fun credentialFile(identity: String, endpoint: URI) = directory.resolve("$identity.json").apply {
        writeText(credential(identity, endpoint))
    }

    private fun credential(identity: String, endpoint: URI) = buildJsonObject {
        put("type", "service_account")
        put("project_id", "local-fixture")
        put("private_key_id", "ephemeral-test-key")
        put("private_key", privateKey)
        put("client_email", "$identity@fixture.invalid")
        put("client_id", "local-test")
        put("token_uri", endpoint.resolve("token").toString())
    }.toString()

    private class Calls {
        val total = AtomicInteger()
        val tokens = AtomicInteger()
        val orders = AtomicInteger()
        @Volatile var identity: String? = null
    }

    private fun provider(block: (URI, Calls) -> Unit) {
        val calls = Calls()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val endpoint = URI.create("http://127.0.0.1:${server.address.port}/")
        server.createContext("/") { exchange ->
            calls.total.incrementAndGet()
            val tokenRequest = exchange.requestURI.path == "/token"
            val valid = runCatching {
                if (tokenRequest) {
                    calls.tokens.incrementAndGet()
                    val form = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8).split("&").associate {
                        URLDecoder.decode(it.substringBefore("="), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8)
                    }
                    val parts = form.getValue("assertion").split('.')
                    val claims = Json.parseToJsonElement(Base64.getUrlDecoder().decode(parts[1]).toString(Charsets.UTF_8)).jsonObject
                    calls.identity = claims.getValue("iss").jsonPrimitive.content.substringBefore('@')
                    exchange.requestMethod == "POST" &&
                        // Google auth 1.17 keeps the standard JWT audience even with a local token endpoint.
                        claims.getValue("aud").jsonPrimitive.content == "https://oauth2.googleapis.com/token" &&
                        claims.getValue("scope").jsonPrimitive.content == "https://www.googleapis.com/auth/androidpublisher" &&
                        Signature.getInstance("SHA256withRSA").run {
                            initVerify(keys.public)
                            update("${parts[0]}.${parts[1]}".toByteArray())
                            verify(Base64.getUrlDecoder().decode(parts[2]))
                        }
                } else {
                    calls.orders.incrementAndGet()
                    exchange.requestMethod == "GET" &&
                        exchange.requestURI.path == "/androidpublisher/v3/applications/example.app/orders/GPA.1234" &&
                        exchange.requestHeaders.getFirst("Authorization") == "Bearer local-access"
                }
            }.getOrDefault(false)
            val body = if (!valid) "{}" else if (tokenRequest)
                """{"access_token":"local-access","token_type":"Bearer","expires_in":3600}"""
            else GoogleOrdersFinancialSourceTest.payload
            val bytes = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (valid) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(endpoint, calls) } finally { server.stop(0) }
    }

    companion object {
        private val enabled = mapOf("FINANCIAL_GOOGLE_ENABLED" to "true",
            "FINANCIAL_COLLECTION_START" to "2026-10-01T00:00:00Z", "GOOGLE_PLAY_PACKAGE_NAME" to "example.app")
        private val credentialSettings = setOf("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "GOOGLE_PLAY_CREDENTIALS_FILE", "GOOGLE_APPLICATION_CREDENTIALS")
        private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val privateKey = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(keys.private.encoded) + "\n-----END PRIVATE KEY-----\n"
    }
}
