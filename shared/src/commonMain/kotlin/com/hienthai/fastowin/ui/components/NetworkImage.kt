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
    var imageBitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        try {
            val bytes = httpClient.get(url).readRawBytes()
            val bitmap = withContext(Dispatchers.Default) {
                bytes.toImageBitmap()
            }
            imageBitmap = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            error = true
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

