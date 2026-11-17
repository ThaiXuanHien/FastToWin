package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.protocol.StorePurchaseRequest
import com.hienthai.fastowin.protocol.StorePurchaseStatus
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class AppStorePurchaseVerifierTest {
    private val userId = "3f2b8c1e-0a4d-4e6f-9b7a-1c2d3e4f5a6b"
    private val chain = AppStoreTestChain()
    private fun verifier(chain: AppStoreTestChain = this.chain, sandboxAllowed: Boolean = true,
        testPlayers: Set<String> = emptySet(), xcode: Boolean = false) =
        AppStorePurchaseVerifier(trustAnchors = setOf(chain.root), sandboxAllowed = sandboxAllowed,
            testPlayerIds = testPlayers, xcodeAllowed = xcode)
    private fun receipt(token: String, user: String = userId, product: String = "fasttowin_gems_80") =
        StorePurchaseVerification(user, StorePlatform.APP_STORE, product, token)

    @Test fun `account token is a stable RFC UUID shared with the client`() {
        assertEquals("487b948d-8c60-8882-b59e-c3879c5c9017", appStoreAccountToken(userId))
        assertNotEquals(appStoreAccountToken(userId), appStoreAccountToken("another"))
    }

    @Test fun `bundled Apple root is pinned`() {
        val root = appleRootCertificates().single()
        assertTrue(root.subjectX500Principal.name.contains("Apple Root CA - G3"))
    }

    @Test fun `signed production consumable is accepted with a transaction keyed receipt`() = runTest {
        val first = verifier().verify(receipt(chain.transaction(userId)))
        assertEquals(StoreVerificationStatus.PURCHASED, first.status)
        val resigned = chain.transaction(userId, mapOf("signedDate" to Instant.now().minusSeconds(60).toEpochMilli()))
        val second = verifier().verify(receipt(resigned))
        assertEquals(first.receiptKey, second.receiptKey)
        assertEquals(appStoreReceiptKey("LIVE", "2000000123456789"), first.receiptKey)
        assertNull(first.verifiedOrder)
    }

    @Test fun `mismatched or unsafe transactions are rejected`() = runTest {
        val cases = listOf(
            mapOf("bundleId" to "com.example.other"),
            mapOf("productId" to "fasttowin_gems_650"),
            mapOf("type" to "Non-Consumable"),
            mapOf("quantity" to 2),
            mapOf("appAccountToken" to appStoreAccountToken("someone-else")),
            mapOf("appAccountToken" to null),
            mapOf("revocationDate" to Instant.now().toEpochMilli()),
            mapOf("environment" to "Xcode"),
            mapOf("environment" to "Unknown"),
            mapOf("transactionId" to "not-a-number"),
            mapOf("signedDate" to Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli())
        )
        for (overrides in cases) {
            assertEquals(StoreVerificationStatus.INVALID, verifier().verify(receipt(chain.transaction(userId, overrides))).status,
                "accepted $overrides")
        }
        assertEquals(StoreVerificationStatus.INVALID,
            verifier().verify(receipt(chain.transaction(userId), user = "3f2b8c1e-0a4d-4e6f-9b7a-000000000000")).status)
    }

    @Test fun `signature chain and Apple markers are enforced`() = runTest {
        val token = chain.transaction(userId)
        val (header, payload, signature) = token.split('.')
        val forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            String(java.util.Base64.getUrlDecoder().decode(payload)).replace("fasttowin_gems_80", "fasttowin_gems_650").toByteArray())
        assertEquals(StoreVerificationStatus.INVALID,
            verifier().verify(receipt("$header.$forged.$signature", product = "fasttowin_gems_650")).status)
        assertEquals(StoreVerificationStatus.INVALID, verifier(AppStoreTestChain()).verify(receipt(token)).status)
        assertEquals(StoreVerificationStatus.INVALID, AppStorePurchaseVerifier().verify(receipt(token)).status)
        val noLeafMarker = AppStoreTestChain(leafOid = false)
        assertEquals(StoreVerificationStatus.INVALID, verifier(noLeafMarker).verify(receipt(noLeafMarker.transaction(userId))).status)
        val noIntermediateMarker = AppStoreTestChain(intermediateOid = false)
        assertEquals(StoreVerificationStatus.INVALID,
            verifier(noIntermediateMarker).verify(receipt(noIntermediateMarker.transaction(userId))).status)
        val expired = AppStoreTestChain(notBefore = Instant.now().minus(60, ChronoUnit.DAYS),
            notAfter = Instant.now().minus(30, ChronoUnit.DAYS))
        assertEquals(StoreVerificationStatus.INVALID, verifier(expired).verify(receipt(expired.transaction(userId))).status)
        for (garbage in listOf("", "a.b", "a.b.c", "dev:APP_STORE:x", "$header..$signature")) {
            assertEquals(StoreVerificationStatus.INVALID, verifier().verify(receipt(garbage)).status)
        }
    }

    @Test fun `sandbox policy follows configuration and QA allowlist`() = runTest {
        val sandbox = chain.transaction(userId, mapOf("environment" to "Sandbox"))
        val allowed = verifier().verify(receipt(sandbox))
        assertEquals(StoreVerificationStatus.PURCHASED, allowed.status)
        assertEquals(appStoreReceiptKey("SANDBOX", "2000000123456789"), allowed.receiptKey)
        assertEquals(StoreVerificationStatus.INVALID, verifier(sandboxAllowed = false).verify(receipt(sandbox)).status)
        assertEquals(StoreVerificationStatus.PURCHASED,
            verifier(sandboxAllowed = false, testPlayers = setOf(userId)).verify(receipt(sandbox)).status)
        val xcode = chain.transaction(userId, mapOf("environment" to "Xcode"))
        assertEquals(StoreVerificationStatus.PURCHASED, verifier(xcode = true).verify(receipt(xcode)).status)
    }

    @Test fun `configuration defaults to an enabled offline verifier and validates values`() {
        assertNotNull(configuredAppStoreVerifier("prod", emptyMap()))
        assertNull(configuredAppStoreVerifier("prod", mapOf("APP_STORE_BILLING_ENABLED" to "false")))
        assertFailsWith<IllegalStateException> { configuredAppStoreVerifier("prod", mapOf("APP_STORE_ALLOW_SANDBOX" to "yes")) }
        assertFailsWith<IllegalArgumentException> { configuredAppStoreVerifier("prod", mapOf("APP_STORE_TEST_PLAYER_IDS" to "*")) }
        assertNotNull(configuredAppStoreVerifier("prod", mapOf("APP_STORE_ALLOW_SANDBOX" to "false",
            "APP_STORE_TEST_PLAYER_IDS" to userId.uppercase())))
    }

    @Test fun `production routes App Store receipts to the App Store verifier`() = runTest {
        val routed = configuredStorePurchaseVerifier("prod", emptyMap())
        assertEquals(StoreVerificationStatus.INVALID, routed.verify(receipt("dev:APP_STORE:fake")).status)
        assertEquals(StoreVerificationStatus.INVALID, routed.verify(receipt(chain.transaction(userId))).status)
    }

    @Test fun `service accepts long signed App Store tokens but keeps the Google Play limit`() = runTest {
        var seen = 0
        val service = StorePurchaseService(NoOpPlayerProfileRepository, StorePurchaseVerifier {
            seen++; StoreVerificationResult(StoreVerificationStatus.INVALID, "")
        })
        val long = "x".repeat(9_000)
        assertEquals(StorePurchaseStatus.INVALID,
            service.verify(userId, StorePurchaseRequest(StorePlatform.APP_STORE, "fasttowin_gems_80", long, "r1")).status)
        assertEquals(1, seen)
        service.verify(userId, StorePurchaseRequest(StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", long, "r2"))
        assertEquals(1, seen)
    }
}
