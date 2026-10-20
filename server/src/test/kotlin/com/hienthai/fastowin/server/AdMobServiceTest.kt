package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.ads.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

internal class AdMobFixture {
    val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    var now = 1_800_000_000_000L
    val unit = "ca-app-pub-1234567890123456/1234567890"
    val quota = InMemoryPlayQuotaRepository()
    val repository = InMemoryAdMobRepository(quota)
    val verifier = AdMobSsvVerifier(AdMobPublicKeys { if (it == "7") pair.public else null })
    val service = AdMobService(AdMobConfiguration(unit), repository, quota, verifier, { now })
    fun signed(customData: String?, tx: String = "abcdef1234", timestamp: Long = now,
        unit: String = "1234567890", reward: String = "2", item: String = "online_matches", extra: String = ""): String {
        val content = "ad_network=5450213213286189855&ad_unit=$unit" +
            (customData?.let { "&custom_data=$it" } ?: "") + "&reward_amount=$reward&reward_item=$item&timestamp=$timestamp&transaction_id=$tx$extra"
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(content.toByteArray()); sign() }
        return "$content&signature=${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}&key_id=7"
    }
}

class AdMobServiceTest {
    @Test fun `signed console probes with unknown nonce acknowledge canned policy without credit`() = runTest {
        val f = AdMobFixture()
        f.service.callback(f.signed("console-probe", unit = "0", reward = "10", item = "coins", timestamp = 1))
        f.service.callback(f.signed(null, unit = "0", reward = "10", item = "coins", timestamp = 1))
        assertEquals(10, f.quota.getSnapshot("owner", f.now).remainingMatches)
        val session = f.service.prepare("owner", "known")
        assertFailsWith<AdMobInvalidRequest> { f.service.callback(f.signed(session.customData, unit = "0", reward = "10", item = "coins", timestamp = 1)) }
        assertEquals(RewardedAdSessionStatus.PENDING, f.service.status("owner", session.sessionId)?.status)
    }
    @Test fun `enabled Android verifier preserves dev simulation only in dev`() = runTest {
        val f = AdMobFixture()
        val configured = configuredRewardedAdVerifier("dev", AdMobRewardedAdVerifier(f.repository))
        assertNotNull(configured.verify("owner", RewardedAdProvider.DEV_SIMULATED, "dev-tx", devRewardedAdProof("owner", "dev-tx")))
        val production = configuredRewardedAdVerifier("prod", AdMobRewardedAdVerifier(f.repository))
        assertNull(production.verify("owner", RewardedAdProvider.DEV_SIMULATED, "dev-tx", devRewardedAdProof("owner", "dev-tx")))
        assertEquals(RewardedAdAvailability.MOBILE_PRODUCTION, configuredRewardedAdAvailability("prod", true))
        assertEquals(RewardedAdAvailability.UNAVAILABLE, configuredRewardedAdAvailability("prod"))
    }
    @Test fun `signed completion grants two once and owner receipt only refreshes`() = runTest {
        val f = AdMobFixture()
        val session = f.service.prepare("owner", "request-1")
        assertEquals(session, f.service.prepare("owner", "request-1"))
        assertNull(f.service.status("other", session.sessionId))
        assertEquals(10, f.quota.getSnapshot("owner", f.now).remainingMatches)
        assertNull(AdMobRewardedAdVerifier(f.repository).verify("owner", RewardedAdProvider.ADMOB_ANDROID, "abcdef1234", session.sessionId))
        val query = f.signed(session.customData)
        coroutineScope { List(12) { async(Dispatchers.Default) { f.service.callback(query) } }.awaitAll() }
        f.service.callback(f.signed(session.customData, tx = "abcdef5678"))
        val granted = assertNotNull(f.service.status("owner", session.sessionId))
        assertEquals(RewardedAdSessionStatus.GRANTED, granted.status)
        assertEquals("abcdef1234", granted.providerTransactionId)
        assertEquals(12, granted.quota?.remainingMatches)
        val receiptVerifier = AdMobRewardedAdVerifier(f.repository)
        assertNull(receiptVerifier.verify("other", RewardedAdProvider.ADMOB_ANDROID, "abcdef1234", session.sessionId))
        assertNull(receiptVerifier.verify("owner", RewardedAdProvider.ADMOB_ANDROID, "wrong", session.sessionId))
        assertNotNull(receiptVerifier.verify("owner", RewardedAdProvider.ADMOB_ANDROID, "abcdef1234", session.sessionId))
        assertEquals(RewardedAdBonusStatus.ALREADY_GRANTED, f.quota.grantRewardedAd("owner", RewardedAdProvider.ADMOB_ANDROID, "abcdef1234", f.now).status)
        f.now += 86_400_000
        f.service.callback(query)
        assertEquals(10, f.service.status("owner", session.sessionId)?.quota?.remainingMatches)
    }
    @Test fun `signature reward unit time and malformed query never grant`() = runTest {
        val f = AdMobFixture(); val session = f.service.prepare("owner", "request")
        val valid = f.signed(session.customData)
        val bad = listOf(valid.replace("reward_amount=2", "reward_amount=3"),
            f.signed(session.customData, unit = "9999999999"), f.signed(session.customData, reward = "3"),
            f.signed(session.customData, item = "coins"), f.signed(session.customData, timestamp = f.now + 300_001),
            f.signed(session.customData, timestamp = f.now - 300_001),
            f.signed(session.customData, extra = "&reward_amount=2"), f.signed("%ZZ"), f.signed("%C0%AF"),
            valid.replace("&key_id=7", "&key_id=8"), valid + "&custom_data=other", "x".repeat(8193))
        for (query in bad) assertFailsWith<AdMobInvalidRequest> { f.service.callback(query) }
        for (duplicate in listOf("ad_unit=1234567890", "custom_data=other", "reward_item=online_matches", "timestamp=${f.now}", "transaction_id=abcd", "signature=abc", "key_id=7")) {
            assertFailsWith<AdMobInvalidRequest> { f.service.callback(f.signed(session.customData, extra = "&$duplicate")) }
        }
        assertEquals(0, f.quota.getSnapshot("owner", f.now).bonusMatchesGranted)
        assertEquals(RewardedAdSessionStatus.PENDING, f.service.status("owner", session.sessionId)?.status)
    }
    @Test fun `unknown probes are acknowledged but expired unsent ads never earn`() = runTest {
        val f = AdMobFixture()
        f.service.callback(f.signed(null)); f.service.callback(f.signed("unknown"))
        val cancelled = f.service.prepare("owner", "cancelled")
        f.now += 1_800_001
        assertEquals(RewardedAdSessionStatus.EXPIRED, f.service.status("owner", cancelled.sessionId)?.status)
        assertEquals(10, f.quota.getSnapshot("owner", f.now).remainingMatches)
        f.now += 300_000
        assertFailsWith<AdMobInvalidRequest> { f.service.callback(f.signed(cancelled.customData)) }
    }
    @Test fun `delayed valid show grants after expiry even if app never polls`() = runTest {
        val f = AdMobFixture(); val session = f.service.prepare("owner", "delayed"); val event = f.now + 1000
        f.now += 7_200_000
        assertEquals(RewardedAdSessionStatus.EXPIRED, f.service.status("owner", session.sessionId)?.status)
        f.service.callback(f.signed(session.customData, timestamp = event))
        assertEquals(12, f.service.status("owner", session.sessionId)?.quota?.remainingMatches)
    }
    @Test fun `session input and outstanding count are bounded and disabled fails closed`() = runTest {
        val f = AdMobFixture()
        for (request in listOf("", "a".repeat(65), "a b", "é")) assertFailsWith<AdMobInvalidRequest> { f.service.prepare("owner", request) }
        repeat(3) { f.service.prepare("owner", "request-$it") }
        assertFailsWith<AdMobTooManySessions> { f.service.prepare("owner", "fourth") }
        assertEquals(RewardedAdSessionStatus.PENDING, f.service.prepare("owner", "request-0").status)
        val disabled = AdMobService(null, f.repository, f.quota, f.verifier)
        assertFailsWith<AdMobUnavailable> { disabled.prepare("owner", "request") }
        assertFailsWith<AdMobUnavailable> { disabled.callback(f.signed(null)) }
        assertNull(AdMobConfiguration.fromEnvironment("prod", false, emptyMap()))
        assertFailsWith<IllegalArgumentException> { AdMobConfiguration.fromEnvironment("prod", false, mapOf("FASTTOWIN_ADMOB_ENABLED" to "true", "FASTTOWIN_ADMOB_ANDROID_AD_UNIT_ID" to f.unit)) }
    }
}
