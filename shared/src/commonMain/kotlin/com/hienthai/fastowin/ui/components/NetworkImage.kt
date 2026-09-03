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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val httpClient = HttpClient()

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
    var imageBitmap by remember(sourceKey) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        try {
            val bytes = httpClient.get(url).readRawBytes()
            val bitmap = withContext(Dispatchers.Default) {
                bytes.toImageBitmap()
            }
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
