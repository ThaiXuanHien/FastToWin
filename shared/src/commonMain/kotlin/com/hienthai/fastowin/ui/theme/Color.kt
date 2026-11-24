package com.hienthai.fastowin.ui.theme

import androidx.compose.ui.graphics.Color

/** Primitive color tokens for the Fast To Win 2D Arcade visual language. */
object ArcadePalette {
    val Blue50 = Color(0xFFF1F7FF)
    val Blue100 = Color(0xFFDCEAFF)
    val Blue300 = Color(0xFF86B5FF)
    val Blue500 = Color(0xFF347CFF)
    val Blue600 = Color(0xFF246BFD)
    val Blue700 = Color(0xFF1552D8)
    val Blue900 = Color(0xFF0A2C79)

    val Violet100 = Color(0xFFEDE5FF)
    val Violet400 = Color(0xFF9D7BFF)
    val Violet600 = Color(0xFF7048E8)
    val Violet900 = Color(0xFF32156F)

    val Coral100 = Color(0xFFFFE1E7)
    val Coral400 = Color(0xFFFF8294)
    val Coral600 = Color(0xFFFF4D67)
    val Coral800 = Color(0xFFB91F3C)

    val Gold100 = Color(0xFFFFF3BF)
    val Gold400 = Color(0xFFFFD84D)
    val Gold500 = Color(0xFFFFC928)
    val Gold800 = Color(0xFF704B00)
    val Silver400 = Color(0xFFC8D4EA)
    val Bronze400 = Color(0xFFE59655)

    val Mint100 = Color(0xFFD5FAEF)
    val Mint400 = Color(0xFF39DDB0)
    val Mint600 = Color(0xFF08A97D)
    val Mint900 = Color(0xFF064F3E)

    val Navy950 = Color(0xFF06132F)
    val Navy900 = Color(0xFF071A44)
    val Navy800 = Color(0xFF0D285D)
    val Navy700 = Color(0xFF153A7B)
    val Ink = Color(0xFF10203F)
    val MutedInk = Color(0xFF526581)

    val Cloud = Color(0xFFF4F8FF)
    val White = Color(0xFFFFFFFF)
    val OutlineLight = Color(0xFFB9CAE5)
    val OutlineDark = Color(0xFF5572A8)
}

/** Semantic accents that stay recognizable across light and dark surfaces. */
val ArcadeGold = ArcadePalette.Gold500
val ArcadeGem = ArcadePalette.Mint400
val ArcadeOpponent = ArcadePalette.Coral600
val ArcadeSuccess = ArcadePalette.Mint600

/**
 * Bảng màu A1 "Ngày nắng" (thiết kế đã chốt 24/11/2026, canvas bản 80).
 * Nền trời sáng, thẻ trắng viền navy dày, nút 3D có đế màu đậm.
 */
object A1Palette {
    val Line = Color(0xFF1C4F86)
    val Ink = Color(0xFF173B66)
    val Muted = Color(0xFF46658F)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFEAF3FF)
    val SurfaceAlt2 = Color(0xFFDBE9FB)
    val Highlight = Color(0xFFFFF3C4)

    val Sky1 = Color(0xFF4FC8FF)
    val Sky2 = Color(0xFF7AD8FF)
    val Sky3 = Color(0xFFC4F0FF)
    val Hill = Color(0xFF6FDC6A)
    val HillDark = Color(0xFF4FC64A)

    val Yellow1 = Color(0xFFFFD84A)
    val Yellow2 = Color(0xFFFFB300)
    val Yellow3 = Color(0xFFF59200)
    val YellowDeep = Color(0xFFA35200)
    val YellowBorder = Color(0xFF7A3D00)

    val Blue1 = Color(0xFF6AB8FF)
    val Blue2 = Color(0xFF2A7DE0)
    val BlueDeep = Color(0xFF0D3A7A)

    val Purple1 = Color(0xFFB48CFF)
    val Purple2 = Color(0xFF7A4DFF)
    val PurpleDeep = Color(0xFF3A1A8A)

    val Green1 = Color(0xFF8CF35C)
    val Green2 = Color(0xFF3CC226)
    val GreenDeep = Color(0xFF1F6B14)
    /** Chữ xanh trên nền trắng (đạt 4.5:1). */
    val GreenText = Color(0xFF1F7A14)

    val Red1 = Color(0xFFFF8A7A)
    val Red2 = Color(0xFFFF4F5A)
    val RedDeep = Color(0xFF8A1A22)
    /** Chữ đỏ trên nền trắng (đạt 4.5:1). */
    val RedText = Color(0xFFB3202B)

    val Gold = Color(0xFFFFC928)
    val Gem = Color(0xFF16A34A)
    val Scrim = Color(0xA60B2447)
}
