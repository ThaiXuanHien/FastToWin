package com.hienthai.fastowin.server

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.*
import java.net.http.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.*
import kotlin.test.*

class StorePurchaseVerifierTest {
    private val receipt = StorePurchaseVerification("account", StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "private-receipt")
    private val purchased = """{"purchaseStateContext":{"purchaseState":"PURCHASED"},"productLineItem":[{"productId":"fasttowin_gems_80"}]}"""
    private fun verifier(send: () -> String) = GooglePlayPurchaseVerifier("example.invalid", GoogleCredentials.create(AccessToken("fake-access", Date(Long.MAX_VALUE))), FakeHttpClient(send))

    @Test fun `verified provider order is retained separately from receipt fingerprint`() = runTest {
        val result = verifier { purchased.dropLast(1) + ",\"orderId\":\"GPA.1234\"}" }.verify(receipt)
        assertEquals(VerifiedStoreOrder("GPA.1234", "example.invalid", "LIVE"), result.verifiedOrder)
        assertNull(verifier { purchased }.verify(receipt).verifiedOrder)
    }

    @Test fun `production cannot accept a dev receipt even when delegate says purchased`() = runTest {
        val verifier = EnvironmentStorePurchaseVerifier("prod", StorePurchaseVerifier { StoreVerificationResult(StoreVerificationStatus.PURCHASED, "") })
        assertEquals(StoreVerificationStatus.INVALID, verifier.verify(receipt.copy(purchaseToken = "dev:GOOGLE_PLAY:fake")).status)
    }
    @Test fun `production rejects provider test purchases`() = runTest {
        assertEquals(StoreVerificationStatus.INVALID, verifier { purchased.dropLast(1) + ",\"testPurchaseContext\":{}}" }.verify(receipt).status)
    }
    @Test fun `provider cancellation propagates`() = runTest {
        assertFailsWith<CancellationException> { verifier { throw CancellationException("cancel") }.verify(receipt) }
    }
    @Test fun `provider failures are safe and never log receipt or endpoint details`() = runTest {
        val output = ByteArrayOutputStream(); val original = System.err
        try {
            System.setErr(PrintStream(output))
            assertEquals(StoreVerificationStatus.UNAVAILABLE, verifier { throw IllegalStateException("private-receipt https://provider.invalid/token") }.verify(receipt).status)
        } finally { System.setErr(original) }
        assertFalse(output.toString().contains("private-receipt"))
        assertFalse(output.toString().contains("https://provider.invalid"))
    }
    @Test fun `current product state and account validation remains authoritative`() = runTest {
        assertEquals(StoreVerificationStatus.PURCHASED, verifier { purchased }.verify(receipt).status)
        assertEquals(StoreVerificationStatus.INVALID, verifier { purchased.replace("PURCHASED", "PENDING") }.verify(receipt).status)
        assertEquals(StoreVerificationStatus.INVALID, verifier { purchased.replace("fasttowin_gems_80", "other") }.verify(receipt).status)
        assertEquals(StoreVerificationStatus.INVALID, verifier { purchased.dropLast(1) + ",\"obfuscatedExternalAccountId\":\"wrong-account\"}" }.verify(receipt).status)
    }
}

private class FakeHttpClient(private val response: () -> String) : HttpClient() {
    override fun <T> send(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
        @Suppress("UNCHECKED_CAST") val body = response() as T
        return object : HttpResponse<T> {
            override fun statusCode() = 200
            override fun request() = request
            override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()
            override fun headers() = HttpHeaders.of(emptyMap()) { _, _ -> true }
            override fun body() = body
            override fun sslSession(): Optional<SSLSession> = Optional.empty()
            override fun uri() = request.uri()
            override fun version() = Version.HTTP_1_1
        }
    }
    override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
    override fun connectTimeout(): Optional<java.time.Duration> = Optional.empty()
    override fun followRedirects() = Redirect.NEVER
    override fun proxy(): Optional<ProxySelector> = Optional.empty()
    override fun sslContext(): SSLContext = SSLContext.getDefault()
    override fun sslParameters() = SSLParameters()
    override fun authenticator(): Optional<Authenticator> = Optional.empty()
    override fun version() = Version.HTTP_1_1
    override fun executor(): Optional<Executor> = Optional.empty()
    override fun <T> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> = error("unused")
    override fun <T> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>, push: HttpResponse.PushPromiseHandler<T>): CompletableFuture<HttpResponse<T>> = error("unused")
}
