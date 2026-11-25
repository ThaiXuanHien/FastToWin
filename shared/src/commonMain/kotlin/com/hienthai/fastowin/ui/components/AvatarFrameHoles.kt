package com.hienthai.fastowin.ui.components

/**
 * Lỗ trong suốt của từng khung avatar, đo trên ảnh khung 280×280 (minX, minY, maxX, maxY).
 * Lỗ của nhiều khung không nằm chính giữa, nên avatar phải đặt theo đúng lỗ để không bị lệch
 * hay hở viền (đã dùng cho bộ sưu tập khung trong thiết kế A1).
 */
internal data class AvatarFrameHole(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    /** Vị trí và kích thước avatar theo tỉ lệ cạnh khung, nới thêm [pad] để avatar lấp kín mép lỗ. */
    fun left(pad: Float) = minX / ARTWORK - pad
    fun top(pad: Float) = minY / ARTWORK - pad
    fun width(pad: Float) = (maxX - minX) / ARTWORK + pad * 2
    fun height(pad: Float) = (maxY - minY) / ARTWORK + pad * 2

    private companion object { const val ARTWORK = 280f }
}

internal val avatarFrameHoles: Map<String, AvatarFrameHole> = mapOf(
    "frame_default" to AvatarFrameHole(36f, 35f, 242f, 234f),
    "frame_bronze" to AvatarFrameHole(49f, 56f, 230f, 227f),
    "frame_silver" to AvatarFrameHole(46f, 59f, 233f, 231f),
    "frame_gold" to AvatarFrameHole(51f, 58f, 228f, 229f),
    "frame_platinum" to AvatarFrameHole(60f, 64f, 220f, 215f),
    "frame_diamond" to AvatarFrameHole(61f, 70f, 218f, 220f),
    "frame_master" to AvatarFrameHole(40f, 62f, 239f, 239f),
    "frame_perfect" to AvatarFrameHole(37f, 43f, 242f, 234f),
    "frame_persistent" to AvatarFrameHole(39f, 48f, 240f, 236f),
    "frame_lightning" to AvatarFrameHole(51f, 54f, 228f, 221f),
    "frame_wildfire" to AvatarFrameHole(49f, 51f, 230f, 222f),
    "frame_warrior" to AvatarFrameHole(51f, 60f, 228f, 221f),
    "frame_veteran" to AvatarFrameHole(48f, 56f, 231f, 221f),
    "frame_challenger" to AvatarFrameHole(38f, 42f, 239f, 230f),
    "frame_glory" to AvatarFrameHole(53f, 69f, 226f, 225f),
    "frame_unyielding" to AvatarFrameHole(57f, 53f, 222f, 221f),
    "frame_legend" to AvatarFrameHole(40f, 46f, 239f, 222f),
    "frame_emperor" to AvatarFrameHole(53f, 58f, 226f, 213f),
    "frame_speed_shadow" to AvatarFrameHole(50f, 51f, 229f, 221f),
    "frame_champion" to AvatarFrameHole(44f, 49f, 235f, 228f),
    "frame_immortal" to AvatarFrameHole(49f, 57f, 230f, 228f),
    "frame_dragon_might" to AvatarFrameHole(53f, 62f, 226f, 217f),
    "frame_supreme" to AvatarFrameHole(51f, 68f, 228f, 218f),
    "frame_peerless" to AvatarFrameHole(56f, 57f, 223f, 220f),
)

/** Khung mùa giải ("season_..._diamond") dùng ảnh của khung bậc tương ứng. */
internal fun avatarFrameHole(frameId: String): AvatarFrameHole? {
    avatarFrameHoles[frameId]?.let { return it }
    if (!frameId.startsWith("season_")) return null
    val tier = frameId.substringAfterLast('_')
    return avatarFrameHoles["frame_$tier"]
}
