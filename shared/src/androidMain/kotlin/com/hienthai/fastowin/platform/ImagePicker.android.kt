package com.hienthai.fastowin.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
actual fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val currentCallback = rememberUpdatedState(onImageSelected)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            currentCallback.value(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { prepareAndroidAvatar(resolver, uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            currentCallback.value(bytes)
        }
    }

    content { launcher.launch("image/*") }
}

private fun prepareAndroidAvatar(resolver: ContentResolver, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val headerStream = resolver.openInputStream(uri) ?: return null
    headerStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    val sample = avatarDecodeSampleSize(bounds.outWidth, bounds.outHeight) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null
    try {
        return prepareAvatarUpload { maxEdge, quality ->
            val (width, height) = avatarScaledSize(bitmap.width, bitmap.height, maxEdge)
            val scaled = if (width != bitmap.width || height != bitmap.height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else bitmap
            try {
                ByteArrayOutputStream().use { output ->
                    if (scaled.compress(Bitmap.CompressFormat.JPEG, (quality * 100).toInt(), output)) {
                        output.toByteArray()
                    } else null
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        }
    } finally {
        bitmap.recycle()
    }
}
