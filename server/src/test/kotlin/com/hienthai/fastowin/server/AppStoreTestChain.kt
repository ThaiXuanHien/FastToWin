package com.hienthai.fastowin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

/** A throwaway three-level chain shaped like Apple's (root → WWDR intermediate → receipt signer). */
class AppStoreTestChain(
    leafOid: Boolean = true,
    intermediateOid: Boolean = true,
    notBefore: Instant = Instant.now().minus(30, ChronoUnit.DAYS),
    notAfter: Instant = Instant.now().plus(30, ChronoUnit.DAYS)
) {
    private val rootKeys = keys()
    private val intermediateKeys = keys()
    private val leafKeys = keys()
    val root: X509Certificate = certificate("Test Root", rootKeys, "Test Root", rootKeys, ca = true, null, notBefore, notAfter)
    val intermediate: X509Certificate = certificate("Test WWDR", intermediateKeys, "Test Root", rootKeys, ca = true,
        "1.2.840.113635.100.6.2.1".takeIf { intermediateOid }, notBefore, notAfter)
    val leaf: X509Certificate = certificate("Test Signer", leafKeys, "Test WWDR", intermediateKeys, ca = false,
        "1.2.840.113635.100.6.11.1".takeIf { leafOid }, notBefore, notAfter)

    fun sign(payload: Map<String, Any?>): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = JsonObject(mapOf(
            "alg" to JsonPrimitive("ES256"),
            "x5c" to JsonArray(listOf(leaf, intermediate, root).map { JsonPrimitive(Base64.getEncoder().encodeToString(it.encoded)) })
        ))
        val body = JsonObject(payload.filterValues { it != null }.mapValues { (_, value) -> json(value!!) })
        val input = "${encoder.encodeToString(header.toString().toByteArray())}.${encoder.encodeToString(body.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initSign(leafKeys.private); update(input.toByteArray())
        }.sign()
        return "$input.${encoder.encodeToString(signature)}"
    }

    fun transaction(userId: String, overrides: Map<String, Any?> = emptyMap()): String = sign(mapOf(
        "transactionId" to "2000000123456789",
        "originalTransactionId" to "2000000123456789",
        "bundleId" to APP_STORE_DEFAULT_BUNDLE_ID,
        "productId" to "fasttowin_gems_80",
        "type" to "Consumable",
        "quantity" to 1,
        "environment" to "Production",
        "appAccountToken" to appStoreAccountToken(userId),
        "purchaseDate" to Instant.now().toEpochMilli(),
        "signedDate" to Instant.now().toEpochMilli()
    ) + overrides)

    private fun json(value: Any): JsonElement = when (value) {
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private companion object {
        fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        fun certificate(subject: String, subjectKeys: KeyPair, issuer: String, issuerKeys: KeyPair, ca: Boolean,
            appleOid: String?, notBefore: Instant, notAfter: Instant): X509Certificate {
            val builder = JcaX509v3CertificateBuilder(X500Name("CN=$issuer"), BigInteger.valueOf(System.nanoTime()),
                Date.from(notBefore), Date.from(notAfter), X500Name("CN=$subject"), subjectKeys.public)
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
            if (appleOid != null) builder.addExtension(ASN1ObjectIdentifier(appleOid), false, DERNull.INSTANCE)
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeys.private)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
