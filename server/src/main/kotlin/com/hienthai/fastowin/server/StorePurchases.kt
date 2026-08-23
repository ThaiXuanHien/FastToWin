package com.hienthai.fastowin.server

import com.google.auth.oauth2.GoogleCredentials
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.FileInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

val GEM_STORE_PACKAGES = listOf(
    GemPackageSnapshot("fasttowin_gems_80", "Gói Tân binh", 80),
    GemPackageSnapshot("fasttowin_gems_250", "Gói Bứt tốc", 250, featured = true),
    GemPackageSnapshot("fasttowin_gems_650", "Gói Cao thủ", 650)
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
    val message: String
)

fun interface StorePurchaseVerifier {
    suspend fun verify(purchase: StorePurchaseVerification): StoreVerificationResult
}

object RejectingStorePurchaseVerifier : StorePurchaseVerifier {
    override suspend fun verify(purchase: StorePurchaseVerification) = StoreVerificationResult(
        StoreVerificationStatus.UNAVAILABLE,
        "Xác thực thanh toán chưa được cấu hình."
    )
}

class EnvironmentStorePurchaseVerifier(
    private val environment: String,
    private val googlePlayVerifier: StorePurchaseVerifier? = null,
    private val appStoreVerifier: StorePurchaseVerifier? = null
) : StorePurchaseVerifier {
    override suspend fun verify(purchase: StorePurchaseVerification): StoreVerificationResult {
        if (environment == "dev" && purchase.purchaseToken.startsWith("dev:${purchase.store.name}:")) {
            return StoreVerificationResult(StoreVerificationStatus.PURCHASED, "Giao dịch sandbox hợp lệ.")
        }
        return when (purchase.store) {
            StorePlatform.GOOGLE_PLAY -> googlePlayVerifier
            StorePlatform.APP_STORE -> appStoreVerifier
        }?.verify(purchase) ?: StoreVerificationResult(
            StoreVerificationStatus.UNAVAILABLE,
            "Store chưa được cấu hình trên máy chủ."
        )
    }
}

class GooglePlayPurchaseVerifier(
    private val packageName: String,
    private val credentials: GoogleCredentials,
    private val httpClient: HttpClient = HttpClient.newBuilder().build()
) : StorePurchaseVerifier {
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
                    .uri(URI.create(
                        "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
                            "$packageName/purchases/productsv2/tokens/$encodedToken"
                    ))
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
                    return@withContext StoreVerificationResult(status, "Google Play từ chối giao dịch.")
                }
                val payload = Json.parseToJsonElement(response.body()).jsonObject
                val state = payload["purchaseStateContext"]
                    ?.jsonObject?.get("purchaseState")?.jsonPrimitive?.contentOrNull
                val productMatches = payload["productLineItem"]?.jsonArray?.any { line ->
                    line.jsonObject["productId"]?.jsonPrimitive?.contentOrNull == purchase.productId
                } == true
                val expectedAccount = obfuscatedStoreAccountId(purchase.userId)
                val purchasedAccount = payload["obfuscatedExternalAccountId"]
                    ?.jsonPrimitive?.contentOrNull
                val accountMatches = purchasedAccount == null || purchasedAccount == expectedAccount
                if (state == "PURCHASED" && productMatches && accountMatches) {
                    StoreVerificationResult(StoreVerificationStatus.PURCHASED, "Google Play đã xác thực.")
                } else {
                    StoreVerificationResult(StoreVerificationStatus.INVALID, "Giao dịch chưa hoàn tất hoặc không khớp tài khoản.")
                }
            } catch (error: Throwable) {
                System.err.println("Google Play purchase verification failed: ${error.message}")
                StoreVerificationResult(StoreVerificationStatus.UNAVAILABLE, "Chưa thể kết nối Google Play để xác thực.")
            }
        }
}

fun configuredStorePurchaseVerifier(environment: String): StorePurchaseVerifier {
    val credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.takeIf(String::isNotBlank)
    val packageName = System.getenv("GOOGLE_PLAY_PACKAGE_NAME")?.takeIf(String::isNotBlank)
    val googleVerifier = if (credentialsPath != null && packageName != null) {
        val credentials = FileInputStream(credentialsPath).use {
            GoogleCredentials.fromStream(it).createScoped(ANDROID_PUBLISHER_SCOPE)
        }
        GooglePlayPurchaseVerifier(packageName, credentials)
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

private const val ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
