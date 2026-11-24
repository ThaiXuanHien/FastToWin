package com.hienthai.fastowin.server

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

const val APP_STORE_DEFAULT_BUNDLE_ID = "com.hienthai.fastowin"

/** SHA-256 of Apple Root CA - G3 (DER), published by Apple; guards the bundled resource. */
private const val APPLE_ROOT_CA_G3_SHA256 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179"
/** Apple marks App Store receipt-signing leaves and the WWDR intermediate with these extensions. */
private const val APPLE_RECEIPT_SIGNING_OID = "1.2.840.113635.100.6.11.1"
private const val APPLE_WWDR_INTERMEDIATE_OID = "1.2.840.113635.100.6.2.1"

/**
 * StoreKit `appAccountToken` bound to an account without exposing the internal UUID to Apple.
 * The shared client derives the same value (`appStoreAccountToken` in shared/platform).
 */
fun appStoreAccountToken(userId: String): String {
    val hex = obfuscatedStoreAccountId(userId).take(32).toCharArray()
    hex[12] = '8' // RFC 9562 version 8 (custom)
    hex[16] = "89ab"[hex[16].digitToInt(16) and 0x3]
    val text = String(hex)
    return "${text.substring(0, 8)}-${text.substring(8, 12)}-${text.substring(12, 16)}-" +
        "${text.substring(16, 20)}-${text.substring(20)}"
}

fun appleRootCertificates(): Set<X509Certificate> {
    val pem = AppStorePurchaseVerifier::class.java.getResourceAsStream("/appstore/AppleRootCA-G3.pem")
        ?.use { it.readBytes() } ?: error("Apple root certificate resource is missing")
    val certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(pem)) as X509Certificate
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }
    check(fingerprint == APPLE_ROOT_CA_G3_SHA256) { "Apple root certificate resource is invalid" }
    return setOf(certificate)
}

/**
 * Offline verification of any App Store JWS (StoreKit 2 transactions and App Store Server
 * Notifications V2): ES256 signature, x5c chain to Apple's pinned root, Apple marker extensions,
 * chain validity at the signing time. Returns the payload, or null when anything does not hold.
 */
class AppleSignedPayloadVerifier(
    private val trustAnchors: Set<X509Certificate> = appleRootCertificates(),
    private val clock: () -> Instant = Instant::now
) {
    init { require(trustAnchors.isNotEmpty()) }

    fun verify(token: String): JsonObject? = try {
        verifyOrThrow(token)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Never log the token; parser and PKIX messages may echo attacker-controlled input.
        null
    }

    private fun verifyOrThrow(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size != 3 || parts.any { it.isEmpty() }) return null
        val decoder = Base64.getUrlDecoder()
        val header = Json.parseToJsonElement(String(decoder.decode(parts[0]), Charsets.UTF_8)).jsonObject
        if (header.string("alg") != "ES256") return null
        val factory = CertificateFactory.getInstance("X.509")
        val chain = header["x5c"]?.jsonArray?.map {
            factory.generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(it.jsonPrimitive.content))) as X509Certificate
        } ?: return null
        if (chain.size != 3) return null
        val (leaf, intermediate, root) = chain
        if (trustAnchors.none { it.encoded.contentEquals(root.encoded) }) return null
        if (leaf.getExtensionValue(APPLE_RECEIPT_SIGNING_OID) == null ||
            intermediate.getExtensionValue(APPLE_WWDR_INTERMEDIATE_OID) == null) return null

        val signature = Signature.getInstance("SHA256withECDSAinP1363Format")
        signature.initVerify(leaf.publicKey)
        signature.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        if (!signature.verify(decoder.decode(parts[2]))) return null

        val payload = Json.parseToJsonElement(String(decoder.decode(parts[1]), Charsets.UTF_8)).jsonObject
        val signedAt = payload.long("signedDate")?.let(Instant::ofEpochMilli) ?: return null
        if (signedAt.isAfter(clock().plus(Duration.ofMinutes(5)))) return null
        // Like Apple's library in offline mode, the chain must have been valid when Apple signed.
        val parameters = PKIXParameters(trustAnchors.map { TrustAnchor(it, null) }.toSet()).apply {
            isRevocationEnabled = false
            date = Date.from(signedAt)
        }
        CertPathValidator.getInstance("PKIX").validate(factory.generateCertPath(listOf(leaf, intermediate)), parameters)
        return payload
    }
}

/**
 * Offline verification of a StoreKit 2 signed transaction (JWS, ES256, x5c chain to Apple's root).
 * No App Store secret is needed: authenticity comes from Apple's certificate chain.
 */
