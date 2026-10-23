package com.hienthai.fastowin.server

import com.google.auth.oauth2.ServiceAccountCredentials
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.io.path.writeText
import kotlin.test.*

class GooglePlayBillingConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test fun `dedicated inline Billing identity takes priority over file and Firebase ADC`() {
        val result = googlePlayBillingCredentials(mapOf(
            "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to credential("billing@example.invalid"),
            "GOOGLE_PLAY_CREDENTIALS_FILE" to directory.resolve("missing-billing.json").toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to directory.resolve("missing-firebase.json").toString()
        )) as ServiceAccountCredentials
        assertEquals("billing@example.invalid", result.clientEmail)
        assertEquals(setOf("https://www.googleapis.com/auth/androidpublisher"), result.scopes.toSet())
    }
    @Test fun `dedicated file wins over legacy ADC while legacy installs still work`() {
        val billing = directory.resolve("billing.json").apply { writeText(credential("billing@example.invalid")) }
        val firebase = directory.resolve("firebase.json").apply { writeText(credential("firebase@example.invalid")) }
        assertEquals("billing@example.invalid", (googlePlayBillingCredentials(mapOf(
            "GOOGLE_PLAY_CREDENTIALS_FILE" to billing.toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to firebase.toString()
        )) as ServiceAccountCredentials).clientEmail)
        assertEquals("firebase@example.invalid", (googlePlayBillingCredentials(mapOf(
            "GOOGLE_APPLICATION_CREDENTIALS" to firebase.toString()
        )) as ServiceAccountCredentials).clientEmail)
        assertNull(googlePlayBillingCredentials(emptyMap()))
    }
    @Test fun `invalid dedicated identity fails closed without falling back or leaking secret`() {
        val firebase = directory.resolve("firebase.json").apply { writeText(credential("firebase@example.invalid")) }
        val error = assertFailsWith<IllegalStateException> { googlePlayBillingCredentials(mapOf(
            "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON" to "private-secret-material",
            "GOOGLE_APPLICATION_CREDENTIALS" to firebase.toString()
        )) }
        assertEquals("Google Play Billing credentials are invalid", error.message)
        assertNull(error.cause)
        assertFailsWith<IllegalStateException> { googlePlayBillingCredentials(mapOf(
            "GOOGLE_PLAY_CREDENTIALS_FILE" to directory.resolve("missing.json").toString(),
            "GOOGLE_APPLICATION_CREDENTIALS" to firebase.toString()
        )) }
    }
    @Test fun `test allowlist is empty by default and accepts only explicit player UUIDs`() {
        assertEquals(emptySet(), googlePlayTestPlayerIds(emptyMap()))
        assertEquals(setOf("10000000-0000-0000-0000-000000000001"), googlePlayTestPlayerIds(mapOf(
            "GOOGLE_PLAY_TEST_PLAYER_IDS" to " 10000000-0000-0000-0000-000000000001 "
        )))
        for (invalid in listOf("*", "qa@example.invalid", "1-1-1-1-1", "10000000-0000-0000-0000-000000000001,")) {
            assertFailsWith<IllegalArgumentException> { googlePlayTestPlayerIds(mapOf("GOOGLE_PLAY_TEST_PLAYER_IDS" to invalid)) }
        }
    }
    @Test fun `unconfigured production remains unavailable and still rejects fake dev receipts`() = runTest {
        val verifier = configuredStorePurchaseVerifier("prod", emptyMap())
        val purchase = StorePurchaseVerification("account", StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "receipt")
        assertEquals(StoreVerificationStatus.UNAVAILABLE, verifier.verify(purchase).status)
        assertEquals(StoreVerificationStatus.INVALID, verifier.verify(purchase.copy(purchaseToken="dev:GOOGLE_PLAY:fake")).status)
    }

    private fun credential(email: String): String = buildJsonObject {
        put("type", "service_account")
        put("client_email", email)
        put("client_id", "test-client")
        put("private_key_id", "test-key")
        put("private_key", privateKey)
        put("token_uri", "https://oauth2.googleapis.com/token")
    }.toString()

    companion object {
        private val privateKey: String = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            .private.encoded.let { "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(it) + "\n-----END PRIVATE KEY-----\n" }
    }
}
