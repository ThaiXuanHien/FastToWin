package com.hienthai.fastowin.server

import com.google.auth.oauth2.GoogleCredentials
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.localization.TextKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

val GEM_STORE_PACKAGES = listOf(
    GemPackageSnapshot("fasttowin_gems_80", legacyFallback("Gói Tân binh"), 80),
    GemPackageSnapshot("fasttowin_gems_250", legacyFallback("Gói Bứt tốc"), 250, featured = true),
    GemPackageSnapshot("fasttowin_gems_650", legacyFallback("Gói Cao thủ"), 650)
)

data class StorePurchaseVerification(
    val userId: String,
    val store: StorePlatform,
    val productId: String,
    val purchaseToken: String
)

enum class StoreVerificationStatus { PURCHASED, INVALID, UNAVAILABLE }

data class StoreVerificationResult(
    val status: StoreVerificationStatus,
    val message: String,
    val messageKey: String = when (status) {
        StoreVerificationStatus.PURCHASED -> TextKey.BillingGemsAdded.name
        StoreVerificationStatus.INVALID -> TextKey.BillingIncomplete.name
        StoreVerificationStatus.UNAVAILABLE -> TextKey.ServerUnavailable.name
    },
    val messageArgs: Map<String, String> = emptyMap(),
    val verifiedOrder: VerifiedStoreOrder? = null
)

/** Provider identity only; the economy transaction remains the receipt fingerprint. */
data class VerifiedStoreOrder(val orderId: String, val packageName: String, val environment: String)

fun interface StorePurchaseVerifier {
    suspend fun verify(purchase: StorePurchaseVerification): StoreVerificationResult
}

object RejectingStorePurchaseVerifier : StorePurchaseVerifier {
    override suspend fun verify(purchase: StorePurchaseVerification) = StoreVerificationResult(
        StoreVerificationStatus.UNAVAILABLE,
        legacyFallback("Xác thực thanh toán chưa được cấu hình.")
    )
}

class EnvironmentStorePurchaseVerifier(
    private val environment: String,
    private val googlePlayVerifier: StorePurchaseVerifier? = null,
    private val appStoreVerifier: StorePurchaseVerifier? = null
) : StorePurchaseVerifier {
    override suspend fun verify(purchase: StorePurchaseVerification): StoreVerificationResult {
        if (environment != "dev" && purchase.purchaseToken.startsWith("dev:")) {
            return StoreVerificationResult(StoreVerificationStatus.INVALID, legacyFallback("Giao dịch sandbox không hợp lệ."))
        }
        if (environment == "dev" && purchase.purchaseToken.startsWith("dev:${purchase.store.name}:")) {
            return StoreVerificationResult(StoreVerificationStatus.PURCHASED, legacyFallback("Giao dịch sandbox hợp lệ."))
        }
        return when (purchase.store) {
            StorePlatform.GOOGLE_PLAY -> googlePlayVerifier
            StorePlatform.APP_STORE -> appStoreVerifier
        }?.verify(purchase) ?: StoreVerificationResult(
            StoreVerificationStatus.UNAVAILABLE,
            legacyFallback("Store chưa được cấu hình trên máy chủ.")
        )
    }
}

