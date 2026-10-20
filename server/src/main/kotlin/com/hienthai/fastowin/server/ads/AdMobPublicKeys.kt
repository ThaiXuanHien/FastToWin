package com.hienthai.fastowin.server.ads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.URI
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

fun interface AdMobPublicKeys { suspend fun key(id: String): PublicKey? }

/** Fixed origin, no redirects, 5s connect/read budgets and 64 KiB response cap. */
suspend fun fetchGoogleAdMobKeys(): String = withContext(Dispatchers.IO) {
    val connection = URI("https://www.gstatic.com/admob/reward/verifier-keys.json").toURL().openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 5_000; connection.readTimeout = 5_000; connection.instanceFollowRedirects = false
    try {
        if (connection.responseCode != 200) throw AdMobUnavailable()
        connection.inputStream.use { stream ->
            val deadline = System.nanoTime() + 5_000_000_000L
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val remainingMillis = (deadline - System.nanoTime()) / 1_000_000
                if (remainingMillis <= 0) throw AdMobUnavailable()
                connection.readTimeout = remainingMillis.coerceAtMost(5_000).toInt()
                val count = stream.read(buffer, 0, minOf(buffer.size, 65_537 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > 65_536) throw AdMobUnavailable()
            }
            output.toString(Charsets.UTF_8)
        }
    } finally { connection.disconnect() }
}

class CachedAdMobPublicKeys(
    private val fetch: suspend () -> String = ::fetchGoogleAdMobKeys,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : AdMobPublicKeys {
    private val mutex = Mutex()
    private var keys = emptyMap<String, PublicKey>()
    private var loadedAt: Long? = null
    private var attemptedAt: Long? = null
    override suspend fun key(id: String): PublicKey? = mutex.withLock {
        val now = nowMillis()
        val fresh = loadedAt?.let { now >= it && now - it < 86_400_000 } == true
        if (fresh && keys.containsKey(id)) return@withLock keys[id]
        if (attemptedAt?.let { now >= it && now - it < 60_000 } == true) {
            if (!fresh) throw AdMobUnavailable()
            return@withLock null
        }
        attemptedAt = now
        try {
            val json = fetch()
            if (json.length > 65_536) throw AdMobUnavailable()
            val array = Json.parseToJsonElement(json).jsonObject.getValue("keys").jsonArray
            if (array.size !in 1..32) throw AdMobUnavailable()
            val parsed = array.associate { entry ->
                val obj = entry.jsonObject
                val keyId = obj.getValue("keyId").jsonPrimitive.content
                require(Regex("[0-9]{1,20}").matches(keyId))
                val encoded = Base64.getDecoder().decode(obj.getValue("base64").jsonPrimitive.content)
                val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) as ECPublicKey
                require(publicKey.params.curve.field.fieldSize == 256)
                keyId to publicKey
            }
            require(parsed.size == array.size)
            keys = parsed; loadedAt = now
            keys[id]
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { throw AdMobUnavailable() }
    }
}
