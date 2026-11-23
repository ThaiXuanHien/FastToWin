package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.AvatarFrameDefinition
import com.hienthai.fastowin.protocol.FRAME_CATALOG
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.server.admin.invalid
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.sql.Connection
import java.util.Base64
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

interface AvatarFrameSource {
    suspend fun avatarFrameDefinitions(): List<StoredAvatarFrame>
    suspend fun avatarFrameImage(id: String): ByteArray?
}

data class StoredAvatarFrame(val id: String, val nameVi: String, val nameEn: String, val version: Int) {
    fun definition() = AvatarFrameDefinition(id, nameVi, nameEn, "/api/avatar-frames/$id/image?v=$version")
}

@Serializable
data class ManagedAvatarFrame(val definition: AvatarFrameDefinition, val builtIn: Boolean, val version: Int)

@Serializable
data class SaveAvatarFrameRequest(
    val nameVi: String,
    val nameEn: String,
    val version: Int?,
    val reason: String,
    val pngBase64: String? = null
)

private val vietnamese = LocalizationService(AppLanguage.VIETNAMESE)
private val english = LocalizationService(AppLanguage.ENGLISH)

private fun builtInFrame(id: String, name: TextKey) = StoredAvatarFrame(id, vietnamese.text(name), english.text(name), 0)

private fun seasonalArtwork(id: String, tier: TextKey) = StoredAvatarFrame(
    id,
    vietnamese.text(TextKey.SeasonalFrame, mapOf("tier" to vietnamese.text(tier))),
    english.text(TextKey.SeasonalFrame, mapOf("tier" to english.text(tier))),
    0
)

internal val builtInAvatarFrames = (listOf(
    builtInFrame("frame_default", TextKey.BasicFrame),
    builtInFrame("frame_bronze", TextKey.BronzeFrame),
    builtInFrame("frame_silver", TextKey.SilverFrame),
    builtInFrame("frame_gold", TextKey.GoldFrame),
    builtInFrame("frame_perfect", TextKey.PerfectFrame),
    builtInFrame("frame_persistent", TextKey.PersistentFrameName),
    seasonalArtwork("frame_platinum", TextKey.RankPlatinum),
    seasonalArtwork("frame_master", TextKey.RankMaster)
) + FRAME_CATALOG.map { builtInFrame(it.id, TextKey.valueOf(it.nameKey)) }).associateBy(StoredAvatarFrame::id)

internal fun managedAvatarFrames(stored: List<StoredAvatarFrame>): List<ManagedAvatarFrame> {
    val overrides = stored.associateBy(StoredAvatarFrame::id)
    return (builtInAvatarFrames + overrides).values.map { frame ->
        ManagedAvatarFrame(frame.definition(), frame.id in builtInAvatarFrames, frame.version)
    }
}

internal val avatarFrameId = Regex("frame_[a-z0-9]+(?:_[a-z0-9]+)*")
private val seasonalFrameId = Regex("season_[0-9]+_(${RankedTier.entries.joinToString("|") { it.name.lowercase() }})")

internal fun isValidAvatarFrameId(id: String) = id.length <= 32 && avatarFrameId.matches(id)

internal fun avatarFrameArtworkId(id: String): String? = when {
    isValidAvatarFrameId(id) -> id
    id.length <= 32 -> seasonalFrameId.matchEntire(id)?.groupValues?.get(1)?.let { "frame_$it" }
    else -> null
}

internal fun builtInAvatarFrameImage(id: String): ByteArray? {
    if (id !in builtInAvatarFrames) return null
    val suffix = id.removePrefix("frame_")
    return StoredAvatarFrame::class.java.getResourceAsStream("/avatar-frames/arcade_frame_$suffix.png")?.use { it.readBytes() }
}

private const val MAX_PNG_BYTES = 256 * 1024
private const val MAX_PNG_DIMENSION = 512
internal const val MAX_FRAME_REQUEST_BYTES = 352 * 1024

internal fun normalizeAvatarFramePng(encoded: String): ByteArray {
    if (encoded.length > (MAX_PNG_BYTES + 2) / 3 * 4) invalid()
    val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { invalid() }
    if (bytes.size > MAX_PNG_BYTES) invalid()
    return try {
        // Inspect the dimensions before allocating a decoded bitmap, then strip ancillary PNG chunks.
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReadersByFormatName("png").next()
            try {
                reader.setInput(input, true, true)
                val width = reader.getWidth(0)
                if (width !in 64..MAX_PNG_DIMENSION || reader.getHeight(0) != width) invalid()
                val image = reader.read(0)
                if (!image.colorModel.hasAlpha()) invalid()
                val output = ByteArrayOutputStream()
                MemoryCacheImageOutputStream(output).use { stream ->
                    if (!ImageIO.write(image, "png", stream)) invalid()
                }
                output.toByteArray().also { if (it.size > MAX_PNG_BYTES) invalid() }
            } finally {
                reader.dispose()
            }
        }
    } catch (_: IOException) { invalid() }
}

internal fun Connection.loadAvatarFrameDefinitions(): List<StoredAvatarFrame> = createStatement().use { statement ->
    statement.executeQuery("SELECT id, name_vi, name_en, version FROM avatar_frames ORDER BY id").use { rows ->
        buildList {
            while (rows.next()) add(StoredAvatarFrame(rows.getString(1), rows.getString(2), rows.getString(3), rows.getInt(4)))
        }
    }
}

internal fun Connection.loadAvatarFrameImage(id: String): ByteArray? =
    prepareStatement("SELECT png FROM avatar_frames WHERE id = ?").use { statement ->
        statement.setString(1, id)
        statement.executeQuery().use { rows -> if (rows.next()) rows.getBytes(1) else null }
    }