class GooglePlayPurchaseVerifier(
    private val packageName: String,
    private val credentials: GoogleCredentials,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val sandboxAllowed: Boolean = false,
    private val endpoint: URI = URI.create("https://androidpublisher.googleapis.com/"),
    private val testPlayerIds: Set<String> = emptySet()
) : StorePurchaseVerifier {
    init {
        require(endpoint == URI.create("https://androidpublisher.googleapis.com/") ||
            (endpoint.scheme == "http" && endpoint.host == "127.0.0.1"))
        require(httpClient.followRedirects() == HttpClient.Redirect.NEVER)
    }
    override suspend fun verify(purchase: StorePurchaseVerification): StoreVerificationResult =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = synchronized(credentials) {
                    credentials.refreshIfExpired()
                    credentials.accessToken ?: credentials.refreshAccessToken()
                }.tokenValue
                val encodedToken = URLEncoder.encode(
                    purchase.purchaseToken,
                    StandardCharsets.UTF_8
                ).replace("+", "%20")
                val request = HttpRequest.newBuilder()
                    .uri(endpoint.resolve("androidpublisher/v3/applications/$packageName/purchases/productsv2/tokens/$encodedToken"))
                    .timeout(java.time.Duration.ofSeconds(20))
                    .header("Authorization", "Bearer $accessToken")
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() !in 200..299) {
                    val status = if (response.statusCode() in 400..499) {
                        StoreVerificationStatus.INVALID
                    } else {
                        StoreVerificationStatus.UNAVAILABLE
                    }
                    return@withContext StoreVerificationResult(status, legacyFallback("Google Play từ chối giao dịch."))
                }
                val payload = Json.parseToJsonElement(response.body()).jsonObject
                val sandbox = "testPurchaseContext" in payload
                if (sandbox && !sandboxAllowed && purchase.userId !in testPlayerIds) {
                    return@withContext StoreVerificationResult(StoreVerificationStatus.INVALID, legacyFallback("Giao dịch sandbox không hợp lệ."))
                }
                val state = payload["purchaseStateContext"]
                    ?.jsonObject?.get("purchaseState")?.jsonPrimitive?.contentOrNull
                val productMatches = payload["productLineItem"]?.jsonArray?.any { line ->
                    line.jsonObject["productId"]?.jsonPrimitive?.contentOrNull == purchase.productId
                } == true
                val expectedAccount = obfuscatedStoreAccountId(purchase.userId)
                val purchasedAccount = payload["obfuscatedExternalAccountId"]
                    ?.jsonPrimitive?.contentOrNull
                val accountMatches = if (sandbox && !sandboxAllowed) purchasedAccount == expectedAccount
                    else purchasedAccount == null || purchasedAccount == expectedAccount
                val orderId = payload["orderId"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.length in 1..256 && it.all { c -> c.isLetterOrDigit() || c in ".-_" } }
                if (state == "PURCHASED" && productMatches && accountMatches && (!sandbox || orderId != null)) {
                    StoreVerificationResult(StoreVerificationStatus.PURCHASED, legacyFallback("Google Play đã xác thực."),
                        verifiedOrder = orderId?.let { VerifiedStoreOrder(it, packageName,
                            if (sandbox) "SANDBOX" else "LIVE") })
                } else {
                    StoreVerificationResult(StoreVerificationStatus.INVALID, legacyFallback("Giao dịch chưa hoàn tất hoặc không khớp tài khoản."))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                System.err.println("Google Play purchase verification unavailable")
                StoreVerificationResult(StoreVerificationStatus.UNAVAILABLE, legacyFallback("Chưa thể kết nối Google Play để xác thực."))
            }
        }
}

fun configuredStorePurchaseVerifier(environment: String, values: Map<String, String> = System.getenv()): StorePurchaseVerifier {
    val packageName = values["GOOGLE_PLAY_PACKAGE_NAME"]?.takeIf(String::isNotBlank)
    val testPlayerIds = googlePlayTestPlayerIds(values)
    val credentials = if (packageName != null) googlePlayBillingCredentials(values) else null
    val googleVerifier = if (credentials != null && packageName != null) {
        GooglePlayPurchaseVerifier(packageName, credentials, sandboxAllowed = environment == "dev",
            testPlayerIds = testPlayerIds)
    } else {
        null
    }
    return EnvironmentStorePurchaseVerifier(environment, googlePlayVerifier = googleVerifier)
}

fun storePurchaseFingerprint(store: StorePlatform, purchaseToken: String): String =
    sha256("${store.name}:$purchaseToken")

fun obfuscatedStoreAccountId(userId: String): String = sha256(userId)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
