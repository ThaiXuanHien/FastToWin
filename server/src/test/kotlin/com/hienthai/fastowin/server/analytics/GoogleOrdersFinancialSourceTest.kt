package com.hienthai.fastowin.server.analytics

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.server.VerifiedStoreOrder
import com.hienthai.fastowin.server.storePurchaseFingerprint
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import java.time.Duration
import java.util.Date
import java.util.UUID
import java.util.Base64
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.net.URLDecoder
import kotlinx.serialization.json.*
import kotlin.test.*

class GoogleOrdersFinancialSourceTest {
    @Test fun authenticatedChargeUsesExactBuyerTotalAndRejectsBindingFailures() = http { source ->
        val event = source.fetch(known()).events.single()
        assertEquals("9007199254740993", event.amount.minor.toString())
        assertEquals("USD", event.amount.currency)
        assertEquals(Instant.parse("2026-10-01T01:00:00Z"), event.occurredAt)
        assertFailsWith<FinancialSourceUnavailable> { source.fetch(known().copy(order = known().order.copy(packageName = "other.app"))) }
        for (body in listOf(payload.replace("GPA.1234", "GPA.other"), payload.replace("private-token", "wrong"),
            payload.replace("fasttowin_gems_80", "foreign"))) {
            response = body
            assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) }
        }
    }

    @Test fun partialThenFullProducesOnlyRemainingRefundAndPendingIsIgnored() = http { source ->
        response = orderJson("USD", "10", "0", "PARTIALLY_REFUNDED", partial("2", "2026-10-02T01:00:00Z"))
        assertEquals(listOf("1000", "200"), source.fetch(known()).events.map { it.amount.minor.toString() })
        for (total in listOf("10", "8")) {
            response = orderJson("USD", "10", "0", "REFUNDED", partial("2", "2026-10-02T01:00:00Z") +
                ",\"refundEvent\":{\"eventTime\":\"2026-10-03T01:00:00Z\",\"refundDetails\":{\"total\":${money("USD", total)}}}")
            assertEquals(listOf("1000", "200", "800"), source.fetch(known()).events.map { it.amount.minor.toString() })
        }
        response = orderJson("USD", "10", "0", "PENDING_REFUND", partial("2", "2026-10-02T01:00:00Z").replace("PROCESSED_SUCCESSFULLY", "PENDING"))
        assertEquals(1, source.fetch(known()).events.size)
    }

    @Test fun zeroScaleCurrenciesAreExactAndMalformedAmountsNeverRound() = http { source ->
        for (currency in listOf("JPY", "VND")) {
            response = orderJson(currency, "9007199254740993", "0")
            assertEquals("9007199254740993", source.fetch(known()).events.single().amount.minor.toString())
        }
        for ((currency, units, nanos) in listOf(Triple("ZZZ", "1", "0"), Triple("USD", "1", "1"),
            Triple("JPY", "1", "100000000"), Triple("USD", "1", "-1"), Triple("USD", "-1", "0"), Triple("USD", "1", "1000000000"))) {
            response = orderJson(currency, units, nanos)
            assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) }
        }
    }

    @Test fun deniedAuthAndDowntimeFailClosedAndAppleNeverAcceptsPriceAsCharge() = http { source ->
        code = 401; assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) }
        code = 503; assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) }
        assertFailsWith<FinancialSourceUnavailable> { UnsupportedAppStoreFinancialSource.fetch(known()) }
    }

    @Test fun stalledBodyAfterHeadersCannotHoldAnOrderLockIndefinitely() = http(Duration.ofMillis(150)) { source ->
        bodyDelayMillis = 500
        assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) }
    }

    @Test fun fullyRefundedPartialsWithZeroRemainingFullEventCreateNoZeroFact() = http { source ->
        response = orderJson("USD", "10", "0", "REFUNDED", partial("10", "2026-10-02T01:00:00Z") +
            ",\"refundEvent\":{\"eventTime\":\"2026-10-03T01:00:00Z\",\"refundDetails\":{\"total\":${money("USD", "0")}}}")
        assertEquals(listOf("1000", "1000"), source.fetch(known()).events.map { it.amount.minor.toString() })
        response = " ".repeat(1_048_577)
        assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) }
    }

    @Test fun ambiguousFullRefundDuplicatePartialAndUnprocessedOrdersAreRejected() = http { source ->
        val partial = partial("2", "2026-10-02T01:00:00Z")
        val invalid = listOf(
            orderJson("USD", "10", "0", "REFUNDED", partial + ",\"refundEvent\":{\"eventTime\":\"2026-10-03T01:00:00Z\",\"refundDetails\":{\"total\":${money("USD", "3")}}}"),
            orderJson("USD", "10", "0", "PARTIALLY_REFUNDED", partial.replace("]", "," + partial.substringAfter("[") )),
            orderJson("USD", "10", "0", "PARTIALLY_REFUNDED", partial("11", "2026-10-02T01:00:00Z")),
            orderJson(state = "PENDING"), orderJson(state = "CANCELED"), "{}", "malformed"
        )
        for (body in invalid) { response = body; assertFailsWith<FinancialSourceUnavailable> { source.fetch(known()) } }
    }

    @Test fun configuredServiceAccountAuthenticatesSignedJwtAtLocalTokenEndpoint() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val endpoint = URI.create("http://127.0.0.1:${server.address.port}/")
        server.createContext("/") { exchange ->
            val authenticated = if (exchange.requestURI.path == "/token") {
                val form = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8).split("&").associate {
                    URLDecoder.decode(it.substringBefore("="), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8)
                }
                val parts = form.getValue("assertion").split(".")
                val claims = Json.parseToJsonElement(Base64.getUrlDecoder().decode(parts[1]).toString(Charsets.UTF_8)).jsonObject
                Signature.getInstance("SHA256withRSA").run {
                    initVerify(keys.public); update("${parts[0]}.${parts[1]}".toByteArray())
                    verify(Base64.getUrlDecoder().decode(parts[2]))
                } && claims["scope"]?.jsonPrimitive?.content == "https://www.googleapis.com/auth/androidpublisher"
            } else exchange.requestHeaders.getFirst("Authorization") == "Bearer local-access"
            val body = if (exchange.requestURI.path == "/token") """{"access_token":"local-access","token_type":"Bearer","expires_in":3600}""" else payload
            val bytes = body.toByteArray(); exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (authenticated) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val file = Files.createTempFile("local-financial-auth-", ".json")
        try {
            val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keys.private.encoded) + "\n-----END PRIVATE KEY-----\n"
            Files.writeString(file, buildJsonObject {
                put("type", "service_account"); put("project_id", "local-fixture"); put("private_key_id", "local-key")
                put("private_key", pem); put("client_email", "local@fixture.invalid"); put("client_id", "123")
                put("token_uri", endpoint.resolve("token").toString())
            }.toString())
            val values = mapOf("FINANCIAL_GOOGLE_ENABLED" to "true", "FINANCIAL_COLLECTION_START" to "2026-10-01T00:00:00Z",
                "GOOGLE_PLAY_PACKAGE_NAME" to "example.app", "GOOGLE_APPLICATION_CREDENTIALS" to file.toString())
            val configured = assertNotNull(configuredFinancialSource(values, endpoint))
            assertEquals("9007199254740993", configured.first.fetch(known()).events.single().amount.minor.toString())
            assertNull(configuredFinancialSource(values + ("FINANCIAL_GOOGLE_ENABLED" to "false"), endpoint))
        } finally { Files.deleteIfExists(file); server.stop(0) }
    }

    private var response = payload
    private var code = 200
    private var bodyDelayMillis = 0L
    private fun http(timeout: Duration = Duration.ofSeconds(20), block: (GoogleOrdersFinancialSource) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val allowed = exchange.requestMethod == "GET" && exchange.requestHeaders.getFirst("Authorization") == "Bearer local-access" &&
                exchange.requestURI.path == "/androidpublisher/v3/applications/example.app/orders/GPA.1234"
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(if (allowed) code else 403, bytes.size.toLong())
            if (bodyDelayMillis > 0) Thread.sleep(bodyDelayMillis)
            runCatching { exchange.responseBody.use { it.write(bytes) } }
        }
        server.start()
        try { block(GoogleOrdersFinancialSource("example.app", GoogleCredentials.create(AccessToken("local-access", Date(Long.MAX_VALUE))),
            endpoint = URI.create("http://127.0.0.1:${server.address.port}/"), requestTimeout = timeout)) } finally { server.stop(0) }
    }

    companion object {
        fun known() = KnownFinancialOrder(VerifiedStoreOrder("GPA.1234", "example.app", "LIVE"), "fasttowin_gems_80",
            storePurchaseFingerprint(StorePlatform.GOOGLE_PLAY, "private-token"), UUID.randomUUID())
        fun money(currency: String, units: String, nanos: String = "0") = """{"currencyCode":"$currency","units":"$units","nanos":$nanos}"""
        fun partial(units: String, time: String) = """"partialRefundEvents":[{"createTime":"$time","processTime":"$time","state":"PROCESSED_SUCCESSFULLY","refundDetails":{"total":${money("USD", units)}}}]"""
        fun orderJson(currency: String = "USD", units: String = "90071992547409", nanos: String = "930000000", state: String = "PROCESSED", history: String = "") =
            """{"orderId":"GPA.1234","purchaseToken":"private-token","state":"$state","lastEventTime":"2026-10-04T01:00:00Z","lineItems":[{"productId":"fasttowin_gems_80","oneTimePurchaseDetails":{"quantity":1}}],"total":${money(currency, units, nanos)},"orderHistory":{"processedEvent":{"eventTime":"2026-10-01T01:00:00Z"}${if (history.isEmpty()) "" else ",$history"}}}"""
        val payload = orderJson()
    }
}
