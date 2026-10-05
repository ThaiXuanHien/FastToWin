package com.hienthai.fastowin.server.analytics

import com.google.auth.oauth2.GoogleCredentials
import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.server.storePurchaseFingerprint
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Currency
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class GoogleOrdersFinancialSource(private val packageName: String, private val credentials: GoogleCredentials,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val endpoint: URI = URI.create("https://androidpublisher.googleapis.com/"),
    private val requestTimeout: Duration = Duration.ofSeconds(20)) : FinancialSource {
    init {
        require(endpoint == URI.create("https://androidpublisher.googleapis.com/") ||
            (endpoint.scheme == "http" && endpoint.host == "127.0.0.1"))
        require(httpClient.followRedirects() == HttpClient.Redirect.NEVER)
        require(!requestTimeout.isNegative && !requestTimeout.isZero)
    }

    override fun fetch(order: KnownFinancialOrder): FinancialSnapshot {
        try {
            require(order.order.packageName == packageName && order.order.environment in setOf("LIVE", "SANDBOX"))
            val access = synchronized(credentials) {
                credentials.refreshIfExpired()
                credentials.accessToken ?: credentials.refreshAccessToken()
            }.tokenValue
            fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
            val request = HttpRequest.newBuilder(endpoint.resolve("androidpublisher/v3/applications/${segment(packageName)}/orders/${segment(order.order.orderId)}"))
                .timeout(requestTimeout).header("Authorization", "Bearer $access").GET().build()
            val pending = httpClient.sendAsync(request) { BoundedBody() }
            val response = try { pending.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS) }
                finally { if (!pending.isDone) pending.cancel(true) }
            val bytes = response.body()
            require(response.statusCode() == 200 && bytes.size <= 1_048_576)
            val body = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            require(body.string("orderId") == order.order.orderId)
            require(storePurchaseFingerprint(StorePlatform.GOOGLE_PLAY, body.string("purchaseToken")) == order.receiptFingerprint)
            val lines = body.getValue("lineItems").jsonArray
            require(lines.size == 1 && lines.single().jsonObject.string("productId") == order.productId)
            require("oneTimePurchaseDetails" in lines.single().jsonObject)
            val state = body.string("state")
            require(state in setOf("PROCESSED", "PARTIALLY_REFUNDED", "PENDING_REFUND", "REFUNDED"))
            val total = money(body.getValue("total").jsonObject)
            val history = body.getValue("orderHistory").jsonObject
            val paidAt = history.getValue("processedEvent").jsonObject.time("eventTime")
            val lastAt = body.time("lastEventTime")
            require(lastAt >= paidAt)
            val events = mutableListOf(FinancialEvent("payment", "PAYMENT", total, paidAt))
            val identities = mutableSetOf<String>()
            var refunded = BigInteger.ZERO
            for (element in history["partialRefundEvents"]?.jsonArray.orEmpty()) {
                val partial = element.jsonObject
                val status = partial.string("state")
                require(status in setOf("PENDING", "PROCESSED_SUCCESSFULLY"))
                if (status == "PENDING") continue
                val created = partial.time("createTime")
                val processed = partial.time("processTime")
                require(created >= paidAt && processed >= created && processed <= lastAt)
                // No provider event ID exists: reject collisions rather than guessing multiplicity.
                val key = "partial:$created"
                require(identities.add(key))
                val amount = money(partial.getValue("refundDetails").jsonObject.getValue("total").jsonObject)
                require(amount.currency == total.currency && amount.exponent == total.exponent)
                refunded += amount.minor
                require(refunded <= total.minor)
                events += FinancialEvent(key, "REFUND", amount, processed)
            }
            val full = history["refundEvent"]?.jsonObject
            if (full != null) {
                require(state == "REFUNDED")
                val at = full.time("eventTime")
                require(at >= paidAt && at <= lastAt && events.all { it.occurredAt <= at })
                val amount = money(full.getValue("refundDetails").jsonObject.getValue("total").jsonObject, allowZero = true)
                require(amount.currency == total.currency && amount.exponent == total.exponent)
                val remaining = total.minor - refunded
                require(amount.minor == total.minor || amount.minor == remaining)
                if (remaining.signum() > 0) events += FinancialEvent("full", "REFUND", total.copy(minor = remaining), at)
            } else require(state != "REFUNDED")
            require(state != "PROCESSED" || refunded == BigInteger.ZERO)
            require(state != "PARTIALLY_REFUNDED" || refunded.signum() > 0)
            return FinancialSnapshot(lastAt, events.sortedWith(compareBy({ it.occurredAt }, { it.key })))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Financial synchronization cancelled")
        } catch (_: Exception) {
            // Provider text, token-bearing URLs and credential exceptions never leave this boundary.
            throw FinancialSourceUnavailable()
        }
    }

    private fun money(value: JsonObject, allowZero: Boolean = false): FinancialMoney {
        val currency = value.string("currencyCode")
        require(currency.matches(Regex("[A-Z]{3}")))
        val exponent = Currency.getInstance(currency).defaultFractionDigits
        require(exponent in 0..6)
        val units = value["units"]?.jsonPrimitive?.content ?: "0"
        require(units.matches(Regex("-?[0-9]+")))
        units.toLong() // Google's units are int64; still perform all money math as decimal/integer.
        val nanos = value["nanos"]?.jsonPrimitive?.int ?: 0
        require(nanos in -999_999_999..999_999_999)
        val whole = BigDecimal(units)
        require(whole.signum() == 0 || nanos == 0 || whole.signum() == nanos.compareTo(0))
        val decimal = whole.add(BigDecimal(nanos).movePointLeft(9))
        require(decimal.signum() > 0 || (allowZero && decimal.signum() == 0))
        val minor = decimal.movePointRight(exponent).toBigIntegerExact()
        require(minor.toString().length <= 38)
        return FinancialMoney(minor, currency, exponent)
    }
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.time(key: String) = Instant.parse(string(key))

    /** Cancels oversized streams before allocating an unbounded provider body. */
    private class BoundedBody : HttpResponse.BodySubscriber<ByteArray> {
        private val completed = CompletableFuture<ByteArray>()
        private val bytes = ByteArrayOutputStream()
        private lateinit var subscription: Flow.Subscription
        override fun getBody() = completed
        override fun onSubscribe(value: Flow.Subscription) { subscription = value; value.request(1) }
        override fun onNext(items: List<ByteBuffer>) {
            if (items.sumOf { it.remaining().toLong() } + bytes.size() > 1_048_576) {
                subscription.cancel(); completed.completeExceptionally(FinancialSourceUnavailable()); return
            }
            items.forEach { buffer -> val chunk = ByteArray(buffer.remaining()); buffer.get(chunk); bytes.write(chunk) }
            subscription.request(1)
        }
        override fun onError(error: Throwable) { completed.completeExceptionally(FinancialSourceUnavailable()) }
        override fun onComplete() { completed.complete(bytes.toByteArray()) }
    }
}
