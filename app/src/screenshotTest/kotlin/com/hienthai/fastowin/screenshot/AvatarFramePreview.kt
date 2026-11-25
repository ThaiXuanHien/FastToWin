package com.hienthai.fastowin.screenshot

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import java.io.File

/** Ảnh khung avatar lấy từ artwork/avatar-frames để ảnh chụp test hiện khung như app thật (không cần server). */
private val frameBitmaps = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

private fun loadFrame(frameId: String): androidx.compose.ui.graphics.ImageBitmap? = frameBitmaps.getOrPut(frameId) {
    val name = frameId.removePrefix("frame_").substringAfterLast("season_").let { id ->
        if (frameId.startsWith("season_")) frameId.substringAfterLast('_') else id
    }
    val file = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "artwork/avatar-frames/arcade_frame_$name.png") }
        .firstOrNull { it.isFile }
    file?.let { f -> f.inputStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
}

val screenshotFramePainter: @Composable (String) -> Painter? = { frameId ->
    remember(frameId) { loadFrame(frameId)?.let(::BitmapPainter) }
}
