package com.hienthai.fastowin.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
actual fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            onImageSelected(null)
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                
                // Downscale if too large to save DB space
                val maxSize = 512
                val ratio = minOf(
                    maxSize.toFloat() / originalBitmap.width,
                    maxSize.toFloat() / originalBitmap.height,
                    1.0f
                )
                
                val scaledBitmap = if (ratio < 1.0f) {
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * ratio).toInt(),
                        (originalBitmap.height * ratio).toInt(),
                        true
                    )
                } else originalBitmap
                
                val outputStream = ByteArrayOutputStream()
                // Compress to JPEG 80%
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                onImageSelected(outputStream.toByteArray())
            } ?: onImageSelected(null)
        } catch (e: Exception) {
            e.printStackTrace()
            onImageSelected(null)
        }
    }

    content {
        launcher.launch("image/*")
    }
}