class AppStorePurchaseVerifier(
    private val bundleId: String = APP_STORE_DEFAULT_BUNDLE_ID,
    trustAnchors: Set<X509Certificate> = appleRootCertificates(),
    private val sandboxAllowed: Boolean = true,
    private val testPlayerIds: Set<String> = emptySet(),
    private val xcodeAllowed: Boolean = false,
    clock: () -> Instant = Instant::now
) : StorePurchaseVerifier {
    private val signed = AppleSignedPayloadVerifier(trustAnchors, clock)

    init {
        require(bundleId.isNotBlank())
    }

    override suspend fun verify(purchase: StorePurchaseVerification): StoreVerificationResult = try {
        verifySigned(purchase)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        invalid()
    }

    private fun verifySigned(purchase: StorePurchaseVerification): StoreVerificationResult {
        val payload = signed.verify(purchase.purchaseToken) ?: return invalid()
        val transactionId = payload.string("transactionId")
            ?.takeIf { it.length in 1..64 && it.all(Char::isDigit) } ?: return invalid()
        val environment = when (payload.string("environment")) {
            "Production" -> "LIVE"
            "Sandbox" -> if (sandboxAllowed || purchase.userId in testPlayerIds) "SANDBOX" else return invalid()
            "Xcode" -> if (xcodeAllowed) "XCODE" else return invalid()
            else -> return invalid()
        }
        val matches = payload.string("bundleId") == bundleId &&
            payload.string("productId") == purchase.productId &&
            payload.string("type") == "Consumable" &&
            (payload.long("quantity") ?: 1L) == 1L &&
            payload["revocationDate"] == null &&
            payload.string("appAccountToken")?.lowercase() == appStoreAccountToken(purchase.userId)
        if (!matches) return invalid()
        return StoreVerificationResult(
            StoreVerificationStatus.PURCHASED,
            legacyFallback("App Store đã xác thực."),
            // Each JWS fetch is re-signed, so idempotency keys on Apple's transaction, never the token bytes.
            receiptKey = appStoreReceiptKey(environment, transactionId),
            payment = appStorePayment(payload, environment, transactionId)
        )
    }

    private fun invalid() = StoreVerificationResult(
        StoreVerificationStatus.INVALID,
        legacyFallback("Giao dịch chưa hoàn tất hoặc không khớp tài khoản.")
    )
}

/**
 * Apple's signed `price` (milliunits) and `currency` of a transaction, or null when the payload has
 * no positive price (offer codes, Xcode testing, older payloads). Missing data stays missing, never 0.
 */
internal fun appStorePayment(payload: JsonObject, environment: String, transactionId: String): StorePayment? {
    if (environment !in setOf("LIVE", "SANDBOX")) return null
    val price = payload.long("price")?.takeIf { it > 0 } ?: return null
    val currency = payload.string("currency")?.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: return null
    val at = payload.long("purchaseDate")?.let(Instant::ofEpochMilli) ?: return null
    return StorePayment("APP_STORE", environment, transactionId, java.math.BigInteger.valueOf(price), currency, 3, at)
}

fun appStoreReceiptKey(environment: String, transactionId: String): String =
    storePurchaseFingerprint(com.hienthai.fastowin.protocol.StorePlatform.APP_STORE, "transaction:$environment:$transactionId")

internal fun configuredAppStoreVerifier(environment: String, values: Map<String, String>): AppStorePurchaseVerifier? {
    if (values["APP_STORE_BILLING_ENABLED"]?.trim()?.lowercase() == "false") return null
    val bundleId = values["APP_STORE_BUNDLE_ID"]?.trim()?.takeIf(String::isNotEmpty) ?: APP_STORE_DEFAULT_BUNDLE_ID
    val sandbox = when (values["APP_STORE_ALLOW_SANDBOX"]?.trim()?.lowercase()) {
        null, "", "true" -> true
        "false" -> false
        else -> throw IllegalStateException("APP_STORE_ALLOW_SANDBOX must be true or false")
    }
    val testPlayerIds = values["APP_STORE_TEST_PLAYER_IDS"]?.takeIf(String::isNotBlank)?.split(',')?.map { id ->
        val canonical = id.trim().lowercase()
        require(runCatching { UUID.fromString(canonical).toString() == canonical }.getOrDefault(false)) {
            "App Store test player IDs must be explicit player UUIDs"
        }
        canonical
    }?.toSet().orEmpty()
    return AppStorePurchaseVerifier(bundleId, sandboxAllowed = sandbox, testPlayerIds = testPlayerIds,
        xcodeAllowed = environment == "dev")
}

internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
