package com.hienthai.fastowin.platform

internal const val AVATAR_UPLOAD_MAX_BYTES = 45 * 1024
internal const val AVATAR_UPLOAD_MAX_EDGE = 512

/** Bounds JPEG output even when reducing quality alone cannot fit a detailed image. */
internal fun prepareAvatarUpload(encode: (maxEdge: Int, quality: Double) -> ByteArray?): ByteArray? {
    var maxEdge = AVATAR_UPLOAD_MAX_EDGE
    var qualityPercent = 82
    repeat(10) {
        val candidate = encode(maxEdge, qualityPercent / 100.0) ?: return null
        if (candidate.isEmpty()) return null
        if (candidate.size <= AVATAR_UPLOAD_MAX_BYTES) return candidate
        if (qualityPercent > 42) {
            qualityPercent -= 10
        } else {
            maxEdge = (maxEdge * 0.82).toInt().coerceAtLeast(1)
        }
    }
    return null
}

/** Power-of-two sampling avoids allocating the full camera image on Android. */
internal fun avatarDecodeSampleSize(width: Int, height: Int): Int? {
    if (width <= 0 || height <= 0) return null
    val longestEdge = maxOf(width, height)
    var sample = 1
    while (longestEdge / sample > AVATAR_UPLOAD_MAX_EDGE) sample *= 2
    return sample
}

internal fun avatarScaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    val ratio = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
    return (width * ratio).toInt().coerceAtLeast(1) to
        (height * ratio).toInt().coerceAtLeast(1)
}
