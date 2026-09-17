package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.hienthai.fastowin.platform.toImageBitmap
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val httpClient = HttpClient()
private const val MAX_MEMORY_IMAGE_CACHE_ENTRIES = 96
private val imageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private val imageCache = SharedResourceCache<String, String, ImageBitmap>(
    scope = imageLoadScope,
    maxEntries = MAX_MEMORY_IMAGE_CACHE_ENTRIES,
    loader = { url ->
        val bytes = httpClient.get(url).readRawBytes()
        withContext(Dispatchers.Default) { bytes.toImageBitmap() }
    }
)

@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {}
) {
    // Keep the previous bitmap while only the cache-busting revision changes.
    // A different player URL still resets immediately and cannot show another user's avatar.
    val sourceKey = url.substringBefore('?')
    var imageBitmap by remember(sourceKey) {
        mutableStateOf(imageCache.peekExact(url) ?: imageCache.peekLatest(sourceKey))
    }

    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        try {
            val bitmap = imageCache.load(url, sourceKey)
            imageBitmap = bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Network images are optional. Keep an already displayed bitmap when
            // refreshing the same player; the initial load still uses the fallback.
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        fallback()
    }
}

/**
 * Owns in-flight work and cache commits independently from any composable waiter.
 * All synchronous peeks and cache writes are confined to [scope].
 */
internal class SharedResourceCache<K, S, V>(
    private val scope: CoroutineScope,
    private val maxEntries: Int,
    private val loader: suspend (K) -> V
) {
    private val mutex = Mutex()
    private val exact = LinkedHashMap<K, V>()
    private val latest = LinkedHashMap<S, V>()
    private val latestRequestedKey = mutableMapOf<S, K>()
    private val pending = mutableMapOf<K, Deferred<V>>()

    fun peekExact(key: K): V? = exact[key]

    fun peekLatest(source: S): V? = latest[source]

    suspend fun load(key: K, source: S): V {
        val sharedLoad = mutex.withLock {
            latestRequestedKey[source] = key
            exact[key]?.let { value ->
                latest[source] = value
                latest.trimCache(maxEntries)
                return value
            }
            pending[key] ?: createLoad(key, source).also { pending[key] = it }
        }
        return sharedLoad.await()
    }

    private fun createLoad(key: K, source: S): Deferred<V> {
        lateinit var sharedLoad: Deferred<V>
        sharedLoad = scope.async(start = CoroutineStart.LAZY) {
            val value = loader(key)
            mutex.withLock {
                exact[key] = value
                if (latestRequestedKey[source] == key) {
                    latest[source] = value
                }
                exact.trimCache(maxEntries)
                latest.trimCache(maxEntries)
            }
            value
        }
        sharedLoad.invokeOnCompletion {
            scope.launch {
                mutex.withLock {
                    if (pending[key] === sharedLoad) pending.remove(key)
                }
            }
        }
        sharedLoad.start()
        return sharedLoad
    }
}

private fun <K, V> LinkedHashMap<K, V>.trimCache(maxEntries: Int) {
    while (size > maxEntries) remove(keys.first())
}
