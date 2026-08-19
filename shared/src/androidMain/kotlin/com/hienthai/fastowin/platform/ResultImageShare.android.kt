package com.hienthai.fastowin.platform

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberResultImageSharer(): ResultImageSharer {
    val context = LocalContext.current
    return remember(context) { AndroidResultImageSharer(context) }
}

@Composable
actual fun rememberTextSharer(): TextSharer {
    val context = LocalContext.current
    return remember(context) {
        TextSharer { text, title ->
            runCatching {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                val chooser = Intent.createChooser(shareIntent, title)
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }
}

private class AndroidResultImageSharer(
    private val context: Context
) : ResultImageSharer {
    override fun share(content: ResultShareContent): Result<Unit> = runCatching {
        val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SHARE_FILE_PREFIX) }
            ?.forEach(File::delete)
        val imageFile = File(directory, "$SHARE_FILE_PREFIX${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { output ->
            check(renderResultImage(content).compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, content.caption)
            clipData = ClipData.newRawUri("Kết quả Fast To Win", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Chia sẻ kết quả")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

private fun renderResultImage(content: ResultShareContent): Bitmap {
    val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        bitmap.width.toFloat(),
        bitmap.height.toFloat(),
        intArrayOf(Color.rgb(9, 18, 38), Color.rgb(22, 47, 74), Color.rgb(11, 28, 48)),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    paint.shader = null

    paint.color = Color.rgb(41, 211, 158)
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(80f, 72f, 1000f, 184f), 56f, 56f, paint)
    drawCenteredText(canvas, paint, "FAST TO WIN", 540f, 147f, 46f, Color.rgb(7, 31, 36), true)

    drawCenteredText(canvas, paint, content.result, 540f, 300f, 70f, Color.WHITE, true)
    drawCenteredText(canvas, paint, "${content.gameMode} • ${content.matchType}", 540f, 365f, 34f, Color.rgb(182, 208, 224))

    paint.color = Color.argb(178, 18, 38, 61)
    canvas.drawRoundRect(RectF(70f, 430f, 1010f, 820f), 42f, 42f, paint)
    drawFittedCenteredText(canvas, paint, content.playerName, 280f, 520f, 330f, 42f, Color.WHITE, true)
    drawFittedCenteredText(canvas, paint, content.opponentName, 800f, 520f, 330f, 42f, Color.WHITE, true)
    drawCenteredText(canvas, paint, content.playerScore.toString(), 280f, 675f, 128f, Color.rgb(41, 211, 158), true)
    drawCenteredText(canvas, paint, content.opponentScore.toString(), 800f, 675f, 128f, Color.WHITE, true)
    drawCenteredText(canvas, paint, "–", 540f, 670f, 76f, Color.rgb(132, 155, 174), true)

    paint.color = Color.argb(155, 28, 54, 80)
    canvas.drawRoundRect(RectF(70f, 865f, 1010f, 1115f), 42f, 42f, paint)
    drawMetric(canvas, paint, "THỜI GIAN", content.duration, 230f)
    drawMetric(canvas, paint, "CHÍNH XÁC", content.accuracy, 540f)
    drawMetric(canvas, paint, "ELO", content.elo ?: "—", 850f)

    drawFittedCenteredText(
        canvas,
        paint,
        content.caption,
        540f,
        1205f,
        880f,
        27f,
        Color.rgb(182, 208, 224),
        false
    )
    drawCenteredText(canvas, paint, "Nhanh mắt • Nhanh tay • Chiến thắng", 540f, 1270f, 28f, Color.rgb(41, 211, 158), true)
    return bitmap
}

private fun drawMetric(canvas: Canvas, paint: Paint, label: String, value: String, x: Float) {
    drawCenteredText(canvas, paint, label, x, 950f, 26f, Color.rgb(148, 174, 193), true)
    drawCenteredText(canvas, paint, value, x, 1045f, 48f, Color.WHITE, true)
}

private fun drawCenteredText(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    size: Float,
    color: Int,
    bold: Boolean = false
) {
    paint.color = color
    paint.textSize = size
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    canvas.drawText(text, x, baseline, paint)
}

private fun drawFittedCenteredText(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    maxWidth: Float,
    preferredSize: Float,
    color: Int,
    bold: Boolean
) {
    var size = preferredSize
    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    paint.textSize = size
    while (paint.measureText(text) > maxWidth && size > 24f) {
        size -= 2f
        paint.textSize = size
    }
    drawCenteredText(canvas, paint, text, x, baseline, size, color, bold)
}

private const val SHARE_DIRECTORY = "shared_results"
private const val SHARE_FILE_PREFIX = "fast_to_win_result_"
