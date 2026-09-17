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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val httpClient = HttpClient()
private const val MAX_MEMORY_IMAGE_CACHE_ENTRIES = 96
private val imageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val imageCacheMutex = Mutex()
private val memoryImageCache = LinkedHashMap<String, ImageBitmap>()
private val pendingImageLoads = mutableMapOf<String, Deferred<ImageBitmap>>()

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
    var imageBitmap by remember(sourceKey) { mutableStateOf(memoryImageCache[sourceKey]) }

    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        try {
            val bitmap = loadNetworkImage(url, sourceKey)
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

private suspend fun loadNetworkImage(url: String, sourceKey: String): ImageBitmap {
    val pending = imageCacheMutex.withLock {
        pendingImageLoads[url] ?: imageLoadScope.async {
            val bytes = httpClient.get(url).readRawBytes()
            withContext(Dispatchers.Default) { bytes.toImageBitmap() }
        }.also { pendingImageLoads[url] = it }
    }
    return try {
        pending.await().also { bitmap ->
            imageCacheMutex.withLock {
                memoryImageCache[sourceKey] = bitmap
                while (memoryImageCache.size > MAX_MEMORY_IMAGE_CACHE_ENTRIES) {
                    memoryImageCache.remove(memoryImageCache.keys.first())
                }
            }
        }
    } finally {
        imageCacheMutex.withLock {
            if (pendingImageLoads[url] === pending) pendingImageLoads.remove(url)
        }
    }
}
